package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图片审核结论缓存：按图片内容 MD5 去重，同一张图全平台只审一次。
 *
 * <p>不继承 BaseEntity：缓存表无逻辑删除、无更新时间，结构保持最小。</p>
 */
@Data
@TableName("t_image_audit_cache")
public class ImageAuditCache implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图片内容 MD5（唯一键） */
    @TableField("image_md5")
    private String imageMd5;

    /** PENDING 审核中 / PASS / REJECT / UNCERTAIN / FAILED */
    @TableField("status")
    private String status;

    /** 审核理由 */
    @TableField("reason")
    private String reason;

    /** 执行的模型 */
    @TableField("model")
    private String model;

    /** 耗时毫秒 */
    @TableField("cost_ms")
    private Integer costMs;

    /** 结论产生时间（PENDING 为 null） */
    @TableField("audited_at")
    private LocalDateTime auditedAt;

    /** 创建时间（库默认 CURRENT_TIMESTAMP） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_REJECT = "REJECT";
    public static final String STATUS_UNCERTAIN = "UNCERTAIN";
    public static final String STATUS_FAILED = "FAILED";
}
