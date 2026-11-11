package com.brix.campus.service;

import com.brix.campus.model.dto.AuditDecision;

import java.util.List;

/**
 * 图片审核服务：上传时逐图审核（异步 + WS 推送），发布时读缓存合并结论。
 *
 * <p>结论按图片内容 MD5 缓存于 {@code t_image_audit_cache}，同一张图全平台只审一次。
 * 图片结论不单独成篇，由 {@code PublishAuditService} 并入 AI 闸（gate2）取严合并。</p>
 */
public interface ImageAuditService {

    /**
     * 上传后触发审核：命中缓存立即返回结论，否则登记 PENDING 并提交后台异步审核。
     *
     * @param userId 上传者 ID（WS 推送结论用）
     * @param url    图片可访问路径（/uploads/item/...）
     * @return 审核结果（md5 + status + reason；异步时 status=PENDING）
     */
    ImageAuditOutcome auditOnUpload(Long userId, String url);

    /**
     * 发布时取全部图片的审核结论：读缓存，未完成的图同步补审（受总时限约束）。
     *
     * @param imageUrls 商品相册图片路径列表
     * @return 逐图明细（与入参顺序一致，index 从 1 开始）；视觉模型未配置返回 null
     */
    List<AuditDecision.ImageFinding> findingsFor(List<String> imageUrls);

    /**
     * 上传审核结果。
     *
     * @param md5    图片内容 MD5
     * @param status PENDING / PASS / REJECT / UNCERTAIN / FAILED
     * @param reason 理由（PENDING 为空）
     */
    record ImageAuditOutcome(String md5, String status, String reason) {
    }
}
