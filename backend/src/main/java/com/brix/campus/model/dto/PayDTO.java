package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 收银台支付入参（E02）：{@code payMethod} 取 WECHAT / ALIPAY
 * （收银台仅提供微信/支付宝两个支付沙箱；BANK 仅存量的种子数据兼容，入口已下线）。
 */
@Data
public class PayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付方式 */
    @NotBlank(message = "支付方式不能为空")
    private String payMethod;
}
