<template>
  <div class="refunds">
    <el-tabs v-model="role" class="refunds__tabs" @tab-change="reload">
      <el-tab-pane label="我申请的（买家）" name="buyer" />
      <el-tab-pane label="我收到的（卖家）" name="seller" />
    </el-tabs>

    <div class="refunds__body" v-loading="loading">
      <el-empty v-if="!loading && rows.length === 0" description="暂无售后记录" :image-size="90" />
      <div v-for="row in rows" :key="row.id" class="refund-card">
        <div class="refund-card__head">
          <el-image :src="resolveImage(row.itemCover)" fit="cover" class="refund-card__cover">
            <template #error>
              <div class="refund-card__cover-placeholder">无图</div>
            </template>
          </el-image>
          <div class="refund-card__info">
            <p class="refund-card__title">{{ row.itemTitle }}</p>
            <p class="refund-card__meta">
              {{ row.typeDesc }} · 金额 ¥{{ row.amount }} · 对方：{{ row.counterpartNickname }}（{{ row.counterpartRole }}）
            </p>
            <p class="refund-card__reason">申请原因：{{ row.reason }}</p>
            <p v-if="row.sellerReply" class="refund-card__reply">卖家回复：{{ row.sellerReply }}</p>
            <p v-if="row.trackingNo" class="refund-card__reply">退货运单号：{{ row.trackingNo }}</p>
            <p v-if="row.closeReason" class="refund-card__reply">关闭原因：{{ row.closeReason }}</p>
          </div>
          <div class="refund-card__side">
            <span class="state" :class="stateClass(row.status)">{{ row.statusDesc }}</span>
            <span class="refund-card__time">{{ formatTime(row.createdAt, 'MM-DD HH:mm') }}</span>
          </div>
        </div>

        <div class="refund-card__images" v-if="row.images && row.images.length">
          <el-image
            v-for="(img, i) in row.images" :key="i"
            :src="resolveImage(img)" fit="cover"
            :preview-src-list="row.images.map(resolveImage)"
            :initial-index="i"
            class="refund-card__image"
          />
        </div>

        <div class="refund-card__ops" v-if="row.actions && row.actions.length">
          <template v-if="isBuyerTab">
            <el-button v-if="row.actions.includes('CANCEL')" link size="small" @click="onCancel(row)">撤销申请</el-button>
            <el-button v-if="row.actions.includes('FILL_LOGISTICS')" type="primary" size="small" @click="openLogistics(row)">填写退货单号</el-button>
            <el-button v-if="row.actions.includes('REVISE')" type="warning" size="small" @click="openRevise(row)">修改重提</el-button>
            <el-button v-if="row.actions.includes('JURY')" type="danger" plain size="small" @click="openJury(row)">申请平台介入</el-button>
          </template>
          <template v-else>
            <el-button v-if="row.actions.includes('AGREE')" type="primary" size="small" @click="onHandle(row, 'AGREE')">同意退款</el-button>
            <el-button v-if="row.actions.includes('REJECT')" type="danger" plain size="small" @click="onHandle(row, 'REJECT')">拒绝</el-button>
            <el-button v-if="row.actions.includes('CONFIRM_RECEIVE')" type="success" size="small" @click="onReceive(row)">确认收到退货</el-button>
          </template>
          <el-button v-if="row.actions.includes('EVIDENCE')" type="primary" size="small" @click="openEvidence(row)">提交举证材料</el-button>
          <el-button v-if="row.juryCaseId" link size="small" @click="goJuryCase">查看小法庭案件</el-button>
        </div>
      </div>

      <div class="refunds__pager" v-if="page.total > page.pageSize">
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

    <!-- 卖家处理：同意 / 拒绝 -->
    <el-dialog v-model="handleDlg.visible" :title="handleDlg.action === 'AGREE' ? '同意退款' : '拒绝售后'" width="440px">
      <p class="dlg-hint">
        {{ handleDlg.action === 'AGREE'
          ? '同意后：仅退款单直接退款；退货退款单将等待买家寄回商品。'
          : '拒绝后买家可在 5 日内修改重提，或申请平台介入（小法庭）。' }}
      </p>
      <el-input v-model="handleDlg.reply" type="textarea" :rows="3" maxlength="300" show-word-limit
                placeholder="回复说明（拒绝时建议填写理由）" />
      <template #footer>
        <el-button @click="handleDlg.visible = false">取消</el-button>
        <el-button :type="handleDlg.action === 'AGREE' ? 'primary' : 'danger'" :loading="handleDlg.loading" @click="confirmHandle">确认</el-button>
      </template>
    </el-dialog>

    <!-- 买家填写退货单号 -->
    <el-dialog v-model="logisticsDlg.visible" title="填写退货运单号" width="420px">
      <el-input v-model="logisticsDlg.trackingNo" maxlength="50" placeholder="请输入快递运单号" />
      <template #footer>
        <el-button @click="logisticsDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="logisticsDlg.loading" @click="confirmLogistics">提交</el-button>
      </template>
    </el-dialog>

    <!-- 买家修改重提 / 申请介入 -->
    <el-dialog v-model="reviseDlg.visible" :title="reviseDlg.mode === 'revise' ? '修改售后申请' : '申请平台介入（小法庭）'" width="480px">
      <p class="dlg-hint" v-if="reviseDlg.mode === 'revise'">
        修改后将重新进入 36 小时卖家处理期；也可申请平台介入由小法庭裁决。
      </p>
      <p class="dlg-hint" v-else>
        案件将匿名进入小法庭：24 小时举证期 → 48 小时用户陪审投票 → 平台终审。请先陈述你的理由，开庭后可继续补充证据。
      </p>
      <el-input v-model="reviseDlg.reason" type="textarea" :rows="4" maxlength="300" show-word-limit
                placeholder="请陈述理由（文字将作为证据展示）" />
      <template #footer>
        <el-button @click="reviseDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="reviseDlg.loading" @click="confirmRevise">提交</el-button>
      </template>
    </el-dialog>

    <!-- 举证期提交证据（买卖双方） -->
    <el-dialog v-model="evidenceDlg.visible" title="提交举证材料" width="480px">
      <p class="dlg-hint">
        举证期 24 小时，每方最多提交 5 条证据；文字说明必填，图片最多 3 张。证据将匿名展示给陪审用户。
      </p>
      <el-input v-model="evidenceDlg.content" type="textarea" :rows="4" maxlength="500" show-word-limit
                placeholder="请描述情况，如：商品与描述不符的细节、聊天记录要点、物流异常说明等" />
      <div class="evidence-uploader">
        <ImageUploader v-model="evidenceDlg.images" :max="3" biz-type="jury" />
      </div>
      <template #footer>
        <el-button @click="evidenceDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="evidenceDlg.loading" @click="confirmEvidence">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUploader from '@/components/ImageUploader.vue'
