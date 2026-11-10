package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.ReportCreateDTO;
import com.brix.campus.model.dto.ReportHandleDTO;
import com.brix.campus.model.query.ReportQuery;
import com.brix.campus.model.vo.ReportVO;

/**
 * 举报服务（H01）：用户提交举报。
 */
public interface ReportService {

    /**
     * 提交举报。
     *
     * @param userId 举报人 ID
     * @param itemId 商品 ID
     * @param dto    入参
     * @return 举报记录
     */
    ReportVO create(Long userId, Long itemId, ReportCreateDTO dto);

    // ========================================================================
    // 以下为后台审核员专用（权限由 SecurityConfig 的 /admin/reports/** 强制）
    // ========================================================================

    /**
     * 后台举报队列（分页，按举报时间倒序）。
     *
     * @param query 状态 / 原因 / 分页
     * @return 分页结果
     */
    IPage<ReportVO> pageForAdmin(ReportQuery query);

    /**
     * 处理举报：成立则下架商品，不成立则仅关闭举报。
     *
     * @param handlerId 审核员 ID
     * @param reportId  举报 ID
     * @param dto       处理动作与备注
     */
    void handle(Long handlerId, Long reportId, ReportHandleDTO dto);

    /**
     * 待处理举报数（后台侧栏角标）。
     *
     * @return 待处理数量
     */
    long countPending();
}
