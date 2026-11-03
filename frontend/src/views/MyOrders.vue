<template>
  <div class="my-orders">
    <div class="brix-section-title">
      <span>我的订单</span>
    </div>

    <div class="my-orders__toolbar">
      <el-radio-group v-model="perspective" @change="onPerspectiveChange">
        <el-radio-button value="buy">我买到的</el-radio-button>
        <el-radio-button value="sell">我卖出的</el-radio-button>
      </el-radio-group>

      <el-select v-model="status" class="my-orders__status" @change="onStatusChange">
        <el-option label="全部状态" value="" />
        <el-option v-for="(info, value) in ORDER_STATUS_DICT" :key="value" :label="info.label" :value="value" />
      </el-select>
    </div>

    <div v-loading="loading" class="my-orders__list">
      <template v-if="orders.length">
        <OrderCard
          v-for="order in orders"
          :key="order.id"
          :order="order"
          :perspective="perspective"
          @detail="goDetail"
          @pay="goPay"
          @cancel="cancel"
          @ship="ship"
          @confirm="confirm"
          @review="openReview"
        />
      </template>
      <EmptyState v-else-if="!loading" text="暂无相关订单" />
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

    <RateDialog v-model="reviewVisible" :order-id="reviewOrderId" @success="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, fetchBoughtOrders, fetchSoldOrders, shipOrder } from '@/api/order'
import { DEFAULT_PAGE, ORDER_STATUS_DICT } from '@/constants'
import OrderCard from '@/components/OrderCard.vue'
import RateDialog from '@/components/RateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const perspective = ref('buy')
const status = ref('')
const orders = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ ...DEFAULT_PAGE })

const reviewVisible = ref(false)
const reviewOrderId = ref('')

/** 加载订单（买家 / 卖家两个视角） */
async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (status.value) params.status = status.value
    const fetcher = perspective.value === 'buy' ? fetchBoughtOrders : fetchSoldOrders
    const data = await fetcher(params)
    orders.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** 视角切换：回第 1 页 */
function onPerspectiveChange() {
  query.currentPage = 1
  load()
}

/** 状态筛选切换：回第 1 页 */
function onStatusChange() {
  query.currentPage = 1
  load()
}

/** 翻页 */
function onPageChange(page) {
  query.currentPage = page
  load()
}

function goDetail(order) {
  router.push(`/orders/${order.id}`)
}

function goPay(order) {
  router.push(`/checkout/${order.id}`)
}

/** 取消订单 */
async function cancel(order) {
  try {
    await ElMessageBox.confirm('取消后该订单不可恢复，确定取消吗？', '取消订单', { type: 'warning' })
  } catch (error) {
    return
  }
  await cancelOrder(order.id)
  ElMessage.success('订单已取消')
  load()
}

/** 卖家发货 */
async function ship(order) {
  try {
    await ElMessageBox.confirm('确认已把商品交给快递并填写了收件信息？', '确认发货', { type: 'info' })
  } catch (error) {
    return
  }
  await shipOrder(order.id)
  ElMessage.success('已发货，等待买家确认收货')
  load()
}

/** 买家确认收货 / 面交单确认完成 */
async function confirm(order) {
  const isMeet = order.tradeType === 'MEET'
  try {
    await ElMessageBox.confirm(
      isMeet ? '请确认已与卖家线下面交且验货无误，确认后交易完成。' : '请确认已收到商品且验收无误，确认后交易完成。',
      isMeet ? '确认完成交易' : '确认收货',
      { type: 'info' }
    )
  } catch (error) {
    return
  }
  await confirmOrder(order.id)
  ElMessage.success(isMeet ? '交易已完成，记得评价卖家哦' : '确认收货成功，记得评价卖家哦')
  load()
}

/** 打开评价弹窗 */
function openReview(order) {
  reviewOrderId.value = order.id
  reviewVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.my-orders__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.my-orders__status {
  width: 160px;
}

.my-orders__list :deep(.order-card) {
  margin-bottom: 12px;
}
</style>
