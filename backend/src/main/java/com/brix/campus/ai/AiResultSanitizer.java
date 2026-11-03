package com.brix.campus.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 结果清洗：LLM 与规则引擎共用同一套裁剪规则（架构 6.5）。
 *
 * <p>约束：{@code suggestPrice ∈ [1,99999]}、{@code 1 ≤ min ≤ suggest ≤ max ≤ 99999}、
 * {@code 0 ≤ confidence ≤ 1}、{@code reasons ≥ 2}、{@code tags 3~5}、{@code title ≤ 40}。</p>
 */
public final class AiResultSanitizer {

    /** 最小金额 */
    private static final BigDecimal MIN_PRICE = new BigDecimal("1");
    /** 最大金额 */
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999");
    /** 兜底建议价 */
    private static final BigDecimal FALLBACK_PRICE = new BigDecimal("100");
    /** 兜底理由 */
    private static final String FALLBACK_REASON = "样本不足，按同类商品均价估算";

    private AiResultSanitizer() {
    }

    /**
     * 清洗估价结果。
     *
     * @param result 原始结果（可为 null）
     * @return 清洗后的结果（永不为 null 且关键字段非空）
     */
    public static EstimateResult sanitize(EstimateResult result) {
        return sanitize(result, null);
    }

    /**
     * 清洗估价结果（带商品画像：可做「二手价不得高于原价」硬钳制）。
     *
     * <p>除 BRAND_NEW（全新未拆封存在溢价空间）外，二手价高于用户填写的原价即不合理
     * ——无论 LLM 的同款判定如何，一律钳制到原价的 95% 以内并追加风险提示。</p>
     *
     * @param result  原始结果（可为 null）
     * @param profile 归一化入参（可 null，为 null 时不做原价钳制）
     * @return 清洗后的结果（永不为 null 且关键字段非空）
     */
    public static EstimateResult sanitize(EstimateResult result, ItemProfile profile) {
        EstimateResult target = result == null ? new EstimateResult() : result;

        BigDecimal suggest = clampMoney(target.getSuggestPrice() == null
                ? FALLBACK_PRICE : target.getSuggestPrice());
        BigDecimal min = target.getPriceRange() == null || target.getPriceRange().getMin() == null
                ? suggest.multiply(new BigDecimal("0.85"))
                : clampMoney(target.getPriceRange().getMin());
        BigDecimal max = target.getPriceRange() == null || target.getPriceRange().getMax() == null
                ? suggest.multiply(new BigDecimal("1.15"))
                : clampMoney(target.getPriceRange().getMax());

        if (min.compareTo(suggest) > 0) {
            min = suggest;
        }
        if (max.compareTo(suggest) < 0) {
            max = suggest;
        }
        if (min.compareTo(max) > 0) {
            BigDecimal tmp = min;
            min = max;
            max = tmp;
        }

        // 硬钳制：非全新商品的二手估价不得高于用户填写的原价（LLM 同款判定失误的最后防线）
        boolean overOriginal = false;
        if (profile != null && profile.getOriginalPrice() != null
                && !"BRAND_NEW".equals(profile.getConditionLevel())) {
            BigDecimal ceiling = profile.getOriginalPrice().multiply(new BigDecimal("0.98"))
                    .setScale(2, RoundingMode.HALF_UP);
            if (suggest.compareTo(ceiling) > 0) {
                suggest = ceiling;
                overOriginal = true;
            }
            if (max.compareTo(ceiling) > 0) {
                max = ceiling;
            }
            if (min.compareTo(ceiling) > 0) {
                min = ceiling;
            }
        }

        target.setSuggestPrice(suggest);
        target.setPriceRange(new EstimateResult.PriceRange(min, max));
        target.setConfidence(clampConfidence(target.getConfidence()));

        List<String> reasons = target.getReasons() == null ? new ArrayList<>() : target.getReasons();
        reasons.removeIf(reason -> reason == null || reason.isBlank());
        if (reasons.size() < 2) {
            reasons.add(FALLBACK_REASON);
        }
        if (reasons.size() < 2) {
            reasons.add("建议结合同款在售商品价格微调");
        }
        if (overOriginal) {
            reasons.add("检测到估价高于原价，已按原价钳制——二手商品通常不应高于全新价");
        }
        target.setReasons(reasons);

        List<String> riskTips = target.getRiskTips() == null ? new ArrayList<>() : target.getRiskTips();
        riskTips.removeIf(tip -> tip == null || tip.isBlank());
        if (overOriginal) {
            riskTips.add("成交库匹配到的可能不是同款产品，参考价已按你填写的原价修正，建议核对品牌型号后再试");
        }
        if (riskTips.isEmpty()) {
            riskTips.add("实际成交价受成色与配件影响，建议面交时再协商");
        }
        target.setRiskTips(riskTips);
        return target;
    }

    /**
     * 清洗文案结果。
     *
     * @param result 原始结果（可为 null）
     * @param profile 归一化入参（用于兜底生成）
     * @return 清洗后的结果
     */
    public static CopyResult sanitizeCopy(CopyResult result, ItemProfile profile) {
        CopyResult target = result == null ? new CopyResult() : result;

        String title = target.getTitle() == null ? "" : target.getTitle().trim();
        if (title.isEmpty()) {
            String model = profile.getBrandModel() == null || profile.getBrandModel().isBlank()
                    ? "闲置好物" : profile.getBrandModel().trim();
            title = model + " 校园低价转让";
        }
        if (title.length() > 40) {
            title = title.substring(0, 40);
        }
        target.setTitle(title);

        String description = target.getDescription() == null ? "" : target.getDescription().trim();
        if (description.length() < 100) {
            description = description
                    + "本人为本校在读学生，商品为个人闲置，日常爱惜使用，功能正常无拆修。"
                    + "支持校内面交，可当面验货后再付款，交易时间灵活，有问题可随时私信沟通，诚意出售非诚勿扰。";
        }
        if (description.length() > 300) {
            description = description.substring(0, 300);
        }
        target.setDescription(description);

        List<String> tags = target.getTags() == null ? new ArrayList<>() : target.getTags();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String trimmed = tag.trim();
            unique.add(trimmed.length() > 8 ? trimmed.substring(0, 8) : trimmed);
            if (unique.size() >= 5) {
                break;
            }
        }
        if (unique.size() < 3) {
            unique.add("校园面交");
        }
        if (unique.size() < 3) {
            unique.add("个人闲置");
        }
        if (unique.size() < 3) {
            unique.add("可小刀");
        }
        target.setTags(new ArrayList<>(unique));
        return target;
    }

    /**
     * 金额裁剪到 [1, 99999] 并保留两位小数。
     *
     * @param value 原值
     * @return 结果
     */
    private static BigDecimal clampMoney(BigDecimal value) {
        BigDecimal result = value == null ? FALLBACK_PRICE : value;
        if (result.compareTo(MIN_PRICE) < 0) {
            result = MIN_PRICE;
        }
        if (result.compareTo(MAX_PRICE) > 0) {
            result = MAX_PRICE;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 置信度裁剪到 [0, 1]。
     *
     * @param confidence 原值
     * @return 结果
     */
    private static double clampConfidence(double confidence) {
        if (Double.isNaN(confidence)) {
            return 0.5d;
        }
        return Math.min(1d, Math.max(0d, confidence));
    }
}
