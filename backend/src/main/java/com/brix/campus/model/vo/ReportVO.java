package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报出参（后台列表 / 提报结果）。
 */
@Data
public class ReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 举报 ID */
    private Long id;

    /** 商品 ID */
    private Long itemId;

    /** 商品标题 */
    private String itemTitle;

    /** 商品首图 */
    private String itemCover;

    /** 举报人 ID */
    private Long reporterId;

    /** 举报人昵称 */
    private String reporterNickname;

    /** 原因码 */
    private String reason;

    /** 原因文案 */
    private String reasonDesc;

    /** 说明 */
    private String description;

    /** 状态码 */
    private String status;

    /** 状态文案 */
    private String statusDesc;

    /** 处理动作码 */
    private String handleAction;

    /** 处理动作文案 */
    private String handleActionDesc;

    /** 处理人 ID */
    private Long handlerId;

    /** 处理人昵称 */
    private String handlerNickname;

    /** 处理备注（审核员填写的判断依据） */
    private String handleRemark;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 举报时间 */
    private LocalDateTime createdAt;
}
