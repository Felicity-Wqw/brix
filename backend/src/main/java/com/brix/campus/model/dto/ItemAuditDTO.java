package com.brix.campus.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品人工审核入参（后台审核员）。
 *
 * <p>三个动作覆盖审核员的全部处置权：</p>
 * <ul>
 *   <li>{@code PASS}：通过 → 商品转 {@code ON_SALE}（仅对 PENDING / REJECTED 有效，可纠正误杀）；</li>
 *   <li>{@code REJECT}：驳回 → 商品转 {@code REJECTED}，必须写原因，卖家能看到；</li>
 *   <li>{@code OFFLINE}：强制下架 → 商品转 {@code OFF_SHELF}（对已在售的违规商品，通常来自举报）。</li>
 * </ul>
 */
@Data
public class ItemAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核动作：PASS / REJECT / OFFLINE */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 审核说明（REJECT 时必填，≤200 字） */
    @Size(max = 200, message = "审核说明不能超过 200 字")
    private String reason;
}
