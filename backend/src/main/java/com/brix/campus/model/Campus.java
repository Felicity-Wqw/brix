package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 校区实体（物理表 {@code t_campus}）。
 *
 * <p>同一校区名可能被多所学校使用（如「雁山校区」有 5 所高校），因此校区的唯一约束是
 * {@code (school_id, name)}，任何按校区的筛选都必须带上 {@code school_id}，
 * 不能只用 {@code name} 匹配。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Campus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属学校 ID */
    private Long schoolId;

    /** 校区名，同校内唯一 */
    private String name;

    /** 详细地址 */
    private String address;

    /** 所在区县（统计维度） */
    private String district;

    /** 纬度 GCJ-02，可空（未采集时定位能力不生效） */
    private BigDecimal lat;

    /** 经度 GCJ-02，可空 */
    private BigDecimal lng;

    /** 定位判定半径（米） */
    private Integer radiusM;

    /** 是否主校区：1 是 / 0 否 */
    @TableField("is_main")
    private Integer isMain;

    /** 排序值 */
    private Integer sort;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
}
