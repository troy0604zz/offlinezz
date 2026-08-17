<script setup lang="ts">
import { computed } from 'vue'
import type { QueryAnswer } from '../../types/query'
import ResultChart from './ResultChart.vue'

const props = withDefaults(defineProps<{ result: QueryAnswer; confirmed?: boolean }>(), { confirmed: false })
defineEmits<{ positive: []; correct: []; download: [] }>()

const columns = computed(() => props.result.rows.length ? Object.keys(props.result.rows[0]) : [])
const hasVisualization = computed(() => ['kpi', 'bar', 'line'].includes(props.result.chart.type))
const visualizationLabel = computed(() => {
  const labels: Record<string, string> = { kpi: '指标卡', bar: '分类对比', line: '趋势分析' }
  return labels[props.result.chart.type] || '表格优先'
})
</script>

<template>
  <div class="result-grid">
    <article class="result-card insight-card" :class="{ 'result-card--wide': !hasVisualization }">
      <div class="card-label">AI 分析结论</div>
      <h2>{{ result.answer }}</h2>
      <div class="result-meta">
        <span>可信度 {{ Math.round(result.confidence * 100) }}%</span>
        <span>{{ result.elapsedMs }} ms</span>
        <span>{{ result.llmProvider }}</span>
      </div>
    </article>

    <article v-if="hasVisualization" class="result-card visualization-card">
      <div class="visualization-heading">
        <div>
          <div class="card-label">智能可视化</div>
          <h3>{{ result.chart.title }}</h3>
        </div>
        <el-tag effect="plain">{{ visualizationLabel }}</el-tag>
      </div>
      <ResultChart :rows="result.rows" :chart="result.chart" />
      <p class="visualization-reason">{{ result.chart.reason }}</p>
    </article>

    <article class="result-card result-card--wide">
      <div class="data-heading">
        <div>
          <div class="card-label">查询数据</div>
          <p>{{ result.rows.length }} 行 · {{ columns.length }} 个字段</p>
        </div>
        <el-tag v-if="!hasVisualization" type="info" effect="plain">表格是本次结果的最佳展示方式</el-tag>
      </div>
      <p v-if="!hasVisualization && result.chart.reason" class="table-reason">{{ result.chart.reason }}</p>
      <el-table :data="result.rows" max-height="420" stripe>
        <el-table-column v-for="column in columns" :key="column" :prop="column" :label="column" min-width="140" show-overflow-tooltip />
      </el-table>
    </article>

    <article class="result-card result-card--wide">
      <div class="sql-heading">
        <div>
          <div class="card-label">可审计 SQL</div>
          <p>{{ result.explanation }}</p>
        </div>
        <el-tag type="success" effect="plain">只读查询</el-tag>
      </div>
      <pre><code>{{ result.sql }}</code></pre>
      <div class="sql-footer">
        <span>涉及数据表：{{ result.tables.join('、') || '未识别' }}</span>
        <div class="result-actions">
          <el-tag v-if="confirmed" type="success" effect="plain">已确认正确</el-tag>
          <el-button v-if="confirmed" type="primary" plain @click="$emit('download')">下载报表</el-button>
          <el-button v-else @click="$emit('positive')">结果正确</el-button>
          <el-button type="warning" plain @click="$emit('correct')">提交修正</el-button>
        </div>
      </div>
    </article>
  </div>
</template>

<style scoped>
.result-grid{display:grid;grid-template-columns:minmax(0,.88fr) minmax(0,1.12fr);gap:18px}.result-card{min-width:0;padding:22px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}.result-card--wide{grid-column:1/-1}.card-label{margin-bottom:12px;color:var(--text-subtle);font-size:11px;font-weight:750;letter-spacing:.09em;text-transform:uppercase}.insight-card{display:flex;min-height:260px;flex-direction:column;color:white;border:0;background:linear-gradient(140deg,#143c70,#2769b5)}.insight-card .card-label{color:#b9d1ec}.insight-card h2{margin:8px 0 28px;font-size:21px;line-height:1.65}.insight-card .result-meta{margin-top:auto}.result-meta{display:flex;gap:18px;flex-wrap:wrap;color:#bed2e8;font-size:12px}.visualization-heading,.data-heading,.sql-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.visualization-heading h3{margin:-4px 0 12px;color:var(--text-strong);font-size:18px}.visualization-reason,.table-reason,.data-heading p,.sql-heading p{color:var(--text-muted);font-size:12px;line-height:1.6}.visualization-reason{margin:8px 0 0;padding-top:12px;border-top:1px solid var(--border-light)}.data-heading p{margin:-6px 0 14px}.table-reason{margin:-3px 0 15px;padding:10px 12px;border-radius:8px;background:var(--surface-soft)}.sql-heading p{margin:-6px 0 14px;font-size:13px}.result-card pre{max-height:360px;margin:0;padding:20px;overflow:auto;border-radius:10px;background:#101a2a;color:#d5e6fb;font:13px/1.7 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap}.sql-footer{display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:16px;color:var(--text-muted);font-size:12px}.result-actions{display:flex;align-items:center;gap:9px;flex-wrap:wrap}@media(max-width:940px){.result-grid{grid-template-columns:1fr}.result-card--wide{grid-column:auto}.insight-card{min-height:0}}@media(max-width:650px){.sql-footer,.visualization-heading,.data-heading{align-items:flex-start;flex-direction:column}.result-card{padding:17px}}
</style>
