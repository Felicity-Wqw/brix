package com.brix.campus.controller;

import com.brix.campus.common.R;
import com.brix.campus.model.vo.UploadVO;
import com.brix.campus.security.SecurityUtils;
import com.brix.campus.service.FileStorageService;
import com.brix.campus.service.ImageAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口（N06、N07）。
 *
 * <p>商品图上传后即触发图片审核（异步，结论经 WS 推送并缓存）：
 * 违规图在上传阶段就被标红拦下，而不是等到发布提交才被打回。</p>
 */
@RestController
@Validated
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final FileStorageService fileStorageService;
    private final ImageAuditService imageAuditService;

    public UploadController(FileStorageService fileStorageService, ImageAuditService imageAuditService) {
        this.fileStorageService = fileStorageService;
        this.imageAuditService = imageAuditService;
    }

    /**
     * 上传单张图片。
     *
     * @param file    图片文件（jpg/jpeg/png/webp，≤5MB）
     * @param bizType 业务类型：item / avatar / review（仅 item 触发图片审核）
     * @return 上传结果（含可访问 URL 与图片审核状态）
     */
    @PostMapping("/upload/image")
    public R<UploadVO> upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "bizType", required = false, defaultValue = "item")
                              String bizType) {
        UploadVO vo = fileStorageService.store(file, bizType);
        if ("item".equals(bizType)) {
            try {
                ImageAuditService.ImageAuditOutcome outcome =
                        imageAuditService.auditOnUpload(SecurityUtils.currentUserId(), vo.getUrl());
                vo.setImageMd5(outcome.md5());
                vo.setAuditStatus(outcome.status());
                vo.setAuditReason(outcome.reason());
            } catch (Exception e) {
                // 图片审核是增强能力，任何异常都不能挡住上传本身
                log.warn("图片审核触发失败（url={}）：{}", vo.getUrl(), e.getMessage());
                vo.setAuditStatus("SKIPPED");
            }
        } else {
            vo.setAuditStatus("SKIPPED");
        }
        return R.ok(vo, "上传成功");
    }
}
