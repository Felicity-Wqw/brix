<template>
  <section class="filter-bar brix-card">
    <!-- ① 一级分类：图标卡片（App 风格） -->
    <div class="filter-bar__cats">
      <button
        type="button"
        class="cat-card"
        :class="{ 'is-active': !topCategoryId }"
        @click="selectTop('')"
      >
        <span class="cat-card__icon"><el-icon><Grid /></el-icon></span>
        <span class="cat-card__label">全部</span>
      </button>
      <button
        v-for="c in topCategories"
        :key="c.id"
        type="button"
        class="cat-card"
        :class="{ 'is-active': String(topCategoryId) === String(c.id) }"
        @click="selectTop(c.id)"
      >
        <span class="cat-card__icon"><el-icon><component :is="c.icon || 'Grid'" /></el-icon></span>
        <span class="cat-card__label">{{ c.name }}</span>
      </button>
    </div>

    <!-- ② 二级分类：胶囊 chips（选中一级后出现） -->
    <div v-if="topCategoryId && subCategories.length" class="filter-bar__subs">
      <button
        type="button"
        class="sub-chip"
        :class="{ 'is-active': !subCategoryId }"
        @click="selectSub('')"
      >
        全部{{ activeTopName }}
      </button>
      <button
        v-for="s in subCategories"
        :key="s.id"
        type="button"
        class="sub-chip"
        :class="{ 'is-active': String(subCategoryId) === String(s.id) }"
        @click="selectSub(s.id)"
      >
        {{ s.name }}
      </button>
    </div>

    <div class="filter-bar__divider"></div>

    <!-- ③ 条件行：校区胶囊 / 价格 / 排序 / 关键词标签 -->
    <div class="filter-bar__row">
      <div class="filter-bar__group">
        <span class="filter-bar__group-label">校区</span>
        <button
          type="button"
          class="seg-chip"
          :class="{ 'is-active': !form.campus }"
          @click="setCampus('')"
        >
          全部
        </button>
        <button
          v-for="c in campusOptions"
          :key="c"
          type="button"
          class="seg-chip"
          :class="{ 'is-active': form.campus === c }"
          @click="setCampus(c)"
        >
          {{ c }}
        </button>
      </div>

      <div class="filter-bar__group">
        <span class="filter-bar__group-label">价格</span>
        <el-input
          v-model="minPriceText"
          class="filter-bar__price-input"
          placeholder="最低"
          @change="emitChange"
        >
          <template #prefix>¥</template>
        </el-input>
        <span class="filter-bar__dash">–</span>
        <el-input
          v-model="maxPriceText"
          class="filter-bar__price-input"
          placeholder="最高"
          @change="emitChange"
        >
          <template #prefix>¥</template>
        </el-input>
      </div>

      <div class="filter-bar__group">
        <span class="filter-bar__group-label">排序</span>
        <el-select v-model="form.sort" class="filter-bar__sort" @change="emitChange">
          <el-option v-for="s in sortOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </div>

      <div v-if="form.keyword" class="filter-bar__group">
        <span class="filter-bar__group-label">关键词</span>
        <span class="kw-chip">
          「{{ form.keyword }}」
          <el-icon class="kw-chip__close" @click="clearKeyword"><CircleCloseFilled /></el-icon>
        </span>
      </div>

      <el-button text type="primary" class="filter-bar__reset" @click="reset">重置</el-button>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { CircleCloseFilled, Grid } from '@element-plus/icons-vue'
import { CAMPUS_OPTIONS, SORT_OPTIONS } from '@/constants'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  /** 完整分类列表（一级 + 二级，含 parentId 字段） */
  categories: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'change'])

const campusOptions = CAMPUS_OPTIONS
const sortOptions = SORT_OPTIONS

// 本地副本：子组件绝不直接改 props
const form = reactive({
  keyword: '',
  categoryId: '',
  campus: '',
  minPrice: null,
  maxPrice: null,
  sort: 'latest'
})

/** 联动用的分类选择值（一级/二级 ID） */
const topCategoryId = ref('')
const subCategoryId = ref('')

const minPriceText = ref('')
const maxPriceText = ref('')

// ---------------- 分类二级联动 ----------------
/** 注意：后端 Long 序列化为字符串（"parentId": "0"），必须用 String 比较 */
const topCategories = computed(() =>
  (props.categories || []).filter((c) => !c.parentId || String(c.parentId) === '0')
)

const subCategories = computed(() => {
  if (!topCategoryId.value) return []
  return (props.categories || []).filter(
    (c) => c.parentId && String(c.parentId) === String(topCategoryId.value)
  )
})

const activeTopName = computed(() => {
  const top = topCategories.value.find((c) => String(c.id) === String(topCategoryId.value))
  return top ? top.name : ''
})

/** 点一级图标卡：空 = 全部分类 */
function selectTop(value) {
  subCategoryId.value = ''
  topCategoryId.value = value || ''
  form.categoryId = value || ''
  emitChange()
}

/** 点二级胶囊 */
function selectSub(value) {
  subCategoryId.value = value || ''
  form.categoryId = value || topCategoryId.value || ''
  emitChange()
}

function setCampus(value) {
  form.campus = value
  emitChange()
}

function clearKeyword() {
  form.keyword = ''
  emitChange()
}

/**
 * 由 categoryId 反推大类/子类回显（URL 直达或父级同步时用）。
 *
 * @param categoryId 分类 ID（大类或子类）
 */
