<template>
  <div class="my-history">
    <div class="brix-section-title">
      <span>浏览历史</span>
      <span class="brix-section-title__sub">同一商品只保留最近一次浏览记录，最多展示最近 {{ MAX_HISTORY_TIP }}</span>
    </div>

    <div v-loading="loading">
      <div v-if="items.length" class="my-history__grid">
        <div v-for="history in items" :key="history.id" class="hist-card brix-card">
          <router-link :to="`/items/${history.itemId}`" class="hist-card__cover">
            <el-image :src="resolveImage(history.itemCover)" fit="cover" class="hist-card__image">
              <template #error>
                <div class="hist-card__placeholder"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
            <span class="hist-card__status">
              <StatusTag kind="item" :value="history.itemStatus" effect="dark" />
            </span>
          </router-link>

          <div class="hist-card__body">
            <router-link :to="`/items/${history.itemId}`" class="hist-card__title" :title="history.itemTitle">
              {{ history.itemTitle }}
            </router-link>
            <div class="hist-card__price">
              <PriceTag :price="history.itemPrice" :show-original="false" size="small" />
            </div>
            <div class="hist-card__meta">
              <span>{{ history.sellerNickname || '匿名同学' }}</span>
              <span>浏览于 {{ formatTime(history.browseTime, 'MM-DD HH:mm') }}</span>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else-if="!loading" text="还没有浏览记录" tip="去首页逛逛，看过的商品会出现在这里" />
    </div>

    <el-pagination
      v-if="total > query.pageSize"
      background
      layout="prev, pager, next, total"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.currentPage"
      @current-change="onPageChange"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getBrowseHistory } from '@/api/user'
import { DEFAULT_PAGE } from '@/constants'
import { formatTime, resolveImage } from '@/utils/format'
import PriceTag from '@/components/PriceTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

/** 提示文案里说明的数据范围（与后端分页一致，仅作展示提示） */
const MAX_HISTORY_TIP = '50 条/页'

const items = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ ...DEFAULT_PAGE })

/** 加载浏览历史 */
async function load() {
  loading.value = true
  try {
    const data = await getBrowseHistory({ ...query })
    items.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** 翻页 */
function onPageChange(page) {
  query.currentPage = page
  load()
}

onMounted(load)
</script>

<style scoped>
.my-history__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.hist-card {
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.hist-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(91, 124, 250, 0.12);
}

.hist-card__cover {
  position: relative;
  display: block;
  height: 160px;
  background: #f2f4f6;
}

.hist-card__image {
  width: 100%;
  height: 100%;
  display: block;
}

.hist-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 160px;
  color: #c9cfd7;
  font-size: 26px;
}

.hist-card__status {
  position: absolute;
  top: 8px;
  left: 8px;
}

.hist-card__body {
  padding: 12px;
}

.hist-card__title {
  display: block;
  font-size: 14px;
  font-weight: 500;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.hist-card__title:hover {
  color: var(--brix-primary);
}

.hist-card__price {
  margin-top: 6px;
}

.hist-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}
</style>
