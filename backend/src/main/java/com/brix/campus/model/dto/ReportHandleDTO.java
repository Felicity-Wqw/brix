package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 举报处理入参（审核员）。
 *
 * <ul>
 *   <li>{@code OFFLINE}：举报成立，商品强制下架（{@code ON_SALE → OFF_SHELF}）；</li>
 *   <li>{@code REJECT}：举报不成立，仅关闭举报，不动商品。</li>
 * </ul>
 */
@Data
public class ReportHandleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处理动作：OFFLINE / REJECT */
    @NotBlank(message = "处理动作不能为空")
    private String action;

    /** 处理备注（判断依据，≤200 字，可空） */
    @Size(max = 200, message = "处理备注不能超过 200 字")
    private String remark;
}
