package com.brix.campus.service.impl;

import com.brix.campus.ai.AiEstimator;
import com.brix.campus.ai.AiQuotaGuard;
import com.brix.campus.ai.AiResultSanitizer;
import com.brix.campus.ai.CopyResult;
import com.brix.campus.ai.EstimateResult;
import com.brix.campus.ai.ItemProfile;
import com.brix.campus.ai.LlmEstimator;
import com.brix.campus.ai.NewPriceHint;
import com.brix.campus.ai.RuleBasedEstimator;
import com.brix.campus.common.BusinessException;
import com.brix.campus.model.dto.AiCopyDTO;
import com.brix.campus.model.dto.AiEstimateDTO;
import com.brix.campus.model.enums.AiSceneEnum;
import com.brix.campus.model.enums.ConditionEnum;
import com.brix.campus.model.vo.CopyResultVO;
import com.brix.campus.model.vo.EstimateResultVO;
import com.brix.campus.service.AiEstimateService;
import com.brix.campus.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 估价服务实现。
 *
 * <p>异常一律降级：LLM 超时 / HTTP 4xx / JSON 解析失败都退回本地规则，接口仍返回 200（J01、J03）。</p>
 */
@Service
public class AiEstimateServiceImpl implements AiEstimateService {

    private static final Logger log = LoggerFactory.getLogger(AiEstimateServiceImpl.class);

    /** 日志中的模型名占位（降级时） */
    private static final String RULE_MODEL = "rule-based";

    private final AiEstimator estimator;
    private final RuleBasedEstimator ruleBasedEstimator;
    private final AiQuotaGuard quotaGuard;
    private final CategoryService categoryService;

    public AiEstimateServiceImpl(AiEstimator estimator,
                                 RuleBasedEstimator ruleBasedEstimator,
                                 AiQuotaGuard quotaGuard,
                                 CategoryService categoryService) {
        this.estimator = estimator;
        this.ruleBasedEstimator = ruleBasedEstimator;
        this.quotaGuard = quotaGuard;
        this.categoryService = categoryService;
    }

    @Override
    public EstimateResultVO estimate(Long userId, AiEstimateDTO dto) {
        quotaGuard.checkAndReserve(userId, AiSceneEnum.ESTIMATE);
        ItemProfile profile = buildProfile(dto.getCategoryId(), dto.getBrandModel(),
                dto.getConditionLevel(), dto.getPurchaseMonths(), dto.getOriginalPrice());

        long start = System.currentTimeMillis();
        EstimateResult result;
        String source = estimator.source();
        try {
            // 阶段一（仅 LLM 路径）：组装定价参考数据包——用户原价 + LLM 新品价估计 + 原价折价参考
            String pricingHints = LlmEstimator.SOURCE.equals(source) ? buildPricingHints(profile) : null;
            // 阶段二：主估价（LLM 会再自行调成交库工具，结合数据包综合定价）
            result = estimator.estimate(profile, pricingHints);
            if (result == null || result.getSuggestPrice() == null) {
                throw new IllegalStateException("模型未返回有效建议价");
            }
        } catch (Exception e) {
            log.warn("AI 估价降级为本地规则：{}", e.getMessage());
            result = ruleBasedEstimator.estimate(profile, null);
            source = RuleBasedEstimator.SOURCE;
        }
        result = AiResultSanitizer.sanitize(result, profile);

        long cost = System.currentTimeMillis() - start;
        quotaGuard.record(userId, AiSceneEnum.ESTIMATE, source,
                LlmEstimator.SOURCE.equals(source) ? "llm" : RULE_MODEL,
                digest(profile), true, cost);

        EstimateResultVO vo = new EstimateResultVO();
        vo.setSuggestPrice(result.getSuggestPrice());
        vo.setPriceRange(new EstimateResultVO.PriceRangeVO(
                result.getPriceRange().getMin(), result.getPriceRange().getMax()));
        vo.setConfidence(result.getConfidence());
        vo.setReasons(result.getReasons());
        vo.setRiskTips(result.getRiskTips());
        vo.setSource(source);
        vo.setRemainingQuota(quotaGuard.remaining(userId));
        return vo;
    }

    @Override
    public CopyResultVO copywriting(Long userId, AiCopyDTO dto) {
        quotaGuard.checkAndReserve(userId, AiSceneEnum.COPY);
        ItemProfile profile = buildProfile(dto.getCategoryId(), dto.getBrandModel(),
                dto.getConditionLevel(), dto.getPurchaseMonths(), dto.getOriginalPrice());

        long start = System.currentTimeMillis();
        CopyResult result;
        String source = estimator.source();
        try {
            result = estimator.copywrite(profile);
            if (result == null || result.getTitle() == null || result.getTitle().isBlank()) {
                throw new IllegalStateException("模型未返回有效标题");
            }
        } catch (Exception e) {
            log.warn("AI 文案生成降级为本地规则：{}", e.getMessage());
            result = ruleBasedEstimator.copywrite(profile);
            source = RuleBasedEstimator.SOURCE;
        }
        result = AiResultSanitizer.sanitizeCopy(result, profile);

        long cost = System.currentTimeMillis() - start;
        quotaGuard.record(userId, AiSceneEnum.COPY, source,
                LlmEstimator.SOURCE.equals(source) ? "llm" : RULE_MODEL,
                digest(profile), true, cost);

        CopyResultVO vo = new CopyResultVO();
        vo.setTitle(result.getTitle());
        vo.setDescription(result.getDescription());
        vo.setTags(result.getTags());
        vo.setSource(source);
        vo.setRemainingQuota(quotaGuard.remaining(userId));
        return vo;
    }

