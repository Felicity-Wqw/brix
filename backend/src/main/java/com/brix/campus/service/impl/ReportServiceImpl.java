package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.ReportMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Item;
import com.brix.campus.model.Report;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.ReportCreateDTO;
import com.brix.campus.model.dto.ReportHandleDTO;
import com.brix.campus.model.enums.HandleActionEnum;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.ReportReasonEnum;
import com.brix.campus.model.enums.ReportStatusEnum;
import com.brix.campus.model.query.ReportQuery;
import com.brix.campus.model.vo.ReportVO;
import com.brix.campus.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 举报服务实现。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;

    public ReportServiceImpl(ReportMapper reportMapper, ItemMapper itemMapper, UserMapper userMapper) {
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO create(Long userId, Long itemId, ReportCreateDTO dto) {
        if (!ReportReasonEnum.isValid(dto.getReason())) {
            throw BusinessException.param("举报原因不合法");
        }
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw BusinessException.notFound("商品不存在");
        }
        if (item.getSellerId().equals(userId)) {
            throw BusinessException.param("不能举报自己的商品");
        }
        Long pendingCount = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getItemId, itemId)
                .eq(Report::getReporterId, userId)
                .eq(Report::getStatus, ReportStatusEnum.PENDING.getCode()));
        if (pendingCount != null && pendingCount > 0) {
            throw BusinessException.param("你已举报过该商品，请等待处理");
        }

        Report report = new Report();
        report.setItemId(itemId);
        report.setReporterId(userId);
        report.setReason(dto.getReason());
        report.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        report.setStatus(ReportStatusEnum.PENDING.getCode());
        reportMapper.insert(report);

        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setItemId(itemId);
        vo.setItemTitle(item.getTitle());
        vo.setItemCover(item.getCoverImage());
        vo.setReporterId(userId);
        vo.setReason(report.getReason());
        vo.setReasonDesc(ReportReasonEnum.descOf(report.getReason()));
        vo.setDescription(report.getDescription());
        vo.setStatus(report.getStatus());
        vo.setStatusDesc(ReportStatusEnum.descOf(report.getStatus()));
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }

    // ==================== 后台审核员 ====================

    @Override
    public IPage<ReportVO> pageForAdmin(ReportQuery query) {
        Page<Report> page = new Page<>(query.getCurrentPage() == null ? 1 : query.getCurrentPage(),
                query.getPageSize() == null ? 10 : query.getPageSize());
        LambdaQueryWrapper<Report> wrapper = Wrappers.lambdaQuery();
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Report::getStatus, query.getStatus());
        }
        if (query.getReason() != null && !query.getReason().isBlank()) {
            wrapper.eq(Report::getReason, query.getReason());
        }
        // 待处理的排最前，其次按时间倒序：审核员打开页面即可处理最新风险
        wrapper.orderByAsc(Report::getStatus).orderByDesc(Report::getCreatedAt);
        return reportMapper.selectPage(page, wrapper).convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long handlerId, Long reportId, ReportHandleDTO dto) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw BusinessException.notFound("举报记录不存在");
        }
        if (ReportStatusEnum.HANDLED.getCode().equals(report.getStatus())) {
            throw BusinessException.param("该举报已处理，不能重复处置");
        }
        if (!HandleActionEnum.isValid(dto.getAction())) {
            throw BusinessException.param("处理动作不合法，仅支持 OFFLINE / REJECT");
        }

        // 举报成立 → 商品强制下架；不成立 → 不动商品。
        // 这里刻意只做「下架」而不做「删除」：留证，且卖家可申诉后重新上架。
        if (HandleActionEnum.OFFLINE.getCode().equals(dto.getAction())) {
            Item item = itemMapper.selectById(report.getItemId());
            if (item != null && ItemStatusEnum.ON_SALE.getCode().equals(item.getStatus())) {
                item.setStatus(ItemStatusEnum.OFF_SHELF.getCode());
                item.setAuditSource("MANUAL");
                item.setAuditBy(handlerId);
                item.setAuditAt(LocalDateTime.now());
                item.setAuditReason("举报成立，强制下架：" + blankTo(dto.getRemark(), "违反平台规则"));
                itemMapper.updateById(item);
            }
        }

        report.setStatus(ReportStatusEnum.HANDLED.getCode());
        report.setHandleAction(dto.getAction());
        report.setHandlerId(handlerId);
        report.setHandleRemark(dto.getRemark() == null ? null : dto.getRemark().trim());
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    @Override
    public long countPending() {
        Long count = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getStatus, ReportStatusEnum.PENDING.getCode()));
        return count == null ? 0L : count;
    }

    /**
     * 实体转 VO（补齐商品、举报人、处理人三处展示信息）。
     *
     * @param report 举报实体
     * @return VO
     */
    private ReportVO toVO(Report report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setItemId(report.getItemId());
        vo.setReporterId(report.getReporterId());
        vo.setReason(report.getReason());
        vo.setReasonDesc(ReportReasonEnum.descOf(report.getReason()));
        vo.setDescription(report.getDescription());
        vo.setStatus(report.getStatus());
        vo.setStatusDesc(ReportStatusEnum.descOf(report.getStatus()));
        vo.setHandleAction(report.getHandleAction());
        vo.setHandleActionDesc(report.getHandleAction() == null
                ? null : HandleActionEnum.descOf(report.getHandleAction()));
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleRemark(report.getHandleRemark());
        vo.setHandledAt(report.getHandledAt());
        vo.setCreatedAt(report.getCreatedAt());

        Item item = itemMapper.selectById(report.getItemId());
        if (item != null) {
            vo.setItemTitle(item.getTitle());
            vo.setItemCover(item.getCoverImage());
        }
        User reporter = userMapper.selectById(report.getReporterId());
        if (reporter != null) {
            vo.setReporterNickname(reporter.getNickname());
        }
        if (report.getHandlerId() != null) {
            User handler = userMapper.selectById(report.getHandlerId());
            if (handler != null) {
                vo.setHandlerNickname(handler.getNickname());
            }
        }
        return vo;
    }

    /**
     * 空白则取默认值。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 非空值
     */
    private static String blankTo(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
