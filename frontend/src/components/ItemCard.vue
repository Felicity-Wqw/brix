<template>
  <router-link :to="`/items/${item.id}`" class="item-card">
    <div class="item-card__cover">
      <el-image :src="resolveImage(item.coverImage)" fit="cover" class="item-card__image" lazy>
        <template #error>
          <div class="item-card__placeholder">
            <el-icon><Picture /></el-icon>
          </div>
        </template>
      </el-image>
      <span v-if="showStatus" class="item-card__status">
        <StatusTag kind="item" :value="item.status" effect="dark" />
      </span>
      <span v-if="item.status === 'SOLD'" class="item-card__mask">已售出</span>
    </div>

    <div class="item-card__body">
      <h3 class="item-card__title" :title="item.title">{{ item.title }}</h3>
      <div class="item-card__price">
        <PriceTag :price="item.price" :original-price="item.originalPrice" size="small" />
      </div>
      <div class="item-card__meta">
        <ConditionTag :level="item.conditionLevel" />
        <span class="item-card__campus" :title="locationTitle">
          <el-icon><Location /></el-icon>
          <span class="item-card__campus-text">{{ campusText }}</span>
        </span>
      </div>
      <div v-if="showSeller" class="item-card__footer">
        <span class="item-card__seller">{{ item.sellerNickname || '匿名同学' }}</span>
        <span class="item-card__views">
          <el-icon><View /></el-icon>
          {{ item.viewCount ?? 0 }}
        </span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import PriceTag from './PriceTag.vue'
import ConditionTag from './ConditionTag.vue'
import StatusTag from './StatusTag.vue'
import { resolveImage } from '@/utils/format'

const props = defineProps({
  item: { type: Object, required: true },
  showStatus: { type: Boolean, default: false },
  showSeller: { type: Boolean, default: true }
})

/** 卡片宽度有限，只显示校区名；学校名放 title，悬停可看完整归属 */
const campusText = computed(() => {
  return props.item.campus || props.item.schoolName || '校内面交'
})

/** 悬停提示：学校 · 校区（历史数据可能缺一边） */
const locationTitle = computed(() => {
  const name = props.item.schoolName
  const campus = props.item.campus
  if (name && campus) return `${name} · ${campus}`
  return campus || name || '校内面交'
})
</script>

<style scoped>
.item-card {
  display: block;
  overflow: hidden;
  background: transparent;
  border: 0.5px solid rgba(255, 255, 255, 0.55);
  border-radius: var(--brix-radius-lg);
  box-shadow: var(--brix-shadow-sm);
  transition: transform 0.24s cubic-bezier(0.25, 1, 0.5, 1), box-shadow 0.24s cubic-bezier(0.25, 1, 0.5, 1),
    border-color 0.24s ease;
}

.item-card:hover {
  transform: translateY(-4px);
  border-color: rgba(14, 122, 80, 0.35);
  box-shadow: var(--brix-shadow-md);
}

/* 下半部分：白色毛玻璃，透出全站浅绿渐变 */
.item-card__body {
  padding: 12px 13px 14px;
  background: rgba(255, 253, 249, 0.55);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
  border-top: 0.5px solid rgba(255, 255, 255, 0.4);
}

.item-card__cover {
  position: relative;
  height: 176px;
  background: #efece4; /* 暖纸占位：图片未加载时不突兀 */
}

.item-card__image {
  width: 100%;
  height: 100%;
  display: block;
}

.item-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 176px;
  color: #c3cdc6;
  font-size: 28px;
}

.item-card__status {
  position: absolute;
  top: 8px;
  left: 8px;
}

.item-card__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(20, 35, 29, 0.45);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
}

.item-card__body {
  padding: 12px 13px 14px;
}

.item-card__title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.45;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  transition: color 0.18s ease;
}

.item-card:hover .item-card__title {
  color: var(--brix-primary);
}

.item-card__price {
  margin-top: 8px;
}

.item-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.item-card__campus {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

/* 校区名过长（如「金鸡岭校区」并列多校）时省略，避免挤掉左侧成色标签 */
.item-card__campus-text {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 0.5px dashed var(--brix-border);
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.item-card__seller {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-card__views {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
</style>
