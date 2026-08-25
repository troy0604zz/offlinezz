<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, MagicStick } from '@element-plus/icons-vue'
import EmptyState from '../components/common/EmptyState.vue'
import PageHeader from '../components/common/PageHeader.vue'
import { apiErrorMessage } from '../services/http'
import { reportApi } from '../services/report-api'
import type { GeneratedReport, ReportListItem } from '../types/report'
import { useDomainStore } from '../stores/domain'

const domainStore = useDomainStore()

const form = reactive({ title: '华东区域销售分析报告', request: '生成华东区域 2026 年销售分析报告' })
const loading = ref(false)
const report = ref<GeneratedReport>()
const history = ref<ReportListItem[]>([])

async function loadHistory(): Promise<void> {
  try {
    if (!domainStore.selectedCode) return
    history.value = (await reportApi.list(domainStore.selectedCode)).data
  } catch {
    history.value = []
  }
}

async function generate(): Promise<void> {
  if (!form.title.trim() || !form.request.trim()) {
    ElMessage.warning('请填写报告名称和分析要求')
    return
  }
  loading.value = true
  try {
    if (!domainStore.selectedCode) throw new Error('请先选择可访问的数据域')
    report.value = (await reportApi.generate(form.title.trim(), form.request.trim(), domainStore.selectedCode)).data
    ElMessage.success('报告已生成')
    await loadHistory()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '报告生成失败'))
  } finally {
    loading.value = false
  }
}

async function openHistory(item: ReportListItem): Promise<void> {
  if (item.status !== 'READY') {
    ElMessage.warning(item.error_message || '该报告尚未生成完成')
    return
  }
  try { report.value = (await reportApi.detail(item.id)).data }
  catch (error) { ElMessage.error(apiErrorMessage(error, '报告详情加载失败')) }
}

onMounted(loadHistory)
watch(() => domainStore.selectedCode, () => { report.value = undefined; loadHistory() })
</script>

<template>
  <PageHeader title="智能报告" description="组合多个受控查询，生成可追溯的经营分析报告。">
    <el-tag type="success" effect="plain">证据可追溯</el-tag>
  </PageHeader>
  <div class="page-container report-workspace">
    <aside class="report-builder panel">
      <div class="section-title"><span class="section-icon"><el-icon><MagicStick /></el-icon></span><div><h2>创建报告</h2><p>描述需要分析的主题和范围</p></div></div>
      <el-form label-position="top">
        <el-form-item label="报告名称"><el-input v-model="form.title" placeholder="例如：华东区域季度销售分析" /></el-form-item>
        <el-form-item label="分析要求"><el-input v-model="form.request" type="textarea" :rows="6" placeholder="请说明区域、时间范围、指标及希望关注的问题" /></el-form-item>
        <el-button class="generate-button" type="primary" :loading="loading" @click="generate">生成智能报告</el-button>
      </el-form>
      <div class="history">
        <h3>最近报告</h3>
        <div v-if="history.length" class="history-list">
          <button v-for="item in history.slice(0, 10)" :key="item.id" class="history-item" type="button" @click="openHistory(item)">
            <el-icon><Document /></el-icon><div><strong>{{ item.title }}</strong><small>{{ item.status }} · {{ item.created_at }}</small></div>
          </button>
        </div>
        <p v-else class="muted">暂时没有历史报告</p>
      </div>
    </aside>

    <section class="report-preview">
      <EmptyState v-if="!report" title="报告将在这里生成" description="填写左侧报告信息后，系统会执行受控数据查询，并组合摘要、分析章节与行动建议。" />
      <article v-else class="report-document">
        <span class="report-document__eyebrow">AI GENERATED BUSINESS REPORT</span>
        <h1>{{ report.title }}</h1>
        <p class="report-document__summary">{{ report.executiveSummary }}</p>
        <el-alert v-if="report.warnings?.length" class="report-warning" title="部分分析章节未能完成" type="warning" :closable="false"><p v-for="warning in report.warnings" :key="warning">{{ warning }}</p></el-alert>
        <div class="report-metadata"><span>数据域 {{ domainStore.current?.name }}</span><span>报告编号 {{ report.id }}</span><span>{{ report.sections.length }} 个动态分析章节</span></div>
        <section v-for="(section, index) in report.sections" :key="section.title" class="report-section">
          <span class="report-section__number">0{{ index + 1 }}</span>
          <div><h2>{{ section.title }}</h2><p v-if="section.query">{{ section.query.answer }}</p><el-alert v-else :title="section.error || '本章节未能生成'" type="error" :closable="false" /><details v-if="section.query"><summary>查看本章节 SQL</summary><pre>{{ section.query.sql }}</pre></details></div>
        </section>
        <section class="recommendations"><h2>行动建议</h2><ol><li v-for="item in report.recommendations" :key="item">{{ item }}</li></ol></section>
      </article>
    </section>
  </div>
