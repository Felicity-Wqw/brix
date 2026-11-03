package com.brix.campus.ai;

import com.brix.campus.model.enums.ConditionEnum;
import com.brix.campus.service.CategoryService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地规则估价引擎（无 API Key / LLM 超时时自动接管，PRD 7.6、架构 6.5）。
 *
 * <p>公式：{@code 建议价 = 品类基准价 × 成色系数 × 时间折损}，
 * 并用原价牵引（不超过「原价 × 成色系数」）与基准价 20% 下限做保护。</p>
 */
public class RuleBasedEstimator implements AiEstimator {

    /** 来源标识 */
    public static final String SOURCE = "RULE";
    /** 降级标注文案（J03 硬要求） */
    public static final String RULE_NOTE = "基于本地规则估算，仅供参考";
    /** 兜底品类基准价 */
    private static final BigDecimal DEFAULT_BASE_PRICE = new BigDecimal("200");
    /** 基准价下限比例 */
    private static final BigDecimal MIN_RATIO = new BigDecimal("0.2");
    /** 区间下浮 */
    private static final BigDecimal RANGE_LOW = new BigDecimal("0.85");
    /** 区间上浮 */
    private static final BigDecimal RANGE_HIGH = new BigDecimal("1.15");
    /** 最小金额 */
    private static final BigDecimal MIN_PRICE = new BigDecimal("1");
    /** 最大金额 */
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999");

    private final CategoryService categoryService;
    private final AiRepository aiRepository;

    public RuleBasedEstimator(CategoryService categoryService, AiRepository aiRepository) {
        this.categoryService = categoryService;
        this.aiRepository = aiRepository;
    }

    @Override
    public EstimateResult estimate(ItemProfile profile, String pricingHints) {
        // 规则引擎不消费定价参考数据包，忽略 hints
        String categoryName = resolveCategoryName(profile);
        BigDecimal basePrice = basePriceOf(categoryName);
        double conditionFactor = ConditionEnum.factorOf(profile.getConditionLevel());
        int months = profile.getPurchaseMonths() == null ? 0 : Math.max(0, profile.getPurchaseMonths());
        int years = months / 12;
        BigDecimal timeFactor = BigDecimal.ONE.subtract(new BigDecimal("0.1").multiply(BigDecimal.valueOf(years)));
        if (timeFactor.compareTo(new BigDecimal("0.5")) < 0) {
            timeFactor = new BigDecimal("0.5");
        }

        BigDecimal suggest = basePrice
                .multiply(BigDecimal.valueOf(conditionFactor))
                .multiply(timeFactor);

        // 原价牵引：建议价不应高于「原价 × 成色系数」
        if (profile.getOriginalPrice() != null && profile.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cap = profile.getOriginalPrice().multiply(BigDecimal.valueOf(conditionFactor));
            if (cap.compareTo(suggest) < 0) {
                suggest = cap;
            }
        }
        // 下限保护：不低于品类基准价的 20%
        BigDecimal floor = basePrice.multiply(MIN_RATIO);
        if (suggest.compareTo(floor) < 0) {
            suggest = floor;
        }
        suggest = clamp(round2(suggest));

        BigDecimal min = clamp(round2(suggest.multiply(RANGE_LOW)));
        BigDecimal max = clamp(round2(suggest.multiply(RANGE_HIGH)));
        if (min.compareTo(suggest) > 0) {
            min = suggest;
        }
        if (max.compareTo(suggest) < 0) {
            max = suggest;
        }

        int soldCount = aiRepository.soldCount(profile.getBrandModel());
        double confidence = soldCount > 0 ? 0.60d : 0.55d;

        List<String> reasons = new ArrayList<>();
        reasons.add("同类商品基准价 ¥" + basePrice.setScale(2, RoundingMode.HALF_UP)
                + "（分类：" + (categoryName.isEmpty() ? "未分类" : categoryName) + "）");
        reasons.add("成色「" + ConditionEnum.descOf(profile.getConditionLevel())
                + "」折价系数 " + conditionFactor);
        if (months > 0) {
            int discountPercent = Math.min(50, years * 10);
            reasons.add("使用 " + months + " 个月，时间折损 -" + discountPercent + "%");
        } else {
            reasons.add("未填写购入时间，时间折损按 0% 计算");
        }
        if (soldCount > 0) {
            reasons.add("已参考近 90 天同款 " + soldCount + " 笔成交记录校准");
        }
        reasons.add(RULE_NOTE);

        List<String> riskTips = new ArrayList<>();
        riskTips.add("无历史成交数据时偏差较大，建议参考同类在售商品价格");
        riskTips.add("开学季/毕业季价格波动明显，可在区间内上下浮动 10%");

        EstimateResult result = new EstimateResult();
        result.setSuggestPrice(suggest);
        result.setPriceRange(new EstimateResult.PriceRange(min, max));
        result.setConfidence(confidence);
        result.setReasons(reasons);
        result.setRiskTips(riskTips);
        return result;
    }

