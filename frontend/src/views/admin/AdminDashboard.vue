<template>
  <div class="dash" v-loading="loading">
    <!-- 顶部工具条 -->
    <div class="dash__bar">
      <div class="dash__tabs">
        <button
          v-for="opt in PERIODS"
          :key="opt.value"
          type="button"
          class="dash__tab"
          :class="{ 'dash__tab--active': days === opt.value }"
          @click="switchPeriod(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="dash__bar-right">
        <span class="dash__updated">数据更新于 {{ updatedAt }}</span>
        <el-button link :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 概览四卡 -->
    <div class="dash__cards">
      <article class="stat">
        <p class="stat__label">用户总数</p>
        <p class="stat__value">{{ num(overview.userCount) }}</p>
        <p class="stat__foot">
          <span class="stat__delta">近 7 天 +{{ num(overview.userNew7d) }}</span>
        </p>
      </article>

      <article class="stat">
        <p class="stat__label">在售商品</p>
        <p class="stat__value">{{ num(overview.itemOnSale) }}</p>
        <p class="stat__foot">
          <span class="stat__delta stat__delta--warn">待审核 {{ num(overview.itemPending) }} 件</span>
        </p>
      </article>

      <article class="stat">
        <p class="stat__label">成交订单（累计）</p>
        <p class="stat__value">{{ num(overview.orderDone) }}</p>
        <p class="stat__foot">
          <span class="stat__delta">今日 +{{ num(overview.orderDoneToday) }}</span>
        </p>
      </article>

      <article class="stat stat--gmv">
        <p class="stat__label">GMV（已完成）</p>
        <p class="stat__value">{{ money(overview.gmv) }}</p>
        <p class="stat__foot">
          <span class="stat__delta">本期 {{ money(overview.periodGmv) }}</span>
          <span v-if="overview.growthRate !== null" class="stat__growth" :class="growthClass">
            {{ overview.growthRate > 0 ? '↑' : overview.growthRate < 0 ? '↓' : '—' }}
            {{ Math.abs(overview.growthRate) }}%
          </span>
          <span v-else class="stat__growth stat__growth--flat">环比不可比</span>
        </p>
      </article>
    </div>

    <!-- AI 运营分析报告 -->
    <section class="panel">
      <header class="panel__head">
        <h3 class="panel__title">AI 运营分析报告</h3>
        <div class="dash__tabs">
          <button v-for="opt in REPORT_PERIODS" :key="opt.value" type="button"
                  class="dash__tab" :class="{ 'dash__tab--active': reportDays === opt.value }"
                  @click="generateReport(opt.value)">
            {{ opt.label }}
          </button>
        </div>
      </header>
      <p v-if="reportLoading" class="report__loading">AI 正在生成报告…</p>
      <p v-else-if="report" class="report__body">{{ report }}</p>
      <p v-else class="report__loading">点击上方「周报 / 月报 / 季度汇报」生成对应周期的 AI 分析报告</p>
    </section>

    <!-- 成交趋势 -->
    <section class="panel">
      <header class="panel__head">
        <h3 class="panel__title">成交趋势</h3>
        <p class="panel__sub">柱=成交金额（左轴）· 线=成交单数（右轴）· 缺失日期按 0 补齐</p>
      </header>
      <AdminTrendChart :data="trend" :height="300" />
    </section>

    <!-- 分类分布 + 风险分布 -->
    <div class="dash__row">
      <section class="panel panel--half">
        <header class="panel__head">
          <h3 class="panel__title">在售商品分类分布</h3>
          <p class="panel__sub">当前挂牌供给侧结构</p>
        </header>
        <PieCategory :data="categoryPie" :height="270" />
      </section>

      <section class="panel panel--half">
        <header class="panel__head">
          <h3 class="panel__title">审核风险分布</h3>
          <p class="panel__sub">
            三级分流落地情况 · 覆盖率 {{ overview.auditCoverage }}% · 直发率 {{ overview.autoPassRate }}%
          </p>
        </header>
        <PieCategory :data="riskPie" :colors="riskColors" :height="270" />
      </section>
    </div>

    <!-- 各学校交易量 + 转化漏斗 -->
    <div class="dash__row">
      <section class="panel panel--wide">
        <header class="panel__head">
          <h3 class="panel__title">各学校交易量</h3>
          <p class="panel__sub">按成交金额降序 · 取前 8 名（数据取订单上的学校快照）</p>
        </header>
        <AdminSchoolChart :data="schools" :height="330" />
        <div class="school-table">
          <div v-for="row in schools" :key="row.schoolId ?? 'none'" class="school-table__row">
            <span class="school-table__name">{{ row.schoolName }}</span>
            <span class="school-table__cell">{{ num(row.orderCount) }} 单</span>
            <span class="school-table__cell">{{ money(row.amount) }}</span>
            <span class="school-table__cell school-table__cell--muted">在售 {{ num(row.onSaleCount) }}</span>
          </div>
        </div>
      </section>

      <section class="panel panel--half">
        <header class="panel__head">
          <h3 class="panel__title">转化漏斗</h3>
          <p class="panel__sub">四步同口径（按商品计数），转化率相对第一步</p>
        </header>
        <div class="funnel">
          <div v-for="(step, index) in funnel" :key="step.name" class="funnel__item">
            <div class="funnel__meta">
              <span class="funnel__name">{{ step.name }}</span>
              <span class="funnel__value">{{ num(step.value) }}</span>
            </div>
            <div class="funnel__track">
              <div
                class="funnel__fill"
                :style="{ width: `${Math.max(step.rate, step.value > 0 ? 4 : 0)}%`, opacity: 1 - index * 0.16 }"
              ></div>
            </div>
            <span class="funnel__rate">{{ step.rate }}%</span>
          </div>
          <p class="funnel__note">
            漏斗看的是「供给侧流转效率」：发布后有多少被看到、被看到后有多少成单。
            若「产生浏览」这一步流失严重，说明首页曝光分配或标题图片质量有问题。
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { fetchDashboard, fetchAiReport } from '@/api/admin'
import AdminTrendChart from '@/components/charts/AdminTrendChart.vue'
import AdminSchoolChart from '@/components/charts/AdminSchoolChart.vue'
import PieCategory from '@/components/charts/PieCategory.vue'

