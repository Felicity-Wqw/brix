<template>
  <div ref="chartRef" class="admin-school"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * 各学校交易量：横向条形图（成交金额），标签带单数与在售数。
 *
 * 用横向而不是纵向：13 所学校的长校名（桂林电子科技大学）竖排会重叠，
 * 横排能完整显示，且「金额排序」在横向更符合阅读习惯。
 */
const props = defineProps({
  // [{ schoolName, orderCount, amount, onSaleCount }]
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 340 }
})

const chartRef = ref(null)
let chart = null
let observer = null

function render() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  // 只取前 8 名，超出的在表格里看，图上塞满会糊成一片
  const points = [...(props.data || [])].slice(0, 8).reverse()
  chart.setOption(
    {
      grid: { left: 8, right: 90, top: 16, bottom: 12, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const row = points[params[0].dataIndex]
          return `${row.schoolName}<br/>成交金额：￥${Number(row.amount || 0).toLocaleString('zh-CN')}<br/>` +
            `成交单数：${row.orderCount || 0} 单<br/>在售商品：${row.onSaleCount || 0} 件`
        }
      },
      xAxis: {
        type: 'value',
        axisLabel: { color: '#7c8794', fontSize: 11, formatter: (v) => (v >= 1000 ? `${v / 1000}k` : v) },
        splitLine: { lineStyle: { color: '#eef0f3' } }
      },
      yAxis: {
        type: 'category',
        data: points.map((item) => item.schoolName),
        axisLine: { lineStyle: { color: '#d9dde3' } },
        axisLabel: { color: '#5c6b63', fontSize: 12 }
      },
      series: [
        {
          type: 'bar',
          barWidth: 14,
          data: points.map((item) => Number(item.amount || 0)),
          itemStyle: {
            borderRadius: [0, 6, 6, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#6fd2a6' },
              { offset: 1, color: '#0e7a50' }
            ])
          },
          label: {
            show: true,
            position: 'right',
            color: '#5c6b63',
            fontSize: 11,
            formatter: (params) => `￥${Number(params.value || 0).toLocaleString('zh-CN')}`
          }
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
.admin-school {
  width: 100%;
  height: v-bind("props.height + 'px'");
}
</style>
