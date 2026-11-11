package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品申诉出参。
 */
@Data
public class AppealVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申诉 ID */
    private Long id;

    /** 申诉人用户名 */
    private String username;

    /** 申诉人昵称 */
    private String nickname;

    /** 申诉商品 ID */
    private Long itemId;

    /** 商品标题 */
    private String itemTitle;

    /** 申诉理由 */
    private String reason;

    /** 状态：PENDING / MAINTAINED / OVERTURNED */
    private String status;

    /** 状态描述 */
    private String statusDesc;

    /** AI 复核建议 JSON（处理参考） */
    private String aiReview;

    /** 处理回复 */
    private String reply;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 提交时间 */
    private LocalDateTime createdAt;
}
