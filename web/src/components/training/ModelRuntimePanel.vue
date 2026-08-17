<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage } from '../../services/http'
import { platformApi } from '../../services/platform-api'
import type { ModelProvider, ModelRuntime, ModelRuntimeUpdate } from '../../types/platform'

const loading = ref(false)
const saving = ref(false)
const runtime = ref<ModelRuntime | null>(null)
const form = reactive<ModelRuntimeUpdate>({ chatProvider: 'ollama', embeddingProvider: 'ollama',
  qwenChatModel: 'qwen3.8-max', qwenEmbeddingModel: 'qwen3.7-text-embedding', reindexKnowledge: true })

const qwenSelected = computed(() => form.chatProvider === 'qwen-api' || form.embeddingProvider === 'qwen-api')
const embeddingWillChange = computed(() => runtime.value
  ? form.embeddingProvider !== runtime.value.activeEmbeddingProvider
    || (form.embeddingProvider === 'qwen-api' && form.qwenEmbeddingModel !== runtime.value.qwenEmbeddingModel)
  : false)

async function load(): Promise<void> {
  loading.value = true
  try {
    runtime.value = (await platformApi.modelRuntime()).data
    Object.assign(form, { chatProvider: runtime.value.activeChatProvider,
      embeddingProvider: runtime.value.activeEmbeddingProvider, qwenChatModel: runtime.value.qwenChatModel,
      qwenEmbeddingModel: runtime.value.qwenEmbeddingModel, reindexKnowledge: true })
  } catch (error) { ElMessage.error(apiErrorMessage(error, '模型配置加载失败')) }
  finally { loading.value = false }
}

async function save(): Promise<void> {
  if (qwenSelected.value && !runtime.value?.qwenApiConfigured) {
    ElMessage.warning('请先在后端服务器配置 DASHSCOPE_API_KEY，再切换到千问官方 API')
    return
  }
  saving.value = true
  try {
    const result = (await platformApi.updateModelRuntime({ ...form })).data
    runtime.value = result.runtime
    window.dispatchEvent(new CustomEvent('model-runtime-changed'))
    ElMessage.success(result.knowledgeReindexed ? '模型已切换，知识索引已重建' : '模型配置已生效')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '模型切换失败')) }
  finally { saving.value = false }
}

function providerLabel(provider: ModelProvider): string {
  return provider === 'ollama' ? 'Ollama 本地模型' : '千问官方 API'
}

onMounted(load)
</script>

<template>
  <section class="runtime-panel" v-loading="loading">
    <div class="runtime-heading"><div><h2>模型运行配置</h2><p>聊天模型负责生成 SQL，向量模型负责检索文档知识；两者可以分别选择。</p></div><el-button :loading="loading" @click="load">重新读取</el-button></div>
    <el-alert v-if="runtime && !runtime.qwenApiConfigured" type="warning" :closable="false" show-icon>
      <template #title>千问官方 API 尚未配置</template>
      API Key 只允许在后端服务器通过 DASHSCOPE_API_KEY 环境变量配置，不会在浏览器中录入或回显。
    </el-alert>
    <el-alert v-else-if="runtime" type="success" :closable="false" show-icon title="千问官方 API 已安全配置，可随时切换。" />
    <div class="current-strip" v-if="runtime">
      <span>当前聊天：<strong>{{ providerLabel(runtime.activeChatProvider) }}</strong> / {{ runtime.activeChatModel }}</span>
      <span>当前向量：<strong>{{ providerLabel(runtime.activeEmbeddingProvider) }}</strong> / {{ runtime.activeEmbeddingModel }}</span>
      <span>维度：{{ runtime.vectorSize }}</span>
    </div>
    <div class="config-grid">
      <div class="config-card">
        <div class="config-card__title"><span>1</span><div><h3>聊天 / SQL 生成模型</h3><p>切换后，下一个没有命中标准 SQL 的问题立即使用新模型。</p></div></div>
        <el-radio-group v-model="form.chatProvider" class="provider-options">
          <el-radio-button value="ollama">Ollama 本地</el-radio-button><el-radio-button value="qwen-api" :disabled="runtime ? !runtime.qwenApiConfigured : true">千问官方 API</el-radio-button>
        </el-radio-group>
        <div class="model-value" v-if="runtime && form.chatProvider === 'ollama'"><label>本地模型</label><strong>{{ runtime.ollamaChatModel }}</strong></div>
        <el-form-item v-else label="官方模型标识"><el-input v-model="form.qwenChatModel" placeholder="qwen3.8-max" /></el-form-item>
      </div>
      <div class="config-card">
        <div class="config-card__title"><span>2</span><div><h3>知识检索向量模型</h3><p>不同向量模型使用不同 Qdrant 集合，不会污染现有索引。</p></div></div>
        <el-radio-group v-model="form.embeddingProvider" class="provider-options">
          <el-radio-button value="ollama">Ollama 本地</el-radio-button><el-radio-button value="qwen-api" :disabled="runtime ? !runtime.qwenApiConfigured : true">千问官方 API</el-radio-button>
        </el-radio-group>
        <div class="model-value" v-if="runtime && form.embeddingProvider === 'ollama'"><label>本地模型</label><strong>{{ runtime.ollamaEmbeddingModel }}</strong></div>
        <el-form-item v-else label="官方模型标识"><el-input v-model="form.qwenEmbeddingModel" placeholder="qwen3.7-text-embedding" /></el-form-item>
        <el-checkbox v-if="embeddingWillChange" v-model="form.reindexKnowledge">切换时重新索引已发布文档（推荐）</el-checkbox>
      </div>
    </div>
    <div class="runtime-footer"><p v-if="runtime">API 地址：{{ runtime.qwenApiHost }} · Key 不会返回到前端</p><el-button type="primary" size="large" :loading="saving" @click="save">保存并应用</el-button></div>
  </section>
</template>

<style scoped>
.runtime-panel{min-height:560px}.runtime-heading{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin:8px 0 20px}.runtime-heading h2{margin:0 0 7px;font-size:18px}.runtime-heading p,.config-card p,.runtime-footer p{margin:0;color:var(--text-muted);font-size:13px}.current-strip{display:flex;gap:22px;flex-wrap:wrap;margin:18px 0;padding:14px 16px;border-radius:10px;background:#f5f8fc;color:var(--text-muted);font-size:13px}.current-strip strong{color:var(--text)}.config-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}.config-card{padding:22px;border:1px solid var(--border);border-radius:14px;background:#fff}.config-card__title{display:flex;gap:12px;margin-bottom:20px}.config-card__title>span{width:28px;height:28px;display:grid;place-items:center;border-radius:8px;background:var(--primary-soft);color:var(--primary);font-weight:700}.config-card h3{margin:1px 0 5px;font-size:15px}.provider-options{margin-bottom:20px}.model-value{display:grid;gap:7px;padding:12px 14px;border-radius:9px;background:#f7f9fc}.model-value label{font-size:12px;color:var(--text-muted)}.model-value strong{font-size:14px;overflow-wrap:anywhere}.runtime-footer{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-top:22px;padding-top:18px;border-top:1px solid var(--border)}@media(max-width:850px){.config-grid{grid-template-columns:1fr}.runtime-footer{align-items:flex-start;flex-direction:column}}
</style>
