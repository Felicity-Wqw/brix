package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 学校出参（含校区列表，前端一次拉全做两级联动）。
 */
@Data
public class SchoolVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学校 ID（Long → String 序列化） */
    private Long id;

    /** 全称 */
    private String name;

    /** 简称 */
    private String shortName;

    /** 层次：本科 / 专科 */
    private String level;

    /** 性质：公办 / 民办 */
    private String nature;

    /** 主校区所在区县 */
    private String district;

    /** 该校全部启用校区，按 sort 升序 */
    private List<CampusVO> campuses = new ArrayList<>();
}
