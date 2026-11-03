package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 浏览历史实体（物理表 {@code t_browse_history}）。
 *
 * <p>同一用户对同一商品只保留一条记录，重复浏览刷新 {@code updated_at}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BrowseHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 浏览者用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 商品 ID */
    @TableField("item_id")
    private Long itemId;
}
