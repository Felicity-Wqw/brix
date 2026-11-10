<template>
  <div class="audit">
    <!-- 页签：审核员的核心动线是「先清高危误杀，再过普通待审」 -->
    <div class="audit__bar">
      <el-tabs v-model="activeTab" class="audit__tabs" @tab-change="onTabChange">
        <el-tab-pane v-for="tab in TABS" :key="tab.name" :label="tab.label" :name="tab.name" />
      </el-tabs>

      <div class="audit__filters">
        <el-input
          v-model="keyword"
          placeholder="搜索标题或描述"
          clearable
          :prefix-icon="Search"
          class="audit__search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="riskLevel" placeholder="风险级别" clearable class="audit__risk" @change="reload">
          <el-option label="低风险（直发）" value="LOW" />
          <el-option label="中风险（待审）" value="MEDIUM" />
          <el-option label="高风险（驳回）" value="HIGH" />
        </el-select>
      </div>
    </div>

    <!-- 高风险优先处理区说明：让审核员知道这一屏是「机器已拦下、等人确认」 -->
    <div v-if="activeTab === 'HIGH_PRIORITY'" class="audit__notice">
      <el-icon class="audit__notice-icon"><WarnTriangleFilled /></el-icon>
      <div>
        <p class="audit__notice-title">高风险优先处理区</p>
        <p class="audit__notice-text">
          这些商品命中了黑名单关键词，或被 AI 判定为无法上架，<strong>已落库留痕但前台不可见</strong>。
          按风险级别排序、高危置顶，请优先确认是「误杀需放行」还是「维持驳回」。
        </p>
      </div>
    </div>

    <div class="audit__body" v-loading="loading">
      <el-table :data="rows" size="default" empty-text="队列已清空，暂无待处理商品">
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div class="cell-item">
              <el-image :src="resolveImage(row.coverImage)" fit="cover" class="cell-item__cover">
                <template #error>
                  <div class="cell-item__placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cell-item__text">
                <p class="cell-item__title" :title="row.title">{{ row.title }}</p>
                <p class="cell-item__meta">
                  {{ row.categoryName || '未分类' }} · {{ row.schoolName || '未归属' }}
                </p>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="88" align="right">
          <template #default="{ row }">
            <span class="cell-price">￥{{ Number(row.price || 0).toLocaleString('zh-CN') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="风险级别" width="88">
          <template #default="{ row }">
            <span class="risk" :class="`risk--${(row.riskLevel || 'none').toLowerCase()}`">
              {{ row.riskLevelDesc || '未分级' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="判定说明" min-width="205">
          <template #default="{ row }">
            <span class="cell-reason" :title="row.auditReason">{{ row.auditReason || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="78">
          <template #default="{ row }">
            <StatusTag kind="item" :value="row.status" />
          </template>
        </el-table-column>

        <el-table-column label="提交时间" width="106">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTime(row.createdAt, 'MM-DD HH:mm') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="146" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'REJECTED'"
              link
              size="small"
              class="op-pass"
              @click="quickAudit(row, 'PASS')"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status !== 'REJECTED'"
              link
              size="small"
              class="op-reject"
              @click="openReject(row)"
            >
              驳回
            </el-button>
            <el-button
              v-if="row.status === 'ON_SALE'"
              link
              size="small"
              class="op-offline"
              @click="quickAudit(row, 'OFFLINE')"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="audit__pager">
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

    <!-- 详情抽屉：审核要看全文与全部图片，表格里放不下 -->
    <el-drawer v-model="drawer.visible" title="商品审核详情" size="560px">
      <div v-if="drawer.detail" class="detail">
        <div class="detail__audit" :class="`detail__audit--${(drawer.detail.riskLevel || 'none').toLowerCase()}`">
          <p class="detail__audit-title">
            {{ drawer.detail.riskLevelDesc || '未分级' }} · 来源 {{ sourceLabel(drawer.detail.auditSource) }}
          </p>
          <p class="detail__audit-text">{{ drawer.detail.auditReason || '系统未记录判定说明' }}</p>
        </div>

        <!-- 审核证据链：两道闸各自判了什么，一条条摆出来，答辩时可直接对着讲 -->
        <div class="gates" v-if="drawer.trail.length">
          <p class="gates__head">
            审核证据链
            <span class="gates__count">{{ drawer.trail.length }} 次</span>
          </p>
          <div v-for="(log, index) in drawer.trail" :key="log.id" class="gates__row">
            <p class="gates__time">
              #{{ drawer.trail.length - index }} · {{ formatTime(log.createdAt, 'YYYY-MM-DD HH:mm:ss') }}
              <span class="gates__level" :class="`risk--${(log.riskLevel || 'none').toLowerCase()}`">
                {{ log.riskLevelDesc }}
              </span>
            </p>
            <div class="gates__pair">
              <div class="gates__card">
                <p class="gates__label">第一道闸 · 关键词</p>
                <p class="gates__value">{{ log.gate1ResultDesc }}</p>
                <p v-if="log.gate1Word" class="gates__extra">
                  命中「{{ log.gate1Word }}」· {{ log.gate1Category }}
                </p>
              </div>
              <div class="gates__card">
                <p class="gates__label">第二道闸 · AI</p>
                <p class="gates__value">{{ log.gate2ResultDesc }}</p>
                <p v-if="log.gate2Reason" class="gates__extra">{{ log.gate2Reason }}</p>
              </div>
            </div>
            <p class="gates__foot">
              结论：落入 <strong>{{ log.decisionStatusDesc }}</strong>
              <span v-if="log.handled">
                · 已由 {{ log.handlerNickname || '审核员' }} 于
                {{ formatTime(log.handledAt, 'MM-DD HH:mm') }} 人工处置
              </span>
              <span v-else> · 待人工确认</span>
            </p>
          </div>
        </div>

        <h4 class="detail__title">{{ drawer.detail.title }}</h4>
        <div class="detail__facts">
          <span>￥{{ Number(drawer.detail.price || 0).toLocaleString('zh-CN') }}</span>
          <span>{{ drawer.detail.conditionDesc }}</span>
          <span>{{ drawer.detail.tradeTypeDesc }}</span>
          <span>{{ drawer.detail.categoryName }}</span>
        </div>
        <p class="detail__school">{{ drawer.detail.schoolName }} · {{ drawer.detail.campus }}</p>

        <div class="detail__images">
          <el-image
            v-for="img in drawer.detail.images || []"
            :key="img.id"
            :src="resolveImage(img.url)"
            :preview-src-list="(drawer.detail.images || []).map((i) => resolveImage(i.url))"
            fit="cover"
            class="detail__image"
          />
        </div>

        <p class="detail__desc">{{ drawer.detail.description }}</p>

        <div class="detail__seller">
          <span>卖家：{{ drawer.detail.sellerNickname }}</span>
          <span>信用分：{{ drawer.detail.seller?.creditScore ?? '—' }}</span>
          <span>联系方式：{{ drawer.detail.contact || '—' }}</span>
        </div>
      </div>

      <template #footer>
        <div class="detail__actions">
          <el-button @click="drawer.visible = false">关闭</el-button>
          <el-button type="danger" plain @click="openReject(drawer.detail)">驳回</el-button>
          <el-button type="primary" @click="quickAudit(drawer.detail, 'PASS')">通过并上架</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 驳回原因：必须写，卖家会看到 -->
    <el-dialog v-model="reject.visible" title="驳回商品" width="420px">
      <p class="reject__hint">
        驳回原因会展示给卖家（在「我的商品」里可见），请写明具体问题与修改方向。
      </p>
      <el-input
        v-model="reject.reason"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="例如：图片与描述不符，第三张图带有其他平台水印"
      />
      <template #footer>
        <el-button @click="reject.visible = false">取消</el-button>
        <el-button type="danger" :loading="reject.loading" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Search, WarnTriangleFilled } from '@element-plus/icons-vue'
import {
  auditItem,
  fetchAdminItemDetail,
  fetchAdminItems,
  fetchItemAuditTrail
} from '@/api/admin'
import { formatTime, resolveImage } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

/**
 * 页签表：把「状态过滤」和「排序口径」一起描述，避免两处各写一份条件而对不上。
 *
 * riskFirst=true 的页签走后端 XML 手写排序（CASE 权重），因为风险级别要按业务严重度排，
 * 不能按字母序排——字母序 DESC 会得到 MEDIUM→LOW→HIGH，正好把高危排到最后。
 */
const TABS = [
  { name: 'HIGH_PRIORITY', label: '高风险优先', status: 'REJECTED', riskFirst: true },
  { name: 'PENDING', label: '待审核', status: 'PENDING', riskFirst: false },
  { name: 'ON_SALE', label: '在售', status: 'ON_SALE', riskFirst: false },
  { name: 'all', label: '全部', status: '', riskFirst: false }
]

const activeTab = ref('HIGH_PRIORITY')
const keyword = ref('')
const riskLevel = ref('')
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })
const drawer = reactive({ visible: false, detail: null, trail: [] })
const reject = reactive({ visible: false, reason: '', loading: false, target: null })

const SOURCE_LABEL = {
  WORD: '违禁词闸',
  RULE: '规则闸',
  AI: 'AI 审核员',
  MANUAL: '人工审核',
  LEGACY: '历史数据'
}

function sourceLabel(source) {
  return SOURCE_LABEL[source] || '系统'
}

async function load() {
  loading.value = true
  try {
    const tab = TABS.find((item) => item.name === activeTab.value) || TABS[0]
    const data = await fetchAdminItems({
      status: tab.status,
      riskFirst: tab.riskFirst,
      riskLevel: riskLevel.value,
      keyword: keyword.value.trim(),
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

function onTabChange() {
  reload()
}

async function openDetail(row) {
  if (!row) return
  drawer.visible = true
  drawer.detail = null
  drawer.trail = []
  try {
    drawer.detail = await fetchAdminItemDetail(row.id)
  } catch (error) {
    drawer.visible = false
    return
  }
  // 证据链单独拉：详情接口给的是「当前状态」，日志给的是「历史上怎么判的」，
  // 两者分开取，商品被编辑后也不会污染审核记录
  try {
    drawer.trail = (await fetchItemAuditTrail(row.id)) || []
  } catch (error) {
    drawer.trail = []
  }
}

/** 通过 / 下架这类不需要填原因的动作，直接二次确认后执行 */
async function quickAudit(row, action) {
  if (!row) return
  const labels = { PASS: '通过并上架', OFFLINE: '强制下架' }
  try {
    await ElMessageBox.confirm(`确认对「${row.title}」执行「${labels[action]}」？`, '审核确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  await submitAudit(row.id, { action })
}

function openReject(row) {
  if (!row) return
  reject.target = row
  reject.reason = ''
  reject.visible = true
}

async function confirmReject() {
  if (!reject.reason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  reject.loading = true
  try {
    await submitAudit(reject.target.id, { action: 'REJECT', reason: reject.reason.trim() })
    reject.visible = false
  } finally {
    reject.loading = false
  }
}

async function submitAudit(id, payload) {
  try {
    await auditItem(id, payload)
    ElMessage.success('处置完成')
    drawer.visible = false
    load()
  } catch (error) {
    /* 提示由拦截器统一弹出 */
  }
}

onMounted(load)
</script>

<style scoped>
.audit {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.audit__bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0 0;
}

.audit__tabs {
  flex: 1;
  min-width: 0;
}

.audit__tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.audit__filters {
  display: flex;
  gap: 10px;
  padding-top: 6px;
}

.audit__search {
  width: 220px;
}

.audit__risk {
  width: 140px;
}

.audit__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

/* 高风险优先区说明条：红色调，和「高危」标签同色系 */
.audit__notice {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  background: #fdf4f1;
  border: 1px solid #f6d8cf;
}

.audit__notice-icon {
  flex: none;
  margin-top: 2px;
  font-size: 16px;
  color: #c9411f;
}

.audit__notice-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #a5351a;
}

.audit__notice-text {
  margin: 4px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #8a4b38;
}

.audit__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

/* ---------------- 表格单元格 ---------------- */
.cell-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-item__cover {
  width: 46px;
  height: 46px;
  flex: none;
  border-radius: 8px;
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

.cell-item__text {
  min-width: 0;
}

.cell-item__title {
  margin: 0;
  font-size: 13.5px;
  color: #1c2a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-item__meta {
  margin: 3px 0 0;
  font-size: 12px;
  color: #8d99a8;
}

.cell-price {
  font-weight: 700;
  color: #ff4d2e;
  font-variant-numeric: tabular-nums;
}

.cell-reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12.5px;
  color: #5c6b63;
  line-height: 1.6;
}

.cell-time {
  font-size: 12.5px;
  color: #8d99a8;
}

/* 风险级别标签：与仪表盘的风险配色一致 */
.risk {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.risk--low {
  background: #e7f3ec;
  color: #0e7a50;
}

.risk--medium {
  background: #fff5e6;
  color: #b8730b;
}

.risk--high {
  background: #fdece8;
  color: #c9411f;
}

.risk--none {
  background: #f0f3f5;
  color: #8d99a8;
}

.op-pass {
  color: #0e7a50;
}

.op-reject,
.op-offline {
  color: #c9411f;
}

/* ---------------- 审核证据链（抽屉内） ---------------- */
.gates {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f7f9fa;
  border: 1px solid #e8ecf1;
}

.gates__head {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #1c2a24;
}

.gates__count {
  margin-left: 6px;
  font-size: 11.5px;
  font-weight: 500;
  color: #8d99a8;
}

.gates__row {
  padding: 10px 0;
  border-top: 1px dashed #e4e9ee;
}

.gates__row:first-of-type {
  border-top: none;
  padding-top: 0;
}

.gates__time {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 12px;
  color: #8d99a8;
  font-variant-numeric: tabular-nums;
}

.gates__level {
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
}

.gates__pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.gates__card {
  padding: 9px 11px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #eaeff3;
}

.gates__label {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.4px;
  color: #9aa5b3;
}

.gates__value {
  margin: 4px 0 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #1c2a24;
}

.gates__extra {
  margin: 3px 0 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: #6b7a70;
}

.gates__foot {
  margin: 8px 0 0;
  font-size: 12px;
  color: #6b7a70;
}

/* ---------------- 抽屉详情 ---------------- */
.detail__audit {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f4f6f8;
}

.detail__audit--high {
  background: #fdece8;
}

.detail__audit--medium {
  background: #fff5e6;
}

.detail__audit--low {
  background: #e7f3ec;
}

.detail__audit-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #1c2a24;
}

.detail__audit-text {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: #5c6b63;
}

.detail__title {
  margin: 18px 0 8px;
  font-size: 17px;
  font-weight: 700;
  color: #1c2a24;
}

.detail__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12.5px;
  color: #5c6b63;
}

.detail__facts span {
  padding: 2px 9px;
  border-radius: 999px;
  background: #f4f6f8;
}

.detail__school {
  margin: 10px 0 0;
  font-size: 12.5px;
  color: #8d99a8;
}

.detail__images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 14px 0;
}

.detail__image {
  width: 100%;
  height: 96px;
  border-radius: 8px;
  background: #f4f6f8;
}

.detail__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: #3c4250;
  white-space: pre-wrap;
}

.detail__seller {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #eef0f3;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12.5px;
  color: #8d99a8;
}

.detail__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.reject__hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #8d99a8;
}
</style>
