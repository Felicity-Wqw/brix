package com.brix.campus.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品详情出参：在列表字段基础上补描述、图片列表、卖家卡片、是否已收藏。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemDetailVO extends ItemVO {

    private static final long serialVersionUID = 1L;

    /** 描述 */
    private String description;

    /** 联系方式 */
    private String contact;

    /** 图片列表（按 sort 升序） */
    private List<ItemImageVO> images;

    /** 待支付订单数（先到先得提示：大于 0 表示已有人下单待支付） */
    private Long pendingBuyerCount;

    /** 卖家卡片 */
    private SellerVO seller;

    /** 当前登录用户是否已收藏（未登录为 null） */
    private Boolean isFavorite;

    /** 当前登录用户是否为卖家本人 */
    private Boolean isOwner;
}
