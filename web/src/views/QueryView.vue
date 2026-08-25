<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/common/PageHeader.vue'
import QuestionComposer from '../components/query/QuestionComposer.vue'
import QueryResultPanel from '../components/query/QueryResultPanel.vue'
import { apiErrorMessage } from '../services/http'
import { queryApi } from '../services/query-api'
import type { QueryAnswer, QueryExportFormat, QueryFeedback } from '../types/query'
import { useDomainStore } from '../stores/domain'

const domainStore = useDomainStore()

const question = ref('查询2026年华东区域每月净销售额')
const loading = ref(false)
const error = ref('')
const result = ref<QueryAnswer>()
const correctionVisible = ref(false)
const correction = ref<QueryFeedback>({ rating: 2, comment: '', correctedSql: null })
const resultConfirmed = ref(false)
const downloadVisible = ref(false)
const downloading = ref(false)
const downloadFormat = ref<QueryExportFormat>('xlsx')

async function askQuestion(): Promise<void> {
  if (!question.value.trim() || loading.value) return
  loading.value = true
  error.value = ''
  try {
    if (!domainStore.selectedCode) throw new Error('请先选择可访问的数据域')
    result.value = (await queryApi.ask(question.value.trim(), domainStore.selectedCode)).data
    resultConfirmed.value = false
  } catch (exception) {
    error.value = apiErrorMessage(exception, '分析失败')
  } finally {
    loading.value = false
  }
}

async function submitPositiveFeedback(): Promise<void> {
  if (!result.value) return
  try {
    await queryApi.feedback(result.value.queryRunId, { rating: 5, comment: '结果正确', correctedSql: null })
    resultConfirmed.value = true
    downloadFormat.value = 'xlsx'
    downloadVisible.value = true
    ElMessage.success('结果已确认为正确')
  } catch (exception) {
    ElMessage.error(apiErrorMessage(exception, '反馈保存失败'))
  }
}

function openDownload(): void {
  downloadVisible.value = true
}

async function downloadReport(): Promise<void> {
  if (!result.value || downloading.value) return
  downloading.value = true
  try {
    const response = await queryApi.download(result.value.queryRunId, downloadFormat.value)
    const disposition = String(response.headers['content-disposition'] || '')
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const fileName = encodedName ? decodeURIComponent(encodedName) : `query-result-${result.value.queryRunId}.${downloadFormat.value}`
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    downloadVisible.value = false
    ElMessage.success(`${downloadFormat.value.toUpperCase()} 报表已生成`)
  } catch (exception) {
    ElMessage.error(apiErrorMessage(exception, '报表下载失败'))
  } finally {
    downloading.value = false
  }
}

function openCorrection(): void {
  if (!result.value) return
  correction.value = { rating: 2, comment: '', correctedSql: result.value.sql }
  correctionVisible.value = true
}

async function submitCorrection(): Promise<void> {
  if (!result.value) return
  try {
    await queryApi.feedback(result.value.queryRunId, correction.value)
    correctionVisible.value = false
    ElMessage.success('修正已提交，管理员可在训练中心审核')
  } catch (exception) {
    ElMessage.error(apiErrorMessage(exception))
  }
}
</script>

<template>
  <PageHeader title="数据问答" description="使用自然语言查询企业数据，所有结果均保留 SQL 与数据来源。">
    <el-tag effect="plain">{{ domainStore.current?.name || '未选择数据域' }}</el-tag>
  </PageHeader>
  <div class="page-container query-page">
    <QuestionComposer v-model="question" :loading="loading" @submit="askQuestion" />
    <el-alert v-if="error" class="query-error" :title="error" type="error" show-icon :closable="false" />
    <QueryResultPanel v-if="result" :result="result" :confirmed="resultConfirmed"
      @positive="submitPositiveFeedback" @download="openDownload" @correct="openCorrection" />
  </div>

  <el-dialog v-model="correctionVisible" title="修正本次问答" width="min(680px, 92vw)">
    <el-form label-position="top">
      <el-form-item label="结果评分"><el-rate v-model="correction.rating" /></el-form-item>
      <el-form-item label="问题说明"><el-input v-model="correction.comment" placeholder="请说明错误的指标口径、关联关系或过滤条件" /></el-form-item>
      <el-form-item label="正确 SQL"><el-input v-model="correction.correctedSql" type="textarea" :rows="10" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="correctionVisible=false">取消</el-button><el-button type="primary" @click="submitCorrection">提交修正</el-button></template>
  </el-dialog>

  <el-dialog v-model="downloadVisible" title="结果已确认，是否下载报表？" width="min(620px, 92vw)" class="download-dialog">
    <p class="download-description">请选择下载格式。文件内容来自本次已确认的查询结果，不会重新执行 SQL。</p>
    <el-radio-group v-model="downloadFormat" class="format-grid">
      <el-radio value="xlsx" border><strong>Excel</strong><span>.xlsx · 适合继续分析和制作图表</span></el-radio>
      <el-radio value="csv" border><strong>CSV</strong><span>.csv · 适合数据交换和批量处理</span></el-radio>
      <el-radio value="xml" border><strong>XML</strong><span>.xml · 适合系统集成和结构化归档</span></el-radio>
    </el-radio-group>
    <template #footer>
      <el-button @click="downloadVisible=false">暂不下载</el-button>
      <el-button type="primary" :loading="downloading" @click="downloadReport">下载 {{ downloadFormat.toUpperCase() }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>.query-page{max-width:1320px}.query-error{margin-bottom:20px}.query-page :deep(.el-dialog textarea){font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.download-description{margin:0 0 18px;color:var(--text-muted);line-height:1.7}.format-grid{width:100%;display:grid;gap:12px}.format-grid :deep(.el-radio){width:100%;height:auto;margin:0;padding:15px 17px;display:flex;align-items:flex-start}.format-grid :deep(.el-radio__label){display:grid;gap:5px;white-space:normal}.format-grid strong{font-size:14px}.format-grid span{color:var(--text-muted);font-size:12px}</style>
