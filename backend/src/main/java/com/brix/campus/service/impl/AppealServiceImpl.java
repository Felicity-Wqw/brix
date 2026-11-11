package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.ai.AppealReviewAgent;
import com.brix.campus.ai.AppealReviewResult;
import com.brix.campus.ai.AiPrompts;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.AppealMapper;
import com.brix.campus.mapper.ItemAuditLogMapper;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Appeal;
import com.brix.campus.model.Item;
import com.brix.campus.model.ItemAuditLog;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.AppealCreateDTO;
import com.brix.campus.model.dto.AppealHandleDTO;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.vo.AppealVO;
import com.brix.campus.service.AppealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品申诉服务实现。
 *
 * <p>可申诉口径：商品 {@code REJECTED}，或最近一条审核日志为运营处置下架
 * （{@code OFFLINE}）且当前 {@code OFF_SHELF}——卖家主动下架不可申诉。
 * 改判（OVERTURNED）时商品恢复在售。AI 复核失败静默返回 null 建议。</p>
 */
@Service
public class AppealServiceImpl implements AppealService {

    private static final Logger log = LoggerFactory.getLogger(AppealServiceImpl.class);

    private final AppealMapper appealMapper;
    private final ItemMapper itemMapper;
    private final ItemAuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AppealReviewAgent> reviewAgentProvider;

