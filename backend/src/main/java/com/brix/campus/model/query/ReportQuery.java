package com.brix.campus.model.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台举报队列查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 状态：PENDING 待处理 / HANDLED 已处理；空表示全部 */
    private String status;

    /** 原因码（可选，用于按类型筛查） */
    private String reason;
}
