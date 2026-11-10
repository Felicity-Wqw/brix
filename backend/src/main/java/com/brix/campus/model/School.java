package com.brix.campus.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学校实体（物理表 {@code t_school}）。
 *
 * <p>{@code level}（本科/专科）与 {@code nature}（公办/民办）是为后台统计预留的维度字段，
 * 业务端展示不使用，但聚合查询（如「各学校交易量」「本科 vs 专科成交占比」）依赖它们。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class School extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 学校全称，唯一 */
    private String name;

    /** 简称，可空 */
    private String shortName;

    /** 层次：本科 / 专科 */
    private String level;

    /** 性质：公办 / 民办 */
    private String nature;

    /** 所在城市 */
    private String city;

    /** 主校区所在区县（统计维度） */
    private String district;

    /** 排序值，升序展示 */
    private Integer sort;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
}