    /**
     * 组装归一化入参（解析分类名，供规则引擎匹配品类基准价）。
     *
     * @param categoryId      分类 ID
     * @param brandModel      品牌型号
     * @param conditionLevel  成色
     * @param purchaseMonths  购入月数
     * @param originalPrice   原价
     * @return 归一化入参
     */
    private ItemProfile buildProfile(Long categoryId, String brandModel, String conditionLevel,
                                     Integer purchaseMonths, java.math.BigDecimal originalPrice) {
        if (categoryId == null) {
            throw BusinessException.param("分类不能为空");
        }
        String categoryName = categoryService.nameOf(categoryId);
        if (categoryName.isEmpty()) {
            throw BusinessException.param("分类不存在");
        }
        if (!ConditionEnum.isValid(conditionLevel)) {
            throw BusinessException.param("成色不合法");
        }
        ItemProfile profile = new ItemProfile();
        profile.setCategoryId(categoryId);
        profile.setCategoryName(categoryName);
        profile.setCategoryFullName(categoryService.displayNameOf(categoryId));
        profile.setBrandModel(brandModel == null ? "" : brandModel.trim());
        profile.setConditionLevel(conditionLevel);
        profile.setPurchaseMonths(purchaseMonths);
        profile.setOriginalPrice(originalPrice);
        return profile;
    }

    /**
     * 生成入参摘要（不存敏感信息）。
     *
     * @param profile 归一化入参
     * @return 摘要文本
     */
    private String digest(ItemProfile profile) {
        return ("category=" + profile.getCategoryName()
                + "; model=" + profile.getBrandModel()
                + "; condition=" + profile.getConditionLevel()
                + "; months=" + profile.getPurchaseMonths());
    }

    /**
     * 组装定价参考数据包（估价阶段一，仅 LLM 路径调用）。
     *
     * <p>① 用户填写了原价 → 直接作为最可靠锚点；② 未填原价 → 先用 LLM 知识查
     * 新品参考价区间（失败静默跳过），计算「新品价 × 折价系数」作为第二锚点。
     * 一次 estimate 请求内部至多 2 次 LLM 调用，额度仍按 1 次计。</p>
     *
     * @param profile 归一化入参
     * @return 数据包文本；无可用锚点返回 null
     */
    private String buildPricingHints(ItemProfile profile) {
        StringBuilder sb = new StringBuilder();
        double factor = ConditionEnum.factorOf(profile.getConditionLevel());

        // ① 用户原价：最可靠的锚点
        if (profile.getOriginalPrice() != null) {
            sb.append("- 用户填写原价：¥").append(profile.getOriginalPrice()).append("\n")
              .append("- 原价折价参考：¥").append(profile.getOriginalPrice()).append(" × ")
              .append(factor).append("（").append(profile.getConditionLevel())
              .append("）≈ ¥").append(profile.getOriginalPrice()
                      .multiply(java.math.BigDecimal.valueOf(factor))
                      .setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
        } else if (profile.getBrandModel() != null && !profile.getBrandModel().isBlank()) {
            // ② 未填原价 → LLM 知识库估新品价（失败静默跳过，不阻断主流程）
            try {
                NewPriceHint hint = estimator.estimateNewPrice(profile);
                if (hint != null && hint.newPriceLow() != null && hint.newPriceHigh() != null
                        && hint.newPriceHigh().signum() > 0) {
                    java.math.BigDecimal mid = hint.newPriceLow().add(hint.newPriceHigh())
                            .divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                    sb.append("- LLM 新品价估计（AI 知识库，仅供参考）：¥").append(hint.newPriceLow())
                      .append("~¥").append(hint.newPriceHigh())
                      .append("（依据：").append(hint.basis()).append("）\n")
                      .append("- 新品价折价参考：区间中值 ¥").append(mid).append(" × ")
                      .append(factor).append(" ≈ ¥").append(mid.multiply(java.math.BigDecimal.valueOf(factor))
                              .setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
                }
            } catch (Exception e) {
                log.warn("LLM 新品价估计失败（跳过该锚点）：{}", e.getMessage());
            }
        }

        if (sb.length() == 0) {
            return null;
        }
        sb.append("- 数据优先级：同款成交记录 > 用户原价 > LLM 新品价估计；引用 LLM 新品价时注明其为知识库估计");
        return sb.toString();
    }
}
