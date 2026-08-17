<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ChartSpec } from '../../types/query'

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ rows: Record<string, unknown>[]; chart: ChartSpec }>()
const container = ref<HTMLElement>()
let instance: echarts.ECharts | null = null

const isKpi = computed(() => props.chart.type === 'kpi')
const isChart = computed(() => props.chart.type === 'bar' || props.chart.type === 'line')
const contextValue = computed(() => {
  const field = props.chart.categoryField
  return field && props.rows.length ? props.rows[0][field] : null
})
const kpis = computed(() => props.chart.valueFields.map((field) => ({
  field,
  label: fieldLabel(field),
  value: formatValue(props.rows[0]?.[field]),
})))

function fieldLabel(field: string): string {
  return field.replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  const number = typeof value === 'number' ? value : Number(value)
  if (Number.isFinite(number)) {
    return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(number)
  }
  return String(value)
}

function dispose(): void {
  instance?.dispose()
  instance = null
}

async function render(): Promise<void> {
  if (!isChart.value) {
    dispose()
    return
  }
  await nextTick()
  if (!container.value || !props.rows.length || !props.chart.categoryField) return

  dispose()
  instance = echarts.init(container.value)
  const categoryField = props.chart.categoryField
  const categories = props.rows.map((row) => String(row[categoryField] ?? '—'))
  const series = props.chart.valueFields.map((field) => ({
    name: fieldLabel(field),
    type: props.chart.type as 'bar' | 'line',
    smooth: props.chart.type === 'line',
    symbolSize: 7,
    data: props.rows.map((row) => {
      const value = Number(row[field])
      return Number.isFinite(value) ? value : null
    }),
    emphasis: { focus: 'series' as const },
    areaStyle: props.chart.type === 'line' && props.chart.valueFields.length === 1
      ? { opacity: 0.08 }
      : undefined,
  }))

  const common = {
    animationDuration: 420,
    color: ['#2f6fed', '#18a67a', '#8b5cf6'],
    tooltip: { trigger: 'axis' as const },
    legend: props.chart.valueFields.length > 1
      ? { top: 0, right: 8, textStyle: { color: '#627086' } }
      : undefined,
    series,
  }

  if (props.chart.type === 'bar') {
    instance.setOption({
      ...common,
      grid: { left: 122, right: 28, bottom: 24, top: props.chart.valueFields.length > 1 ? 38 : 18 },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f6' } } },
      yAxis: {
        type: 'category',
        inverse: true,
        data: categories,
        axisLabel: { width: 104, overflow: 'truncate', color: '#627086' },
        axisLine: { show: false },
        axisTick: { show: false },
      },
    })
  } else {
    instance.setOption({
      ...common,
      grid: { left: 66, right: 24, bottom: 52, top: props.chart.valueFields.length > 1 ? 38 : 18 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: categories,
        axisLabel: { hideOverlap: true, color: '#627086' },
        axisLine: { lineStyle: { color: '#d9e1eb' } },
      },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f6' } } },
    })
  }
  instance.resize()
}

watch(() => [props.rows, props.chart], render, { deep: true })
onMounted(() => {
  render()
  window.addEventListener('resize', render)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', render)
  dispose()
})
</script>

<template>
  <div v-if="isKpi" class="kpi-grid">
    <div v-for="kpi in kpis" :key="kpi.field" class="kpi-item">
      <span>{{ kpi.label }}</span>
      <strong>{{ kpi.value }}</strong>
      <small v-if="contextValue !== null">{{ contextValue }}</small>
    </div>
  </div>
  <div v-else ref="container" class="result-chart" />
</template>

<style scoped>
.result-chart{width:100%;height:300px}.kpi-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}.kpi-item{min-width:0;padding:18px;border:1px solid #dce6f3;border-radius:12px;background:linear-gradient(145deg,#f8fbff,#eef5ff)}.kpi-item span{display:block;overflow:hidden;color:var(--text-muted);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.kpi-item strong{display:block;margin-top:11px;color:var(--text-strong);font-size:26px;line-height:1.2;overflow-wrap:anywhere}.kpi-item small{display:block;margin-top:8px;color:var(--text-subtle);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}@media(max-width:650px){.result-chart{height:260px}.kpi-grid{grid-template-columns:1fr 1fr}.kpi-item strong{font-size:21px}}
</style>
