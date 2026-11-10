package com.brix.campus.model.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品查询条件（前台列表 / 我的商品共用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 关键词：匹配标题或描述 */
    private String keyword;

    /** 分类 ID */
    private Long categoryId;

    /** 校区（名称，历史参数，保留兼容） */
    private String campus;

    /** 学校 ID（筛选该校全部校区商品） */
    private Long schoolId;

    /** 校区 ID（精确筛选，优先于 schoolId） */
    private Long campusId;

    /** 价格下限（含） */
    private BigDecimal minPrice;

    /** 价格上限（含） */
    private BigDecimal maxPrice;

    /** 排序：latest / price_asc / price_desc / hot */
    private String sort;

    /**
     * 优先展示的学校 ID（只影响排序，不过滤）。
     *
     * <p>首页默认传当前用户所属学校：同校商品排在最前，方便面交；其他学校的商品仍然保留在后面，
     * 不削减可逛的内容。与 {@link #schoolId}（真过滤）是两件事。</p>
     */
    private Long preferSchoolId;

    /** 状态（「我的商品」使用；前台列表由服务端固定 ON_SALE） */
    private String status;

    /**
     * 风险级别（后台审核队列使用，前台传了也无效）。
     *
     * <p>取值 LOW / MEDIUM / HIGH；空串或 null 表示不限。</p>
     */
    private String riskLevel;

    /**
     * 高风险优先排序（后台「高风险优先处理区」使用，前台传了也无效）。
     *
     * <p>为 true 时按 {@code HIGH → MEDIUM → LOW → 未知} 排序，同级内按提交时间倒序。
     * 与 {@link #riskLevel}（真过滤）是两件事：这里只改顺序，不筛数据。</p>
     */
    private Boolean riskFirst;

    /** 卖家 ID（服务端注入，不接受前端传值） */
    private Long sellerId;
}
