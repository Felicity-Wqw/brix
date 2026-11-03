<template>
  <div class="analysis" v-loading="loading">
    <div class="brix-section-title">
      <span>数据概览</span>
    </div>

    <!-- 无任何交易数据时的空态 -->
    <el-empty
      v-if="!loading && isEmpty"
      description="还没有交易数据，去首页逛逛或发布第一件闲置吧"
      :image-size="120"
    >
      <el-button type="primary" @click="router.push('/')">去首页看看</el-button>
    </el-empty>

    <template v-else>
      <!-- ============ 顶部：买入 / 卖出 汇总卡 ============ -->
      <div class="analysis__hero">
        <!-- 买入卡 -->
        <div class="hero-card hero-card--buy">
          <div class="hero-card__head">
            <span class="hero-card__icon"><el-icon :size="22"><ShoppingCart /></el-icon></span>
            <div>
              <div class="hero-card__title">累计买入</div>
              <div class="hero-card__amount">{{ money(buy.summary?.totalAmount) }}</div>
            </div>
          </div>
          <div class="hero-card__chips">
            <div class="chip"><span class="chip__num">{{ buy.summary?.orderCount ?? 0 }}</span><span class="chip__label">成交笔数</span></div>
            <div class="chip"><span class="chip__num">{{ money(buy.summary?.avgAmount) }}</span><span class="chip__label">均价</span></div>
            <div class="chip"><span class="chip__num">{{ buy.summary?.categoryCount ?? 0 }}</span><span class="chip__label">涉及分类</span></div>
            <div class="chip"><span class="chip__num">{{ money(buy.summary?.maxAmount) }}</span><span class="chip__label">最大单笔</span></div>
          </div>
        </div>

        <!-- 卖出卡 -->
        <div class="hero-card hero-card--sell">
          <div class="hero-card__head">
            <span class="hero-card__icon"><el-icon :size="22"><Sell /></el-icon></span>
            <div>
              <div class="hero-card__title">累计卖出</div>
              <div class="hero-card__amount">{{ money(sell.summary?.totalAmount) }}</div>
            </div>
          </div>
          <div class="hero-card__chips">
            <div class="chip"><span class="chip__num">{{ sell.summary?.orderCount ?? 0 }}</span><span class="chip__label">成交笔数</span></div>
            <div class="chip"><span class="chip__num">{{ money(sell.summary?.avgAmount) }}</span><span class="chip__label">均价</span></div>
            <div class="chip">
              <span class="chip__num">{{ sell.summary?.avgDaysToSell != null ? sell.summary.avgDaysToSell + ' 天' : '-' }}</span>
              <span class="chip__label">平均成交天数</span>
            </div>
            <div class="chip"><span class="chip__num">{{ money(sell.summary?.maxAmount) }}</span><span class="chip__label">最大单笔</span></div>
          </div>
        </div>
      </div>

      <!-- ============ 中部：月度趋势 ============ -->
      <div class="analysis__card brix-card">
        <div class="analysis__card-head">
          <h3 class="analysis__card-title">月度交易趋势</h3>
          <el-radio-group v-model="trendMetric" size="small">
            <el-radio-button value="amount">按金额</el-radio-button>
            <el-radio-button value="count">按笔数</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="trendRef" class="analysis__chart analysis__chart--trend"></div>
      </div>

      <!-- ============ 下部：分类占比（买 / 卖） ============ -->
      <div class="analysis__pies">
        <div class="analysis__card brix-card">
          <div class="analysis__card-head">
            <h3 class="analysis__card-title">买入分类占比</h3>
            <span class="analysis__card-sub">按成交金额</span>
          </div>
          <div ref="buyPieRef" class="analysis__chart analysis__chart--pie"></div>
        </div>
        <div class="analysis__card brix-card">
          <div class="analysis__card-head">
            <h3 class="analysis__card-title">卖出分类占比</h3>
            <span class="analysis__card-sub">按成交金额</span>
          </div>
          <div ref="sellPieRef" class="analysis__chart analysis__chart--pie"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getMyAnalysis } from '@/api/user'

