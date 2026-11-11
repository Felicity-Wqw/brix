package com.brix.campus.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 上传结果出参（N06、N07）。
 */
@Data
public class UploadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可访问的图片 URL（/uploads/{bizType}/{yyyyMM}/{uuid}.{ext}） */
    private String url;

    /** 文件字节数 */
    private Long size;

    /** 文件类型 */
    private String contentType;

    /** 图片内容 MD5（仅商品图审核时返回） */
    private String imageMd5;

    /** 图片审核状态：PENDING 审核中 / PASS / REJECT / UNCERTAIN / FAILED / SKIPPED 非商品图不审 */
    private String auditStatus;

    /** 图片审核理由（违规时给卖家看） */
    private String auditReason;
}
