package com.brix.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brix.campus.model.ItemAuditLog;

/**
 * 审核日志 Mapper。
 *
 * <p>只依赖 {@link BaseMapper}：因为日志里已经存了商品与卖家的<b>快照</b>，
 * 列表页要展示的标题、价格、昵称、学校全在本表内，不需要 join {@code t_item} / {@code t_user}。
 * 这也是「落快照」这个设计带来的直接收益——商品被删除后，审核记录依然完整可查。</p>
 */
public interface ItemAuditLogMapper extends BaseMapper<ItemAuditLog> {
}
