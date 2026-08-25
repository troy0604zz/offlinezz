<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, MagicStick } from '@element-plus/icons-vue'
import EmptyState from '../components/common/EmptyState.vue'
import PageHeader from '../components/common/PageHeader.vue'
import { apiErrorMessage } from '../services/http'
import { reportApi } from '../services/report-api'
import type { GeneratedReport, ReportExportFormat, ReportListItem } from '../types/report'
import { useDomainStore } from '../stores/domain'

const domainStore = useDomainStore()

const form = reactive({ title: '华东区域销售分析报告', request: '生成华东区域 2026 年销售分析报告' })
const loading = ref(false)
const report = ref<GeneratedReport>()
const history = ref<ReportListItem[]>([])
const selectedReportId = ref('')
const deletingReportId = ref('')
const downloadVisible = ref(false)
const downloading = ref(false)
const downloadFormat = ref<ReportExportFormat>('pdf')
const downloadTarget = ref<{ id: string; title: string }>()

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
    selectedReportId.value = report.value.id
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
  try {
    report.value = (await reportApi.detail(item.id)).data
    selectedReportId.value = item.id
  }
  catch (error) { ElMessage.error(apiErrorMessage(error, '报告详情加载失败')) }
}

function openDownload(id: string, title: string, status = 'READY'): void {
  if (status !== 'READY') {
    ElMessage.warning('只有已完成的报告可以下载')
    return
  }
  downloadTarget.value = { id, title }
  downloadFormat.value = 'pdf'
  downloadVisible.value = true
}

async function downloadReport(): Promise<void> {
  if (!downloadTarget.value || downloading.value) return
  downloading.value = true
  try {
    const target = downloadTarget.value
    const response = await reportApi.download(target.id, downloadFormat.value)
    const disposition = String(response.headers['content-disposition'] || '')
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const quotedName = disposition.match(/filename="([^"]+)"/i)?.[1]
    const fileName = encodedName
      ? decodeURIComponent(encodedName)
      : quotedName || `${target.title}.${downloadFormat.value}`
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    downloadVisible.value = false
    ElMessage.success(`${downloadFormat.value === 'pdf' ? 'PDF' : 'Word'} 报告已下载`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '报告下载失败'))
  } finally {
    downloading.value = false
  }
}

