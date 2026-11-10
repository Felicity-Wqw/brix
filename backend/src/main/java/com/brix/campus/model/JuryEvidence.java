package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小法庭证据（物理表 {@code t_jury_evidence}）。
 *
 * <p>append-only：只增不改，与商品审核日志同一留痕风格；
 * 双方在举证期内各自最多提交 5 条（Service 层保证）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JuryEvidence extends BaseEntity {

    /** 案件 ID */
    @TableField("case_id")
    private Long caseId;

    /** 提交方：BUYER / SELLER */
    private String side;

    /** 证据说明（文字） */
    private String content;

    /** 证据图片 URL 列表（JSON 数组） */
    private String images;
}
