<template>
  <div v-loading="loading" class="item-detail">
    <template v-if="item">
      <div class="item-detail__main">
        <!-- 左：图片轮播 -->
        <div class="item-detail__gallery brix-card">
          <el-carousel
            v-if="images.length"
            :autoplay="false"
            height="420px"
            indicator-position="outside"
            arrow="always"
          >
            <el-carousel-item v-for="image in images" :key="image">
              <el-image :src="resolveImage(image)" fit="cover" class="item-detail__image" :preview-src-list="previewList" />
            </el-carousel-item>
          </el-carousel>
          <div v-else class="item-detail__no-image">
            <el-icon><Picture /></el-icon>
            <p>卖家没有上传图片</p>
          </div>
        </div>

        <!-- 右：信息区 -->
        <div class="item-detail__info">
          <h1 class="item-detail__title">{{ item.title }}</h1>

          <div class="item-detail__price brix-card">
            <PriceTag :price="item.price" :original-price="item.originalPrice" size="large" />
            <span class="item-detail__views">
              <el-icon><View /></el-icon>
              {{ item.viewCount }} 次浏览 · {{ item.favoriteCount }} 人收藏
            </span>
          </div>

          <dl class="item-detail__specs">
            <div class="item-detail__spec">
              <dt>成色</dt>
              <dd><ConditionTag :level="item.conditionLevel" /></dd>
            </div>
            <div v-if="item.brandModel" class="item-detail__spec">
              <dt>品牌型号</dt>
              <dd>{{ item.brandModel }}</dd>
            </div>
            <div class="item-detail__spec">
              <dt>交易方式</dt>
              <dd><StatusTag kind="trade" :value="item.tradeType" effect="plain" /></dd>
            </div>
            <div class="item-detail__spec">
              <dt>校区</dt>
              <dd>{{ campusText }}</dd>
            </div>
            <div class="item-detail__spec">
              <dt>分类</dt>
              <dd>{{ item.categoryName || '-' }}</dd>
            </div>
            <div class="item-detail__spec">
              <dt>联系方式</dt>
              <dd>{{ item.contact || '通过站内私信联系' }}</dd>
            </div>
          </dl>

          <p class="item-detail__desc">{{ item.description }}</p>

          <div class="item-detail__actions">
            <el-button
              type="primary"
              size="large"
              :disabled="!purchasable"
              @click="wantIt"
            >
              {{ actionText }}
            </el-button>
            <el-button size="large" :disabled="!purchasable" @click="goChat">私信卖家</el-button>
            <el-button size="large" :type="item.isFavorite ? 'warning' : 'default'" plain @click="toggleFavorite">
              {{ item.isFavorite ? '已收藏' : '收藏' }}
            </el-button>
            <el-button size="large" text type="danger" @click="reportVisible = true">举报</el-button>
          </div>
          <p v-if="pendingHint" class="item-detail__pending-tip">{{ pendingHint }}</p>
          <p v-if="!purchasable" class="item-detail__sold-tip">{{ unavailableTip }}</p>
          <div v-if="canAppeal || appeal" class="item-detail__appeal">
            <p v-if="appeal" class="item-detail__appeal-status">
              申诉进度：<el-tag size="small" :type="appeal.status === 'OVERTURNED' ? 'success' : appeal.status === 'MAINTAINED' ? 'info' : 'warning'">{{ appeal.statusDesc }}</el-tag>
              <span v-if="appeal.reply" class="item-detail__appeal-reply">审核员回复：{{ appeal.reply }}</span>
            </p>
            <el-button v-if="canAppeal && !appeal" size="small" plain @click="appealVisible = true">提交申诉</el-button>
          </div>
        </div>
      </div>

      <div class="item-detail__aside-row">
        <div class="item-detail__comments">
          <CommentList :item-id="itemId" />
        </div>
        <div class="item-detail__seller">
          <SellerCard :seller="item.seller" />
        </div>
      </div>

      <div v-if="similarItems.length" class="item-detail__similar">
        <div class="brix-section-title">
          <span>同类好物</span>
          <span class="brix-section-title__sub">同分类在售商品，按热度推荐</span>
        </div>
        <div class="item-detail__similar-grid">
          <ItemCard v-for="similar in similarItems" :key="similar.id" :item="similar" />
        </div>
      </div>
    </template>

    <EmptyState v-else-if="!loading" text="商品不存在或已下架" show-reset @reset="router.push('/')" />

    <ReportDialog v-model="reportVisible" :item-id="itemId" />
    <el-dialog v-model="appealVisible" title="提交申诉" width="520">
      <p class="appeal-dialog__tip">
        申诉仅针对「平台对商品的处置」（审核拒绝 / 被下架），请说明商品真实情况，
        审核员将对照原审核结论与你的理由处理。
      </p>
      <el-input
        v-model="appealReason" type="textarea" :rows="4" maxlength="500" show-word-limit
        placeholder="申诉理由（10~500 字，例如说明商品来源、澄清误判）"
      />
      <template #footer>
        <el-button @click="appealVisible = false">取消</el-button>
        <el-button type="primary" :loading="appealSubmitting" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="tradeDialog" title="确认订单信息" width="480px">
      <el-form label-width="86px" label-position="left">
        <el-form-item v-if="item?.tradeType === 'BOTH'" label="交易方式">
          <el-radio-group v-model="tradeType">
            <el-radio value="MEET">面交（可当面验货）</el-radio>
            <el-radio value="SHIP">邮寄</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 面单：时间 + 地点 -->
        <template v-if="tradeType === 'MEET'">
          <el-form-item label="面交时间" required>
            <el-date-picker
              v-model="orderForm.meetTime"
              type="datetime"
              placeholder="选择双方方便的时间"
              format="YYYY-MM-DD HH:mm"
              :disabled-date="(d) => d.getTime() < Date.now() - 86400000"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="面交地点" required>
            <el-input v-model="orderForm.meetPlace" maxlength="100" placeholder="如：东校区 3 号宿舍楼下 / 图书馆门口" />
          </el-form-item>
        </template>

        <!-- 邮寄：收货三件套 -->
        <template v-else>
          <el-form-item label="收货人" required>
            <el-input v-model="orderForm.receiverName" maxlength="30" placeholder="收货人姓名" />
          </el-form-item>
          <el-form-item label="手机号" required>
            <el-input
              v-model="orderForm.receiverPhone"
              type="tel"
              maxlength="11"
              placeholder="11 位手机号"
              @input="onReceiverPhoneInput"
            />
          </el-form-item>
          <el-form-item label="收货地址" required>
            <el-input
              v-model="orderForm.receiverAddress"
              type="textarea"
              :rows="2"
              maxlength="200"
              placeholder="校区 / 楼栋 / 宿舍号，或校外快递地址"
            />
          </el-form-item>
        </template>

        <el-form-item label="备注">
          <el-input v-model="orderForm.buyerNote" type="textarea" :rows="2" maxlength="200" placeholder="选填，如「周六下午都在」「希望走菜鸟驿站」" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tradeDialog = false">取消</el-button>
        <el-button type="primary" :loading="ordering" @click="createOrderNow">确定下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchItemDetail, fetchSimilarItems } from '@/api/item'