async function deleteReport(item: ReportListItem): Promise<void> {
  if (deletingReportId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除“${item.title}”吗？删除后不能恢复，但相关查询记录、训练数据和审计记录不会受影响。`,
      '删除历史报告',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deletingReportId.value = item.id
  try {
    await reportApi.delete(item.id)
    if (selectedReportId.value === item.id) {
      report.value = undefined
      selectedReportId.value = ''
    }
    await loadHistory()
    ElMessage.success('历史报告已删除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '报告删除失败'))
  } finally {
    deletingReportId.value = ''
  }
}

function statusText(status: string): string {
  return ({ READY: '已完成', GENERATING: '生成中', FAILED: '生成失败' } as Record<string, string>)[status] || status
}

onMounted(loadHistory)
watch(() => domainStore.selectedCode, () => {
  report.value = undefined
  selectedReportId.value = ''
  loadHistory()
})
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
        <div class="history-heading"><h3>历史报告</h3><span>{{ history.length }} 份</span></div>
        <div v-if="history.length" class="history-list">
          <div v-for="item in history" :key="item.id" class="history-item" :class="{ 'is-active': selectedReportId === item.id }" @click="openHistory(item)">
            <div class="history-item__content">
              <el-icon><Document /></el-icon>
              <div class="history-item__text"><strong>{{ item.title }}</strong><small>{{ statusText(item.status) }} · {{ item.created_at }}</small><small>创建人 {{ item.created_by }}</small></div>
            </div>
            <div class="history-item__actions" @click.stop>
              <el-tooltip content="下载报告" placement="top">
                <el-button text circle :disabled="item.status !== 'READY'" aria-label="下载报告" @click="openDownload(item.id, item.title, item.status)"><el-icon><Download /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip v-if="item.can_delete" content="删除报告" placement="top">
                <el-button text circle type="danger" :loading="deletingReportId === item.id" :disabled="Boolean(deletingReportId)" aria-label="删除报告" @click="deleteReport(item)"><el-icon v-if="deletingReportId !== item.id"><Delete /></el-icon></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <p v-else class="muted">暂时没有历史报告</p>
      </div>
    </aside>

    <section class="report-preview">
      <EmptyState v-if="!report" title="报告将在这里生成" description="填写左侧报告信息后，系统会执行受控数据查询，并组合摘要、分析章节与行动建议。" />
      <article v-else class="report-document">
        <div class="report-toolbar"><span>历史内容快照</span><el-button type="primary" plain :icon="Download" @click="openDownload(report.id, report.title)">下载报告</el-button></div>
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

  <el-dialog v-model="downloadVisible" title="下载智能报告" width="min(620px, 92vw)" class="download-dialog">
    <p class="download-description">文件将使用报告生成完成时保存的内容快照，不会重新执行 SQL 或调用大模型。</p>
    <el-radio-group v-model="downloadFormat" class="format-grid">
      <el-radio value="pdf" border><strong>PDF 文档</strong><span>.pdf · 适合分享、审批和归档</span></el-radio>
      <el-radio value="docx" border><strong>Word 文档</strong><span>.docx · 适合继续编辑和补充说明</span></el-radio>
    </el-radio-group>
    <template #footer>
      <el-button :disabled="downloading" @click="downloadVisible=false">取消</el-button>
      <el-button type="primary" :loading="downloading" @click="downloadReport">下载 {{ downloadFormat === 'pdf' ? 'PDF' : 'Word' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.report-workspace{max-width:1440px;display:grid;grid-template-columns:360px minmax(0,1fr);gap:22px;align-items:start}.panel{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);box-shadow:var(--shadow-xs)}.report-builder{position:sticky;top:86px;padding:24px}.section-title{display:flex;align-items:center;gap:12px;margin-bottom:26px}.section-title h2{margin:0 0 5px;font-size:18px}.section-title p{margin:0;color:var(--text-muted);font-size:12px}.section-icon{width:40px;height:40px;border-radius:11px;display:grid;place-items:center;background:var(--primary-soft);color:var(--primary)}.generate-button{width:100%}.history{margin-top:30px;padding-top:22px;border-top:1px solid var(--border-light)}.history-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.history-heading h3{margin:0;font-size:13px}.history-heading span{color:var(--text-subtle);font-size:11px}.history-list{display:grid;gap:8px;max-height:390px;overflow:auto;padding-right:4px}.history-item{width:100%;padding:11px;border:1px solid transparent;border-radius:10px;background:var(--surface-subtle);cursor:pointer;transition:.18s ease}.history-item:hover,.history-item.is-active{background:var(--primary-soft);border-color:#cfe1f7}.history-item__content{display:flex;align-items:flex-start;gap:10px}.history-item__content>.el-icon{flex:0 0 auto;margin-top:2px;color:var(--primary)}.history-item__text{min-width:0}.history-item strong,.history-item small{display:block}.history-item strong{overflow:hidden;font-size:12px;line-height:1.45;text-overflow:ellipsis;white-space:nowrap}.history-item small{margin-top:4px;color:var(--text-subtle);font-size:10px}.history-item__actions{display:flex;justify-content:flex-end;margin-top:5px}.history-item__actions .el-button{margin-left:2px}.muted{color:var(--text-subtle);font-size:12px}.report-document{padding:30px clamp(30px,6vw,78px) 54px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);box-shadow:var(--shadow-sm)}.report-toolbar{display:flex;align-items:center;justify-content:space-between;margin:0 0 28px;padding-bottom:18px;border-bottom:1px solid var(--border-light);color:var(--text-subtle);font-size:11px}.report-document__eyebrow{color:var(--primary);font-size:10px;font-weight:750;letter-spacing:.18em}.report-document>h1{margin:18px 0;font-size:clamp(30px,4vw,45px);line-height:1.25;letter-spacing:-.025em}.report-document__summary{color:#56697f;font-size:16px;line-height:1.8}.report-metadata{display:flex;gap:20px;padding:15px 0 28px;border-bottom:1px solid var(--border);color:var(--text-subtle);font-size:11px}.report-section{display:grid;grid-template-columns:54px 1fr;gap:18px;padding:32px 0;border-bottom:1px solid var(--border-light)}.report-section__number{color:#a6b4c4;font-size:14px;font-weight:700}.report-section h2{margin:0 0 12px;font-size:19px}.report-section p{color:var(--text-muted);line-height:1.8}.report-section details{margin-top:16px}.report-section summary{color:var(--primary);font-size:12px;cursor:pointer}.report-section pre{padding:16px;overflow:auto;border-radius:8px;background:#101a2a;color:#d5e6fb;font:11px/1.6 ui-monospace,monospace;white-space:pre-wrap}.recommendations{margin-top:34px;padding:24px;border-radius:12px;background:#f1f7ff}.recommendations h2{margin:0 0 14px;font-size:17px}.recommendations li{margin:8px 0;color:#4d6279;line-height:1.6}.download-description{margin:0 0 18px;color:var(--text-muted);line-height:1.7}.format-grid{width:100%;display:grid;gap:12px}.format-grid :deep(.el-radio){width:100%;height:auto;margin:0;padding:15px 17px;display:flex;align-items:flex-start}.format-grid :deep(.el-radio__label){display:grid;gap:5px;white-space:normal}.format-grid strong{font-size:14px}.format-grid span{color:var(--text-muted);font-size:12px}@media(max-width:1020px){.report-workspace{grid-template-columns:1fr}.report-builder{position:static}.history-list{max-height:320px}}@media(max-width:650px){.report-document{padding:22px 20px 30px}.report-section{grid-template-columns:1fr}.report-section__number{display:none}.report-metadata{flex-direction:column;gap:8px}.report-toolbar{align-items:flex-start;gap:12px}}
</style>
