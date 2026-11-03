package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的浏览历史列表项（已售出/已下架仍展示，需带状态角标；已删除商品过滤）。
 */
@Data
public class BrowseHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 浏览记录 ID */
    private Long id;

    /** 商品 ID */
    private Long itemId;

    /** 商品标题 */
    private String itemTitle;

    /** 商品首图 */
    private String itemCover;

    /** 商品价格 */
    private BigDecimal itemPrice;

    /** 商品状态码 */
    private String itemStatus;

    /** 商品状态文案 */
    private String itemStatusDesc;

    /** 卖家昵称 */
    private String sellerNickname;

    /** 最近浏览时间 */
    private LocalDateTime browseTime;
}
