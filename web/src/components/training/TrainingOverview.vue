<script setup lang="ts">
import type { TrainingDashboard } from '../../types/training'

defineProps<{ dashboard: Partial<TrainingDashboard> }>()

const statistics: { key: keyof TrainingDashboard; label: string }[] = [
  { key: 'documents', label: '知识文档' }, { key: 'schemas', label: 'Schema' },
  { key: 'metrics', label: '业务指标' }, { key: 'relations', label: '表关系' },
  { key: 'synonyms', label: '同义词' }, { key: 'sqlExamples', label: '标准 SQL' },
  { key: 'goldenQuestions', label: '黄金问题' }, { key: 'feedback', label: '用户反馈' },
]
</script>

<template>
  <div class="overview">
    <div class="statistics">
      <article v-for="item in statistics" :key="item.key"><strong>{{ dashboard[item.key] || 0 }}</strong><span>{{ item.label }}</span></article>
    </div>
    <section class="training-flow">
      <div class="training-flow__heading"><h3>企业知识训练流程</h3><p>训练是持续治理业务知识，不是直接微调大模型。</p></div>
      <div class="steps">
        <div><b>01</b><strong>导入知识</strong><span>DDL 与业务文档</span></div><i>→</i>
        <div><b>02</b><strong>配置语义</strong><span>指标、关系、同义词</span></div><i>→</i>
        <div><b>03</b><strong>发布案例</strong><span>审核标准 SQL</span></div><i>→</i>
        <div><b>04</b><strong>持续评测</strong><span>黄金问题与反馈</span></div>
      </div>
      <el-alert title="真实模式会使用 BGE-M3 + Qdrant 检索企业知识，并把语义规则和标准 SQL 提供给 Qwen3.5。" type="info" :closable="false" show-icon />
    </section>
  </div>
</template>

<style scoped>
.statistics{display:grid;grid-template-columns:repeat(4,1fr);gap:13px}.statistics article{padding:22px 16px;border:1px solid #dde7f2;border-radius:12px;background:linear-gradient(145deg,#f8fbff,#eff5fc);text-align:center}.statistics strong,.statistics span{display:block}.statistics strong{color:#245fae;font-size:29px}.statistics span{margin-top:7px;color:var(--text-muted);font-size:12px}.training-flow{margin-top:22px;padding:26px;border:1px solid var(--border);border-radius:12px;background:var(--surface-subtle)}.training-flow__heading h3{margin:0 0 6px}.training-flow__heading p{margin:0;color:var(--text-muted);font-size:13px}.steps{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:26px 0}.steps>div{flex:1;padding:16px;border:1px solid var(--border);border-radius:10px;background:var(--surface)}.steps b,.steps strong,.steps span{display:block}.steps b{color:var(--primary);font-size:11px}.steps strong{margin:8px 0 4px;font-size:13px}.steps span{color:var(--text-subtle);font-size:11px}.steps i{color:#9caabc;font-style:normal}@media(max-width:920px){.statistics{grid-template-columns:repeat(2,1fr)}.steps{align-items:stretch;flex-direction:column}.steps>div{width:100%}.steps i{display:none}}@media(max-width:520px){.statistics{grid-template-columns:1fr 1fr}}
</style>
