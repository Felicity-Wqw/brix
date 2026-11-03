package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ItemMapper;
import com.brix.campus.mapper.ReportMapper;
import com.brix.campus.model.Item;
import com.brix.campus.model.Report;
import com.brix.campus.model.dto.ReportCreateDTO;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.ReportReasonEnum;
import com.brix.campus.model.enums.ReportStatusEnum;
import com.brix.campus.model.vo.ReportVO;
import com.brix.campus.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报服务实现。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final ItemMapper itemMapper;

    public ReportServiceImpl(ReportMapper reportMapper, ItemMapper itemMapper) {
        this.reportMapper = reportMapper;
        this.itemMapper = itemMapper;
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
}
