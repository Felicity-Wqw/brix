package com.brix.campus.service.impl;

import com.brix.campus.common.BusinessException;
import com.brix.campus.config.WebMvcConfig;
import com.brix.campus.model.vo.UploadVO;
import com.brix.campus.service.FileStorageService;
import com.brix.campus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * 本地文件存储实现。
 *
 * <p>校验顺序（架构 5.9）：扩展名白名单 → Content-Type 白名单 → 大小 ≤ 5MB → 落盘。</p>
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    /** 允许的业务类型 */
    private static final Set<String> BIZ_TYPES = Set.of("item", "avatar", "review");

    private final WebMvcConfig webMvcConfig;
    private final long maxSize;

    public FileStorageServiceImpl(WebMvcConfig webMvcConfig,
                                  @Value("${brix.upload.max-size:5242880}") long maxSize) {
        this.webMvcConfig = webMvcConfig;
        this.maxSize = maxSize;
    }

    @Override
    public UploadVO store(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.param("请选择要上传的图片");
        }
        String type = (bizType == null || bizType.isBlank()) ? "item" : bizType.trim();
        if (!BIZ_TYPES.contains(type)) {
            throw BusinessException.param("不支持的业务类型");
        }

        String extension = FileUtils.extensionOf(file.getOriginalFilename());
        if (!FileUtils.isAllowedExtension(extension)) {
            throw BusinessException.param("仅支持 jpg/jpeg/png/webp 格式");
        }

        String contentType = file.getContentType();
        if (contentType != null && !FileUtils.ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.param("仅支持 jpg/jpeg/png/webp 格式");
        }

        if (file.getSize() > maxSize) {
            throw BusinessException.param("图片大小不能超过 5MB");
        }

        // 文件头校验：扩展名与 Content-Type 都由客户端提供，可伪造，必须按真实字节判断
        String detected = FileUtils.detectImageExtension(readHead(file));
        if (detected == null || !FileUtils.matchesExtension(detected, extension)) {
            throw BusinessException.param("图片内容与格式不符，仅支持真实的 jpg/jpeg/png/webp 图片");
        }

        String relativeUrl = FileUtils.buildRelativeUrl(type, extension);
        Path target = FileUtils.toDiskPath(webMvcConfig.resolveUploadRoot(), relativeUrl);
        try {
            FileUtils.createParentDirs(target);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("保存上传文件失败：{}", target, e);
            throw BusinessException.param("图片上传失败，请重试");
        }

        UploadVO vo = new UploadVO();
        vo.setUrl(relativeUrl);
        vo.setSize(file.getSize());
        vo.setContentType(contentType);
        return vo;
    }

    /**
     * 读取文件前 12 字节（足够识别 jpg/png/webp 魔数）。
     *
     * @param file 上传文件
     * @return 头部字节（不足 12 字节时返回实际长度）
     */
    private static byte[] readHead(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            byte[] buffer = new byte[12];
            int read = inputStream.readNBytes(buffer, 0, buffer.length);
            if (read < buffer.length) {
                byte[] actual = new byte[read];
                System.arraycopy(buffer, 0, actual, 0, read);
                return actual;
            }
            return buffer;
        } catch (IOException e) {
            log.error("读取上传文件头失败：{}", file.getOriginalFilename(), e);
            throw BusinessException.param("图片读取失败，请重试");
        }
    }

    @Override
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank() || !url.startsWith("/uploads/")) {
            return;
        }
        Path target = FileUtils.toDiskPath(webMvcConfig.resolveUploadRoot(), url);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除物理文件失败：{}", target, e);
        }
    }
}
