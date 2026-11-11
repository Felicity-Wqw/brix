package com.brix.campus.service.impl;

import com.brix.campus.ai.AiPrompts;
import com.brix.campus.ai.ImageAuditVerdict;
import com.brix.campus.config.WebMvcConfig;
import com.brix.campus.mapper.ImageAuditCacheMapper;
import com.brix.campus.model.ImageAuditCache;
import com.brix.campus.model.dto.AuditDecision;
import com.brix.campus.service.ImageAuditService;
import com.brix.campus.util.FileUtils;
import com.brix.campus.ws.WsMessage;
import com.brix.campus.ws.WsSessionManager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片审核实现：glm-4v-flash 单图单次调用，结论按内容 MD5 缓存。
 *
 * <p>性能三件套（与产品约定）：①上传时逐图异步审，发布时读缓存零调用；
 * ②审核前缩图 768px；③输出 JSON 且短小。失败/超时一律 FAILED 不阻塞业务。</p>
 */
@Service
public class ImageAuditServiceImpl implements ImageAuditService {

    private static final Logger log = LoggerFactory.getLogger(ImageAuditServiceImpl.class);

    /** 审核用图片最长边（px）：审核不需要原图分辨率 */
    private static final int MAX_SIDE = 768;
    /** 发布时同步补审的总时限（毫秒），超限的图记 FAILED */
    private static final long SYNC_DEADLINE_MS = 9000L;
    /** 模型名降级标识 */
    private static final String MODEL_UNAVAILABLE = "unavailable";

    private final ObjectProvider<ChatModel> visionChatModelProvider;
    private final ImageAuditCacheMapper cacheMapper;
    private final WebMvcConfig webMvcConfig;
    private final WsSessionManager wsSessionManager;
    private final ObjectMapper objectMapper;

