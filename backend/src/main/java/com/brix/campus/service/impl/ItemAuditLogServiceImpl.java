package com.brix.campus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.brix.campus.mapper.CategoryMapper;
import com.brix.campus.mapper.ItemAuditLogMapper;
import com.brix.campus.mapper.UserMapper;
import com.brix.campus.model.Category;
import com.brix.campus.model.Item;
import com.brix.campus.model.ItemAuditLog;
import com.brix.campus.model.User;
import com.brix.campus.model.dto.AuditDecision;
import com.brix.campus.model.enums.ItemStatusEnum;
import com.brix.campus.model.enums.RiskLevelEnum;
import com.brix.campus.model.query.ItemAuditLogQuery;
import com.brix.campus.model.vo.ItemAuditLogVO;
import com.brix.campus.service.ItemAuditLogService;
import com.brix.campus.service.SchoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核日志服务实现。
 *
 * <p>写入侧的核心是「抄快照」：商品与卖家信息在审核那一刻取值存进日志表，
 * 之后 {@code t_item} / {@code t_user} 怎么变都不影响这条证据。</p>
 *
 * <p>读取侧的核心是「零 join」：因为快照齐全，列表页要展示的标题、价格、昵称、
 * 学校、信用分全在本表内；只有「处置人昵称」需要补一次批量查询（日志表只存 ID）。</p>
 */