const router = useRouter()

const loading = ref(true)
/** 原始接口数据 */
const data = ref({ buy: {}, sell: {} })
/** 趋势图指标切换：amount / count */
const trendMetric = ref('amount')

const buy = computed(() => data.value.buy || {})
const sell = computed(() => data.value.sell || {})
const isEmpty = computed(
  () => !(buy.value.summary?.orderCount > 0) && !(sell.value.summary?.orderCount > 0)
)

/** 金额格式化：¥1,280（不足 1 万保留整数即可，页面更清爽） */
function money(value) {
  const num = Number(value || 0)
  if (!num) return '¥0'
  return '¥' + num.toLocaleString('zh-CN', { maximumFractionDigits: num < 100 ? 2 : 0 })
}

/* ------------------------------ ECharts ------------------------------ */

const trendRef = ref(null)
const buyPieRef = ref(null)
const sellPieRef = ref(null)
let trendChart = null
let buyPieChart = null
let sellPieChart = null
let observer = null

/** 品牌配色：买入=蓝紫，卖出=品牌绿 */
const BUY_COLOR = '#5b7cfa'
const SELL_COLOR = '#0fa968'
/** 饼图调色板（跟随全站绿色系 + 暖色点缀） */
const PALETTE = ['#0fa968', '#5b7cfa', '#f5a623', '#ff7a45', '#36b37e', '#8c8c8c']

/** 合并买卖两个维度的月份（升序、去重），保证两组柱子对齐 */
function unionMonths(buyMonthly, sellMonthly) {
  const set = new Set()
  ;(buyMonthly || []).forEach((p) => set.add(p.month))
  ;(sellMonthly || []).forEach((p) => set.add(p.month))
  return Array.from(set).sort()
}

/** 月度趋势：双柱状图（买入 / 卖出），可切换金额 / 笔数 */
function renderTrend() {
  if (!trendChart) return
  const months = unionMonths(buy.value.monthly, sell.value.monthly)
  const buyMap = new Map((buy.value.monthly || []).map((p) => [p.month, p]))
  const sellMap = new Map((sell.value.monthly || []).map((p) => [p.month, p]))
  const field = trendMetric.value === 'amount' ? 'totalAmount' : 'orderCount'
  const unit = trendMetric.value === 'amount' ? '金额' : '笔数'

  trendChart.setOption(
    {
      grid: { left: 56, right: 16, top: 40, bottom: 32 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (v) => (trendMetric.value === 'amount' ? money(v) : v + ' 笔')
      },
      legend: { top: 4, itemWidth: 12, itemHeight: 8, textStyle: { color: '#646a73', fontSize: 12 } },
      xAxis: {
        type: 'category',
        data: months.map((m) => m.slice(2)),
        axisLine: { lineStyle: { color: '#d9dde3' } },
        axisLabel: { color: '#646a73' }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: {
          color: '#646a73',
          formatter: (v) => (trendMetric.value === 'amount' && v >= 1000 ? v / 1000 + 'k' : v)
        },
        splitLine: { lineStyle: { color: '#eef0f3' } }
      },
      series: [
        {
          name: '买入' + unit,
          type: 'bar',
          barMaxWidth: 22,
          itemStyle: { borderRadius: [6, 6, 0, 0], color: BUY_COLOR },
          data: months.map((m) => Number(buyMap.get(m)?.[field] || 0))
        },
        {
          name: '卖出' + unit,
          type: 'bar',
          barMaxWidth: 22,
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#36b37e' },
              { offset: 1, color: '#0fa968' }
            ])
          },
          data: months.map((m) => Number(sellMap.get(m)?.[field] || 0))
        }
      ],
      graphic: months.length
        ? []
        : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#8f959e', fontSize: 14 } }]
    },
    true
  )
}

