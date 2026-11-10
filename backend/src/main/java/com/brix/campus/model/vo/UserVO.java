package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息出参（不含密码等敏感字段）。
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID（Long → 字符串下发） */
    private Long id;

    /** 登录名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 学校 */
    private String school;

    /** 校区 */
    private String campus;

    /** 学校 ID（两级联动回显用，可空） */
    private Long schoolId;

    /** 校区 ID（两级联动回显用，可空） */
    private Long campusId;

    /** 手机号 */
    private String phone;

    /** 简介 */
    private String bio;

    /** 角色码 */
    private String role;

    /** 角色文案 */
    private String roleDesc;

    /** 状态码 */
    private String status;

    /** 状态文案 */
    private String statusDesc;

    /** 信用分 */
    private Integer creditScore;

    /** 注册时间 */
    private LocalDateTime createdAt;

    /** 个人数据（仅 /auth/me 返回） */
    private UserStatsVO stats;
}
