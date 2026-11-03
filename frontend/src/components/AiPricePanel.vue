<template>
  <div class="ai-panel brix-card">
    <div class="ai-panel__head">
      <div>
        <h3 class="ai-panel__title">
          <el-icon><MagicStick /></el-icon>
          AI 帮我定价 / 写文案
        </h3>
        <p class="ai-panel__sub">填写品牌型号等信息，一键得到建议价与可填入表单的标题描述</p>
      </div>
      <el-tag v-if="remainingQuota !== null" size="small" effect="plain">
        今日剩余 {{ remainingQuota }} 次
      </el-tag>
    </div>

    <div class="ai-panel__form">
      <el-input v-model="brandModel" placeholder="品牌型号，如 iPad 9 64G" class="ai-panel__input" />
      <el-input-number v-model="profile.purchaseMonths" :min="0" :max="240" :controls="false" placeholder="购入月数" class="ai-panel__number" />
      <el-input-number v-model="originalPrice" :min="0" :precision="2" :controls="false" placeholder="原价（与左侧表单同步）" class="ai-panel__number" />
      <el-button type="primary" :loading="estimating" @click="doEstimate">AI 帮我定价</el-button>
      <el-button :loading="writing" @click="doCopywriting">生成文案</el-button>
    </div>

    <div v-if="estimate" class="ai-panel__result">
      <div class="ai-panel__price-row">
        <div>
          <div class="ai-panel__label">建议价</div>
          <div class="ai-panel__price">
            <span class="ai-panel__price-symbol">¥</span>{{ formatPrice(estimate.suggestPrice) }}
          </div>
          <div class="ai-panel__range">
            区间 ¥{{ formatPrice(estimate.priceRange?.min) }} ~ ¥{{ formatPrice(estimate.priceRange?.max) }}
          </div>
        </div>
        <div class="ai-panel__confidence">
          <div class="ai-panel__label">置信度</div>
          <el-progress
            :percentage="Math.round((estimate.confidence || 0) * 100)"
            :stroke-width="8"
            :color="confidenceColor"
            class="ai-panel__progress"
          />
          <el-tag size="small" effect="plain">{{ estimate.source === 'LLM' ? 'AI 模型' : '本地规则' }}</el-tag>
        </div>
      </div>

      <div class="ai-panel__reasons">
        <div class="ai-panel__label">定价依据</div>
        <ul>
          <li v-for="(reason, index) in estimate.reasons" :key="index">{{ reason }}</li>
        </ul>
      </div>

      <div v-if="estimate.riskTips?.length" class="ai-panel__risks">
        <div class="ai-panel__label">风险提示</div>
        <ul>
          <li v-for="(tip, index) in estimate.riskTips" :key="index">{{ tip }}</li>
        </ul>
      </div>

      <el-button type="primary" plain size="small" @click="emit('apply-price', estimate.suggestPrice)">
        使用此价
      </el-button>
    </div>

    <div v-if="copywriting" class="ai-panel__copy">
      <div class="ai-panel__label">生成结果</div>
      <p class="ai-panel__copy-title">{{ copywriting.title }}</p>
      <p class="ai-panel__copy-desc">{{ copywriting.description }}</p>
      <div class="ai-panel__tags">
        <el-tag v-for="tag in copywriting.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
      </div>
      <el-button type="primary" plain size="small" class="brix-mt-8" @click="emit('apply-copy', copywriting)">
        填入表单
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { estimatePrice, generateCopywriting } from '@/api/ai'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  categoryId: { type: [String, Number], default: '' },
  conditionLevel: { type: String, default: 'LIGHT_USE' }
})

const emit = defineEmits(['apply-price', 'apply-copy'])

/** 品牌型号与发布表单双向同步（v-model:brand-model） */
const brandModel = defineModel('brandModel', { type: String, default: '' })

/** 原价与发布表单双向同步（v-model:original-price）——估价直接用表单里填的原价 */
const originalPrice = defineModel('originalPrice', { type: Number, default: null })

const profile = reactive({
  purchaseMonths: 12
})

const estimating = ref(false)
const writing = ref(false)
const estimate = ref(null)
const copywriting = ref(null)
const remainingQuota = ref(null)

const confidenceColor = computed(() => {
  const value = estimate.value?.confidence || 0
  if (value >= 0.75) return '#0FA968'
  if (value >= 0.55) return '#E6A23C'
  return '#909399'
})

/** 组装 AI 入参 */
function payload() {
  return {
    categoryId: props.categoryId,
    brandModel: brandModel.value,
    conditionLevel: props.conditionLevel,
    purchaseMonths: profile.purchaseMonths ?? null,
    originalPrice: originalPrice.value ?? null
  }
}

/** 校验必填 */
function validate() {
  if (!props.categoryId) {
    ElMessage.warning('请先选择分类')
    return false
  }
  if (!brandModel.value || !brandModel.value.trim()) {
    ElMessage.warning('请填写品牌型号')
    return false
  }
  if (!props.conditionLevel) {
    ElMessage.warning('请先选择成色')
    return false
  }
  return true
}

/** 智能估价 */
async function doEstimate() {
  if (!validate()) return
  estimating.value = true
  try {
    const data = await estimatePrice(payload())
    estimate.value = data
    remainingQuota.value = data?.remainingQuota ?? null
  } catch (error) {
    // request.js 已提示（含 429 每日限额）
  } finally {
    estimating.value = false
  }
}

/** 生成文案 */
async function doCopywriting() {
  if (!validate()) return
  writing.value = true
  try {
    const data = await generateCopywriting({ ...payload(), suggestPrice: estimate.value?.suggestPrice ?? null })
    copywriting.value = data
    remainingQuota.value = data?.remainingQuota ?? null
  } catch (error) {
    // request.js 已提示
  } finally {
    writing.value = false
  }
}
</script>

<style scoped>
.ai-panel {
  padding: 16px;
  background: linear-gradient(180deg, #f4fdf8 0%, #ffffff 60%);
}

.ai-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-panel__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}

.ai-panel__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.ai-panel__form {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.ai-panel__input {
  width: 220px;
}

.ai-panel__number {
  width: 130px;
}

.ai-panel__result,
.ai-panel__copy {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 0.5px dashed var(--brix-border);
}

.ai-panel__label {
  font-size: 12px;
  color: var(--brix-text-placeholder);
  margin-bottom: 6px;
}

.ai-panel__price-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.ai-panel__price {
  color: var(--brix-price);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.ai-panel__price-symbol {
  font-size: 15px;
  margin-right: 2px;
}

.ai-panel__range {
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.ai-panel__confidence {
  width: 220px;
}

.ai-panel__progress {
  margin-bottom: 6px;
}

.ai-panel__reasons ul,
.ai-panel__risks ul {
  padding-left: 16px;
  list-style: disc;
  font-size: 13px;
  color: var(--brix-text-secondary);
  line-height: 1.7;
}

.ai-panel__risks {
  margin: 12px 0;
}

.ai-panel__copy-title {
  font-weight: 600;
}

.ai-panel__copy-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--brix-text-secondary);
  line-height: 1.7;
  white-space: pre-wrap;
}

.ai-panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
</style>
