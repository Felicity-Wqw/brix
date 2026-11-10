package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 求购墙帖子展示 VO。
 */
@Data
public class WantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    /** 发帖人昵称 */
    private String nickname;

    /** 发帖人头像 */
    private String avatar;

    private String title;

    private String description;

    /** 预算上限（null = 面议） */
    private BigDecimal budget;

    private String campus;

    /** 学校 ID */
    private Long schoolId;

    /** 学校名称 */
    private String schoolName;

    /** OPEN / CLOSED */
    private String status;

    private String statusDesc;

    /** 当前登录用户是否为发帖人（决定能否关闭） */
    private Boolean mine;

    private LocalDateTime createdAt;
}