function syncCategorySelects(categoryId) {
  if (!categoryId) {
    topCategoryId.value = ''
    subCategoryId.value = ''
    return
  }
  const current = (props.categories || []).find((c) => String(c.id) === String(categoryId))
  if (!current) {
    // 分类数据未加载完成时先只显示大类值，加载后 watch 会再同步
    topCategoryId.value = categoryId
    subCategoryId.value = ''
    return
  }
  if (current.parentId && String(current.parentId) !== '0') {
    topCategoryId.value = current.parentId
    subCategoryId.value = current.id
  } else {
    topCategoryId.value = current.id
    subCategoryId.value = ''
  }
}

// 父级（URL query）变化时同步到本地表单
watch(
  () => props.modelValue,
  (value) => {
    if (!value) return
    form.keyword = value.keyword || ''
    form.categoryId = value.categoryId || ''
    form.campus = value.campus || ''
    form.minPrice = value.minPrice === '' || value.minPrice === undefined || value.minPrice === null
      ? null
      : Number(value.minPrice)
    form.maxPrice = value.maxPrice === '' || value.maxPrice === undefined || value.maxPrice === null
      ? null
      : Number(value.maxPrice)
    form.sort = value.sort || 'latest'
    minPriceText.value = form.minPrice === null ? '' : String(form.minPrice)
    maxPriceText.value = form.maxPrice === null ? '' : String(form.maxPrice)
    syncCategorySelects(form.categoryId)
  },
  { immediate: true, deep: true }
)

// 分类字典异步加载完成后，再按当前 categoryId 修正大类/子类回显
watch(
  () => props.categories,
  () => syncCategorySelects(form.categoryId),
  { deep: true }
)

/** 条件变化：向上抛出（父级负责回到第 1 页并写 URL） */
function emitChange() {
  form.minPrice = minPriceText.value === '' ? null : Number(minPriceText.value)
  form.maxPrice = maxPriceText.value === '' ? null : Number(maxPriceText.value)
  const payload = {
    keyword: form.keyword || '',
    categoryId: form.categoryId || '',
    campus: form.campus || '',
    minPrice: form.minPrice ?? '',
    maxPrice: form.maxPrice ?? '',
    sort: form.sort || 'latest'
  }
  emit('update:modelValue', payload)
  emit('change', payload)
}

/** 重置筛选 */
function reset() {
  form.keyword = ''
  form.categoryId = ''
  form.campus = ''
  form.minPrice = null
  form.maxPrice = null
  form.sort = 'latest'
  minPriceText.value = ''
  maxPriceText.value = ''
  topCategoryId.value = ''
  subCategoryId.value = ''
  emitChange()
}
</script>

<style scoped>
.filter-bar {
  padding: 18px 20px 14px;
  margin-bottom: 22px;
  border-radius: var(--brix-radius-lg);
}

/* ---------- ① 一级分类图标卡 ---------- */
.filter-bar__cats {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.cat-card {
  flex-shrink: 0;
  width: 82px;
  padding: 12px 4px 10px;
  border: none;
  border-radius: 14px;
  background: rgba(28, 42, 36, 0.045);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  transition: background 0.22s cubic-bezier(0.25, 1, 0.5, 1), color 0.22s ease, transform 0.22s cubic-bezier(0.25, 1, 0.5, 1);
}

.cat-card:hover {
  background: rgba(28, 42, 36, 0.08);
  transform: translateY(-2px);
}

.cat-card.is-active {
  background: var(--brix-primary);
}

.cat-card__icon {
  display: inline-flex;
  font-size: 22px;
  color: var(--brix-text);
}

.cat-card__label {
  font-size: 12px;
  font-weight: 500;
  color: var(--brix-text-secondary);
  white-space: nowrap;
}

.cat-card.is-active .cat-card__icon,
.cat-card.is-active .cat-card__label {
  color: #fff;
}

/* ---------- ② 二级胶囊 ---------- */
.filter-bar__subs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.sub-chip {
  padding: 5px 14px;
  border: none;
  border-radius: 999px;
  background: rgba(28, 42, 36, 0.045);
  color: var(--brix-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s cubic-bezier(0.25, 1, 0.5, 1);
}

.sub-chip:hover {
  background: rgba(28, 42, 36, 0.09);
  color: var(--brix-text);
}

.sub-chip.is-active {
  background: var(--brix-primary);
  color: #fff;
  font-weight: 500;
}

/* ---------- 分割线 ---------- */
.filter-bar__divider {
  margin: 14px 0 12px;
  border-top: 0.5px solid var(--brix-border);
}

/* ---------- ③ 条件行 ---------- */
.filter-bar__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 22px;
}

.filter-bar__group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-bar__group-label {
  font-size: 12px;
  color: var(--brix-text-placeholder);
  margin-right: 2px;
}

.seg-chip {
  padding: 4px 13px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--brix-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.seg-chip:hover {
  color: var(--brix-text);
}

.seg-chip.is-active {
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
  font-weight: 600;
}

.filter-bar__price-input {
  width: 84px;
}

.filter-bar__price-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  padding-left: 12px;
}

.filter-bar__dash {
  color: var(--brix-text-placeholder);
}

.filter-bar__sort {
  width: 130px;
}

.filter-bar__sort :deep(.el-select__wrapper) {
  border-radius: 999px;
}

/* 当前关键词标签（来自顶部导航搜索） */
.kw-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 4px 12px;
  border-radius: 999px;
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
  font-size: 13px;
  font-weight: 500;
}

.kw-chip__close {
  cursor: pointer;
  font-size: 15px;
  opacity: 0.7;
}

.kw-chip__close:hover {
  opacity: 1;
}

.filter-bar__reset {
  margin-left: auto;
}
</style>