const PERIODS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 }
]

const REPORT_PERIODS = [
  { label: '周报', value: 7 },
  { label: '月报', value: 30 },
  { label: '季度汇报', value: 90 }
]

const reportDays = ref(7)
const report = ref('')
const reportLoading = ref(false)

async function generateReport(value) {
  reportDays.value = value
  reportLoading.value = true
  report.value = ''
  try {
    report.value = await fetchAiReport(value)
  } catch (error) {
    /* 拦截器已提示 */
  } finally {
    reportLoading.value = false
  }
}

const loading = ref(false)
const days = ref(7)
const updatedAt = ref('—')
const overview = ref({})
const trend = ref([])
const categories = ref([])
const schools = ref([])
const funnel = ref([])
const riskDistribution = ref([])

/** 分类饼图数据：Slice → {name, value} */
const categoryPie = computed(() =>
  categories.value.map((item) => ({ name: item.name, value: item.count }))
)

/** 风险饼图数据：按「低 → 中 → 高」固定顺序，颜色与审核页签的风险标签一致 */
const RISK_ORDER = ['低风险（直发）', '中风险（待审）', '高风险（驳回）', '未分级']
const RISK_COLORS = {
  '低风险（直发）': '#0FA968',
  '中风险（待审）': '#F5A623',
  '高风险（驳回）': '#FF4D2E',
  未分级: '#B8C2CE'
}

const riskPie = computed(() =>
  riskDistribution.value
    .filter((item) => Number(item.count) > 0)
    .map((item) => ({ name: item.name, value: Number(item.count) }))
    .sort((a, b) => RISK_ORDER.indexOf(a.name) - RISK_ORDER.indexOf(b.name))
)

const riskColors = computed(() => riskPie.value.map((item) => RISK_COLORS[item.name] || '#B8C2CE'))

/** 环比涨跌配色：涨红跌绿（A 股习惯），与前台价格色一致 */
const growthClass = computed(() => {
  const rate = Number(overview.value.growthRate || 0)
  return rate >= 0 ? 'stat__growth--up' : 'stat__growth--down'
})