    /** 免费模型并发有限：后台审核串行排队 */
    private final ExecutorService auditExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "image-audit");
        thread.setDaemon(true);
        return thread;
    });

    /** 免费档限流（429 伪装成 1113「余额不足」）：两次调用的最小间隔 */
    private static final long MIN_CALL_INTERVAL_MS = 10_000L;
    /** 上一次视觉调用时间（限流器） */
    private volatile long lastCallAt = 0L;

    public ImageAuditServiceImpl(@Qualifier("visionChatModel") ObjectProvider<ChatModel> visionChatModelProvider,
                                 ImageAuditCacheMapper cacheMapper,
                                 WebMvcConfig webMvcConfig,
                                 WsSessionManager wsSessionManager,
                                 ObjectMapper objectMapper) {
        this.visionChatModelProvider = visionChatModelProvider;
        this.cacheMapper = cacheMapper;
        this.webMvcConfig = webMvcConfig;
        this.wsSessionManager = wsSessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageAuditOutcome auditOnUpload(Long userId, String url) {
        String md5 = md5Of(url);
        ImageAuditCache cached = cacheMapper.selectOne(Wrappers.<ImageAuditCache>lambdaQuery()
                .eq(ImageAuditCache::getImageMd5, md5));
        if (cached != null && !ImageAuditCache.STATUS_PENDING.equals(cached.getStatus())
                && !ImageAuditCache.STATUS_FAILED.equals(cached.getStatus())) {
            return new ImageAuditOutcome(md5, cached.getStatus(), cached.getReason());
        }
        if (cached == null) {
            ImageAuditCache row = new ImageAuditCache();
            row.setImageMd5(md5);
            row.setStatus(ImageAuditCache.STATUS_PENDING);
            try {
                cacheMapper.insert(row);
            } catch (Exception e) {
                // 并发上传同一张图：唯一键冲突说明另一线程已在审，直接返回 PENDING
                log.info("图片审核缓存并发登记（{}）：{}", md5, e.getMessage());
                return new ImageAuditOutcome(md5, ImageAuditCache.STATUS_PENDING, null);
            }
        } else {
            // FAILED 的历史行复位为 PENDING，重新审核
            cached.setStatus(ImageAuditCache.STATUS_PENDING);
            cacheMapper.updateById(cached);
        }

        Long cacheId = cached == null ? null : cached.getId();
        Long finalUserId = userId;
        auditExecutor.submit(() -> auditInBackground(cacheId, finalUserId, md5, url));
        return new ImageAuditOutcome(md5, ImageAuditCache.STATUS_PENDING, null);
    }

    @Override
    public List<AuditDecision.ImageFinding> findingsFor(List<String> imageUrls) {
        ChatModel visionModel = visionChatModelProvider.getIfAvailable();
        if (visionModel == null) {
            // 未配置视觉模型：返回 null 表示「图片闸未启用」，由审核编排层记 UNAVAILABLE
            return null;
        }
        long deadline = System.currentTimeMillis() + SYNC_DEADLINE_MS;
        List<AuditDecision.ImageFinding> findings = new ArrayList<>(imageUrls.size());
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            int index = i + 1;
            if (System.currentTimeMillis() > deadline) {
                findings.add(new AuditDecision.ImageFinding(index, url,
                        AuditDecision.ImageFinding.FAILED, "图片审核超时，本次未执行"));
                continue;
            }
            findings.add(auditSingle(visionModel, url, index));
        }
        return findings;
    }

    /**
     * 后台异步审核：写缓存 + WS 推送结论给上传者。
     */
    private void auditInBackground(Long cacheId, Long userId, String md5, String url) {
        ChatModel visionModel = visionChatModelProvider.getIfAvailable();
        AuditDecision.ImageFinding finding;
        if (visionModel == null) {
            finding = new AuditDecision.ImageFinding(0, url,
                    AuditDecision.ImageFinding.FAILED, "未配置视觉审核模型");
        } else {
            // 后台路径允许对免费档限流做一次重试
            finding = auditSingle(visionModel, url, 0, true);
        }
        updateCache(cacheId, md5, finding, visionModel);
        pushResult(userId, md5, finding);
    }

    /**
     * 单图审核：读文件 → 缩图 → 视觉模型 → JSON 解析清洗。异常一律 FAILED，不抛出。
     *
     * <p>智谱免费档限流时返回 429（伪装成 1113「余额不足」），后台路径允许重试一次。</p>
     */
    private AuditDecision.ImageFinding auditSingle(ChatModel visionModel, String url, int index) {
        return auditSingle(visionModel, url, index, false);
    }

    private AuditDecision.ImageFinding auditSingle(ChatModel visionModel, String url, int index,
                                                   boolean allowRetry) {
        long start = System.currentTimeMillis();
        try {
            byte[] raw = Files.readAllBytes(diskPath(url));
            String md5 = DigestUtils.md5DigestAsHex(raw);
            ImageAuditCache cached = cacheMapper.selectOne(Wrappers.<ImageAuditCache>lambdaQuery()
                    .eq(ImageAuditCache::getImageMd5, md5));
            if (cached != null && !ImageAuditCache.STATUS_PENDING.equals(cached.getStatus())
                    && !ImageAuditCache.STATUS_FAILED.equals(cached.getStatus())) {
                return new AuditDecision.ImageFinding(index, url, cached.getStatus(), cached.getReason());
            }

            byte[] reviewBytes = downscale(raw);
            String base64 = Base64.getEncoder().encodeToString(reviewBytes);
            UserMessage userMessage = UserMessage.from(
                    TextContent.from(AiPrompts.imageAuditUserPrompt(null)),
                    ImageContent.from(base64, "image/jpeg"));
            throttle();
            String answer = visionModel.chat(List.of(SystemMessage.from(AiPrompts.IMAGE_AUDIT_SYSTEM), userMessage))
                    .aiMessage().text();

            ImageAuditVerdict verdict = objectMapper.readValue(extractJson(answer), ImageAuditVerdict.class);
            String normalized = normalizeVerdict(verdict.getVerdict());
            long cost = System.currentTimeMillis() - start;
            log.info("图片审核完成：url={}，verdict={}，cost={}ms", url, normalized, cost);
            return new AuditDecision.ImageFinding(index, url, normalized,
                    verdict.getReason() == null ? "" : verdict.getReason().trim());
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage());
            boolean rateLimited = message.contains("1113") || message.contains("429")
                    || message.toLowerCase().contains("rate");
            if (allowRetry && rateLimited) {
                log.info("视觉模型限流（url={}），10s 后重试一次", url);
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return auditSingle(visionModel, url, index, false);
            }
            log.warn("图片审核失败（url={}）：{}", url, message);
            return new AuditDecision.ImageFinding(index, url,
                    AuditDecision.ImageFinding.FAILED, "图片审核调用失败（超时或网络异常）");
        }
    }

    /**
     * 免费档限流保护：两次视觉调用间隔不小于 {@link #MIN_CALL_INTERVAL_MS}。
     */
    private void throttle() {
        long wait = lastCallAt + MIN_CALL_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    /**
     * 判定值清洗：模型输出不认识时按 UNCERTAIN 转人工，绝不误杀。
     */
    private String normalizeVerdict(String raw) {
        if (raw == null) {
            return AuditDecision.ImageFinding.UNCERTAIN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "PASS" -> AuditDecision.ImageFinding.PASS;
            case "REJECT" -> AuditDecision.ImageFinding.REJECT;
            default -> AuditDecision.ImageFinding.UNCERTAIN;
        };
    }

    /**
     * 从模型回复中提取 JSON：glm-4v-flash 即使被要求「只输出 JSON」也可能包一层
     * ```json 代码围栏，取第一个 { 到最后一个 } 之间的内容兜底。
     */
    private String extractJson(String answer) {
        if (answer == null) {
            throw new IllegalStateException("模型返回为空");
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("模型返回不含 JSON：" + trim(answer, 120));
        }
        return answer.substring(start, end + 1);
    }

    private void updateCache(Long cacheId, String md5, AuditDecision.ImageFinding finding, ChatModel visionModel) {
        try {
            ImageAuditCache row = cacheId == null
                    ? cacheMapper.selectOne(Wrappers.<ImageAuditCache>lambdaQuery()
                            .eq(ImageAuditCache::getImageMd5, md5))
                    : cacheMapper.selectById(cacheId);
            if (row == null) {
                return;
            }
            row.setStatus(finding.verdict());
            row.setReason(trim(finding.reason(), 480));
            row.setModel(visionModel == null ? MODEL_UNAVAILABLE : "vision");
            row.setAuditedAt(LocalDateTime.now());
            cacheMapper.updateById(row);
        } catch (Exception e) {
            log.warn("图片审核缓存回写失败（{}）：{}", md5, e.getMessage());
        }
    }

    private void pushResult(Long userId, String md5, AuditDecision.ImageFinding finding) {
        try {
            if (userId == null || !wsSessionManager.isOnline(userId)) {
                return;
            }
            WsMessage message = new WsMessage();
            message.setType("IMAGE_AUDIT");
            message.setToUserId(String.valueOf(userId));
            message.setItemId(md5);
            message.setContent(objectMapper.writeValueAsString(java.util.Map.of(
                    "md5", md5,
                    "status", finding.verdict(),
                    "reason", finding.reason() == null ? "" : finding.reason())));
            wsSessionManager.sendTo(userId, message);
        } catch (Exception e) {
            log.warn("图片审核结果 WS 推送失败（{}）：{}", md5, e.getMessage());
        }
    }

    /**
     * 缩图：最长边压到 768px，透明 PNG 铺白底后转 JPEG（视觉模型输入更小更快）。
     */
    private byte[] downscale(byte[] raw) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
        if (source == null) {
            throw new IOException("无法解码图片");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0d, (double) MAX_SIDE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, java.awt.Color.WHITE, null);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.dispose();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(target, "jpg", buffer);
        return buffer.toByteArray();
    }

    private String md5Of(String url) {
        try {
            return DigestUtils.md5DigestAsHex(Files.readAllBytes(diskPath(url)));
        } catch (IOException e) {
            throw new IllegalStateException("读取上传图片失败：" + url, e);
        }
    }

    private Path diskPath(String url) {
        return FileUtils.toDiskPath(webMvcConfig.resolveUploadRoot(), url);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