    @Override
    public CopyResult copywrite(ItemProfile profile) {
        String categoryName = resolveCategoryName(profile);
        String model = profile.getBrandModel() == null || profile.getBrandModel().isBlank()
                ? "闲置好物" : profile.getBrandModel().trim();
        String conditionDesc = ConditionEnum.descOf(profile.getConditionLevel());

        String title = model + " " + conditionDesc + " 校园低价转让";
        if (title.length() > 40) {
            title = title.substring(0, 40);
        }

        StringBuilder description = new StringBuilder();
        description.append("【成色】").append(model).append("，成色为「").append(conditionDesc)
                .append("」，日常自用、爱惜使用，功能一切正常，具体外观瑕疵以实拍图为准。");
        description.append("【使用体验】")
                .append(profile.getPurchaseMonths() == null
                        ? "购入时间较久但保养良好，"
                        : "已使用约 " + profile.getPurchaseMonths() + " 个月，")
                .append("配件与包装尽量一并附上，到手即可正常使用，无异味无拆修，个人闲置诚意出售。");
        description.append("【交易方式】优先校内面交，可当面验机后再交易，支持在宿舍楼下或教学楼门口碰面；")
                .append("需要邮寄的同学也可以商量，邮费按实际情况协商，售后问题可随时私信沟通。");

        String text = description.toString();
        if (text.length() < 100) {
            text = text + "本人是本校在读学生，交易时间灵活，欢迎私信询问更多细节，看到消息会尽快回复。";
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }

        List<String> tags = new ArrayList<>();
        if (!categoryName.isEmpty() && categoryName.length() <= 8) {
            tags.add(categoryName);
        }
        tags.add("校园面交");
        tags.add("个人闲置");
        if (model.length() <= 8) {
            tags.add(model);
        } else {
            tags.add(model.substring(0, 8));
        }
        tags.add("可小刀");

        CopyResult result = new CopyResult();
        result.setTitle(title);
        result.setDescription(text);
        result.setTags(tags);
        return result;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public NewPriceHint estimateNewPrice(ItemProfile profile) {
        // 规则引擎无 LLM 知识能力，不提供新品价锚点
        return null;
    }

    /**
     * 解析分类名（优先用入参已带的名称，缺失时查库）。
     *
     * @param profile 归一化入参
     * @return 分类名，未知返回空串
     */
    private String resolveCategoryName(ItemProfile profile) {
        if (profile.getCategoryName() != null && !profile.getCategoryName().isBlank()) {
            return profile.getCategoryName();
        }
        if (profile.getCategoryId() == null) {
            return "";
        }
        String name = categoryService.nameOf(profile.getCategoryId());
        return name == null ? "" : name;
    }

    /**
     * 品类基准价映射（PRD 7.6 常量表）。
     *
     * @param categoryName 分类名
     * @return 基准价
     */
    private BigDecimal basePriceOf(String categoryName) {
        if (categoryName == null) {
            return DEFAULT_BASE_PRICE;
        }
        return switch (categoryName) {
            case "数码电子" -> new BigDecimal("1500");
            case "电脑办公" -> new BigDecimal("3000");
            case "图书教材" -> new BigDecimal("80");
            case "生活用品" -> new BigDecimal("120");
            case "运动户外" -> new BigDecimal("300");
            case "美妆个护" -> new BigDecimal("150");
            default -> DEFAULT_BASE_PRICE;
        };
    }

    /**
     * 两位小数四舍五入。
     *
     * @param value 原值
     * @return 结果
     */
    private BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 金额裁剪到 [1, 99999]。
     *
     * @param value 原值
     * @return 结果
     */
    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(MIN_PRICE) < 0) {
            return MIN_PRICE.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(MAX_PRICE) > 0) {
            return MAX_PRICE.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
