package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.Item;
import com.brix.campus.model.dto.AuditDecision;
import com.brix.campus.model.query.ItemAuditLogQuery;
import com.brix.campus.model.vo.ItemAuditLogVO;

import java.util.List;

/**
 * 商品审核日志服务：负责把「一次审核」变成一条不可变的证据。
 *
 * <p>职责边界刻意收窄——本服务只做两件事：<b>写日志</b>与<b>读日志</b>。
 * 它不决定风险级别（那是 {@link PublishAuditService} 的事），也不改商品状态
 * （那是 {@code ItemService} 的事）。这样「怎么判」「判完怎么落库」「判完怎么留痕」
 * 三件事各自独立，任何一件改口径都不会波及另外两件。</p>
 */
public interface ItemAuditLogService {

    /**
     * 记录一次自动审核（低风险直发也要记）。
     *
     * <p>会按当前时刻的商品与卖家信息落<b>快照</b>，之后商品被编辑、卖家改昵称都不影响本记录。</p>
     *
     * @param item     已完成状态落库的商品实体（需含 id / sellerId / 标题 / 价格 / 首图）
     * @param decision 审核结论（含两道闸明细）
     */
    Long record(Item item, AuditDecision decision);

    /**
     * 黑名单命中场景的 AI 复核补档：覆盖指定日志行的 AI 闸四列（gate2_result/verdict/reason/suggestion）。
     *
     * @param logId 审核日志行 ID
     * @param gate2 补档后的 AI 闸结论
     */
    void updateGate2(Long logId, AuditDecision.Gate2 gate2);

    /**
     * 分页查询审核日志。
     *
     * @param query 条件
     * @return 分页结果
     */
    IPage<ItemAuditLogVO> page(ItemAuditLogQuery query);

    /**
     * 取某件商品的完整审核轨迹（按时间倒序）。
     *
     * <p>用于后台审核详情页展示「这件商品被审过几次、每次的结论是什么」——
     * 比如卖家反复改标题试探，轨迹会连成一条链。</p>
     *
     * @param itemId 商品 ID
     * @return 日志列表，无记录返回空列表
     */
    List<ItemAuditLogVO> listByItem(Long itemId);

    /**
     * 回写人工处置结果到该商品最近一条日志。
     *
     * <p>不回新行，是为了保持「一次自动审核 = 一条记录」的粒度：
     * 审核员是在<b>复核</b>机器的判定，而不是产生了第二次机器判定。</p>
     *
     * @param itemId    商品 ID
     * @param handlerId 审核员 ID
     * @param note      处置说明（通过理由 / 驳回原因）
     */
    void markHandled(Long itemId, Long handlerId, String note);
}
