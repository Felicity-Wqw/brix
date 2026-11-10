package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brix.campus.common.BusinessException;
import com.brix.campus.mapper.ChatMessageMapper;
import com.brix.campus.mapper.JuryCaseMapper;
import com.brix.campus.mapper.JuryEvidenceMapper;
import com.brix.campus.mapper.JuryVoteMapper;
import com.brix.campus.mapper.OrderMapper;
import com.brix.campus.mapper.RefundMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.ChatMessage;
import com.brix.campus.model.JuryCase;
import com.brix.campus.model.JuryEvidence;
import com.brix.campus.model.JuryVote;
import com.brix.campus.model.Order;
import com.brix.campus.model.Refund;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.AdminRulingDTO;
import com.brix.campus.model.dto.JuryEvidenceDTO;
import com.brix.campus.model.dto.JuryVoteDTO;
import com.brix.campus.model.enums.JuryStageEnum;
import com.brix.campus.model.enums.RefundStatusEnum;
import com.brix.campus.model.vo.AdminJuryCaseVO;
import com.brix.campus.model.vo.JuryCaseVO;
import com.brix.campus.service.JuryService;
import com.brix.campus.service.RefundService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 小法庭服务实现。
 *
 * <p>匿名规则：用户端一律「当事人 A（买家）/ 当事人 B（卖家）」，不吐用户 ID；
 * 真实身份仅 {@link #adminDetail} 返回。投票唯一性由数据库唯一索引兜底。</p>
 */
@Service
public class JuryServiceImpl implements JuryService {

    private static final Logger log = LoggerFactory.getLogger(JuryServiceImpl.class);

    /** 举证期时长：24 小时 */
    private static final int EVIDENCE_HOURS = 24;
    /** 陪审期时长：48 小时（用户口径「两天」） */
    private static final int VOTING_HOURS = 48;
    /** 双方各最多提交的证据条数 */
    private static final int MAX_EVIDENCE_PER_SIDE = 5;
    private static final String SIDE_BUYER = "BUYER";
    private static final String SIDE_SELLER = "SELLER";
    private static final String PLATFORM_USERNAME = "platform";
    private static final DateTimeFormatter CASE_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JuryCaseMapper juryCaseMapper;
    private final JuryEvidenceMapper juryEvidenceMapper;
    private final JuryVoteMapper juryVoteMapper;
    private final RefundMapper refundMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;

    private volatile Long platformUserId;

    public JuryServiceImpl(JuryCaseMapper juryCaseMapper, JuryEvidenceMapper juryEvidenceMapper,
                           JuryVoteMapper juryVoteMapper, RefundMapper refundMapper,
                           OrderMapper orderMapper, UserMapper userMapper,
                           ChatMessageMapper chatMessageMapper, RefundService refundService,
                           ObjectMapper objectMapper) {
        this.juryCaseMapper = juryCaseMapper;
        this.juryEvidenceMapper = juryEvidenceMapper;
        this.juryVoteMapper = juryVoteMapper;
        this.refundMapper = refundMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.refundService = refundService;
        this.objectMapper = objectMapper;
    }

    // ================================ 建案与举证 ================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JuryCaseVO initiate(Long userId, Long refundId, String reason) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw BusinessException.notFound("售后单不存在");
        }
        boolean isBuyer = refund.getBuyerId().equals(userId);
        boolean isSeller = refund.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw BusinessException.forbidden("只有买卖双方可以申请平台介入");
        }
        if (!RefundStatusEnum.REJECTED.getCode().equals(refund.getStatus())) {
            throw BusinessException.param("仅被拒绝的售后可以申请平台介入");
        }
        if (refund.getRejectAt() == null
                || refund.getRejectAt().plusDays(5).isBefore(LocalDateTime.now())) {
            throw BusinessException.param("已超过 5 日响应期，无法申请平台介入");
        }
        Long existing = juryCaseMapper.selectCount(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getRefundId, refundId));
        if (existing > 0) {
            throw BusinessException.param("该售后已进入平台介入流程");
        }

        LocalDateTime now = LocalDateTime.now();
        JuryCase juryCase = new JuryCase();
        juryCase.setCaseNo(generateCaseNo());
        juryCase.setRefundId(refundId);
        juryCase.setOrderId(refund.getOrderId());
        juryCase.setBuyerId(refund.getBuyerId());
        juryCase.setSellerId(refund.getSellerId());
        juryCase.setStatus(JuryStageEnum.EVIDENCE.getCode());
        juryCase.setReason(reason.trim());
        juryCase.setInitiator(isBuyer ? SIDE_BUYER : SIDE_SELLER);
        juryCase.setEvidenceDeadline(now.plusHours(EVIDENCE_HOURS));
        juryCase.setVoteDeadline(now.plusHours(EVIDENCE_HOURS + VOTING_HOURS));
        juryCaseMapper.insert(juryCase);

        refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                .eq(Refund::getId, refundId)
                .eq(Refund::getStatus, RefundStatusEnum.REJECTED.getCode())
                .set(Refund::getStatus, RefundStatusEnum.JURY_EVIDENCE.getCode()));

        // 发起人的介入陈述作为第一条证据
        JuryEvidence first = new JuryEvidence();
        first.setCaseId(juryCase.getId());
        first.setSide(juryCase.getInitiator());
        first.setContent(reason.trim());
        juryEvidenceMapper.insert(first);

        notifyBoth(juryCase, "您的订单纠纷已由平台介入，进入小法庭举证期：请在 24 小时内提交文字与图片证据，"
                + "逾期将直接进入陪审。提交入口：售后详情 → 小法庭。");
        return toUserVO(juryCase, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitEvidence(Long userId, Long caseId, JuryEvidenceDTO dto) {
        JuryCase juryCase = getCase(caseId);
        String side = requireParty(juryCase, userId);
        if (!JuryStageEnum.EVIDENCE.getCode().equals(juryCase.getStatus())) {
            throw BusinessException.param("当前不在举证期，无法提交证据");
        }
        Long count = juryEvidenceMapper.selectCount(Wrappers.<JuryEvidence>lambdaQuery()
                .eq(JuryEvidence::getCaseId, caseId)
                .eq(JuryEvidence::getSide, side));
        if (count >= MAX_EVIDENCE_PER_SIDE) {
            throw BusinessException.param("每方最多提交 " + MAX_EVIDENCE_PER_SIDE + " 条证据");
        }
        JuryEvidence evidence = new JuryEvidence();
        evidence.setCaseId(caseId);
        evidence.setSide(side);
        evidence.setContent(dto.getContent().trim());
        evidence.setImages(writeImages(dto.getImages()));
        juryEvidenceMapper.insert(evidence);

        JuryCase counterpartTarget = juryCase;
        Long counterpartId = SIDE_BUYER.equals(side)
                ? juryCase.getSellerId() : juryCase.getBuyerId();
        notify(counterpartId, counterpartTarget.getOrderId(), "对方在小法庭提交了新的证据，请及时查看并补充你的证据。");
    }

    // ================================ 陪审 ================================

    @Override
    public IPage<JuryCaseVO> pageOpenCases(long page, long size) {
        Page<JuryCase> p = new Page<>(page, size);
        IPage<JuryCase> result = juryCaseMapper.selectPage(p, Wrappers.<JuryCase>lambdaQuery()
                .in(JuryCase::getStatus, JuryStageEnum.VOTING.getCode(),
                        JuryStageEnum.EVIDENCE.getCode())
                .orderByAsc(JuryCase::getVoteDeadline));
        List<JuryCaseVO> vos = new ArrayList<>(result.getRecords().size());
        for (JuryCase juryCase : result.getRecords()) {
            vos.add(toUserVO(juryCase, null));
        }
        Page<JuryCaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public JuryCaseVO detailForUser(Long caseId, Long userId) {
        JuryCase juryCase = getCase(caseId);
        return toUserVO(juryCase, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vote(Long userId, Long caseId, JuryVoteDTO dto) {
        JuryCase juryCase = getCase(caseId);
        if (!JuryStageEnum.VOTING.getCode().equals(juryCase.getStatus())) {
            throw BusinessException.param("案件当前不在陪审期，无法投票");
        }
        if (juryCase.getBuyerId().equals(userId) || juryCase.getSellerId().equals(userId)) {
            throw BusinessException.forbidden("当事人不能参与自己案件的投票");
        }
        if (!SIDE_BUYER.equals(dto.getVote()) && !SIDE_SELLER.equals(dto.getVote())) {
            throw BusinessException.param("投票立场不合法");
        }
        Long voted = juryVoteMapper.selectCount(Wrappers.<JuryVote>lambdaQuery()
                .eq(JuryVote::getCaseId, caseId)
                .eq(JuryVote::getUserId, userId));
        if (voted > 0) {
            throw BusinessException.param("您已投过票，每案仅限一票");
        }
        JuryVote vote = new JuryVote();
        vote.setCaseId(caseId);
        vote.setUserId(userId);
        vote.setVote(dto.getVote());
        try {
            juryVoteMapper.insert(vote);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BusinessException.param("您已投过票，每案仅限一票");
        }
    }

    // ================================ 定时流转 ================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int transferEvidenceTimeout() {
        List<JuryCase> overdue = juryCaseMapper.selectList(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getStatus, JuryStageEnum.EVIDENCE.getCode())
                .apply("evidence_deadline < {0}", LocalDateTime.now())
                .last("LIMIT 100"));
        for (JuryCase juryCase : overdue) {
            int rows = juryCaseMapper.update(null, Wrappers.<JuryCase>lambdaUpdate()
                    .eq(JuryCase::getId, juryCase.getId())
                    .eq(JuryCase::getStatus, JuryStageEnum.EVIDENCE.getCode())
                    .set(JuryCase::getStatus, JuryStageEnum.VOTING.getCode()));
            if (rows > 0) {
                refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                        .eq(Refund::getId, juryCase.getRefundId())
                        .eq(Refund::getStatus, RefundStatusEnum.JURY_EVIDENCE.getCode())
                        .set(Refund::getStatus, RefundStatusEnum.JURY_VOTING.getCode()));
                notifyBoth(juryCase, "举证期已结束，案件进入小法庭陪审期，平台用户投票 48 小时后由平台终审。");
            }
        }
        return overdue.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int transferVoteTimeout() {
        List<JuryCase> overdue = juryCaseMapper.selectList(Wrappers.<JuryCase>lambdaQuery()
                .eq(JuryCase::getStatus, JuryStageEnum.VOTING.getCode())
                .apply("vote_deadline < {0}", LocalDateTime.now())
                .last("LIMIT 100"));
        for (JuryCase juryCase : overdue) {
            int rows = juryCaseMapper.update(null, Wrappers.<JuryCase>lambdaUpdate()
                    .eq(JuryCase::getId, juryCase.getId())
                    .eq(JuryCase::getStatus, JuryStageEnum.VOTING.getCode())
                    .set(JuryCase::getStatus, JuryStageEnum.PENDING_RULING.getCode()));
            if (rows > 0) {
                refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                        .eq(Refund::getId, juryCase.getRefundId())
                        .eq(Refund::getStatus, RefundStatusEnum.JURY_VOTING.getCode())
                        .set(Refund::getStatus, RefundStatusEnum.PENDING_RULING.getCode()));
                notifyBoth(juryCase, "陪审期已结束，案件已提交平台终审，请留意裁决结果。");
            }
        }
        return overdue.size();
    }

    // ================================ 管理端 ================================

    @Override
    public IPage<AdminJuryCaseVO> pagePendingRuling(long page, long size) {
        Page<JuryCase> p = new Page<>(page, size);
        IPage<JuryCase> result = juryCaseMapper.selectPage(p, Wrappers.<JuryCase>lambdaQuery()
                .orderByAsc(JuryCase::getVoteDeadline)
                .eq(JuryCase::getStatus, JuryStageEnum.PENDING_RULING.getCode()));
        List<AdminJuryCaseVO> vos = new ArrayList<>(result.getRecords().size());
        for (JuryCase juryCase : result.getRecords()) {
            vos.add(toAdminVO(juryCase));
        }
        Page<AdminJuryCaseVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public AdminJuryCaseVO adminDetail(Long caseId) {
        return toAdminVO(getCase(caseId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ruling(Long adminId, Long caseId, AdminRulingDTO dto) {
        JuryCase juryCase = getCase(caseId);
        if (!JuryStageEnum.PENDING_RULING.getCode().equals(juryCase.getStatus())) {
            throw BusinessException.param("案件不在待裁决状态");
        }
        if (!SIDE_BUYER.equals(dto.getResult()) && !SIDE_SELLER.equals(dto.getResult())) {
            throw BusinessException.param("裁决结果不合法");
        }
        int rows = juryCaseMapper.update(null, Wrappers.<JuryCase>lambdaUpdate()
                .eq(JuryCase::getId, caseId)
                .eq(JuryCase::getStatus, JuryStageEnum.PENDING_RULING.getCode())
                .set(JuryCase::getStatus, JuryStageEnum.RULED.getCode())
                .set(JuryCase::getRulingResult, dto.getResult())
                .set(JuryCase::getRulingReason, dto.getReason().trim())
                .set(JuryCase::getRulingBy, adminId)
                .set(JuryCase::getRuledAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BusinessException.param("案件状态已变化，请刷新后重试");
        }
        if (SIDE_BUYER.equals(dto.getResult())) {
            refundService.markRefunded(juryCase.getRefundId());
        } else {
            refundMapper.update(null, Wrappers.<Refund>lambdaUpdate()
                    .eq(Refund::getId, juryCase.getRefundId())
                    .in(Refund::getStatus, RefundStatusEnum.PENDING_RULING.getCode())
                    .set(Refund::getStatus, RefundStatusEnum.CLOSED.getCode())
                    .set(Refund::getCloseReason, "小法庭裁决支持卖家：" + dto.getReason().trim()));
        }
        notifyBoth(juryCase, "小法庭裁决结果：" + (SIDE_BUYER.equals(dto.getResult()) ? "支持买家" : "支持卖家")
                + "。理由：" + dto.getReason().trim()
                + (SIDE_BUYER.equals(dto.getResult()) ? "，退款已执行。" : "，售后已关闭。"));
    }

    // ================================ 私有工具 ================================

    private JuryCase getCase(Long caseId) {
        JuryCase juryCase = juryCaseMapper.selectById(caseId);
        if (juryCase == null) {
            throw BusinessException.notFound("案件不存在");
        }
        return juryCase;
    }

    /**
     * 校验当前用户是案件当事人，返回其立场（BUYER/SELLER）。
     */
    private String requireParty(JuryCase juryCase, Long userId) {
        if (juryCase.getBuyerId().equals(userId)) {
            return SIDE_BUYER;
        }
        if (juryCase.getSellerId().equals(userId)) {
            return SIDE_SELLER;
        }
        throw BusinessException.forbidden("只有案件当事人可以提交证据");
    }

    private String generateCaseNo() {
        String date = LocalDateTime.now().format(CASE_NO_FMT);
        Long todayCount = juryCaseMapper.selectCount(Wrappers.<JuryCase>lambdaQuery()
                .likeRight(JuryCase::getCaseNo, "JF" + date));
        return "JF" + date + "-" + String.format("%03d", todayCount + 1);
    }

    private JuryCaseVO toUserVO(JuryCase juryCase, Long viewerId) {
        JuryCaseVO vo = new JuryCaseVO();
        vo.setId(juryCase.getId());
        vo.setCaseNo(juryCase.getCaseNo());
        vo.setStatus(juryCase.getStatus());
        vo.setStatusDesc(JuryStageEnum.descOf(juryCase.getStatus()));
        vo.setReason(juryCase.getReason());
        vo.setInitiator(juryCase.getInitiator());
        vo.setVoteDeadline(juryCase.getVoteDeadline());
        vo.setEvidenceDeadline(juryCase.getEvidenceDeadline());
        vo.setRulingResult(juryCase.getRulingResult());
        vo.setRulingReason(juryCase.getRulingReason());
        vo.setRuledAt(juryCase.getRuledAt());
        vo.setCreatedAt(juryCase.getCreatedAt());

        Order order = orderMapper.selectById(juryCase.getOrderId());
        if (order != null) {
            vo.setItemTitle(order.getItemTitle());
            vo.setItemCover(order.getItemCover());
            vo.setAmount(order.getAmount());
            vo.setTradeType(order.getTradeType());
        }

        List<JuryEvidence> evidences = juryEvidenceMapper.selectList(
                Wrappers.<JuryEvidence>lambdaQuery()
                        .eq(JuryEvidence::getCaseId, juryCase.getId())
                        .orderByAsc(JuryEvidence::getId));
        List<JuryCaseVO.EvidenceVO> evidenceVos = new ArrayList<>(evidences.size());
        for (JuryEvidence evidence : evidences) {
            JuryCaseVO.EvidenceVO evidenceVo = new JuryCaseVO.EvidenceVO();
            evidenceVo.setSide(evidence.getSide());
            evidenceVo.setSideLabel(SIDE_BUYER.equals(evidence.getSide())
                    ? "当事人 A（买家）" : "当事人 B（卖家）");
            evidenceVo.setContent(evidence.getContent());
            evidenceVo.setImages(readImages(evidence.getImages()));
            evidenceVo.setCreatedAt(evidence.getCreatedAt());
            evidenceVos.add(evidenceVo);
        }
        vo.setEvidences(evidenceVos);

        fillVoteStats(vo, juryCase.getId());

        boolean isParty = viewerId != null
                && (juryCase.getBuyerId().equals(viewerId) || juryCase.getSellerId().equals(viewerId));
        if (viewerId != null && JuryStageEnum.VOTING.getCode().equals(juryCase.getStatus()) && !isParty) {
            Long voted = juryVoteMapper.selectCount(Wrappers.<JuryVote>lambdaQuery()
                    .eq(JuryVote::getCaseId, juryCase.getId())
                    .eq(JuryVote::getUserId, viewerId));
            vo.setCanVote(voted == 0);
            if (voted > 0) {
                JuryVote mine = juryVoteMapper.selectOne(Wrappers.<JuryVote>lambdaQuery()
                        .eq(JuryVote::getCaseId, juryCase.getId())
                        .eq(JuryVote::getUserId, viewerId)
                        .last("LIMIT 1"));
                vo.setMyVote(mine == null ? null : mine.getVote());
            }
        } else {
            vo.setCanVote(false);
        }
        return vo;
    }

    private void fillVoteStats(JuryCaseVO vo, Long caseId) {
        Long buyerVotes = juryVoteMapper.selectCount(Wrappers.<JuryVote>lambdaQuery()
                .eq(JuryVote::getCaseId, caseId)
                .eq(JuryVote::getVote, SIDE_BUYER));
        Long sellerVotes = juryVoteMapper.selectCount(Wrappers.<JuryVote>lambdaQuery()
                .eq(JuryVote::getCaseId, caseId)
                .eq(JuryVote::getVote, SIDE_SELLER));
        long total = buyerVotes + sellerVotes;
        vo.setVoteBuyer(buyerVotes);
        vo.setVoteSeller(sellerVotes);
        vo.setVoteTotal(total);
        if (total == 0) {
            vo.setVoteBuyerPercent(0);
            vo.setVoteSellerPercent(0);
        } else {
            vo.setVoteBuyerPercent(BigDecimal.valueOf(buyerVotes * 100)
                    .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValue());
            vo.setVoteSellerPercent(100 - vo.getVoteBuyerPercent());
        }
    }

    private AdminJuryCaseVO toAdminVO(JuryCase juryCase) {
        AdminJuryCaseVO vo = new AdminJuryCaseVO();
        // 复用匿名部分的组装逻辑
        JuryCaseVO base = toUserVO(juryCase, null);
        org.springframework.beans.BeanUtils.copyProperties(base, vo);
        vo.setRefundId(juryCase.getRefundId());
        vo.setBuyerId(juryCase.getBuyerId());
        vo.setSellerId(juryCase.getSellerId());
        User buyer = userMapper.selectById(juryCase.getBuyerId());
        if (buyer != null) {
            vo.setBuyerNickname(buyer.getNickname());
            vo.setBuyerUsername(buyer.getUsername());
            vo.setBuyerSchool(buyer.getSchool());
        }
        User seller = userMapper.selectById(juryCase.getSellerId());
        if (seller != null) {
            vo.setSellerNickname(seller.getNickname());
            vo.setSellerUsername(seller.getUsername());
            vo.setSellerSchool(seller.getSchool());
        }
        return vo;
    }

    private void notifyBoth(JuryCase juryCase, String content) {
        notify(juryCase.getBuyerId(), juryCase.getOrderId(), content);
        notify(juryCase.getSellerId(), juryCase.getOrderId(), content);
    }

    private void notify(Long toUserId, Long itemId, String content) {
        try {
            ChatMessage message = new ChatMessage();
            message.setFromUserId(resolvePlatformUserId());
            message.setToUserId(toUserId);
            message.setItemId(itemId);
            message.setContent(content);
            chatMessageMapper.insert(message);
        } catch (Exception e) {
            log.warn("小法庭通知发送失败（to={}）：{}", toUserId, e.getMessage());
        }
    }

    private Long resolvePlatformUserId() {
        if (platformUserId == null) {
            User platform = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                    .eq(User::getUsername, PLATFORM_USERNAME)
                    .last("LIMIT 1"));
            if (platform == null) {
                throw BusinessException.param("平台系统账号缺失，请先执行 db/p2-refund.sql");
            }
            platformUserId = platform.getId();
        }
        return platformUserId;
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.warn("证据图片序列化失败", e);
            return "";
        }
    }

    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("证据图片反序列化失败：{}", json);
            return new ArrayList<>();
        }
    }
}