</template>

<style scoped>
.report-workspace{max-width:1440px;display:grid;grid-template-columns:340px minmax(0,1fr);gap:22px;align-items:start}.panel{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);box-shadow:var(--shadow-xs)}.report-builder{position:sticky;top:86px;padding:24px}.section-title{display:flex;align-items:center;gap:12px;margin-bottom:26px}.section-title h2{margin:0 0 5px;font-size:18px}.section-title p{margin:0;color:var(--text-muted);font-size:12px}.section-icon{width:40px;height:40px;border-radius:11px;display:grid;place-items:center;background:var(--primary-soft);color:var(--primary)}.generate-button{width:100%}.history{margin-top:30px;padding-top:22px;border-top:1px solid var(--border-light)}.history h3{margin:0 0 14px;font-size:13px}.history-list{display:grid;gap:8px}.history-item{width:100%;border:0;text-align:left;display:flex;align-items:flex-start;gap:10px;padding:10px;border-radius:9px;background:var(--surface-subtle);cursor:pointer}.history-item:hover{background:var(--primary-soft)}.history-item .el-icon{margin-top:2px;color:var(--primary)}.history-item strong,.history-item small{display:block}.history-item strong{font-size:12px;line-height:1.45}.history-item small{margin-top:4px;color:var(--text-subtle);font-size:10px}.muted{color:var(--text-subtle);font-size:12px}.report-document{padding:54px clamp(30px,6vw,78px);background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);box-shadow:var(--shadow-sm)}.report-document__eyebrow{color:var(--primary);font-size:10px;font-weight:750;letter-spacing:.18em}.report-document>h1{margin:18px 0;font-size:clamp(30px,4vw,45px);line-height:1.25;letter-spacing:-.025em}.report-document__summary{color:#56697f;font-size:16px;line-height:1.8}.report-metadata{display:flex;gap:20px;padding:15px 0 28px;border-bottom:1px solid var(--border);color:var(--text-subtle);font-size:11px}.report-section{display:grid;grid-template-columns:54px 1fr;gap:18px;padding:32px 0;border-bottom:1px solid var(--border-light)}.report-section__number{color:#a6b4c4;font-size:14px;font-weight:700}.report-section h2{margin:0 0 12px;font-size:19px}.report-section p{color:var(--text-muted);line-height:1.8}.report-section details{margin-top:16px}.report-section summary{color:var(--primary);font-size:12px;cursor:pointer}.report-section pre{padding:16px;overflow:auto;border-radius:8px;background:#101a2a;color:#d5e6fb;font:11px/1.6 ui-monospace,monospace;white-space:pre-wrap}.recommendations{margin-top:34px;padding:24px;border-radius:12px;background:#f1f7ff}.recommendations h2{margin:0 0 14px;font-size:17px}.recommendations li{margin:8px 0;color:#4d6279;line-height:1.6}@media(max-width:1020px){.report-workspace{grid-template-columns:1fr}.report-builder{position:static}}@media(max-width:650px){.report-document{padding:30px 20px}.report-section{grid-template-columns:1fr}.report-section__number{display:none}}
</style>
