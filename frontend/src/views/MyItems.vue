<template>
  <div class="my-items">
    <div class="brix-section-title">
      <span>我的商品</span>
      <el-button type="primary" @click="router.push('/publish')">发布闲置</el-button>
    </div>

    <el-tabs v-model="activeStatus" @tab-change="onTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="在售" name="ON_SALE" />
      <el-tab-pane label="待审核" name="PENDING" />
      <el-tab-pane label="已下架" name="OFF_SHELF" />
      <el-tab-pane label="已售出" name="SOLD" />
      <el-tab-pane label="审核拒绝" name="REJECTED" />
    </el-tabs>

    <div v-loading="loading" class="my-items__body">
      <template v-if="items.length">
        <div v-for="item in items" :key="item.id" class="my-item brix-card">
          <router-link :to="`/items/${item.id}`" class="my-item__cover">
            <el-image :src="resolveImage(item.coverImage)" fit="cover" class="my-item__image">
              <template #error>
                <div class="my-item__placeholder"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
          </router-link>

          <div class="my-item__info">
            <router-link :to="`/items/${item.id}`" class="my-item__title">{{ item.title }}</router-link>
            <div class="my-item__meta">
              <StatusTag kind="item" :value="item.status" />
              <ConditionTag :level="item.conditionLevel" />
              <span class="my-item__time">发布于 {{ formatTime(item.createdAt, 'YYYY-MM-DD HH:mm') }}</span>
            </div>
            <div class="my-item__stats">
              <span><el-icon><View /></el-icon>{{ item.viewCount }} 浏览</span>
              <span><el-icon><Star /></el-icon>{{ item.favoriteCount }} 收藏</span>
            </div>
          </div>

          <div class="my-item__price">
            <PriceTag :price="item.price" :show-original="false" size="small" />
          </div>

          <div class="my-item__actions">
            <el-button
              v-if="item.status === 'ON_SALE'"
              size="small"
              @click="changeStatus(item, 'OFF_SHELF')"
            >
              下架
            </el-button>
            <el-button
              v-else-if="item.status === 'OFF_SHELF'"
              size="small"
              type="primary"
              plain
              @click="changeStatus(item, 'ON_SALE')"
            >
              重新上架
            </el-button>

            <el-button
              v-if="item.status !== 'SOLD'"
              size="small"
              @click="router.push(`/publish?id=${item.id}`)"
            >
              编辑
            </el-button>
            <el-button size="small" type="danger" plain @click="remove(item)">删除</el-button>
          </div>
        </div>
      </template>
      <EmptyState v-else-if="!loading" text="还没有发布过商品" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteItem, fetchMyItems, updateItemStatus } from '@/api/item'
import { DEFAULT_PAGE } from '@/constants'
import { formatTime, resolveImage } from '@/utils/format'
import PriceTag from '@/components/PriceTag.vue'
import ConditionTag from '@/components/ConditionTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const activeStatus = ref('all')
const items = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ ...DEFAULT_PAGE })

/** 加载我的商品（Tab 切换带参请求） */
async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (activeStatus.value !== 'all') params.status = activeStatus.value
    const data = await fetchMyItems(params)
    items.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** Tab 切换：回第 1 页 */
function onTabChange() {
  query.currentPage = 1
  load()
}

/** 翻页 */
function onPageChange(page) {
  query.currentPage = page
  load()
}

/** 上架 / 下架 */
async function changeStatus(item, action) {
  const label = action === 'ON_SALE' ? '重新上架（需重新审核）' : '下架（首页立即不可见）'
  try {
    await ElMessageBox.confirm(`确定要${label}吗？`, '操作确认', { type: 'warning' })
  } catch (error) {
    return
  }
  await updateItemStatus(item.id, action)
  ElMessage.success('操作成功')
  load()
}

/** 删除（逻辑删除） */
async function remove(item) {
  try {
    await ElMessageBox.confirm('删除后前台不可见且不可恢复，确定删除吗？', '删除商品', { type: 'warning' })
  } catch (error) {
    return
  }
  await deleteItem(item.id)
  ElMessage.success('已删除')
  // 删除当前页最后一条时回退一页
  if (items.value.length === 1 && query.currentPage > 1) {
    query.currentPage -= 1
  }
  load()
}

onMounted(load)
</script>

<style scoped>
.my-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  margin-bottom: 12px;
}

.my-item__cover {
  flex-shrink: 0;
}

.my-item__image {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f4f6;
  display: block;
}

.my-item__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  color: #c9cfd7;
  font-size: 24px;
}

.my-item__info {
  flex: 1;
  min-width: 0;
}

.my-item__title {
  display: block;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-item__title:hover {
  color: var(--brix-primary);
}

.my-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.my-item__time {
  color: var(--brix-text-placeholder);
}

.my-item__stats {
  display: flex;
  gap: 16px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.my-item__stats span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.my-item__price {
  flex-shrink: 0;
  text-align: right;
}

.my-item__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.my-item__reason {
  font-size: 13px;
  line-height: 1.6;
}
</style>
