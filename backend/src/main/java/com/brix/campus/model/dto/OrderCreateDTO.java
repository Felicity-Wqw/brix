package com.brix.campus.model.dto;

import com.brix.campus.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 下单入参（E01）。{@code tradeType} 仅当商品交易方式为 {@code BOTH} 时生效。
 *
 * <p>面交单必填 {@code meetTime} / {@code meetPlace}；邮寄单必填收货三件套。
 * 交叉校验在 {@code OrderServiceImpl#create} 中按实际成交方式执行。</p>
 */
@Data
public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    @NotNull(message = "商品不能为空")
    private Long itemId;

    /** 买家选择的交易方式：MEET / SHIP（商品为 BOTH 时必填） */
    private String tradeType;

    /** 面交时间（面交单必填，须为未来时间，校验在服务层） */
    private LocalDateTime meetTime;

    /** 面交地点（校区 + 宿舍楼；面交单必填） */
    @Size(max = 100, message = "面交地点过长")
    private String meetPlace;

    /** 收货人姓名（邮寄单必填） */
    @Size(max = 30, message = "收货人姓名过长")
    private String receiverName;

    /** 收货人手机号（邮寄单必填，11 位大陆手机号） */
    @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
    private String receiverPhone;

    /** 收货地址（邮寄单必填） */
    @Size(max = 200, message = "收货地址过长")
    private String receiverAddress;

    /** 买家备注 */
    @Size(max = 200, message = "备注不能超过 200 个字")
    private String buyerNote;
}
