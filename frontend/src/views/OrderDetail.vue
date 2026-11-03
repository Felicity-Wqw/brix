<template>
  <div v-loading="loading" class="order-detail">
    <template v-if="order">
      <div class="order-detail__head brix-card">
        <div class="order-detail__head-main">
          <div class="order-detail__status-row">
            <StatusTag kind="order" :value="order.status" size="large" />
            <h1 class="order-detail__status-text">{{ order.statusDesc }}</h1>
          </div>
          <p class="order-detail__tip">{{ statusTip }}</p>
          <p class="order-detail__meta">
            订单号：{{ order.orderNo }}
            <el-button text size="small" type="primary" @click="copyOrderNo">复制</el-button>
          </p>
        </div>
        <div class="order-detail__amount">
          <span class="order-detail__amount-label">应付 / 已付金额</span>
          <PriceTag :price="order.amount" :show-original="false" size="large" />
          <span v-if="order.payMethodDesc" class="order-detail__pay-method">{{ order.payMethodDesc }}</span>
        </div>
      </div>

      <div class="order-detail__grid">
        <!-- 时间线 -->
        <div class="order-detail__card brix-card">
          <h3 class="order-detail__card-title">订单时间线</h3>
          <el-timeline>
            <el-timeline-item
              v-for="node in timeline"
              :key="node.label"
              :type="node.time ? 'success' : 'info'"
              :hollow="!node.time"
              :timestamp="node.time || '待完成'"
            >
              {{ node.label }}
            </el-timeline-item>
          </el-timeline>
          <el-alert
            v-if="order.closeReasonDesc"
            type="info"
            :closable="false"
            :title="`关闭原因：${order.closeReasonDesc}`"
          />
        </div>

        <!-- 商品快照 -->
        <div class="order-detail__card brix-card">
          <h3 class="order-detail__card-title">商品信息</h3>
          <div class="order-detail__item">
            <router-link :to="`/items/${order.itemId}`" class="order-detail__item-cover">
              <el-image :src="resolveImage(order.itemCover)" fit="cover" class="order-detail__item-image" />
            </router-link>
            <div class="order-detail__item-info">
              <router-link :to="`/items/${order.itemId}`" class="order-detail__item-title">
                {{ order.itemTitle }}
              </router-link>
              <div class="order-detail__item-price">
                单价 <PriceTag :price="order.itemPrice" :show-original="false" size="small" />
              </div>
              <div class="order-detail__item-trade">
                <StatusTag kind="trade" :value="order.tradeType" effect="plain" />
              </div>
            </div>
          </div>
          <div v-if="order.meetPlace" class="order-detail__place">
            <el-icon><Location /></el-icon>
            面交地点：{{ order.meetPlace }}
          </div>
          <div v-if="order.meetTime" class="order-detail__place">
            <el-icon><AlarmClock /></el-icon>
            面交时间：{{ formatTime(order.meetTime, 'YYYY-MM-DD HH:mm') }}
          </div>
          <div v-if="order.receiverName" class="order-detail__place">
            <el-icon><User /></el-icon>
            收货信息：{{ order.receiverName }} · {{ order.receiverPhone }} · {{ order.receiverAddress }}
          </div>
          <div v-if="order.escrowDesc" class="order-detail__place order-detail__escrow" :class="{ 'is-settled': !!order.settleTime }">
            <el-icon><Wallet /></el-icon>
            预付款状态：{{ order.escrowDesc }}
            <span v-if="order.settleTime">（{{ formatTime(order.settleTime, 'YYYY-MM-DD HH:mm') }}）</span>
          </div>
          <div v-if="order.buyerNote" class="order-detail__note">买家备注：{{ order.buyerNote }}</div>
        </div>

        <!-- 买卖双方 -->
        <div class="order-detail__card brix-card">
          <h3 class="order-detail__card-title">交易双方</h3>
          <div class="order-detail__party">
            <div class="order-detail__party-row">
              <span class="order-detail__party-label">买家</span>
              <span>{{ order.buyerNickname }}</span>
              <span v-if="order.buyerCampus" class="order-detail__party-sub">{{ order.buyerCampus }}</span>
            </div>
            <div class="order-detail__party-row">
              <span class="order-detail__party-label">卖家</span>
              <span>{{ order.sellerNickname }}</span>
              <span v-if="order.sellerCampus" class="order-detail__party-sub">{{ order.sellerCampus }}</span>
            </div>
            <p class="order-detail__privacy">联系方式仅买卖双方可见，请通过私信或面交沟通</p>
          </div>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="order-detail__actions">
        <el-button v-if="order.canPay" type="primary" size="large" @click="router.push(`/checkout/${order.id}`)">
          去支付
        </el-button>
        <el-button v-if="order.canCancel" size="large" @click="cancel">取消订单</el-button>
        <el-button v-if="order.canShip" type="primary" size="large" @click="ship">已发货</el-button>
        <el-button v-if="order.canConfirm" type="primary" size="large" @click="confirm">
          {{ order.tradeType === 'MEET' ? '确认完成交易' : '确认收货' }}
        </el-button>
        <el-button v-if="order.canReview" type="primary" plain size="large" @click="reviewVisible = true">评价卖家</el-button>
        <el-tag v-else-if="order.reviewed" size="large" effect="plain">已评价</el-tag>
      </div>
    </template>

    <EmptyState v-else-if="!loading" text="订单不存在或无权查看" />

    <RateDialog v-model="reviewVisible" :order-id="orderId" @success="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlarmClock, Location, User, Wallet } from '@element-plus/icons-vue'
