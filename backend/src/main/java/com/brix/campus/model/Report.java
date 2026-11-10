package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报实体（物理表 {@code t_report}）。
 *
 * <p>与其它表不同：<b>无逻辑删除列</b>，故不继承 {@code BaseEntity}。</p>
 */
@Data
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID */
    @TableField("item_id")
    private Long itemId;

    /** 举报人 */
    @TableField("reporter_id")
    private Long reporterId;

    /** 原因：FAKE_INFO / CONTRABAND / PRICE_FRAUD / STOLEN_IMAGE / OTHER */
    private String reason;

    /** 说明 0~200 字 */
    private String description;

    /** 状态：PENDING / HANDLED */
    private String status;

    /** 处理动作：OFFLINE / REJECT */
    @TableField("handle_action")
    private String handleAction;

    /** 处理人 */
    @TableField("handler_id")
    private Long handlerId;

    /** 处理备注（审核员填写的判断依据，可空） */
    @TableField("handle_remark")
    private String handleRemark;

    /** 处理时间 */
    @TableField("handled_at")
    private LocalDateTime handledAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
