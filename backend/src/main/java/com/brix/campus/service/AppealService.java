package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.AppealCreateDTO;
import com.brix.campus.model.dto.AppealHandleDTO;
import com.brix.campus.model.vo.AppealVO;

/**
 * 商品申诉服务。
 *
 * <p>范围：仅受理「平台对商品的处置」争议——审核拒绝（REJECTED）与
 * 运营处置下架（audit OFFLINE）；卖家主动下架、交易纠纷不在其列
 * （后者由售后与小法庭兜底）。处理人为审核员（REVIEWER）。</p>
 */
public interface AppealService {

    /**
     * 提交申诉（卖家本人；同一商品同时只允许一笔待处理申诉）。
     *
     * @param userId 申诉人
     * @param dto    入参
     * @return 申诉 VO
     */
    AppealVO create(Long userId, AppealCreateDTO dto);

    /**
     * 我的申诉分页。
     */
    IPage<AppealVO> pageMine(Long userId, long page, long size);

    /**
     * 指定商品的最新一笔申诉（商品详情页展示申诉进度；无则 null）。
     */
    AppealVO latestByItem(Long itemId);

    /**
     * 管理端：申诉分页（可按状态筛选）。
     */
    IPage<AppealVO> pageForAdmin(String status, long page, long size);

    /**
     * 管理端处理申诉：MAINTAINED 维持原判 / OVERTURNED 改判（商品恢复在售）。
     *
     * @param adminId 审核员 ID
     * @param appealId 申诉 ID
     * @param dto     处理动作与回复
     */
    void handle(Long adminId, Long appealId, AppealHandleDTO dto);

    /**
     * AI 复核辅助：对照原审核留痕 + 被判文案 + 申诉理由，输出维持/改判建议。
     * 结果落库 {@code t_appeal.ai_review} 供审核员参考；AI 不可用时抛业务异常。
     *
     * @param appealId 申诉 ID
     * @return AI 复核建议 JSON
     */
    String aiReview(Long appealId);
}
