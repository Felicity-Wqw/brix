package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体（物理表 {@code t_user}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录名，唯一 */
    private String username;

    /** BCrypt 密文，禁止任何接口下发 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 学校 */
    private String school;

    /** 校区 */
    private String campus;

    /** 手机号（11 位数字） */
    private String phone;

    /** 简介 */
    private String bio;

    /** 角色：USER */
    private String role;

    /** 状态：NORMAL / DISABLED */
    private String status;

    /** 信用分，业务层 clamp [0,200] */
    @TableField("credit_score")
    private Integer creditScore;

    /** 禁用操作人（K06） */
    @TableField("disabled_by")
    private Long disabledBy;

    /** 禁用操作时间（K06） */
    @TableField("disabled_at")
    private LocalDateTime disabledAt;
}
