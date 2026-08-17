<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import PageHeader from '../../components/common/PageHeader.vue'
import TrainingDataTable, { type TableColumn } from '../../components/training/TrainingDataTable.vue'
import TrainingEditorDialog from '../../components/training/TrainingEditorDialog.vue'
import TrainingOverview from '../../components/training/TrainingOverview.vue'
import ModelRuntimePanel from '../../components/training/ModelRuntimePanel.vue'
import { apiErrorMessage } from '../../services/http'
import { trainingApi } from '../../services/training-api'
import type { DataRow, TrainingResource, TrainingState } from '../../types/training'

const activeTab = ref('overview')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editorResource = ref<TrainingResource | null>(null)
const state = reactive<TrainingState>({ dashboard: {}, documents: [], schemas: [], metrics: [], relations: [], synonyms: [], examples: [], golden: [], feedback: [] })

const columns: Record<string, TableColumn[]> = {
  documents: [{ key: 'file_name', label: '文件', minWidth: 220 }, { key: 'domain', label: '数据域', width: 110 }, { key: 'index_version', label: '索引版本', minWidth: 150 }, { key: 'status', label: '状态', width: 110 }],
  schemas: [{ key: 'name', label: '名称', minWidth: 180 }, { key: 'dialect', label: '方言', width: 130 }, { key: 'description', label: '业务说明', minWidth: 280, overflow: true }, { key: 'status', label: '状态', width: 110 }],
  metrics: [{ key: 'name', label: '名称', minWidth: 150 }, { key: 'code', label: '编码', minWidth: 150 }, { key: 'expression_sql', label: '表达式', minWidth: 320, overflow: true }, { key: 'base_table', label: '基础表', minWidth: 150 }, { key: 'status', label: '状态', width: 110 }],
  relations: [{ key: 'left_table', label: '左表', minWidth: 150 }, { key: 'right_table', label: '右表', minWidth: 150 }, { key: 'join_type', label: 'Join', width: 90 }, { key: 'join_condition', label: '关联条件', minWidth: 300, overflow: true }, { key: 'cardinality', label: '基数', minWidth: 130 }],
  synonyms: [{ key: 'business_term', label: '标准词', minWidth: 150 }, { key: 'synonyms', label: '同义表达', minWidth: 240 }, { key: 'target_expression', label: '映射目标', minWidth: 240 }, { key: 'status', label: '状态', width: 110 }],
  examples: [{ key: 'question', label: '标准问题', minWidth: 240 }, { key: 'sql_text', label: 'SQL', minWidth: 380, overflow: true }, { key: 'hit_count', label: '命中次数', width: 100 }, { key: 'status', label: '状态', width: 110 }],
  golden: [{ key: 'question', label: '问题', minWidth: 260 }, { key: 'last_run_status', label: '上次结果', minWidth: 120 }, { key: 'last_score', label: '得分', width: 90 }, { key: 'last_run_at', label: '执行时间', minWidth: 170 }],
  feedback: [{ key: 'question', label: '问题', minWidth: 220 }, { key: 'rating', label: '评分', width: 80 }, { key: 'comment', label: '说明', minWidth: 180 }, { key: 'corrected_sql', label: '修正 SQL', minWidth: 320, overflow: true }],
}

async function refresh(): Promise<void> {
  loading.value = true
  const requests = [trainingApi.dashboard(), trainingApi.documents(), trainingApi.schemas(), trainingApi.metrics(), trainingApi.relations(), trainingApi.synonyms(), trainingApi.examples(), trainingApi.golden(), trainingApi.feedback()]
  try {
    const values = await Promise.allSettled(requests)
    const data = <T,>(index: number, fallback: T): T => values[index].status === 'fulfilled' ? values[index].value.data as T : fallback
    state.dashboard = data(0, {})
    state.documents = data(1, [])
    state.schemas = data(2, [])
    state.metrics = data(3, [])
    state.relations = data(4, [])
    state.synonyms = data(5, [])
    state.examples = data(6, [])
    state.golden = data(7, [])
    state.feedback = data(8, [])
    if (values.some((value) => value.status === 'rejected')) ElMessage.warning('部分训练数据暂时无法加载')
  } finally {
    loading.value = false
  }
}

function openEditor(resource: TrainingResource): void {
  editorResource.value = resource
  dialogVisible.value = true
}

async function saveResource(resource: TrainingResource, form: DataRow): Promise<void> {
  saving.value = true
  try {
    await trainingApi.create(resource, form)
    dialogVisible.value = false
    ElMessage.success('训练内容已保存')
    await refresh()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    saving.value = false
  }
}

async function uploadDocument(options: UploadRequestOptions): Promise<void> {
  try {
    await trainingApi.uploadDocument(options.file)
    ElMessage.success('文档已解析并发布到知识索引')
    await refresh()
    options.onSuccess({})
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  }
}

async function publishMetric(row: DataRow): Promise<void> {
  await trainingApi.publishMetric(Number(row.id)); ElMessage.success('指标已发布'); await refresh()
}
async function publishExample(row: DataRow): Promise<void> {
  await trainingApi.publishExample(Number(row.id)); ElMessage.success('标准 SQL 已发布'); await refresh()
}
async function evaluateGolden(row: DataRow): Promise<void> {
  try {
    const result = (await trainingApi.runGolden(Number(row.id))).data
    ElMessage[result.status === 'PASSED' ? 'success' : 'warning'](`评测 ${result.status}，得分 ${result.score}`)
    await refresh()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '评测失败')) }
}
async function promoteFeedback(row: DataRow): Promise<void> {
  await trainingApi.promoteFeedback(Number(row.id)); ElMessage.success('已转为待审核 SQL 案例'); activeTab.value = 'examples'; await refresh()
}

