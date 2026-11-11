package com.brix.campus.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * 单张图片的视觉审核结论（glm-4v-flash 返回，人工解析 JSON 后装载）。
 */
@Data
public class ImageAuditVerdict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 判定：PASS / REJECT / UNCERTAIN */
    private String verdict;

    /** 判定理由（≤40 字） */
    private String reason;
}
