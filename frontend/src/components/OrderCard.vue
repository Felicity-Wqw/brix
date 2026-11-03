<template>
  <div class="order-card brix-card">
    <div class="order-card__head">
      <div class="order-card__no">
        <span class="order-card__no-label">订单号</span>
        <span>{{ order.orderNo }}</span>
        <span class="order-card__time">{{ formatTime(order.createdAt) }}</span>
      </div>
      <StatusTag kind="order" :value="order.status" />
    </div>

    <div class="order-card__body">
      <router-link :to="`/items/${order.itemId}`" class="order-card__cover">
        <el-image :src="resolveImage(order.itemCover)" fit="cover" class="order-card__image">
          <template #error>
            <div class="order-card__placeholder"><el-icon><Picture /></el-icon></div>
          </template>
        </el-image>
      </router-link>

      <div class="order-card__info">
        <router-link :to="`/items/${order.itemId}`" class="order-card__title">{{ order.itemTitle }}</router-link>
        <div class="order-card__meta">
          <StatusTag kind="trade" :value="order.tradeType" effect="plain" />
          <span v-if="order.meetPlace" class="order-card__place">
            <el-icon><Location /></el-icon>
            {{ order.meetPlace }}
          </span>
          <span v-if="order.meetTime" class="order-card__place">
            <el-icon><AlarmClock /></el-icon>
            {{ formatTime(order.meetTime, 'MM-DD HH:mm') }}
          </span>
          <span v-if="order.escrowDesc" class="order-card__escrow" :class="{ 'is-settled': !!order.settleTime }">
            {{ order.escrowDesc }}
          </span>
        </div>
        <div class="order-card__counterpart">
          {{ perspective === 'buy' ? '卖家' : '买家' }}：
          {{ perspective === 'buy' ? order.sellerNickname : order.buyerNickname }}
        </div>
      </div>

      <div class="order-card__amount">
        <PriceTag :price="order.amount" :show-original="false" size="small" />
        <span v-if="order.payMethodDesc" class="order-card__pay">{{ order.payMethodDesc }}</span>
      </div>
    </div>

    <div class="order-card__actions">
      <el-button text @click="emit('detail', order)">订单详情</el-button>
      <el-button v-if="order.canPay" type="primary" size="small" @click="emit('pay', order)">去支付</el-button>
      <el-button v-if="order.canCancel" size="small" @click="emit('cancel', order)">取消订单</el-button>
      <el-button v-if="order.canShip" type="primary" size="small" @click="emit('ship', order)">已发货</el-button>
      <el-button v-if="order.canConfirm" type="primary" size="small" @click="emit('confirm', order)">
        {{ order.tradeType === 'MEET' ? '确认完成' : '确认收货' }}
      </el-button>
      <el-button v-if="order.canReview" type="primary" plain size="small" @click="emit('review', order)">评价</el-button>
      <el-tag v-else-if="order.reviewed" size="small" effect="plain">已评价</el-tag>
    </div>
  </div>
</template>

<script setup>
import PriceTag from './PriceTag.vue'
import StatusTag from './StatusTag.vue'
import { formatTime, resolveImage } from '@/utils/format'

defineProps({
  order: { type: Object, required: true },
  // buy：我买到的；sell：我卖出的
  perspective: { type: String, default: 'buy' }
})

const emit = defineEmits(['detail', 'pay', 'cancel', 'ship', 'confirm', 'review'])
</script>

<style scoped>
.order-card {
  padding: 14px 16px;
}

.order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 0.5px solid var(--brix-border);
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.order-card__no {
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-card__no-label {
  color: var(--brix-text-placeholder);
}

.order-card__time {
  color: var(--brix-text-placeholder);
}

.order-card__body {
  display: flex;
  gap: 14px;
  padding: 14px 0;
}

.order-card__cover {
  flex-shrink: 0;
}

.order-card__image {
  width: 84px;
  height: 84px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f4f6;
}

.order-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
  height: 84px;
  color: #c9cfd7;
}

.order-card__info {
  flex: 1;
  min-width: 0;
}

.order-card__title {
  display: block;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-card__title:hover {
  color: var(--brix-primary);
}

.order-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.order-card__place {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

/* 预付款托管徽标 */
.order-card__escrow {
  padding: 1px 8px;
  border-radius: 999px;
  background: #fff7e8;
  color: #b25e00;
  font-size: 11px;
}

.order-card__escrow.is-settled {
  background: var(--brix-primary-soft);
  color: var(--brix-primary);
}

.order-card__counterpart {
  margin-top: 6px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.order-card__amount {
  text-align: right;
  flex-shrink: 0;
}

.order-card__pay {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--brix-text-placeholder);
}

.order-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  border-top: 0.5px solid var(--brix-border);
}
</style>
