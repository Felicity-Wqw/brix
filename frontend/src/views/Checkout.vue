<template>
  <div v-loading="loading" class="checkout">
    <template v-if="order">
      <div class="checkout__card brix-card">
        <h1 class="checkout__title">收银台</h1>

        <div class="checkout__countdown" :class="{ 'is-danger': remainSeconds <= 60 }">
          <el-icon><AlarmClock /></el-icon>
          <span v-if="order.status === 'UNPAID'">请在 {{ formatCountdown(remainSeconds) }} 内完成支付</span>
          <span v-else>订单已关闭或已支付</span>
        </div>

        <div class="checkout__order">
          <el-image :src="resolveImage(order.itemCover)" fit="cover" class="checkout__image">
            <template #error>
              <div class="checkout__placeholder"><el-icon><Picture /></el-icon></div>
            </template>
          </el-image>
          <div class="checkout__order-info">
            <div class="checkout__order-title">{{ order.itemTitle }}</div>
            <div class="checkout__order-meta">
              订单号 {{ order.orderNo }} ·
              {{ order.tradeType === 'MEET' ? '面交' : '邮寄' }}
              <template v-if="order.meetPlace"> · {{ order.meetPlace }}</template>
            </div>
          </div>
          <div class="checkout__amount">
            <span class="checkout__amount-label">应付金额</span>
            <PriceTag :price="order.amount" :show-original="false" size="large" />
          </div>
        </div>

        <el-divider />

        <div class="checkout__methods">
          <div class="checkout__methods-title">选择支付方式（预付款将进入平台托管）</div>
          <div class="checkout__pay-cards">
            <div
              class="pay-card pay-card--wechat"
              :class="{ 'is-active': payMethod === 'WECHAT' }"
              @click="payMethod = 'WECHAT'"
            >
              <span class="pay-card__logo pay-card__logo--wechat">
                <svg viewBox="0 0 28 28" width="22" height="22" aria-hidden="true">
                  <path fill="#fff" d="M11.2 4C6.7 4 3 7.1 3 11c0 2.2 1.2 4.1 3.1 5.4l-.8 2.5 2.9-1.5c.9.3 1.9.4 3 .4h.4a6.6 6.6 0 0 1-.3-2c0-3.9 3.7-7 8.2-7h.5C19.2 6 15.5 4 11.2 4Zm-2.9 4.5a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2Zm5.8 0a1.1 1.1 0 1 1 0 2.2 1.1 1.1 0 0 1 0-2.2Z" />
                  <path fill="#fff" d="M25 14.9c0-3.2-3.1-5.9-6.9-5.9s-6.9 2.7-6.9 5.9 3.1 5.9 6.9 5.9c.8 0 1.6-.1 2.3-.3l2.5 1.3-.7-2.1A5.7 5.7 0 0 0 25 14.9Zm-9.2-1.6a.95 1 0 1 1 0-1.9.95 1 0 0 1 0 1.9Zm4.6 0a.95 1 0 1 1 0-1.9.95 1 0 0 1 0 1.9Z" />
                </svg>
              </span>
              <span class="pay-card__name">微信支付<small>全球 13 亿人的选择</small></span>
              <span class="pay-card__tick"></span>
            </div>
            <div
              class="pay-card pay-card--alipay"
              :class="{ 'is-active': payMethod === 'ALIPAY' }"
              @click="payMethod = 'ALIPAY'"
            >
              <span class="pay-card__logo pay-card__logo--alipay">支</span>
              <span class="pay-card__name">支付宝<small>先验货后付款更安心</small></span>
              <span class="pay-card__tick"></span>
            </div>
          </div>
        </div>

        <el-button
          type="primary"
          size="large"
          class="checkout__submit"
          :disabled="order.status !== 'UNPAID' || remainSeconds <= 0"
          @click="openSandbox"
        >
          确认支付 ¥{{ formatPrice(order.amount) }}
        </el-button>

        <p class="checkout__tip">
          支付为平台托管预付款：面交单验货完成、邮寄单确认收货后，款项才会结算给卖家
        </p>
      </div>
    </template>

    <EmptyState v-else-if="!loading" text="订单不存在或无权查看" />

    <!-- ============ 支付沙箱弹窗 ============ -->
    <el-dialog
      v-model="sandboxVisible"
      width="360px"
      align-center
      append-to-body
      :close-on-click-modal="false"
      :show-close="sandboxStep === 'pay'"
    >
      <!-- 微信支付沙箱 -->
      <div v-if="payMethod === 'WECHAT'" class="sandbox sandbox--wechat">
        <div class="sandbox__bar sandbox__bar--wechat">微信支付</div>
        <div class="sandbox__body">
          <p class="sandbox__merchant">物归校园 · 向卖家付款</p>
          <div class="sandbox__amount">¥{{ formatPrice(order?.amount) }}</div>
          <template v-if="sandboxStep !== 'success'">
            <div class="sandbox__pin">
              <span v-for="i in 6" :key="i" class="sandbox__pin-cell" :class="{ 'is-filled': wxPin >= i }"></span>
            </div>
            <p class="sandbox__pin-tip">{{ sandboxStep === 'pay' ? '请输入 6 位支付密码' : '正在验证…' }}</p>
          </template>
          <template v-else>
            <el-icon class="sandbox__ok sandbox__ok--wechat"><SuccessFilled /></el-icon>
            <p class="sandbox__ok-text">支付成功</p>
            <p class="sandbox__ok-sub">预付款已进入平台托管</p>
          </template>
        </div>
        <div class="sandbox__foot">
          <el-button
            v-if="sandboxStep === 'pay'"
            class="sandbox__btn sandbox__btn--wechat"
            @click="fillPin"
          >
            确认支付
          </el-button>
        </div>
      </div>

      <!-- 支付宝沙箱 -->
      <div v-else class="sandbox sandbox--alipay">
        <div class="sandbox__bar sandbox__bar--alipay">支付宝 · 确认付款</div>
        <div class="sandbox__body">
          <p class="sandbox__merchant">物归校园</p>
          <div class="sandbox__amount">¥{{ formatPrice(order?.amount) }}</div>
          <template v-if="sandboxStep === 'pay'">
            <div class="sandbox__channel">
              <span>付款方式</span><b>余额（沙箱）</b>
            </div>
          </template>
          <template v-else-if="sandboxStep === 'processing'">
            <el-icon class="sandbox__loading is-loading"><Loading /></el-icon>
            <p class="sandbox__pin-tip">正在付款…</p>
          </template>
          <template v-else>
            <el-icon class="sandbox__ok sandbox__ok--alipay"><SuccessFilled /></el-icon>
            <p class="sandbox__ok-text">付款成功</p>
            <p class="sandbox__ok-sub">预付款已进入平台托管</p>
          </template>
        </div>
        <div class="sandbox__foot">
          <el-button
            v-if="sandboxStep === 'pay'"
            class="sandbox__btn sandbox__btn--alipay"
            @click="finishPay"
          >
            立即付款
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, SuccessFilled } from '@element-plus/icons-vue'
import { fetchOrderDetail, payOrder } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { formatCountdown, formatPrice, payRemainSeconds, resolveImage } from '@/utils/format'
import PriceTag from '@/components/PriceTag.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const orderId = String(route.params.orderId || '')
const order = ref(null)
const loading = ref(false)
const payMethod = ref('WECHAT')
const remainSeconds = ref(0)
let timer = null

