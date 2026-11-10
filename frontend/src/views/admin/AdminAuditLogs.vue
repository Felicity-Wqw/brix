<template>
  <div class="logs">
    <!-- 筛选条：日志是只增不改的，所以这里只筛「哪一类结论」，不筛状态 -->
    <div class="logs__bar">
      <div class="logs__filters">
        <el-input
          v-model="filters.keyword"
          placeholder="商品标题 / 卖家 / 命中词"
          clearable
          :prefix-icon="Search"
          class="logs__search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select v-model="filters.riskLevel" placeholder="风险级别" clearable class="logs__select" @change="reload">
          <el-option label="低风险" value="LOW" />
          <el-option label="中风险" value="MEDIUM" />
          <el-option label="高风险" value="HIGH" />
        </el-select>
        <el-select v-model="filters.gate1Result" placeholder="第一道闸" clearable class="logs__select" @change="reload">
          <el-option label="未命中违禁词" value="PASS" />
          <el-option label="命中轻度风险词" value="MILD_HIT" />
          <el-option label="命中黑名单" value="BLACK_HIT" />
        </el-select>
        <el-select v-model="filters.gate2Result" placeholder="第二道闸" clearable class="logs__select" @change="reload">
          <el-option label="AI 通过" value="PASS" />
          <el-option label="AI 模糊不确定" value="UNCERTAIN" />
          <el-option label="AI 判不可上架" value="BAN" />
          <el-option label="AI 未参与" value="UNAVAILABLE" />
        </el-select>
        <el-select v-model="filters.handled" placeholder="人工处置" clearable class="logs__select" @change="reload">
          <el-option label="已人工处置" :value="true" />
          <el-option label="待人工确认" :value="false" />
        </el-select>
      </div>
      <p class="logs__hint">
        审核日志只增不改：商品被编辑、卖家改昵称都不会影响这里的历史记录
      </p>
    </div>

    <div class="logs__body" v-loading="loading">
      <el-table :data="rows" size="default" empty-text="暂无审核记录">
        <el-table-column label="商品（快照）" min-width="230">
          <template #default="{ row }">
            <div class="cell-item">
              <el-image :src="resolveImage(row.itemCover)" fit="cover" class="cell-item__cover">
                <template #error>
                  <div class="cell-item__placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cell-item__text">
                <p class="cell-item__title" :title="row.itemTitle">{{ row.itemTitle || '—' }}</p>
                <p class="cell-item__meta">
                  ￥{{ Number(row.itemPrice || 0).toLocaleString('zh-CN') }} ·
                  {{ row.categoryName || '未分类' }}
                </p>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="卖家（快照）" width="150">
          <template #default="{ row }">
            <p class="cell-seller">{{ row.sellerNickname || '—' }}</p>
            <p class="cell-seller-sub">
              {{ row.sellerUsername || '—' }} · 信用 {{ row.sellerCredit ?? '—' }}
            </p>
            <p class="cell-seller-sub">{{ row.sellerSchool || '—' }}</p>
          </template>
        </el-table-column>

        <el-table-column label="第一道闸 · 关键词" width="150">
          <template #default="{ row }">
            <span class="gate" :class="`gate--${(row.gate1Result || 'pass').toLowerCase()}`">
              {{ row.gate1ResultDesc }}
            </span>
            <p v-if="row.gate1Word" class="cell-gate-word" :title="row.gate1Category">
              「{{ row.gate1Word }}」· {{ row.gate1Category }}
            </p>
          </template>
        </el-table-column>

        <el-table-column label="第二道闸 · AI" width="160">
          <template #default="{ row }">
            <span class="gate" :class="`gate--${(row.gate2Result || 'pass').toLowerCase()}`">
              {{ row.gate2ResultDesc }}
            </span>
            <p v-if="row.gate2Reason" class="cell-gate-word" :title="row.gate2Reason">
              {{ row.gate2Reason }}
            </p>
          </template>
        </el-table-column>

        <el-table-column label="风险" width="86">
          <template #default="{ row }">
            <span class="risk" :class="`risk--${(row.riskLevel || 'none').toLowerCase()}`">
              {{ row.riskLevelDesc }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="机器结论" width="100">
          <template #default="{ row }">
            <p class="cell-seller">{{ row.decisionStatusDesc }}</p>
            <p v-if="row.decisionStatus === 'SUPERSEDED'" class="cell-seller-sub">已失效 · 非待办</p>
            <p v-else class="cell-seller-sub">{{ row.handled ? '已人工确认' : '待人工确认' }}</p>
          </template>
        </el-table-column>

        <el-table-column label="审核时间" width="106">
          <template #default="{ row }">
            <span class="cell-time">{{ formatTime(row.createdAt, 'MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="76" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openDetail(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="logs__pager">
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

    <!-- 明细抽屉：把一条日志的完整证据链摊开 -->
    <el-drawer v-model="drawer.visible" title="审核日志明细" size="520px">
      <div v-if="drawer.log" class="detail">
        <div class="detail__head" :class="`detail__head--${(drawer.log.riskLevel || 'none').toLowerCase()}`">
          <p class="detail__head-title">
            {{ drawer.log.riskLevelDesc }} · 机器判定落入「{{ drawer.log.decisionStatusDesc }}」
          </p>
          <p class="detail__head-time">
            {{ formatTime(drawer.log.createdAt, 'YYYY-MM-DD HH:mm:ss') }} · 日志 #{{ drawer.log.id }}
          </p>
        </div>

        <p class="detail__section">商品信息快照</p>
        <div class="detail__row"><span>标题</span><strong>{{ drawer.log.itemTitle || '—' }}</strong></div>
        <div class="detail__row"><span>价格</span><strong>￥{{ Number(drawer.log.itemPrice || 0).toLocaleString('zh-CN') }}</strong></div>
        <div class="detail__row"><span>分类</span><strong>{{ drawer.log.categoryName || '—' }}</strong></div>

        <p class="detail__section">卖家信息快照</p>
        <div class="detail__row"><span>昵称 / 登录名</span><strong>{{ drawer.log.sellerNickname || '—' }} / {{ drawer.log.sellerUsername || '—' }}</strong></div>
        <div class="detail__row"><span>学校 / 校区</span><strong>{{ drawer.log.sellerSchool || '—' }} · {{ drawer.log.sellerCampus || '—' }}</strong></div>
        <div class="detail__row"><span>信用分</span><strong>{{ drawer.log.sellerCredit ?? '—' }}</strong></div>

        <p class="detail__section">第一道闸 · 关键词</p>
        <div class="detail__row"><span>结果</span><strong>{{ drawer.log.gate1ResultDesc }}</strong></div>
        <div class="detail__row"><span>命中词</span><strong>{{ drawer.log.gate1Word || '无' }}</strong></div>
        <div class="detail__row"><span>词所属类目</span><strong>{{ drawer.log.gate1Category || '无' }}</strong></div>

        <p class="detail__section">第二道闸 · AI 审核</p>
        <div class="detail__row"><span>结果</span><strong>{{ drawer.log.gate2ResultDesc }}</strong></div>
        <div class="detail__row"><span>原始判定值</span><strong>{{ drawer.log.gate2Verdict || '—' }}</strong></div>
        <div class="detail__row detail__row--block">
          <span>AI 理由</span>
          <strong>{{ drawer.log.gate2Reason || '无' }}</strong>
        </div>

        <p class="detail__section">人工处置</p>
        <div class="detail__row"><span>处置人</span><strong>{{ drawer.log.handlerNickname || '尚未处置' }}</strong></div>
        <div class="detail__row"><span>处置时间</span><strong>{{ drawer.log.handledAt ? formatTime(drawer.log.handledAt, 'YYYY-MM-DD HH:mm:ss') : '—' }}</strong></div>
        <div class="detail__row detail__row--block"><span>处置说明</span><strong>{{ drawer.log.handleNote || '—' }}</strong></div>

        <p class="detail__section">给卖家看的判定说明</p>
        <p class="detail__note">{{ drawer.log.decisionNote || '无（低风险直接放行）' }}</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Picture, Search } from '@element-plus/icons-vue'
import { fetchAuditLogs } from '@/api/admin'
import { formatTime, resolveImage } from '@/utils/format'

const filters = reactive({
  keyword: '',
  riskLevel: '',
  gate1Result: '',
  gate2Result: '',
  handled: null
})
const loading = ref(false)
const rows = ref([])
const page = reactive({ currentPage: 1, pageSize: 10, total: 0 })
const drawer = reactive({ visible: false, log: null })

async function load() {
  loading.value = true
  try {
    // handled 是三态（不限 / true / false）：只有明确布尔值才传，避免空串被后端当成 false
    const handled = typeof filters.handled === 'boolean' ? filters.handled : undefined
    const data = await fetchAuditLogs({
      keyword: filters.keyword.trim(),
      riskLevel: filters.riskLevel,
      gate1Result: filters.gate1Result,
      gate2Result: filters.gate2Result,
      handled,
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

function openDetail(row) {
  drawer.log = row
  drawer.visible = true
}

onMounted(load)
</script>

<style scoped>
.logs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.logs__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.logs__filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.logs__search {
  width: 210px;
}

.logs__select {
  width: 132px;
}

.logs__hint {
  margin: 0;
  font-size: 12px;
  color: #9aa5b3;
}

.logs__body {
  padding: 14px 16px 8px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.logs__pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

/* ---------------- 单元格 ---------------- */
.cell-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cell-item__cover {
  width: 40px;
  height: 40px;
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
  font-size: 13px;
  color: #1c2a24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-item__meta {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: #8d99a8;
}

.cell-seller {
  margin: 0;
  font-size: 12.5px;
  color: #1c2a24;
}

.cell-seller-sub {
  margin: 3px 0 0;
  font-size: 11.5px;
  color: #8d99a8;
}

.cell-gate-word {
  margin: 4px 0 0;
  font-size: 11.5px;
  line-height: 1.55;
  color: #6b7a70;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cell-time {
  font-size: 12px;
  color: #8d99a8;
  font-variant-numeric: tabular-nums;
}

/* 两道闸的结果标签 */
.gate {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}

.gate--pass {
  background: #e7f3ec;
  color: #0e7a50;
}

.gate--mild_hit,
.gate--uncertain {
  background: #fff5e6;
  color: #b8730b;
}

.gate--black_hit,
.gate--ban {
  background: #fdece8;
  color: #c9411f;
}

.gate--unavailable {
  background: #f0f3f5;
  color: #8d99a8;
}

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

/* ---------------- 明细抽屉 ---------------- */
.detail__head {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f4f6f8;
}

.detail__head--low {
  background: #e7f3ec;
}

.detail__head--medium {
  background: #fff5e6;
}

.detail__head--high {
  background: #fdece8;
}

.detail__head-title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
  color: #1c2a24;
}

.detail__head-time {
  margin: 5px 0 0;
  font-size: 12px;
  color: #6b7a70;
}

.detail__section {
  margin: 18px 0 6px;
  padding-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #0e7a50;
  border-bottom: 1px dashed #e4e9ee;
}

.detail__row {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 12.5px;
}

.detail__row > span {
  flex: none;
  width: 96px;
  color: #8d99a8;
}

.detail__row > strong {
  flex: 1;
  min-width: 0;
  font-weight: 500;
  color: #3c4250;
  word-break: break-all;
}

.detail__row--block {
  flex-direction: column;
  gap: 4px;
}

.detail__row--block > strong {
  line-height: 1.75;
}

.detail__note {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f7f9fa;
  font-size: 12.5px;
  line-height: 1.75;
  color: #5c6b63;
}
</style>
