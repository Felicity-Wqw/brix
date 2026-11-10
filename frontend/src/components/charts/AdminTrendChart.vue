<template>
  <div ref="chartRef" class="admin-trend"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 成交趋势：柱=成交金额（左轴），线=成交单数（右轴）。
 *
 * 为什么双轴：单数和金额量级差 2~3 个数量级，同轴会把单数压成一条贴底的直线，
 * 看不出「单多但客单价低」这种关键信号。
 */
const props = defineProps({
  // [{ date: '2026-11-01', orderCount: 3, amount: 2590 }]
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 300 }
})

const chartRef = ref(null)
let chart = null
let observer = null

const money = (value) => `￥${Number(value || 0).toLocaleString('zh-CN')}`

function render() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const points = props.data || []
  chart.setOption(
    {
      grid: { left: 56, right: 44, top: 36, bottom: 34 },
      tooltip: {
        trigger: 'axis',
        formatter: (params) => {
          const [bar, line] = params
          return `${bar.axisValue}<br/>成交金额：${money(bar.value)}<br/>成交单数：${line.value} 单`
        }
      },
      xAxis: {
        type: 'category',
        data: points.map((item) => (item.date || '').slice(5)),
        axisLine: { lineStyle: { color: '#d9dde3' } },
        axisLabel: { color: '#7c8794', fontSize: 11 }
      },
      yAxis: [
        {
          type: 'value',
          name: '金额',
          nameTextStyle: { color: '#9aa5b3', fontSize: 11 },
          axisLabel: { color: '#7c8794', fontSize: 11, formatter: (v) => (v >= 1000 ? `${v / 1000}k` : v) },
          splitLine: { lineStyle: { color: '#eef0f3' } }
        },
        {
          type: 'value',
          name: '单数',
          nameTextStyle: { color: '#9aa5b3', fontSize: 11 },
          minInterval: 1,
          axisLabel: { color: '#7c8794', fontSize: 11 },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '成交金额',
          type: 'bar',
          barWidth: '46%',
          data: points.map((item) => Number(item.amount || 0)),
          itemStyle: { color: 'rgba(14,122,80,0.72)', borderRadius: [4, 4, 0, 0] }
        },
        {
          name: '成交单数',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          symbolSize: 6,
          data: points.map((item) => Number(item.orderCount || 0)),
          itemStyle: { color: '#ff7a45' },
          lineStyle: { color: '#ff7a45', width: 2 }
        }
      ],
      graphic: points.length
        ? []
        : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无数据', fill: '#8f959e', fontSize: 14 } }]
    },
    true
  )
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(chartRef.value)
})

watch(
  () => props.data,
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.admin-trend {
  width: 100%;
  height: v-bind("props.height + 'px'");
}
</style>
