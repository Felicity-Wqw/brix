<template>
  <div class="screen-viewport">
    <div class="screen-stage" :style="stageStyle">
      <!-- 顶部标题栏 -->
      <header class="scr-head">
        <div class="scr-head__todo">
          待审 <b :class="{ 'is-warn': pending.pendingItems > 0 }">{{ pending.pendingItems ?? 0 }}</b>
          · 待举报 <b :class="{ 'is-warn': pending.pendingReports > 0 }">{{ pending.pendingReports ?? 0 }}</b>
          · 待裁决 <b :class="{ 'is-warn': pending.pendingJury > 0 }">{{ pending.pendingJury ?? 0 }}</b>
        </div>
        <div class="scr-head__title">
          <span class="scr-head__line"></span>
          <h1>物归校园 · 数据可视化大屏</h1>
          <span class="scr-head__line scr-head__line--r"></span>
        </div>
        <div class="scr-head__right">
          <span class="scr-head__clock">{{ clock }}</span>
          <button class="scr-back" @click="goBack">返回后台</button>
        </div>
      </header>

      <!-- KPI 卡 -->
      <div class="scr-kpis">
        <div class="scr-kpi" v-for="k in kpis" :key="k.label">
          <p class="scr-kpi__label">{{ k.label }}</p>
          <p class="scr-kpi__num">{{ k.value }}</p>
        </div>
      </div>

      <!-- 三栏主体 -->
      <div class="scr-body">
        <!-- 左列 -->
        <section class="scr-col">
          <div class="panel">
            <p class="panel__title">成交量统计分析</p>
            <div ref="trendRef" class="panel__chart"></div>
          </div>
          <div class="panel">
            <p class="panel__title">品类类型统计</p>
            <div ref="categoryRef" class="panel__chart"></div>
          </div>
          <div class="panel">
            <p class="panel__title">审核转化漏斗</p>
            <div ref="funnelRef" class="panel__chart"></div>
          </div>
        </section>

        <!-- 中央地图 -->
        <section class="scr-center">
          <p class="scr-center__hint">学校成交分布 · 桂林 —— 气泡大小 = 成交 GMV，hover 查看明细</p>
          <div ref="mapRef" class="scr-map"></div>
        </section>

        <!-- 右列 -->
        <section class="scr-col">
          <div class="panel">
            <p class="panel__title">实时动态</p>
            <ul class="rt-list">
              <li v-for="(ev, i) in realtime" :key="i" :class="`rt--${(ev.type || '').toLowerCase()}`">
                <span class="rt__time">{{ formatTime(ev.time) }}</span>
                <span class="rt__text">{{ ev.content }}</span>
              </li>
              <li v-if="!realtime.length" class="rt__empty">暂无动态</li>
            </ul>
          </div>
          <div class="panel">
            <p class="panel__title">AI 服务监控</p>
            <div class="ai-hex">
              <div class="ai-hex__item">
                <b>{{ llmRate }}%</b>
                <span>LLM 直答率</span>
              </div>
              <div class="ai-hex__item">
                <b>{{ imgCost }}</b>
                <span>图片均耗时</span>
              </div>
              <div class="ai-hex__item ai-hex__item--warn">
                <b>{{ aiStats.imageReject ?? 0 }}</b>
                <span>违规拦截</span>
              </div>
            </div>
            <ul class="ai-lines">
              <li>估价 {{ aiStats.estimateCount ?? 0 }} 次 · 文案 {{ aiStats.copyCount ?? 0 }} 次 · 规则降级 {{ aiStats.ruleCount ?? 0 }}</li>
              <li>图片存疑转人工 {{ aiStats.imageUncertain ?? 0 }} 张</li>
            </ul>
          </div>
          <div class="panel">
            <p class="panel__title">学校成交排行</p>
            <div ref="rankRef" class="panel__chart"></div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { fetchScreen } from '@/api/admin'
import guilinGeo from '@/assets/guilin-geo.json'

const router = useRouter()

/* ---------------- 数据 ---------------- */
const overview = reactive({})
const aiStats = reactive({})
const pending = reactive({})
const trend = ref([])
const categories = ref([])
const schools = ref([])
const points = ref([])
const funnel = ref([])
const realtime = ref([])

const DESIGN_W = 1920
const DESIGN_H = 1080

const clock = ref('')
let clockTimer = null
let pollTimer = null
let stopped = false

const kpis = computed(() => [
  { label: '在售商品', value: overview.itemOnSale ?? 0 },
  { label: '本月成交', value: (overview.orderDone ?? 0) + ' 单' },
  { label: '本月 GMV', value: '¥' + Number(overview.periodGmv ?? 0).toLocaleString('zh-CN') },
  { label: '平台用户', value: overview.userCount ?? 0 },
  { label: '自动过审率', value: Math.round(overview.autoPassRate ?? 0) + '%' },
  { label: 'AI 今日调用', value: (aiStats.aiCallsToday ?? 0) + ' 次' }
])