import { cancelOrder, confirmOrder, fetchOrderDetail, shipOrder } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { formatTime, resolveImage } from '@/utils/format'
import PriceTag from '@/components/PriceTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import RateDialog from '@/components/RateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const orderId = String(route.params.id || '')
const order = ref(null)
const loading = ref(false)
const reviewVisible = ref(false)

/** 状态提示文案 */
const statusTip = computed(() => {
  if (!order.value) return ''
  switch (order.value.status) {
    case 'UNPAID':
      return `请在 ${order.payRemainSeconds ? Math.ceil(order.payRemainSeconds / 60) : 30} 分钟内完成支付，超时订单将自动关闭`
    case 'TO_SHIP':
      return '等待卖家发货'
    case 'TO_RECEIVE':
      return '卖家已发货，请在收到商品并验收后确认收货'
    case 'TO_MEET':
      return order.value.viewerRole === 'BUYER'
        ? '请与卖家约定时间线下面交，验货无误后确认完成交易'
        : '与买家线下面交，买家确认后交易完成'
    case 'DONE':
      return '交易已完成，感谢使用校园二手平台'
    case 'CANCELED':
      return '订单已取消'
    default:
      return ''
  }
})

/** 时间线节点 */
const timeline = computed(() => {
  const target = order.value
  if (!target) return []
  return [
    { label: '创建订单', time: formatLine(target.createdAt) },
    { label: '买家付款', time: formatLine(target.payTime) },
    {
      label: target.tradeType === 'MEET' ? '等待面交' : '卖家发货',
      time: target.tradeType === 'MEET' ? (target.payTime ? '进行中' : '') : formatLine(target.shipTime)
    },
    {
      label: target.tradeType === 'MEET' ? '买家确认完成' : '买家确认收货',
      time: formatLine(target.finishTime)
    }
  ]
})

function formatLine(value) {
  return value || ''
}

/** 复制订单号 */
async function copyOrderNo() {
  try {
    await navigator.clipboard.writeText(order.value.orderNo)
    ElMessage.success('订单号已复制')
  } catch (error) {
    ElMessage.warning('复制失败，请手动记录')
  }
}

/** 取消订单 */
async function cancel() {
  try {
    await ElMessageBox.confirm('取消后该订单不可恢复，确定取消吗？', '取消订单', { type: 'warning' })
  } catch (error) {
    return
  }
  await cancelOrder(orderId)
  ElMessage.success('订单已取消')
  load()
}

/** 发货 */
async function ship() {
  try {
    await ElMessageBox.confirm('确认已把商品交给快递？', '确认发货', { type: 'info' })
  } catch (error) {
    return
  }
  await shipOrder(orderId)
  ElMessage.success('已发货')
  load()
}

/** 确认收货 / 面交单确认完成 */
async function confirm() {
  const isMeet = order.value.tradeType === 'MEET'
  try {
    await ElMessageBox.confirm(
      isMeet ? '请确认已与卖家线下面交且验货无误。' : '请确认已收到商品且验收无误。',
      isMeet ? '确认完成交易' : '确认收货',
      { type: 'info' }
    )
  } catch (error) {
    return
  }
  await confirmOrder(orderId)
  ElMessage.success('交易完成')
  load()
}

/** 加载详情（后端校验买卖双方/后台角色可见） */
async function load() {
  loading.value = true
  try {
    order.value = await fetchOrderDetail(orderId)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!auth.isLogin) {
    router.push({ path: '/login', query: { redirect: encodeURIComponent(route.fullPath) } })
    return
  }
  load()
})
</script>

<style scoped>
.order-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.order-detail__status-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-detail__status-text {
  font-size: 20px;
  margin: 0;
}

.order-detail__tip {
  margin-top: 6px;
  color: var(--brix-text-secondary);
}

.order-detail__meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.order-detail__amount {
  text-align: right;
}

.order-detail__amount-label {
  display: block;
  font-size: 12px;
  color: var(--brix-text-placeholder);
  margin-bottom: 4px;
}

.order-detail__pay-method {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.order-detail__grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
}

.order-detail__card {
  padding: 16px;
}

.order-detail__card-title {
  font-size: 15px;
  margin: 0 0 14px;
}

.order-detail__item {
  display: flex;
  gap: 12px;
}

.order-detail__item-image {
  width: 76px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f4f6;
}

.order-detail__item-title {
  font-weight: 500;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-detail__item-price {
  margin-top: 4px;
  font-size: 13px;
  color: var(--brix-text-secondary);
}

.order-detail__item-trade {
  margin-top: 6px;
}

.order-detail__place,
.order-detail__note {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 0.5px dashed var(--brix-border);
  font-size: 13px;
  color: var(--brix-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 预付款托管状态条 */
.order-detail__escrow {
  color: #b25e00;
  background: #fff7e8;
  border: 0.5px solid #ffe1b0;
  border-top: 0.5px solid #ffe1b0;
  border-radius: 8px;
  padding: 9px 12px;
  margin-top: 12px;
}

.order-detail__escrow.is-settled {
  color: var(--brix-primary);
  background: var(--brix-primary-soft);
  border-color: rgba(14, 122, 80, 0.25);
}

.order-detail__party-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 14px;
}

.order-detail__party-label {
  width: 40px;
  color: var(--brix-text-placeholder);
  font-size: 12px;
}

.order-detail__party-sub {
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.order-detail__privacy {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.order-detail__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 20px 0 8px;
  justify-content: flex-end;
}
</style>
