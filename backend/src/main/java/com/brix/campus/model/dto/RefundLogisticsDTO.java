package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 买家填写退货运单号入参。
 */
@Data
public class RefundLogisticsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退货运单号 */
    @NotBlank(message = "运单号不能为空")
    @Size(max = 50, message = "运单号不能超过 50 位")
    private String trackingNo;
}