import { createAppeal, fetchItemAppeal } from '@/api/appeal'
import { toggleFavorite as toggleFavoriteApi } from '@/api/favorite'
import { createOrder } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { PHONE_MESSAGE, PHONE_PATTERN, sanitizePhone } from '@/constants'
import { resolveImage } from '@/utils/format'
import PriceTag from '@/components/PriceTag.vue'
import ConditionTag from '@/components/ConditionTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import SellerCard from '@/components/SellerCard.vue'
import CommentList from '@/components/CommentList.vue'
import ItemCard from '@/components/ItemCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReportDialog from '@/components/ReportDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 商品 id 跟随路由：详情页复用组件实例（点「同类好物」互跳），必须响应式跟随 params */
const itemId = computed(() => String(route.params.id || ''))
const item = ref(null)
const similarItems = ref([])
const loading = ref(false)
const reportVisible = ref(false)
const tradeDialog = ref(false)
const tradeType = ref('MEET')
const ordering = ref(false)

/** 申诉（卖家本人，商品被拒/被处置下架时可见） */
const appealVisible = ref(false)
const appealReason = ref('')
const appealSubmitting = ref(false)
const appeal = ref(null)

/** 下单表单：面交=时间+地点；邮寄=收货三件套；备注通用 */
const orderForm = reactive({
  meetTime: '',
  meetPlace: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  buyerNote: ''
})

/** 收货手机号输入即过滤非数字（maxlength 只管长度拦不住字母） */
function onReceiverPhoneInput(value) {
  orderForm.receiverPhone = sanitizePhone(value)
}

/** 只有「在售」可下单；下架/待审核/驳回都要在详情页直接拦住，不能等提交后才报错 */
const purchasable = computed(() => item.value?.status === 'ON_SALE')

