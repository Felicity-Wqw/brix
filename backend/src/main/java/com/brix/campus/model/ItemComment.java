package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品留言实体（物理表 {@code t_item_comment}）。
 *
 * <p>{@code parentId} 为空即一级留言；非空表示对一级留言的回复（仅一层，不做多级评论）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    @TableField("item_id")
    private Long itemId;

    /** 留言人 */
    @TableField("user_id")
    private Long userId;

    /** 父留言 ID，空=一级留言 */
    @TableField("parent_id")
    private Long parentId;

    /** 内容 1~500 字 */
    private String content;

    /** 卖家是否已读：0 未读（触发导航红点提醒） / 1 已读 */
    @TableField("is_read")
    private Integer isRead;
}
