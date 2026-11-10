package com.brix.campus.model.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台用户管理查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 关键词：匹配用户名 / 昵称 / 手机号 */
    private String keyword;

    /** 角色：USER / REVIEWER / OPERATOR */
    private String role;

    /** 状态：NORMAL / DISABLED */
    private String status;

    /** 学校 ID（按学校筛选用户，配合校区统计用） */
    private Long schoolId;
}
