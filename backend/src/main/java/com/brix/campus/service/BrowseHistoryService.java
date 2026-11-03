package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.query.BaseQuery;
import com.brix.campus.model.vo.BrowseHistoryVO;

/**
 * 浏览历史服务。
 */
public interface BrowseHistoryService {

    /**
     * 记录一次浏览（同一用户同一商品仅保留一条，重复浏览刷新时间）。
     *
     * <p>内部吞掉一切异常：浏览记录失败不影响详情主流程。</p>
     *
     * @param userId 用户 ID（非空才记录）
     * @param itemId 商品 ID
     */
    void record(Long userId, Long itemId);

    /**
     * 我的浏览历史分页（按最近浏览倒序，已删除商品过滤）。
     *
     * @param userId 用户 ID
     * @param query  分页条件
     * @return 分页结果
     */
    IPage<BrowseHistoryVO> page(Long userId, BaseQuery query);
}