/** 先到先得提示：已有待支付订单时提醒买家支付顺序裁决规则 */
const pendingHint = computed(() => {
  const n = Number(item.value?.pendingBuyerCount || 0)
  if (!purchasable.value || n <= 0) return ''
  return `已有 ${n} 人下单待支付，按支付顺序先到先得，请尽快完成支付～`
})

/** 购买按钮文案：按商品状态给出明确原因 */
const actionText = computed(() => {
  switch (item.value?.status) {
    case 'SOLD': return '已售出'
    case 'OFF_SHELF': return '已下架'
    case 'PENDING': return '待审核'
    case 'REJECTED': return '未通过'
    default: return '我想要'
  }
})

/** 不可购买时的说明文案 */
const unavailableTip = computed(() => {
  switch (item.value?.status) {
    case 'SOLD': return '这件宝贝已经找到新主人啦，看看同类好物吧～'
    case 'OFF_SHELF': return '这件宝贝已下架，看看同类好物吧～'
    case 'PENDING': return '这件宝贝正在审核中，通过后即可购买。'
    case 'REJECTED': return '这件宝贝未通过审核，暂时无法购买。'
    default: return ''
  }
})
const images = computed(() => (item.value?.images || []).map((image) => image.url))
const previewList = computed(() => images.value.map((url) => resolveImage(url)))

/** 归属展示：有学校时用「学校 · 校区」，历史数据只有校区名称时退化展示 */
const campusText = computed(() => {
  const name = item.value?.schoolName
  const campus = item.value?.campus
  if (name && campus) return `${name} · ${campus}`
  return campus || name || '-'
})

/** 加载详情（后端顺带浏览量 +1），并拉同类推荐 */
async function load() {
  loading.value = true
  try {
    item.value = await fetchItemDetail(itemId.value)
    if (item.value?.tradeType === 'SHIP') tradeType.value = 'SHIP'
    similarItems.value = (await fetchSimilarItems(itemId.value)) || []
    if (item.value?.isOwner) {
      appeal.value = await fetchItemAppeal(itemId.value)
    }
  } finally {
    loading.value = false
  }
}

/** 卖家可申诉：商品被拒或下架（被处置的判断由后端校验，主动下架会被提示不支持） */
const canAppeal = computed(() =>
  item.value?.isOwner && ['REJECTED', 'OFF_SHELF'].includes(item.value?.status))

async function submitAppeal() {
  const reason = appealReason.value.trim()
  if (reason.length < 10) {
    ElMessage.warning('申诉理由至少 10 个字')
    return
  }
  appealSubmitting.value = true
  try {
    appeal.value = await createAppeal({ itemId: itemId.value, reason })
    appealVisible.value = false
    appealReason.value = ''
    ElMessage.success('申诉已提交，请等待审核员处理')
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    appealSubmitting.value = false
  }
}

/** 同页互跳（如点「同类好物」）：路由 id 变化时重置表单并重新加载新商品 */
watch(itemId, (id, old) => {
  if (!id || id === old) return
  Object.assign(orderForm, {
    meetTime: '',
    meetPlace: '',
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    buyerNote: ''
  })
  load()
})

/** 我想要：未登录先登录（带回跳），买家选择交易方式后下单 */
function wantIt() {
  if (!purchasable.value) {
    ElMessage.info(unavailableTip.value || '该商品暂不可购买')
    return
  }
  if (!auth.isLogin) {
    router.push({ path: '/login', query: { redirect: encodeURIComponent(route.fullPath) } })
    return
  }
  if (item.value?.isOwner) {
    ElMessage.info('这是你自己发布的商品')
    return
  }
  tradeType.value = item.value?.tradeType === 'SHIP' ? 'SHIP' : 'MEET'
  // 手机号默认带出个人资料，减少输入
  if (!orderForm.receiverPhone && auth.user?.phone) {
    orderForm.receiverPhone = auth.user.phone
  }
  tradeDialog.value = true
}

