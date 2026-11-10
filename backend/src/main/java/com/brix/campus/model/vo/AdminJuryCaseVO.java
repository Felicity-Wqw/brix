package com.brix.campus.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小法庭案件 VO —— 管理端终审视角：补充买卖双方真实身份。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminJuryCaseVO extends JuryCaseVO {

    /** 买家 ID */
    private Long buyerId;

    /** 买家昵称（管理端不脱敏） */
    private String buyerNickname;

    /** 买家账号 */
    private String buyerUsername;

    /** 买家学校 */
    private String buyerSchool;

    /** 卖家 ID */
    private Long sellerId;

    /** 卖家昵称 */
    private String sellerNickname;

    /** 卖家账号 */
    private String sellerUsername;

    /** 卖家学校 */
    private String sellerSchool;

    /** 售后单 ID（关联回售后详情） */
    private Long refundId;
}
