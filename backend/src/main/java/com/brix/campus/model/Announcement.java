package com.brix.campus.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.brix.campus.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台公告：首页公告轮播文字，运营端维护。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_announcement")
public class Announcement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 公告标题 */
    private String title;

    /** 公告内容（首页轮播展示） */
    private String content;

    /** 1 上架 / 0 下架 */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;
}