const UNPAID = 'UNPAID'

/* ==================== 支付沙箱状态机 ====================
 * pay（输入密码/确认付款）→ processing（验证中，同时调真实 pay 接口）
 * → success（成功页）→ 自动跳订单详情。微信沙箱多一步密码格自动填充。
 */
const sandboxVisible = ref(false)
const sandboxStep = ref('pay') // pay | processing | success
const wxPin = ref(0)
let pinTimer = null

/** 打开沙箱弹窗 */
function openSandbox() {
  if (order.value?.status !== UNPAID || remainSeconds.value <= 0) {
    return
  }
  sandboxStep.value = 'pay'
  wxPin.value = 0
  sandboxVisible.value = true
}

/** 微信沙箱：逐格填充密码后进入验证 */
function fillPin() {
  if (pinTimer) return
  pinTimer = setInterval(() => {
    wxPin.value += 1
    if (wxPin.value >= 6) {
      clearInterval(pinTimer)
      pinTimer = null
      setTimeout(finishPay, 450)
    }
  }, 260)
}

/** 统一出口：调用真实支付接口（托管入账），展示成功页后跳转 */
async function finishPay() {
  if (sandboxStep.value === 'processing' || sandboxStep.value === 'success') return
  sandboxStep.value = 'processing'
  const startedAt = Date.now()
  try {
    const paid = await payOrder(orderId, payMethod.value)
    // 保证验证动画至少 900ms，节奏拟真
    const elapsed = Date.now() - startedAt
    setTimeout(() => {
      sandboxStep.value = 'success'
      setTimeout(() => {
        sandboxVisible.value = false
        ElMessage.success('支付成功，预付款已进入平台托管')
        router.replace(`/orders/${paid.id}`)
      }, 1100)
    }, Math.max(0, 900 - elapsed))
  } catch (error) {
    sandboxVisible.value = false
    sandboxStep.value = 'pay'
    wxPin.value = 0
  }
}

function stopPinTimer() {
  if (pinTimer) {
    clearInterval(pinTimer)
    pinTimer = null
  }
}