const llmRate = computed(() => {
  const total = (aiStats.llmCount ?? 0) + (aiStats.ruleCount ?? 0)
  return total ? Math.round(((aiStats.llmCount ?? 0) / total) * 100) : 100
})
const imgCost = computed(() =>
  aiStats.imageAvgCostMs == null ? '—' : (aiStats.imageAvgCostMs / 1000).toFixed(1) + 's'
)

function formatTime(value) {
  if (!value) return ''
  return String(value).slice(5, 16)
}

/* ---------------- 缩放适配：宽高各自贴合窗口，横向竖向都撑满 ---------------- */
const stageStyle = ref({})
function fit() {
  const scaleX = window.innerWidth / DESIGN_W
  const scaleY = window.innerHeight / DESIGN_H
  stageStyle.value = {
    width: DESIGN_W + 'px',
    height: DESIGN_H + 'px',
    transform: `translate(-50%, -50%) scale(${scaleX}, ${scaleY})`
  }
}

/* ---------------- 图表 ---------------- */
const trendRef = ref(null)
const categoryRef = ref(null)
const funnelRef = ref(null)
const mapRef = ref(null)
const rankRef = ref(null)
let charts = {}

function initChart(refEl, key) {
  if (!charts[key] && refEl) {
    charts[key] = echarts.init(refEl)
  }
  return charts[key]
}

const TECH = {
  text: '#8fd6ff',
  sub: '#4f7ca8',
  bar: '#2e8fd8',
  barLight: '#37c6ff',
  amber: '#ffc95c',
  split: 'rgba(46, 143, 216, 0.25)'
}

function renderTrend() {
  const chart = initChart(trendRef.value, 'trend')
  const rows = trend.value || []
  chart.setOption({
    grid: { left: 44, right: 16, top: 26, bottom: 24 },
    tooltip: { trigger: 'axis', textStyle: { fontSize: 14 } },
    legend: { data: ['成交单数', 'GMV'], textStyle: { color: TECH.text, fontSize: 14 }, top: 0, right: 0, itemWidth: 14 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.date?.slice(5) || ''),
      axisLine: { lineStyle: { color: TECH.split } },
      axisLabel: { color: TECH.sub, fontSize: 13, interval: 4 }
    },
    yAxis: [
      { type: 'value', name: '单', nameTextStyle: { color: TECH.sub }, axisLabel: { color: TECH.sub, fontSize: 13 }, splitLine: { lineStyle: { color: TECH.split } } },
      { type: 'value', name: '元', nameTextStyle: { color: TECH.sub }, axisLabel: { color: TECH.sub, fontSize: 13, formatter: (v) => (v >= 1000 ? v / 1000 + 'k' : v) }, splitLine: { show: false } }
    ],
    series: [
      { name: '成交单数', type: 'bar', barWidth: 8, data: rows.map((r) => r.orderCount), itemStyle: { color: TECH.barLight } },
      { name: 'GMV', type: 'line', yAxisIndex: 1, smooth: true, data: rows.map((r) => Number(r.amount || 0)), lineStyle: { color: TECH.amber }, itemStyle: { color: TECH.amber } }
    ]
  })
}

function renderCategory() {
  const chart = initChart(categoryRef.value, 'category')
  const rows = [...(categories.value || [])].slice(0, 6).reverse()
  chart.setOption({
    grid: { left: 8, right: 60, top: 10, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = rows[params[0].dataIndex]
        return `${row.name}<br/>成交：￥${Number(row.amount || 0).toLocaleString('zh-CN')}（${row.count || 0} 单）`
      }
    },
    xAxis: { type: 'value', axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLine: { lineStyle: { color: TECH.split } },
      axisLabel: { color: TECH.text, fontSize: 14 }
    },
    series: [{
      type: 'bar',
      barWidth: 10,
      data: rows.map((r) => Number(r.amount || 0)),
      itemStyle: { color: TECH.bar },
      label: { show: true, position: 'right', color: TECH.sub, fontSize: 13, formatter: (p) => '¥' + Number(p.value || 0).toLocaleString('zh-CN') }
    }]
  })
}

function renderFunnel() {
  const chart = initChart(funnelRef.value, 'funnel')
  const rows = funnel.value || []
  chart.setOption({
    grid: { left: 10, right: 70, top: 14, bottom: 10, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = rows[params[0].dataIndex]
        return `${row.name}：${row.value}（转化率 ${Math.round(row.rate || 0)}%）`
      }
    },
    xAxis: { type: 'value', axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLine: { lineStyle: { color: TECH.split } },
      axisLabel: { color: TECH.text, fontSize: 14 }
    },
    series: [{
      type: 'bar',
      barWidth: 12,
      data: rows.map((r) => r.value),
      itemStyle: { color: TECH.bar },
      label: { show: true, position: 'right', color: TECH.sub, fontSize: 13, formatter: (p) => `${p.value} · ${Math.round(rows[p.dataIndex]?.rate || 0)}%` }
    }]
  })
}