/** 校验并提交订单（面交=时间+地点必填；邮寄=收货三件套必填） */
async function createOrderNow() {
  if (tradeType.value === 'MEET') {
    if (!orderForm.meetTime) {
      ElMessage.warning('请选择面交时间')
      return
    }
    if (!orderForm.meetPlace.trim()) {
      ElMessage.warning('请填写面交地点')
      return
    }
  } else {
    if (!orderForm.receiverName.trim()) {
      ElMessage.warning('请填写收货人姓名')
      return
    }
    if (!orderForm.receiverPhone.trim()) {
      ElMessage.warning('请填写收货人手机号')
      return
    }
    if (!PHONE_PATTERN.test(orderForm.receiverPhone.trim())) {
      ElMessage.warning(PHONE_MESSAGE)
      return
    }
    if (!orderForm.receiverAddress.trim()) {
      ElMessage.warning('请填写收货地址')
      return
    }
  }
  ordering.value = true
  try {
    const payload = { itemId: itemId.value, tradeType: tradeType.value }
    if (tradeType.value === 'MEET') {
      // 后端 LocalDateTime 反序列化要求 yyyy-MM-dd HH:mm:ss
      payload.meetTime = dayjs(orderForm.meetTime).format('YYYY-MM-DD HH:mm:ss')
      payload.meetPlace = orderForm.meetPlace.trim()
    } else {
      payload.receiverName = orderForm.receiverName.trim()
      payload.receiverPhone = orderForm.receiverPhone.trim()
      payload.receiverAddress = orderForm.receiverAddress.trim()
    }
    if (orderForm.buyerNote.trim()) payload.buyerNote = orderForm.buyerNote.trim()
    const data = await createOrder(payload)
    ElMessage.success('下单成功，请尽快完成预付款')
    tradeDialog.value = false
    router.push(`/checkout/${data.id}`)
  } finally {
    ordering.value = false
  }
}

/** 跳私信并带上商品来源 */
function goChat() {
  if (!auth.isLogin) {
    router.push({ path: '/login', query: { redirect: encodeURIComponent(route.fullPath) } })
    return
  }
  router.push({ path: '/messages', query: { peerId: String(item.value?.seller?.id || ''), itemId: itemId.value } })
}

/** 收藏切换（未登录先登录） */
async function toggleFavorite() {
  if (!auth.isLogin) {
    router.push({ path: '/login', query: { redirect: encodeURIComponent(route.fullPath) } })
    return
  }
  try {
    const data = await toggleFavoriteApi(itemId.value)
    item.value.isFavorite = !!data?.favorited
    item.value.favoriteCount = data?.favoriteCount ?? item.value.favoriteCount
    ElMessage.success(item.value.isFavorite ? '已加入收藏' : '已取消收藏')
  } catch (error) {
    // request.js 已提示
  }
}

onMounted(load)
</script>

<style scoped>
.item-detail__main {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.item-detail__similar {
  margin-top: 24px;
}

.item-detail__similar-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 992px) {
  .item-detail__similar-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.item-detail__gallery {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.item-detail__image {
  width: 100%;
  height: 420px;
  display: block;
}

.item-detail__no-image {
  height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--brix-text-placeholder);
  background: #f7f8fa;
}

.item-detail__no-image .el-icon {
  font-size: 44px;
}

.item-detail__info {
  width: 420px;
  flex-shrink: 0;
}

.item-detail__title {
  font-size: 22px;
  line-height: 1.4;
  margin: 0 0 14px;
}

.item-detail__price {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #fff8f6;
  border-color: rgba(255, 77, 46, 0.12);
}

.item-detail__views {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--brix-text-secondary);
}

.item-detail__specs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
  margin: 16px 0;
  padding: 14px 16px;
  background: #fff;
  border: 0.5px solid var(--brix-border);
  border-radius: var(--brix-radius);
}

.item-detail__spec {
  display: flex;
  gap: 10px;
  font-size: 13px;
}

.item-detail__spec dt {
  color: var(--brix-text-placeholder);
  flex-shrink: 0;
}

.item-detail__spec dd {
  margin: 0;
  color: var(--brix-text);
}

.item-detail__desc {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--brix-text);
  background: #fff;
  border: 0.5px solid var(--brix-border);
  border-radius: var(--brix-radius);
  padding: 14px 16px;
  font-size: 14px;
}

.item-detail__actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.item-detail__sold-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-price);
}

.item-detail__appeal {
  margin-top: 10px;
}

.item-detail__appeal-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 12.5px;
  color: var(--brix-text-secondary);
}

.item-detail__appeal-reply {
  color: var(--brix-text-primary);
}

.item-detail__pending-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--brix-warning, #b8860b);
}

.item-detail__aside-row {
  display: flex;
  gap: 20px;
  margin-top: 20px;
  align-items: flex-start;
}

.item-detail__comments {
  flex: 1;
  min-width: 0;
}

.item-detail__seller {
  width: 340px;
  flex-shrink: 0;
}

.item-detail__trade {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