@Service
public class ItemAuditLogServiceImpl extends ServiceImpl<ItemAuditLogMapper, ItemAuditLog>
        implements ItemAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(ItemAuditLogServiceImpl.class);

    /** 命中词、类目等短文本列宽，超长截断避免插入报错 */
    private static final int MAX_SHORT = 50;
    /** AI 理由、判定说明的列宽 */
    private static final int MAX_NOTE = 480;

    /** 待人工审核的处置状态（落库状态机的 PENDING） */
    private static final String STATUS_PENDING = "PENDING";
    /** 已被该商品后续审核覆盖的历史待审记录 */
    private static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final SchoolService schoolService;

    public ItemAuditLogServiceImpl(CategoryMapper categoryMapper,
                                   UserMapper userMapper,
                                   SchoolService schoolService) {
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.schoolService = schoolService;
    }

    // ========================================================================
    // 写
    // ========================================================================

    @Override
    public void record(Item item, AuditDecision decision) {
        if (item == null || item.getId() == null || decision == null) {
            return;
        }
        try {
            AuditDecision.Gate1 gate1 = decision.gate1() == null
                    ? AuditDecision.Gate1.pass() : decision.gate1();
            AuditDecision.Gate2 gate2 = decision.gate2() == null
                    ? AuditDecision.Gate2.unavailable("未执行 AI 审核") : decision.gate2();
            RiskLevelEnum level = decision.level() == null ? RiskLevelEnum.LOW : decision.level();

            ItemAuditLog entity = new ItemAuditLog();
            entity.setItemId(item.getId());
            entity.setSellerId(item.getSellerId());

            // 商品快照
            entity.setItemTitle(trim(item.getTitle(), MAX_SHORT));
            entity.setItemPrice(item.getPrice());
            entity.setItemCover(trim(item.getCoverImage(), 255));
            entity.setCategoryName(trim(displayCategoryOf(item.getCategoryId()), MAX_SHORT));

            // 卖家快照
            fillSellerSnapshot(entity, item.getSellerId());

            // 第一道闸
            entity.setGate1Result(gate1.result());
            entity.setGate1Word(trim(gate1.hitWord(), MAX_SHORT));
            entity.setGate1Category(trim(gate1.category(), MAX_SHORT));

            // 第二道闸
            entity.setGate2Result(gate2.result());
            entity.setGate2Verdict(trim(gate2.verdict(), 20));
            entity.setGate2Reason(trim(gate2.reason(), MAX_NOTE));

            // 结论：状态取商品真实落库的结果，而不是按级别推算，
            // 避免「编辑流程里 AI 判高危直接抛异常、商品状态其实没变」时日志写错状态
            entity.setRiskLevel(level.getCode());
            entity.setDecisionStatus(item.getStatus() == null ? level.getInitialStatus() : item.getStatus());
            entity.setDecisionNote(trim(decisionNoteOf(decision), MAX_NOTE));

            entity.setCreatedAt(LocalDateTime.now());
            // 该商品此前若有「待人工审核」的历史记录，本次审核已产生新结论：
            // 旧记录的处置状态标记为 SUPERSEDED（已被覆盖），避免后台「待人工」计数虚高、
            // 出现「日志里有待审、审核队列却是空的」的口径错位。日志内容仍是 append-only 证据，只改处置状态。
            supersedePending(item.getId());
            save(entity);
        } catch (Exception e) {
            // 审核留痕是「事后证据」，不是发布的前置条件：写日志失败不能让卖家发不出商品。
            // 但必须 ERROR 告警——事故排查时要能发现证据链断过。
            // 【兜底】t_item 上仍保留 risk_level / audit_source / audit_reason，可反查最近一次判定。
            log.error("审核日志写入失败（itemId={}，risk={}）：{}",
                    item.getId(), decision.level(), e.getMessage(), e);
        }
    }

    @Override
    public void markHandled(Long itemId, Long handlerId, String note) {
        if (itemId == null || handlerId == null) {
            return;
        }
        ItemAuditLog latest = latestOf(itemId);
        if (latest == null) {
            // 历史商品（改造前发布）没有自动审核日志，人工处置无处可回写，属正常情况
            return;
        }
        latest.setHandlerId(handlerId);
        latest.setHandledAt(LocalDateTime.now());
        latest.setHandleNote(trim(note, 200));
        updateById(latest);
    }

    /**
     * 将该商品此前处于「待人工审核」的历史日志标记为已失效（SUPERSEDED）。
     *
     * <p>语义：商品同一时刻只能有一条待办，旧待办被新审核结论覆盖后不再是待办；
     * 日志行本身保留原文与快照，仅处置状态变化，与 {@link #markHandled} 的回写粒度一致。</p>
     *
     * @param itemId 商品 ID
     */
    private void supersedePending(Long itemId) {
        try {
            update(Wrappers.lambdaUpdate(ItemAuditLog.class)
                    .eq(ItemAuditLog::getItemId, itemId)
                    .eq(ItemAuditLog::getDecisionStatus, STATUS_PENDING)
                    .set(ItemAuditLog::getDecisionStatus, STATUS_SUPERSEDED)
                    .set(ItemAuditLog::getDecisionNote,
                            "商品后续重新审核产生新结论，本条待审记录已自动失效（最新结论见该商品最新一行日志）"));
        } catch (Exception e) {
            // 失效标记是体验优化而非发布前置条件，失败只告警不阻断。
            log.warn("旧待审日志失效标记失败（itemId={}）：{}", itemId, e.getMessage(), e);
        }
    }

    // ========================================================================
    // 读
    // ========================================================================

    @Override
    public IPage<ItemAuditLogVO> page(ItemAuditLogQuery query) {
        Page<ItemAuditLog> page = new Page<>(pageOf(query), sizeOf(query));
        LambdaQueryWrapper<ItemAuditLog> wrapper = Wrappers.lambdaQuery();
        if (query.getItemId() != null) {
            wrapper.eq(ItemAuditLog::getItemId, query.getItemId());
        }
        if (query.getSellerId() != null) {
            wrapper.eq(ItemAuditLog::getSellerId, query.getSellerId());
        }
        if (notBlank(query.getRiskLevel())) {
            wrapper.eq(ItemAuditLog::getRiskLevel, query.getRiskLevel().trim());
        }
        if (notBlank(query.getGate1Result())) {
            wrapper.eq(ItemAuditLog::getGate1Result, query.getGate1Result().trim());
        }
        if (notBlank(query.getGate2Result())) {
            wrapper.eq(ItemAuditLog::getGate2Result, query.getGate2Result().trim());
        }
        if (notBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ItemAuditLog::getItemTitle, keyword)
                    .or().like(ItemAuditLog::getSellerNickname, keyword)
                    .or().like(ItemAuditLog::getSellerUsername, keyword)
                    .or().like(ItemAuditLog::getGate1Word, keyword));
        }
        if (query.getHandled() != null) {
            if (query.getHandled()) {
                wrapper.isNotNull(ItemAuditLog::getHandlerId);
            } else {
                wrapper.isNull(ItemAuditLog::getHandlerId);
                // 「待人工确认」筛选要的是真正需要人处理的记录：
                // 已被后续审核覆盖的 SUPERSEDED 行虽然也没人处置过，但不再是待办
                wrapper.ne(ItemAuditLog::getDecisionStatus, STATUS_SUPERSEDED);
            }
        }
        LocalDate start = parseDate(query.getStartDate());
        if (start != null) {
            wrapper.ge(ItemAuditLog::getCreatedAt, start.atStartOfDay());
        }
        LocalDate end = parseDate(query.getEndDate());
        if (end != null) {
            // 上界含当天：用「次日零点」做开区间，避免 23:59:59.xxx 被漏掉
            wrapper.lt(ItemAuditLog::getCreatedAt, end.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(ItemAuditLog::getCreatedAt).orderByDesc(ItemAuditLog::getId);

        IPage<ItemAuditLog> raw = baseMapper.selectPage(page, wrapper);
        Page<ItemAuditLogVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        voPage.setRecords(convert(raw.getRecords()));
        return voPage;
    }

    @Override
    public List<ItemAuditLogVO> listByItem(Long itemId) {
        if (itemId == null) {
            return Collections.emptyList();
        }
        List<ItemAuditLog> list = baseMapper.selectList(Wrappers.<ItemAuditLog>lambdaQuery()
                .eq(ItemAuditLog::getItemId, itemId)
                .orderByDesc(ItemAuditLog::getCreatedAt)
                .orderByDesc(ItemAuditLog::getId));
        return convert(list);
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 取某商品最近一条审核日志。
     *
     * @param itemId 商品 ID
     * @return 日志，无记录返回 null
     */
    private ItemAuditLog latestOf(Long itemId) {
        Page<ItemAuditLog> page = new Page<>(1, 1);
        List<ItemAuditLog> list = baseMapper.selectPage(page, Wrappers.<ItemAuditLog>lambdaQuery()
                .eq(ItemAuditLog::getItemId, itemId)
                .orderByDesc(ItemAuditLog::getCreatedAt)
                .orderByDesc(ItemAuditLog::getId)).getRecords();
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 把卖家信息抄进日志（审核当时的快照）。
     *
     * @param entity   待写入的日志
     * @param sellerId 卖家 ID
     */
    private void fillSellerSnapshot(ItemAuditLog entity, Long sellerId) {
        if (sellerId == null) {
            return;
        }
        User seller = userMapper.selectById(sellerId);
        if (seller == null) {
            return;
        }
        entity.setSellerUsername(trim(seller.getUsername(), MAX_SHORT));
        entity.setSellerNickname(trim(seller.getNickname(), MAX_SHORT));
        entity.setSellerCampus(trim(seller.getCampus(), MAX_SHORT));
        entity.setSellerCredit(seller.getCreditScore());
        // 学校优先取 ID 对应的名称（校区改名/学校更名后仍能对上主数据），
        // 取不到再退回用户表里的字符串快照（历史数据可能没填 schoolId）
        String schoolName = schoolService.nameOf(seller.getSchoolId());
        entity.setSellerSchool(trim(schoolName != null ? schoolName : seller.getSchool(), 100));
    }

    /**
     * 分类展示名：子类带父类前缀，与前台、后台列表保持一致。
     *
     * @param categoryId 分类 ID
     * @return 展示名
     */
    private String displayCategoryOf(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return null;
        }
        if (category.getParentId() == null || category.getParentId() == 0L) {
            return category.getName();
        }
        Category parent = categoryMapper.selectById(category.getParentId());
        return parent == null ? category.getName() : parent.getName() + " / " + category.getName();
    }

    /**
     * 拼装给卖家看的判定说明（原因 + 修改建议）。
     *
     * @param decision 审核结论
     * @return 说明，无风险时返回 null
     */
    private String decisionNoteOf(AuditDecision decision) {
        if (decision.reason() == null || decision.reason().isBlank()) {
            return null;
        }
        String reason = decision.reason().trim();
        if (decision.suggestion() != null && !decision.suggestion().isBlank()) {
            reason = reason + "；修改建议：" + decision.suggestion().trim();
        }
        return reason;
    }

    /**
     * 实体列表转出参列表（补中文文案 + 处置人昵称）。
     *
     * @param records 日志实体
     * @return 出参列表
     */
    private List<ItemAuditLogVO> convert(List<ItemAuditLog> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> handlerIds = records.stream()
                .map(ItemAuditLog::getHandlerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> handlerNames = new HashMap<>();
        if (!handlerIds.isEmpty()) {
            for (User user : userMapper.selectByIds(handlerIds)) {
                handlerNames.put(user.getId(), user.getNickname());
            }
        }
        List<ItemAuditLogVO> voList = new ArrayList<>(records.size());
        for (ItemAuditLog entity : records) {
            ItemAuditLogVO vo = new ItemAuditLogVO();
            vo.setId(entity.getId());
            vo.setItemId(entity.getItemId());
            vo.setSellerId(entity.getSellerId());
            vo.setItemTitle(entity.getItemTitle());
            vo.setItemPrice(entity.getItemPrice());
            vo.setItemCover(entity.getItemCover());
            vo.setCategoryName(entity.getCategoryName());
            vo.setSellerUsername(entity.getSellerUsername());
            vo.setSellerNickname(entity.getSellerNickname());
            vo.setSellerSchool(entity.getSellerSchool());
            vo.setSellerCampus(entity.getSellerCampus());
            vo.setSellerCredit(entity.getSellerCredit());
            vo.setGate1Result(entity.getGate1Result());
            vo.setGate1ResultDesc(gate1DescOf(entity.getGate1Result()));
            vo.setGate1Word(entity.getGate1Word());
            vo.setGate1Category(entity.getGate1Category());
            vo.setGate2Result(entity.getGate2Result());
            vo.setGate2ResultDesc(gate2DescOf(entity.getGate2Result()));
            vo.setGate2Verdict(entity.getGate2Verdict());
            vo.setGate2Reason(entity.getGate2Reason());
            vo.setRiskLevel(entity.getRiskLevel());
            vo.setRiskLevelDesc(RiskLevelEnum.descOf(entity.getRiskLevel()));
            vo.setDecisionStatus(entity.getDecisionStatus());
            vo.setDecisionStatusDesc(decisionStatusDescOf(entity.getDecisionStatus()));
            vo.setDecisionNote(entity.getDecisionNote());
            vo.setHandlerId(entity.getHandlerId());
            vo.setHandlerNickname(handlerNames.get(entity.getHandlerId()));
            vo.setHandledAt(entity.getHandledAt());
            vo.setHandleNote(entity.getHandleNote());
            vo.setHandled(entity.getHandlerId() != null);
            vo.setCreatedAt(entity.getCreatedAt());
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 第一道闸结果文案。
     *
     * @param result 结果码
     * @return 文案
     */
    private String gate1DescOf(String result) {
        if (result == null) {
            return "未执行";
        }
        return switch (result) {
            case AuditDecision.Gate1.PASS -> "未命中违禁词";
            case AuditDecision.Gate1.MILD_HIT -> "命中轻度风险词";
            case AuditDecision.Gate1.BLACK_HIT -> "命中黑名单";
            default -> result;
        };
    }

    /**
     * 处置状态文案。
     *
     * <p>SUPERSEDED 不是商品状态机的成员（它只是日志处置口径），
     * {@link ItemStatusEnum#descOf} 兜底会原样吐出英文码，这里单独映射。</p>
     *
     * @param code 处置状态码
     * @return 中文文案
     */
    private String decisionStatusDescOf(String code) {
        if (STATUS_SUPERSEDED.equals(code)) {
            return "已失效（已被后续审核覆盖）";
        }
        return ItemStatusEnum.descOf(code);
    }

    /**
     * 第二道闸结果文案。
     *
     * @param result 结果码
     * @return 文案
     */
    private String gate2DescOf(String result) {
        if (result == null) {
            return "未执行";
        }
        return switch (result) {
            case AuditDecision.Gate2.PASS -> "AI 判定通过";
            case AuditDecision.Gate2.UNCERTAIN -> "AI 判定模糊，需人工判断";
            case AuditDecision.Gate2.BAN -> "AI 判定不可上架";
            case AuditDecision.Gate2.UNAVAILABLE -> "AI 未参与（降级）";
            default -> result;
        };
    }

    /**
     * 页码兜底。
     *
     * @param query 查询条件
     * @return 页码
     */
    private static long pageOf(ItemAuditLogQuery query) {
        return query.getCurrentPage() == null ? 1L : query.getCurrentPage();
    }

    /**
     * 每页条数兜底。
     *
     * @param query 查询条件
     * @return 条数
     */
    private static long sizeOf(ItemAuditLogQuery query) {
        return query.getPageSize() == null ? 10L : query.getPageSize();
    }

    /**
     * 解析 yyyy-MM-dd 日期，格式不对按「不筛选」处理。
     *
     * @param value 日期字符串
     * @return 日期，解析失败返回 null
     */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 非空判断。
     *
     * @param value 原值
     * @return 是否非空
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 按列宽截断（null 安全）。
     *
     * @param value     原值
     * @param maxLength 最大长度
     * @return 截断后的值
     */
    private static String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength - 1) + "…" : trimmed;
    }
}
