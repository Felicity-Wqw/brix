<template>
  <div ref="chartRef" class="pie-category"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  // [{ name: '数码电子', value: 5 }]
  data: { type: Array, default: () => [] },
  height: { type: Number, default: 280 },
  /** 自定义配色（如风险分布用绿/琥珀/红表达风险高低），不传则用品牌绿系 */
  colors: { type: Array, default: () => ['#0FA968', '#36B37E', '#5AC8A2', '#F5A623', '#FF7A45', '#8C8C8C'] }
})

const chartRef = ref(null)
let chart = null
let observer = null

const PALETTE = ['#0FA968', '#36B37E', '#5AC8A2', '#F5A623', '#FF7A45', '#8C8C8C']

/** 渲染饼图（空数据时显示「暂无数据」） */
function render() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const points = props.data || []
  chart.setOption(
    {
      color: props.colors && props.colors.length ? props.colors : PALETTE,
      tooltip: { trigger: 'item', formatter: '{b}：{c} 件（{d}%）' },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#646a73', fontSize: 12 } },
      series: [
        {
          name: '分类分布',
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            label: { show: true, formatter: '{b}\n{c} 件', fontSize: 13, fontWeight: 600 }
          },
          data: points.map((item) => ({ name: item.name, value: Number(item.value || 0) }))
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
.pie-category {
  width: 100%;
  height: v-bind("props.height + 'px'");
}
</style>