function renderMap() {
  echarts.registerMap('guilin', guilinGeo)
  const chart = initChart(mapRef.value, 'map')
  const pts = (points.value || []).filter((p) => p.lat != null && p.lng != null)
  const maxAmount = Math.max(1, ...pts.map((p) => Number(p.amount || 0)))
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const d = params.data?.detail
        if (!d) return params.name
        return `${d.schoolName}<br/>校区：${d.campusName}<br/>在售：${d.onSaleCount} 件<br/>成交：${d.orderCount} 单 · ￥${Number(d.amount || 0).toLocaleString('zh-CN')}`
      }
    },
    geo: {
      map: 'guilin',
      roam: true,
      scaleLimit: { min: 0.8, max: 8 },
      layoutCenter: ['50%', '50%'],
      layoutSize: '92%',
      aspectScale: 1,
      itemStyle: {
        areaColor: '#0b2e52',
        borderColor: '#3ba0ff',
        borderWidth: 1,
        shadowColor: 'rgba(59, 160, 255, 0.55)',
        shadowBlur: 18
      },
      emphasis: {
        itemStyle: { areaColor: '#123d6b' },
        label: { show: false }
      },
      label: { show: false }
    },
    series: [{
      type: 'effectScatter',
      coordinateSystem: 'geo',
      data: pts.map((p) => ({
        name: p.schoolName,
        value: [p.lng, p.lat, Number(p.amount || 0)],
        detail: p
      })),
      symbolSize: (value) => 10 + 26 * Math.sqrt((value?.[2] || 0) / maxAmount),
      rippleEffect: { brushType: 'stroke', scale: 2.6 },
      itemStyle: { color: '#37c6ff', shadowColor: '#37c6ff', shadowBlur: 10 },
      label: {
        show: true,
        formatter: '{b}',
        position: 'right',
        color: '#bfe8ff',
        fontSize: 14
      }
    }]
  })
}

function renderRank() {
  const chart = initChart(rankRef.value, 'rank')
  const rows = [...(schools.value || [])].slice(0, 8).reverse()
  chart.setOption({
    grid: { left: 8, right: 64, top: 10, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = rows[params[0].dataIndex]
        return `${row.schoolName}<br/>GMV：￥${Number(row.amount || 0).toLocaleString('zh-CN')} · ${row.orderCount || 0} 单`
      }
    },
    xAxis: { type: 'value', axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.schoolName?.replace('桂林', '') || ''),
      axisLine: { lineStyle: { color: TECH.split } },
      axisLabel: { color: TECH.text, fontSize: 14 }
    },
    series: [{
      type: 'bar',
      barWidth: 9,
      data: rows.map((r) => Number(r.amount || 0)),
      itemStyle: { color: TECH.bar },
      label: { show: true, position: 'right', color: TECH.sub, fontSize: 13, formatter: (p) => '¥' + Number(p.value || 0).toLocaleString('zh-CN') }
    }]
  })
}

function renderAll() {
  nextTick(() => {
    renderTrend()
    renderCategory()
    renderFunnel()
    renderMap()
    renderRank()
  })
}

/* ---------------- 数据加载 ---------------- */
async function load() {
  if (stopped) return
  try {
    // request.js 拦截器已剥壳：直接返回 ScreenVO
    const data = await fetchScreen()
    if (!data) return
    Object.assign(overview, data.overview || {})
    Object.assign(aiStats, data.aiStats || {})
    Object.assign(pending, data.pending || {})
    trend.value = data.trend || []
    categories.value = data.categories || []
    schools.value = data.schools || []
    points.value = data.points || []
    funnel.value = data.funnel || []
    realtime.value = data.realtime || []
    renderAll()
  } catch (error) {
    // 轮询失败静默，下一轮再试
  }
}