import {
  cancelRefund, handleRefund, initiateJury,
  reviseRefund, submitRefundLogistics, submitJuryEvidence,
  fetchMyRefunds, fetchReceivedRefunds
} from '@/api/refund'
import { formatTime, resolveImage } from '@/utils/format'

const router = useRouter()

const role = ref('buyer')
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })

const isBuyerTab = computed(() => role.value === 'buyer')

const handleDlg = reactive({ visible: false, loading: false, action: '', target: null, reply: '' })
const logisticsDlg = reactive({ visible: false, loading: false, target: null, trackingNo: '' })
const reviseDlg = reactive({ visible: false, loading: false, mode: 'revise', target: null, reason: '' })
const evidenceDlg = reactive({ visible: false, loading: false, target: null, content: '', images: [] })

async function load() {
  loading.value = true
  try {
    const fetcher = isBuyerTab.value ? fetchMyRefunds : fetchReceivedRefunds
    const data = await fetcher({ currentPage: page.currentPage, pageSize: page.pageSize })
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

function stateClass(status) {
  if (['REFUNDED'].includes(status)) return 'state--ok'
  if (['CLOSED', 'CANCELED', 'REJECTED'].includes(status)) return 'state--done'
  return 'state--pending'
}

function onCancel(row) {
  ElMessageBox.confirm('撤销后本次售后作废，保障期内可重新申请。确认撤销？', '撤销售后', { type: 'warning' })
    .then(async () => {
      await cancelRefund(row.id)
      ElMessage.success('已撤销')
      load()
    })
    .catch(() => {})
}

function onHandle(row, action) {
  handleDlg.target = row
  handleDlg.action = action
  handleDlg.reply = ''
  handleDlg.visible = true
}

async function confirmHandle() {
  handleDlg.loading = true
  try {
    await handleRefund(handleDlg.target.id, { action: handleDlg.action, reply: handleDlg.reply.trim() })
    ElMessage.success('处理完成')
    handleDlg.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    handleDlg.loading = false
  }
}

function openLogistics(row) {
  logisticsDlg.target = row
  logisticsDlg.trackingNo = ''
  logisticsDlg.visible = true
}

async function confirmLogistics() {
  if (!logisticsDlg.trackingNo.trim()) {
    ElMessage.warning('请输入运单号')
    return
  }
  logisticsDlg.loading = true
  try {
    await submitRefundLogistics(logisticsDlg.target.id, { trackingNo: logisticsDlg.trackingNo.trim() })
    ElMessage.success('运单号已登记')
    logisticsDlg.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    logisticsDlg.loading = false
  }
}

function openRevise(row, mode = 'revise') {
  reviseDlg.target = row
  reviseDlg.mode = mode
  reviseDlg.reason = mode === 'revise' ? row.reason : ''
  reviseDlg.visible = true
}

async function confirmRevise() {
  if (!reviseDlg.reason.trim()) {
    ElMessage.warning('请填写内容')
    return
  }
  reviseDlg.loading = true
  try {
    if (reviseDlg.mode === 'revise') {
      await reviseRefund(reviseDlg.target.id, {
        orderId: reviseDlg.target.orderId,
        type: reviseDlg.target.type,
        reason: reviseDlg.reason.trim()
      })
      ElMessage.success('已重新提交')
    } else {
      await initiateJury(reviseDlg.target.id, { content: reviseDlg.reason.trim() })
      ElMessage.success('已进入平台介入流程')
    }
    reviseDlg.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    reviseDlg.loading = false
  }
}

function openJury(row) {
  openRevise(row, 'jury')
}

function openEvidence(row) {
  evidenceDlg.target = row
  evidenceDlg.content = ''
  evidenceDlg.images = []
  evidenceDlg.visible = true
}

async function confirmEvidence() {
  if (!evidenceDlg.content.trim()) {
    ElMessage.warning('请填写证据说明')
    return
  }
  evidenceDlg.loading = true
  try {
    await submitJuryEvidence(evidenceDlg.target.id, {
      content: evidenceDlg.content.trim(),
      images: evidenceDlg.images
    })
    ElMessage.success('证据已提交')
    evidenceDlg.visible = false
    load()
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    evidenceDlg.loading = false
  }
}

function goJuryCase() {
  router.push('/jury')
}

async function onReceive(row) {
  await ElMessageBox.confirm('确认已收到买家寄回的商品？确认后立即执行退款。', '确认收货', { type: 'warning' })
  await confirmRefundReceive(row.id)
  ElMessage.success('退款已完成')
  load()
}

onMounted(load)
</script>

<style scoped>
.refunds {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.refunds__body {
  padding: 14px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
  min-height: 240px;
}

.refunds__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 4px;
}

.refund-card {
  padding: 14px 0;
  border-bottom: 1px dashed #eef1f5;
}

.refund-card:last-of-type {
  border-bottom: none;
}

.refund-card__head {
  display: flex;
  gap: 12px;
}

.refund-card__cover {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 8px;
  background: #f4f6f8;
}

.refund-card__cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #c0c8d2;
}

.refund-card__info {
  flex: 1;
  min-width: 0;
}

.refund-card__title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: #1c2a24;
}

.refund-card__meta,
.refund-card__reason,
.refund-card__reply {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #7c8894;
  line-height: 1.6;
}

.refund-card__reply {
  color: #a2703a;
}

.refund-card__side {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.refund-card__time {
  font-size: 12px;
  color: #9aa5b3;
}

.refund-card__images {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.refund-card__image {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background: #f4f6f8;
}

.refund-card__ops {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.state {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.state--pending {
  background: #fff5e6;
  color: #b8730b;
}

.state--ok {
  background: #e8f6ee;
  color: #1f8a4c;
}

.state--done {
  background: #f0f3f5;
  color: #5c6b63;
}

.dlg-hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #8d99a8;
}

.evidence-uploader {
  margin-top: 10px;
}
</style>
