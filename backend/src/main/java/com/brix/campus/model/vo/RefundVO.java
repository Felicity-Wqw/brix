package com.brix.campus.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后单 VO（买家/卖家视角）。
 */
@Data
public class RefundVO {

    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 商品标题快照 */
    private String itemTitle;

    /** 商品首图快照 */
    private String itemCover;

    /** 退款金额 */
    private BigDecimal amount;

    /** 类型：REFUND_ONLY / RETURN_REFUND */
    private String type;

    /** 类型描述 */
    private String typeDesc;

    /** 状态：见 RefundStatusEnum */
    private String status;

    /** 状态描述 */
    private String statusDesc;

    /** 买家申请原因 */
    private String reason;

    /** 买家凭证图 */
    private List<String> images;

    /** 卖家回复 */
    private String sellerReply;

    /** 退货运单号 */
    private String trackingNo;

    /** 最近拒绝时间 */
    private LocalDateTime rejectAt;

    /** 修改重提次数 */
    private Integer reviseCount;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 关闭原因 */
    private String closeReason;

    /** 视角化：当前用户视角下对方的角色文案（如「卖家」） */
    private String counterpartRole;

    /** 视角化：对方昵称（脱敏） */
    private String counterpartNickname;

    /** 当前用户在该售后单上可执行的动作（前端按钮渲染依据） */
    private List<String> actions;

    /** 关联的小法庭案件 ID（进入司法流程后有值） */
    private Long juryCaseId;

    private LocalDateTime createdAt;
}
