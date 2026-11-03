package com.brix.campus.service;

import com.brix.campus.model.dto.ReportCreateDTO;
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
}
