package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.brix.campus.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 求购墙帖子：买家发布需求，卖家私信接单，成交后发帖人关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_want")
public class Want extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 发帖人用户 ID */
    private Long userId;

    /** 求购标题 */
    private String title;

    /** 需求描述 */
    private String description;

    /** 预算上限（可空：面议） */
    private BigDecimal budget;

    /** 期望交易校区（可空） */
    private String campus;

    /** 学校 ID（可空） */
    private Long schoolId;

    /** 状态：OPEN 求购中 / CLOSED 已结束 */
    private String status;
}