/** 环形占比图（买 / 卖共用），tooltip 同时给出金额与笔数 */
function renderPie(chart, categories) {
  if (!chart) return
  const list = (categories || []).map((c) => ({
    name: c.categoryName,
    value: Number(c.totalAmount || 0),
    count: c.orderCount
  }))
  const total = list.reduce((sum, item) => sum + item.value, 0)
  chart.setOption(
    {
      color: PALETTE,
      tooltip: {
        trigger: 'item',
        formatter: (p) => `${p.name}<br/>${money(p.value)} · ${p.data.count ?? 0} 件（${p.percent}%）`
      },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#646a73', fontSize: 12 } },
      title: {
        text: money(total),
        subtext: '总金额',
        left: 'center',
        top: '34%',
        textStyle: { fontSize: 20, fontWeight: 700, color: '#1f2329' },
        subtextStyle: { fontSize: 12, color: '#8f959e' }
      },
      series: [
        {
          name: '分类占比',
          type: 'pie',
          radius: ['50%', '72%'],
          center: ['50%', '44%'],
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            label: { show: true, formatter: '{b}\n' + '¥{c}', fontSize: 13, fontWeight: 600 }
          },
          data: list
        }
      ],
      graphic: list.length
        ? []
        : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#8f959e', fontSize: 14 } }]
    },
    true
  )
}

function renderAll() {
  renderTrend()
  renderPie(buyPieChart, buy.value.categories)
  renderPie(sellPieChart, sell.value.categories)
}

// 切换 金额/笔数 只需重绘趋势图（图表未初始化时 renderTrend 直接返回）
watch(trendMetric, renderTrend)

onMounted(async () => {
  try {
    data.value = await getMyAnalysis()
  } finally {
    loading.value = false
  }
  if (isEmpty.value) return

  await nextTick()
  trendChart = echarts.init(trendRef.value)
  buyPieChart = echarts.init(buyPieRef.value)
  sellPieChart = echarts.init(sellPieRef.value)
  renderAll()

  observer = new ResizeObserver(() => {
    trendChart?.resize()
    buyPieChart?.resize()
    sellPieChart?.resize()
  })
  observer.observe(trendRef.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  trendChart?.dispose()
  buyPieChart?.dispose()
  sellPieChart?.dispose()
})
</script>

<style scoped>
.analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------------- 顶部汇总卡 ---------------- */
.analysis__hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.hero-card {
  border-radius: 14px;
  padding: 22px 24px 18px;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.hero-card::after {
  content: '';
  position: absolute;
  right: -34px;
  top: -34px;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.hero-card--buy {
  background: linear-gradient(135deg, #5b7cfa 0%, #7a5bff 100%);
  box-shadow: 0 8px 20px rgba(91, 124, 250, 0.28);
}

.hero-card--sell {
  background: linear-gradient(135deg, #0fa968 0%, #2bbf85 100%);
  box-shadow: 0 8px 20px rgba(15, 169, 104, 0.28);
}

.hero-card__head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.hero-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-card__title {
  font-size: 13px;
  opacity: 0.9;
}

.hero-card__amount {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.5px;
  line-height: 1.2;
}

.hero-card__chips {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.chip {
  background: rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.chip__num {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip__label {
  font-size: 11px;
  opacity: 0.85;
  white-space: nowrap;
}

/* ---------------- 图表卡片 ---------------- */
.analysis__card {
  padding: 18px 20px 12px;
}

.analysis__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.analysis__card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--brix-text, #1f2329);
}

.analysis__card-sub {
  font-size: 12px;
  color: #8f959e;
}

.analysis__chart {
  width: 100%;
}

.analysis__chart--trend {
  height: 320px;
}

.analysis__chart--pie {
  height: 300px;
}

.analysis__pies {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .analysis__hero,
  .analysis__pies {
    grid-template-columns: 1fr;
  }

  .hero-card__chips {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