    public AppealServiceImpl(AppealMapper appealMapper, ItemMapper itemMapper,
                             ItemAuditLogMapper auditLogMapper, UserMapper userMapper,
                             ObjectMapper objectMapper,
                             ObjectProvider<AppealReviewAgent> reviewAgentProvider) {
        this.appealMapper = appealMapper;
        this.itemMapper = itemMapper;
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.reviewAgentProvider = reviewAgentProvider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppealVO create(Long userId, AppealCreateDTO dto) {
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw BusinessException.notFound("商品不存在");
        }
        if (!item.getSellerId().equals(userId)) {
            throw BusinessException.forbidden("只有卖家本人可以申诉");
        }
        if (!isAppealable(item)) {
            throw BusinessException.param("当前商品状态不支持申诉（仅审核拒绝或被处置下架可申诉）");
        }
        Long pending = appealMapper.selectCount(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getItemId, item.getId())
                .eq(Appeal::getStatus, Appeal.PENDING));
        if (pending != null && pending > 0) {
            throw BusinessException.param("该商品已有待处理的申诉，请耐心等待审核员处理");
        }
        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setItemId(item.getId());
        appeal.setReason(dto.getReason().trim());
        appeal.setStatus(Appeal.PENDING);
        appealMapper.insert(appeal);
        return toVO(appealMapper.selectById(appeal.getId()));
    }

    @Override
    public IPage<AppealVO> pageMine(Long userId, long page, long size) {
        IPage<Appeal> result = appealMapper.selectPage(new Page<>(page, size),
                Wrappers.<Appeal>lambdaQuery()
                        .eq(Appeal::getUserId, userId)
                        .orderByDesc(Appeal::getId));
        return result.convert(this::toVO);
    }

    @Override
    public AppealVO latestByItem(Long itemId) {
        Appeal appeal = appealMapper.selectOne(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getItemId, itemId)
                .orderByDesc(Appeal::getId)
                .last("LIMIT 1"));
        return appeal == null ? null : toVO(appeal);
    }

    @Override
    public IPage<AppealVO> pageForAdmin(String status, long page, long size) {
        IPage<Appeal> result = appealMapper.selectPage(new Page<>(page, size),
                Wrappers.<Appeal>lambdaQuery()
                        .eq(status != null && !status.isBlank(), Appeal::getStatus, status)
                        .orderByAsc(Appeal::getStatus)
                        .orderByDesc(Appeal::getId));
        return result.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long adminId, Long appealId, AppealHandleDTO dto) {
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw BusinessException.notFound("申诉不存在");
        }
        if (!Appeal.PENDING.equals(appeal.getStatus())) {
            throw BusinessException.param("该申诉已处理完毕");
        }
        boolean overturn = "OVERTURNED".equals(dto.getAction());
        if (!overturn && !"MAINTAINED".equals(dto.getAction())) {
            throw BusinessException.param("处理动作不合法");
        }
        int rows = appealMapper.update(null, Wrappers.<Appeal>lambdaUpdate()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getStatus, Appeal.PENDING)
                .set(Appeal::getStatus, dto.getAction())
                .set(Appeal::getReply, dto.getReply().trim())
                .set(Appeal::getHandlerId, adminId)
                .set(Appeal::getHandledAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("申诉状态已变化，请刷新后重试");
        }
        if (overturn) {
            // 改判：商品恢复在售（限仍处于被拒/被下架状态，避免覆盖卖家的主动操作）
            Item item = itemMapper.selectById(appeal.getItemId());
            if (item != null && (ItemStatusEnum.REJECTED.getCode().equals(item.getStatus())
                    || ItemStatusEnum.OFF_SHELF.getCode().equals(item.getStatus()))) {
                itemMapper.update(null, Wrappers.<Item>lambdaUpdate()
                        .eq(Item::getId, item.getId())
                        .in(Item::getStatus, ItemStatusEnum.REJECTED.getCode(), ItemStatusEnum.OFF_SHELF.getCode())
                        .set(Item::getStatus, ItemStatusEnum.ON_SALE.getCode()));
            }
        }
        notifyAppealer(appeal, overturn, dto.getReply().trim());
    }

    @Override
    public String aiReview(Long appealId) {
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw BusinessException.notFound("申诉不存在");
        }
        AppealReviewAgent agent = reviewAgentProvider.getIfAvailable();
        if (agent == null) {
            throw BusinessException.param("AI 未配置，无法生成复核建议");
        }
        Item item = itemMapper.selectById(appeal.getItemId());
        ItemAuditLog latestLog = auditLogMapper.selectOne(Wrappers.<ItemAuditLog>lambdaQuery()
                .eq(ItemAuditLog::getItemId, appeal.getItemId())
                .orderByDesc(ItemAuditLog::getId)
                .last("LIMIT 1"));
        String originalDecision = buildOriginalDecision(latestLog, item);
        String itemContent = item == null ? "（商品已删除）"
                : "标题：" + safe(item.getTitle()) + "\n描述：" + safe(item.getDescription());
        try {
            AppealReviewResult result = agent.review(
                    AiPrompts.appealReviewUserPrompt(originalDecision, itemContent, appeal.getReason()));
            if (result == null || result.getSuggestion() == null
                    || (!"MAINTAIN".equals(result.getSuggestion())
                        && !"OVERTURN".equals(result.getSuggestion())
                        && !"UNCERTAIN".equals(result.getSuggestion()))) {
                throw BusinessException.param("AI 未返回有效复核建议，请人工对照材料处理");
            }
            String json = objectMapper.writeValueAsString(result);
            appealMapper.update(null, Wrappers.<Appeal>lambdaUpdate()
                    .eq(Appeal::getId, appealId)
                    .set(Appeal::getAiReview, json));
            log.info("申诉 AI 复核已生成：appealId={}，suggestion={}", appealId, result.getSuggestion());
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("申诉 AI 复核失败：appealId={}，{}", appealId, e.getMessage());
            throw BusinessException.param("AI 复核生成失败，请人工对照材料处理");
        }
    }

    /** 商品是否可申诉：REJECTED，或被处置下架（最近日志 OFFLINE）的 OFF_SHELF */
    private boolean isAppealable(Item item) {
        if (ItemStatusEnum.REJECTED.getCode().equals(item.getStatus())) {
            return true;
        }
        if (!ItemStatusEnum.OFF_SHELF.getCode().equals(item.getStatus())) {
            return false;
        }
        ItemAuditLog latestLog = auditLogMapper.selectOne(Wrappers.<ItemAuditLog>lambdaQuery()
                .eq(ItemAuditLog::getItemId, item.getId())
                .orderByDesc(ItemAuditLog::getId)
                .last("LIMIT 1"));
        return latestLog != null && "OFFLINE".equals(latestLog.getDecisionStatus());
    }

    private String buildOriginalDecision(ItemAuditLog latestLog, Item item) {
        if (latestLog == null) {
            return "（无审核日志）当前商品状态：" + (item == null ? "未知" : item.getStatus());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("gate1=").append(safe(latestLog.getGate1Result()));
        if (notBlank(latestLog.getGate1Word())) {
            sb.append("（命中词：").append(latestLog.getGate1Word()).append("）");
        }
        sb.append("，gate2=").append(safe(latestLog.getGate2Result()));
        if (notBlank(latestLog.getGate2Reason())) {
            sb.append("，AI 说明：").append(latestLog.getGate2Reason());
        }
        sb.append("，风险级=").append(safe(latestLog.getRiskLevel()));
        sb.append("，处置=").append(safe(latestLog.getDecisionStatus()));
        if (notBlank(latestLog.getDecisionNote())) {
            sb.append("（").append(latestLog.getDecisionNote()).append("）");
        }
        return sb.toString();
    }

    private void notifyAppealer(Appeal appeal, boolean overturn, String reply) {
        try {
            User user = userMapper.selectById(appeal.getUserId());
            if (user == null) {
                return;
            }
            log.info("申诉处理通知：appealId={}，user={}，结果={}，回复={}",
                    appeal.getId(), user.getUsername(), overturn ? "改判恢复在售" : "维持原判", reply);
        } catch (Exception e) {
            log.warn("申诉处理通知失败：{}", e.getMessage());
        }
    }

    private AppealVO toVO(Appeal appeal) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setItemId(appeal.getItemId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setStatusDesc(switch (appeal.getStatus()) {
            case Appeal.PENDING -> "待处理";
            case Appeal.MAINTAINED -> "维持原判";
            case Appeal.OVERTURNED -> "已改判恢复在售";
            default -> appeal.getStatus();
        });
        vo.setAiReview(appeal.getAiReview());
        vo.setReply(appeal.getReply());
        vo.setHandledAt(appeal.getHandledAt());
        vo.setCreatedAt(appeal.getCreatedAt());
        User user = userMapper.selectById(appeal.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        Item item = itemMapper.selectById(appeal.getItemId());
        if (item != null) {
            vo.setItemTitle(item.getTitle());
        }
        return vo;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }
}
