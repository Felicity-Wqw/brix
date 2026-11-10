<template>
  <div class="orders">
    <div class="orders__bar">
      <div class="orders__filters">
        <el-input
          v-model="keyword"
          placeholder="订单号 / 商品标题"
          clearable
          :prefix-icon="Search"
          class="orders__search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="status" placeholder="订单状态" clearable class="orders__select" @change="reload">
          <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
      <span class="orders__tip">只读：订单状态只能由买卖双方的动作驱动，后台不直接改</span>
    </div>

    <div class="orders__body" v-loading="loading">
      <el-table :data="rows" size="default" empty-text="没有匹配的订单">
        <el-table-column label="订单号" width="190">
          <template #default="{ row }">
            <span class="cell-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>

        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="cell-item">
              <el-image :src="resolveImage(row.itemCover)" fit="cover" class="cell-item__cover">
                <template #error>
                  <div class="cell-item__placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <p class="cell-item__title" :title="row.itemTitle">{{ row.itemTitle }}</p>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="买家 → 卖家" min-width="180">
          <template #default="{ row }">
            <span class="cell-party">{{ row.buyerNickname || '—' }}</span>
            <span class="cell-arrow">→</span>
            <span class="cell-party">{{ row.sellerNickname || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="cell-amount">￥{{ Number(row.amount || 0).toLocaleString('zh-CN') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag kind="order" :value="row.status" />
          </template>
        </el-table-column>

        <el-table-column label="下单时间" width="140">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTime(row.createdAt, 'YYYY-MM-DD HH:mm') }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="orders__pager">
        <el-pagination
          v-model:current-page="page.currentPage"
          :page-size="page.pageSize"
          :total="page.total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Picture, Search } from '@element-plus/icons-vue'
import { fetchAdminOrders } from '@/api/admin'
import { formatTime, resolveImage } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

/** 与 OrderStatusEnum 保持一致（顺序即状态机推进顺序） */
const STATUS_OPTIONS = [
  { label: '待付款', value: 'UNPAID' },
  { label: '待发货', value: 'TO_SHIP' },
  { label: '待收货', value: 'TO_RECEIVE' },
  { label: '待面交', value: 'TO_MEET' },
  { label: '已完成', value: 'DONE' },
  { label: '已取消', value: 'CANCELED' },
  { label: '已关闭', value: 'CLOSED' }
]

const keyword = ref('')
const status = ref('')
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminOrders({
      keyword: keyword.value.trim(),
      status: status.value,
      currentPage: page.currentPage,
      pageSize: page.pageSize
    })
    rows.value = data?.records || []
    page.total = Number(data?.total || 0)
  } catch (error) {
    rows.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.currentPage = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.orders {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.orders__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.orders__filters {
  display: flex;
  gap: 10px;
}

.orders__search {
  width: 240px;
}

.orders__select {
  width: 140px;
}

.orders__tip {
  font-size: 12px;
  color: #9aa5b3;
}

.orders__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.orders__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.cell-no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: #5c6b63;
}

.cell-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-item__cover {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 7px;
  background: #f4f6f8;
}

.cell-item__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #c0c8d2;
}

.cell-item__title {
  margin: 0;
  font-size: 13px;
  color: #1c2a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-party {
  font-size: 12.5px;
  color: #3c4250;
}

.cell-arrow {
  margin: 0 6px;
  color: #c0c8d2;
}

.cell-amount {
  font-weight: 700;
  color: #1c2a24;
  font-variant-numeric: tabular-nums;
}

.cell-time {
  font-size: 12.5px;
  color: #8d99a8;
}
</style>
