package com.brix.campus.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.brix.campus.model.dto.AdminRulingDTO;
import com.brix.campus.model.dto.JuryEvidenceDTO;
import com.brix.campus.model.dto.JuryVoteDTO;
import com.brix.campus.model.vo.AdminJuryCaseVO;
import com.brix.campus.model.vo.JuryCaseVO;

/**
 * 小法庭服务。
 *
 * <p>流程：售后被拒绝 → 任一方申请平台介入建案 → 举证期 24h（双方各最多 5 条文字+图片证据）
 * → 陪审期 48h（平台用户匿名投票，一人一票，当事人不可投自己的案子）
 * → 期满自动推送管理端终审 → 管理员裁决（支持买家退款 / 支持卖家关闭）。
 * 陪审完全自愿，不给围观用户推送；节点通知只发买卖双方（聊天通道）。</p>
 */
public interface JuryService {

    /**
     * 申请平台介入（售后被拒绝后 5 日内，买卖双方均可发起）→ 建案进入举证期。
     *
     * @param userId   发起人
     * @param refundId 售后 ID
     * @param reason   纠纷事由陈述
     * @return 案件匿名 VO
     */
    JuryCaseVO initiate(Long userId, Long refundId, String reason);

    /**
     * 当事人提交证据（举证期内，每方最多 5 条）。
     *
     * @param userId 当事人 ID
     * @param caseId 案件 ID
     * @param dto    证据内容
     */
    void submitEvidence(Long userId, Long caseId, JuryEvidenceDTO dto);

    /**
     * 陪审案件列表（陪审期中的案件，自愿围观，无推送）。
     */
    IPage<JuryCaseVO> pageOpenCases(long page, long size);

    /**
     * 案件匿名详情（用户端）。
     *
     * @param caseId 案件 ID
     * @param userId 当前用户（用于计算 canVote / myVote）
     */
    JuryCaseVO detailForUser(Long caseId, Long userId);

    /**
     * 陪审投票（陪审期中、非当事人、每案一票）。
     *
     * @param userId 陪审用户 ID
     * @param caseId 案件 ID
     * @param dto    立场
     */
    void vote(Long userId, Long caseId, JuryVoteDTO dto);

    /**
     * 【定时任务】举证期到期 → 自动转入陪审期。
     *
     * @return 处理件数
     */
    int transferEvidenceTimeout();

    /**
     * 【定时任务】陪审期到期 → 自动推送管理端待裁决。
     *
     * @return 处理件数
     */
    int transferVoteTimeout();

    /**
     * 管理端：待裁决案件分页。
     */
    IPage<AdminJuryCaseVO> pagePendingRuling(long page, long size);

    /**
     * 管理端：案件详情（真实身份 + 证据 + 投票统计）。
     */
    AdminJuryCaseVO adminDetail(Long caseId);

    /**
     * 管理员终审裁决：支持买家 → 退款完成；支持卖家 → 售后关闭。
     *
     * @param adminId 管理员 ID
     * @param caseId  案件 ID
     * @param dto     裁决结果与理由
     */
    void ruling(Long adminId, Long caseId, AdminRulingDTO dto);

    /**
     * 平台 AI 生成案件预审参考（争议焦点 / 双方证据摘要 / 倾向），
     * 落库 {@code t_jury_case.ai_preview}，仅供陪审员参考，不参与计票与裁决。
     *
     * <p>幂等：已生成过（ai_previewed_at 非空）直接返回 false；
     * 举证期尚未结束的案件不生成；AI 不可用或调用失败时静默返回 false。</p>
     *
     * @param caseId 案件 ID
     * @return 是否成功生成
     */
    boolean generateAiPreview(Long caseId);

    /**
     * 平台 AI 按裁决方向起草裁决理由（供审核员编辑确认，不直接发布、不落库）。
     * AI 不可用或生成失败时抛业务异常，由前端提示改手写。
     *
     * @param caseId 案件 ID
     * @param result 裁决方向 BUYER / SELLER
     * @return 裁决理由正文
     */
    String draftRuling(Long caseId, String result);
}
