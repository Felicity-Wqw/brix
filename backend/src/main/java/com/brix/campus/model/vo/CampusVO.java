package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 校区出参。
 */
@Data
public class CampusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 校区 ID */
    private Long id;

    /** 所属学校 ID */
    private Long schoolId;

    /** 校区名 */
    private String name;

    /** 详细地址 */
    private String address;

    /** 所在区县 */
    private String district;

    /** 纬度 GCJ-02，未采集为 null */
    private BigDecimal lat;

    /** 经度 GCJ-02，未采集为 null */
    private BigDecimal lng;

    /** 是否主校区：1 是 / 0 否 */
    private Integer isMain;
}