function num(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function money(value) {
  const n = Number(value || 0)
  if (n >= 10000) return `￥${(n / 10000).toFixed(2)} 万`
  return `￥${n.toLocaleString('zh-CN')}`
}

async function load() {
  loading.value = true
  try {
    const data = await fetchDashboard(days.value)
    overview.value = data?.overview || {}
    trend.value = data?.trend || []
    categories.value = data?.categories || []
    schools.value = data?.schools || []
    funnel.value = data?.funnel || []
    riskDistribution.value = data?.riskDistribution || []
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch (error) {
    ElMessage.error('看板数据加载失败')
  } finally {
    loading.value = false
  }
}

function switchPeriod(value) {
  if (days.value === value) return
  days.value = value
  load()
}

onMounted(load)
</script>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------------- 顶部工具条 ---------------- */
.dash__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dash__tabs {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  background: #eef1f4;
}

.dash__tab {
  border: none;
  background: none;
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 13px;
  color: #5c6b63;
  cursor: pointer;
  transition: all 0.16s ease;
}

.dash__tab--active {
  background: #fff;
  color: #1c2a24;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(23, 37, 31, 0.1);
}

.dash__bar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12.5px;
  color: #8d99a8;
}

/* ---------------- 概览卡 ---------------- */
.dash__cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat {
  padding: 18px 20px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.stat--gmv {
  background: linear-gradient(140deg, #0f7a50 0%, #0b5c3c 100%);
  border-color: transparent;
}

.stat__label {
  margin: 0;
  font-size: 12.5px;
  color: #8d99a8;
}

.stat--gmv .stat__label {
  color: rgba(255, 255, 255, 0.72);
}

.stat__value {
  margin: 8px 0 0;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #1c2a24;
}

.stat--gmv .stat__value {
  color: #fff;
}

.stat__foot {
  margin: 8px 0 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #9aa5b3;
}

.stat--gmv .stat__foot {
  color: rgba(255, 255, 255, 0.78);
}

.stat__delta--warn {
  color: #c9411f;
}

.stat__growth {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
}

.stat__growth--up {
  background: rgba(255, 255, 255, 0.18);
  color: #ffd7cc;
}

.stat__growth--down {
  background: rgba(255, 255, 255, 0.18);
  color: #c8f0dc;
}

.stat__growth--flat {
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.75);
  font-weight: 400;
}

/* ---------------- 面板 ---------------- */
.panel {
  padding: 18px 20px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e8ecf1;
}

.panel__head {
  margin-bottom: 6px;
}

.panel__title {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: #1c2a24;
}

.panel__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8d99a8;
}

.dash__row {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

.panel--wide,
.panel--half {
  min-width: 0;
}

/* ---------------- 学校明细表 ---------------- */
.school-table {
  margin-top: 10px;
  border-top: 1px dashed #eef0f3;
}

.school-table__row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) 80px 110px 90px;
  align-items: center;
  padding: 7px 0;
  font-size: 12.5px;
  border-bottom: 1px solid #f4f6f8;
}

.school-table__name {
  color: #3c4250;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.school-table__cell {
  text-align: right;
  color: #1c2a24;
  font-variant-numeric: tabular-nums;
}

.school-table__cell--muted {
  color: #8d99a8;
}

/* ---------------- 漏斗 ---------------- */
.funnel {
  padding-top: 10px;
}

.funnel__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 62px;
  grid-template-areas:
    'meta rate'
    'track rate';
  align-items: center;
  column-gap: 10px;
  margin-bottom: 14px;
}

.funnel__meta {
  grid-area: meta;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 5px;
}

.funnel__name {
  font-size: 13px;
  color: #3c4250;
}

.funnel__value {
  font-size: 13.5px;
  font-weight: 700;
  color: #1c2a24;
  font-variant-numeric: tabular-nums;
}

.funnel__track {
  grid-area: track;
  height: 10px;
  border-radius: 999px;
  background: #f0f3f5;
  overflow: hidden;
}

.funnel__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #6fd2a6, #0e7a50);
}

.funnel__rate {
  grid-area: rate;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  color: #0e7a50;
}

.funnel__note {
  margin: 6px 0 0;
  padding-top: 12px;
  border-top: 1px dashed #eef0f3;
  font-size: 11.5px;
  line-height: 1.8;
  color: #9aa5b3;
}

.report__loading {
  margin: 0;
  font-size: 13px;
  color: #9aa5b3;
}

.report__body {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: #2c2c2a;
}
</style>