onMounted(refresh)
</script>

<template>
  <PageHeader title="AI 训练中心" description="集中治理 Schema、业务语义、标准 SQL、评测集和用户反馈。">
    <el-button :loading="loading" @click="refresh">刷新数据</el-button>
  </PageHeader>
  <div class="page-container training-page" v-loading="loading">
    <el-tabs v-model="activeTab" class="training-tabs">
      <el-tab-pane label="训练概览" name="overview"><TrainingOverview :dashboard="state.dashboard" /></el-tab-pane>
      <el-tab-pane label="模型配置" name="models"><ModelRuntimePanel /></el-tab-pane>

      <el-tab-pane label="文档知识" name="documents">
        <div class="pane-heading"><div><h2>文档知识库</h2><p>上传 Word、PDF、Excel 或 Markdown，系统会解析、切分并写入向量索引。</p></div><el-upload :http-request="uploadDocument" :show-file-list="false"><el-button type="primary">上传并训练</el-button></el-upload></div>
        <TrainingDataTable :rows="state.documents" :columns="columns.documents" />
      </el-tab-pane>

      <el-tab-pane label="DDL / Schema" name="schemas">
        <div class="pane-heading"><div><h2>数据库结构</h2><p>维护可供模型使用的表、字段、约束和业务说明。</p></div><el-button type="primary" @click="openEditor('schema')">新增 Schema</el-button></div>
        <TrainingDataTable :rows="state.schemas" :columns="columns.schemas" />
      </el-tab-pane>

      <el-tab-pane label="业务指标" name="metrics">
        <div class="pane-heading"><div><h2>业务指标</h2><p>定义唯一可信的指标口径，发布前应由业务负责人审核。</p></div><el-button type="primary" @click="openEditor('metric')">新增指标</el-button></div>
        <TrainingDataTable :rows="state.metrics" :columns="columns.metrics" action-label="发布" :action-visible="row => row.status !== 'PUBLISHED'" @action="publishMetric" />
      </el-tab-pane>

      <el-tab-pane label="表关系" name="relations">
        <div class="pane-heading"><div><h2>表关系</h2><p>明确 Join 路径、类型和基数，降低多表查询错误。</p></div><el-button type="primary" @click="openEditor('relation')">新增关系</el-button></div>
        <TrainingDataTable :rows="state.relations" :columns="columns.relations" />
      </el-tab-pane>

      <el-tab-pane label="同义词" name="synonyms">
        <div class="pane-heading"><div><h2>业务同义词</h2><p>把用户口语映射到标准指标、维度或过滤条件。</p></div><el-button type="primary" @click="openEditor('synonym')">新增同义词</el-button></div>
        <TrainingDataTable :rows="state.synonyms" :columns="columns.synonyms" />
      </el-tab-pane>

      <el-tab-pane label="标准 SQL" name="examples">
        <div class="pane-heading"><div><h2>问题与标准 SQL</h2><p>发布后的案例会参与相似问题匹配，并作为 Qwen3.5 的受控上下文。</p></div><el-button type="primary" @click="openEditor('example')">新增案例</el-button></div>
        <TrainingDataTable :rows="state.examples" :columns="columns.examples" action-label="发布" :action-visible="row => row.status !== 'PUBLISHED'" @action="publishExample" />
      </el-tab-pane>

      <el-tab-pane label="黄金评测" name="golden">
        <div class="pane-heading"><div><h2>黄金问题评测</h2><p>使用标准结果持续度量准确率，避免仅凭感觉调整提示词。</p></div><el-button type="primary" @click="openEditor('golden')">新增黄金问题</el-button></div>
        <TrainingDataTable :rows="state.golden" :columns="columns.golden" action-label="立即评测" @action="evaluateGolden" />
      </el-tab-pane>

      <el-tab-pane label="用户反馈" name="feedback">
        <div class="pane-heading"><div><h2>问答纠错闭环</h2><p>审核用户提交的正确 SQL，并转为待发布的标准案例。</p></div></div>
        <TrainingDataTable :rows="state.feedback" :columns="columns.feedback" action-label="转为案例" @action="promoteFeedback" />
      </el-tab-pane>
    </el-tabs>
  </div>
  <TrainingEditorDialog v-model="dialogVisible" :resource="editorResource" :saving="saving" @save="saveResource" />
</template>

<style scoped>
.training-page{max-width:1500px}.training-tabs{min-height:650px;padding:10px 25px 30px;border:1px solid var(--border);border-radius:var(--radius-lg);background:var(--surface);box-shadow:var(--shadow-xs)}.training-tabs :deep(.el-tabs__header){margin-bottom:25px}.pane-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:8px 0 22px}.pane-heading h2{margin:0 0 7px;font-size:18px}.pane-heading p{margin:0;color:var(--text-muted);font-size:13px}@media(max-width:650px){.training-tabs{padding:7px 14px 22px}.pane-heading{align-items:flex-start;flex-direction:column}}
</style>