/** 启动每秒倒计时 */
function startCountdown() {
  stopCountdown()
  const tick = () => {
    remainSeconds.value = payRemainSeconds(order.value?.createdAt)
    if (remainSeconds.value <= 0 && order.value?.status === UNPAID) {
      stopCountdown()
      ElMessage.warning('订单已超时关闭')
      router.replace('/my/orders')
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function load() {
  loading.value = true
  try {
    order.value = await fetchOrderDetail(orderId)
    if (order.value?.status === UNPAID) {
      startCountdown()
    }
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

onBeforeUnmount(() => {
  stopCountdown()
  stopPinTimer()
})
</script>

<style scoped>
.checkout__card {
  max-width: 720px;
  margin: 0 auto;
  padding: 28px 32px;
}

.checkout__title {
  font-size: 22px;
  margin: 0 0 12px;
}

.checkout__countdown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  background: #fff7e8;
  color: #b25e00;
  font-size: 13px;
}

.checkout__countdown.is-danger {
  background: #fdeaea;
  color: #d03050;
}

.checkout__order {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.checkout__image {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  overflow: hidden;
  background: #f2f4f6;
  flex-shrink: 0;
}

.checkout__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  color: #c9cfd7;
}

.checkout__order-info {
  flex: 1;
  min-width: 0;
}

.checkout__order-title {
  font-weight: 600;
}

.checkout__order-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.checkout__amount {
  text-align: right;
  flex-shrink: 0;
}

.checkout__amount-label {
  display: block;
  font-size: 12px;
  color: var(--brix-text-placeholder);
  margin-bottom: 4px;
}

.checkout__methods {
  margin-top: 4px;
}

.checkout__methods-title {
  font-size: 13px;
  color: var(--brix-text-secondary);
  margin-bottom: 10px;
}

.checkout__method-group {
  display: flex;
}

.checkout__method-group :deep(.el-radio-button) {
  flex: 1;
}

.checkout__submit {
  width: 100%;
  margin-top: 20px;
  height: 46px;
  font-size: 16px;
}

.checkout__tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
  text-align: center;
}

/* ---------------- 支付方式卡片 ---------------- */
.checkout__pay-cards {
  display: flex;
  gap: 14px;
}

.pay-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1.5px solid var(--brix-border);
  border-radius: var(--brix-radius-lg);
  background: var(--brix-surface);
  cursor: pointer;
  transition: border-color 0.2s cubic-bezier(0.25, 1, 0.5, 1), box-shadow 0.2s ease;
}

.pay-card.is-active {
  border-color: var(--brix-primary);
  box-shadow: 0 0 0 2px var(--brix-primary-soft);
}

.pay-card__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
}

.pay-card__logo--wechat {
  background: #1aad19;
}

.pay-card__logo--alipay {
  background: #1677ff;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
}

.pay-card__name {
  display: flex;
  flex-direction: column;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.pay-card__name small {
  font-size: 11px;
  font-weight: 400;
  color: var(--brix-text-placeholder);
}

.pay-card__tick {
  margin-left: auto;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid var(--brix-border-strong);
  transition: all 0.2s ease;
}

.pay-card.is-active .pay-card__tick {
  border: 5px solid var(--brix-primary);
}

/* ---------------- 支付沙箱弹窗 ---------------- */
.sandbox {
  margin: -12px 0;
}

.sandbox__bar {
  margin: -12px -16px 0;
  padding: 14px 18px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  letter-spacing: 1px;
}

.sandbox__bar--wechat {
  background: #1aad19;
  border-radius: var(--el-dialog-border-radius) var(--el-dialog-border-radius) 0 0;
}

.sandbox__bar--alipay {
  background: #1677ff;
  border-radius: var(--el-dialog-border-radius) var(--el-dialog-border-radius) 0 0;
}

.sandbox__body {
  padding: 26px 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sandbox__merchant {
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.sandbox__amount {
  margin-top: 8px;
  font-size: 34px;
  font-weight: 700;
  color: #16181d;
  font-variant-numeric: tabular-nums;
}

.sandbox__pin {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.sandbox__pin-cell {
  width: 34px;
  height: 42px;
  border: 1px solid var(--brix-border-strong);
  border-radius: 6px;
  position: relative;
  transition: border-color 0.15s ease;
}

.sandbox__pin-cell.is-filled {
  border-color: #1aad19;
}

.sandbox__pin-cell.is-filled::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #16181d;
  transform: translate(-50%, -50%);
}

.sandbox__pin-tip {
  margin-top: 14px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
  min-height: 18px;
}

.sandbox__channel {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f5f7fa;
  font-size: 13px;
  color: var(--brix-text-secondary);
}

.sandbox__channel b {
  color: var(--brix-text);
}

.sandbox__loading {
  font-size: 34px;
  color: #1677ff;
}

.sandbox__ok {
  font-size: 52px;
}

.sandbox__ok--wechat {
  color: #1aad19;
}

.sandbox__ok--alipay {
  color: #1677ff;
}

.sandbox__ok-text {
  margin-top: 10px;
  font-size: 17px;
  font-weight: 700;
  color: #16181d;
}

.sandbox__ok-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.sandbox__foot {
  padding: 4px 24px 22px;
}

.sandbox__btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.sandbox__btn--wechat {
  background: #1aad19;
}

.sandbox__btn--wechat:hover {
  background: #18b218;
  color: #fff;
}

.sandbox__btn--alipay {
  background: #1677ff;
}

.sandbox__btn--alipay:hover {
  background: #3c86ff;
  color: #fff;
}
</style>