function tick() {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

function goBack() {
  router.push('/admin/dashboard')
}

onMounted(() => {
  fit()
  window.addEventListener('resize', fit)
  tick()
  clockTimer = setInterval(tick, 1000)
  load()
  pollTimer = setInterval(load, 30000)
})

onBeforeUnmount(() => {
  stopped = true
  window.removeEventListener('resize', fit)
  clearInterval(clockTimer)
  clearInterval(pollTimer)
  Object.values(charts).forEach((chart) => chart?.dispose())
  charts = {}
})
</script>

<style scoped>
.screen-viewport {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: #04122e;
}

.screen-stage {
  position: absolute;
  left: 50%;
  top: 50%;
  transform-origin: center;
  display: flex;
  flex-direction: column;
  padding: 18px 24px;
  background:
    radial-gradient(1200px 600px at 50% 55%, rgba(23, 76, 128, 0.5) 0%, transparent 65%),
    linear-gradient(180deg, #041a38 0%, #04122e 60%, #030c20 100%);
}

/* ---------- 顶部 ---------- */
.scr-head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 56px;
}

.scr-head__todo {
  font-size: 17px;
  color: #7fb3d8;
}

.scr-head__todo b {
  color: #37c6ff;
  font-weight: 500;
}

.scr-head__todo b.is-warn {
  color: #ffc95c;
}

.scr-head__title {
  display: flex;
  align-items: center;
  gap: 18px;
}

.scr-head__title h1 {
  margin: 0;
  font-size: 36px;
  font-weight: 500;
  letter-spacing: 6px;
  color: #e6f6ff;
  text-shadow: 0 0 18px rgba(55, 198, 255, 0.65);
}

.scr-head__line {
  width: 180px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #2e8fd8);
}

.scr-head__line--r {
  background: linear-gradient(270deg, transparent, #2e8fd8);
}

.scr-head__right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
}

.scr-head__clock {
  font-size: 18px;
  color: #9fdcff;
  letter-spacing: 1px;
}

.scr-back {
  padding: 5px 14px;
  font-size: 15px;
  color: #9fdcff;
  background: rgba(46, 143, 216, 0.12);
  border: 0.5px solid rgba(46, 143, 216, 0.55);
  border-radius: 4px;
  cursor: pointer;
}

.scr-back:hover {
  color: #fff;
  border-color: #37c6ff;
}

/* ---------- KPI ---------- */
.scr-kpis {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin: 10px 0 14px;
}

.scr-kpi {
  padding: 10px 16px;
  background: rgba(11, 46, 82, 0.55);
  border: 0.5px solid rgba(46, 143, 216, 0.55);
  border-radius: 4px;
  text-align: center;
}

.scr-kpi__label {
  margin: 0 0 4px;
  font-size: 16px;
  color: #4fd8c8;
}

.scr-kpi__num {
  margin: 0;
  font-size: 28px;
  font-weight: 500;
  color: #eaf6ff;
}

/* ---------- 主体三栏 ---------- */
.scr-body {
  flex: 1;
  display: grid;
  grid-template-columns: 400px 1fr 400px;
  gap: 14px;
  min-height: 0;
}

.scr-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px;
  background: rgba(11, 46, 82, 0.55);
  border: 0.5px solid rgba(46, 143, 216, 0.45);
  clip-path: polygon(14px 0, 100% 0, 100% calc(100% - 14px), calc(100% - 14px) 100%, 0 100%, 0 14px);
}

.panel__title {
  margin: 0 0 6px;
  padding-left: 10px;
  font-size: 19px;
  color: #bfe8ff;
  border-left: 3px solid #37c6ff;
  line-height: 1.3;
}

.panel__chart {
  flex: 1;
  min-height: 0;
}

/* ---------- 中央地图 ---------- */
.scr-center {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  background: rgba(11, 46, 82, 0.35);
  border: 0.5px solid rgba(46, 143, 216, 0.45);
  clip-path: polygon(14px 0, 100% 0, 100% calc(100% - 14px), calc(100% - 14px) 100%, 0 100%, 0 14px);
}

.scr-center__hint {
  margin: 0 0 4px;
  font-size: 16px;
  color: #7fb3d8;
  text-align: center;
}

.scr-map {
  flex: 1;
  min-height: 0;
}

/* ---------- 实时动态 ---------- */
.rt-list {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow: hidden;
}

.rt-list li {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  font-size: 16px;
  border-bottom: 0.5px solid rgba(46, 143, 216, 0.18);
}

.rt__time {
  flex: none;
  color: #4f7ca8;
}

.rt__text {
  color: #bfe8ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rt--intercept .rt__text {
  color: #ffc95c;
}

.rt__empty {
  color: #4f7ca8;
  justify-content: center;
}

/* ---------- AI 监控 ---------- */
.ai-hex {
  display: flex;
  gap: 12px;
  margin: 8px 0;
}

.ai-hex__item {
  flex: 1;
  padding: 10px 6px;
  text-align: center;
  background: rgba(46, 143, 216, 0.14);
  clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%);
}

.ai-hex__item b {
  display: block;
  font-size: 26px;
  font-weight: 500;
  color: #4fe0ff;
}

.ai-hex__item--warn b {
  color: #ffc95c;
}

.ai-hex__item span {
  font-size: 14px;
  color: #7fb3d8;
}

.ai-lines {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 15px;
  color: #7fb3d8;
}

.ai-lines li {
  padding: 4px 0;
  border-bottom: 0.5px solid rgba(46, 143, 216, 0.18);
}
</style>
