package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 买家申请售后入参。
 */
@Data
public class RefundApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    @NotNull(message = "订单不能为空")
    private Long orderId;

    /** 售后类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
    @NotBlank(message = "售后类型不能为空")
    private String type;

    /** 申请原因 */
    @NotBlank(message = "申请原因不能为空")
    @Size(max = 300, message = "申请原因不能超过 300 字")
    private String reason;

    /** 凭证图片（最多 3 张） */
    @Size(max = 3, message = "凭证图片最多 3 张")
    private List<String> images;
}
