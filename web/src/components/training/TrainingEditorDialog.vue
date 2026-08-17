<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DataRow, TrainingResource } from '../../types/training'

const props = defineProps<{ modelValue: boolean; resource: TrainingResource | null; saving: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [resource: TrainingResource, form: DataRow] }>()
const form = ref<Record<string, string>>({})

const title = computed(() => ({ schema: '新增 DDL / Schema', metric: '新增业务指标', relation: '新增表关系', synonym: '新增同义词', example: '新增标准 SQL 案例', golden: '新增黄金问题' } as const)[props.resource || 'schema'])

const defaults: Record<TrainingResource, Record<string, string>> = {
  schema: { domain: 'sales', name: '', dialect: 'Oracle 19c', ddlText: '', description: '' },
  metric: { code: '', name: '', description: '', expressionSql: '', baseTable: '' },
  relation: { leftTable: '', rightTable: '', joinType: 'LEFT', joinCondition: '', cardinality: 'ONE_TO_MANY' },
  synonym: { domain: 'sales', businessTerm: '', synonyms: '', targetExpression: '' },
  example: { domain: 'sales', question: '', sql: '', explanation: '' },
  golden: { domain: 'sales', question: '', expectedSql: '', expectedResultJson: '' },
}

watch(() => [props.modelValue, props.resource], () => {
  if (props.modelValue && props.resource) form.value = { ...defaults[props.resource] }
})

function save(): void {
  if (props.resource) emit('save', props.resource, form.value)
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(700px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <template v-if="resource==='schema'">
        <div class="form-grid"><el-form-item label="数据域"><el-input v-model="form.domain" /></el-form-item><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></div>
        <el-form-item label="数据库方言"><el-select v-model="form.dialect"><el-option label="Oracle 19c" value="Oracle 19c" /></el-select></el-form-item>
        <el-form-item label="DDL"><el-input v-model="form.ddlText" type="textarea" :rows="10" /></el-form-item><el-form-item label="业务说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </template>
      <template v-else-if="resource==='metric'">
        <div class="form-grid"><el-form-item label="指标编码"><el-input v-model="form.code" /></el-form-item><el-form-item label="业务名称"><el-input v-model="form.name" /></el-form-item></div>
        <el-form-item label="指标说明"><el-input v-model="form.description" /></el-form-item><el-form-item label="SQL 表达式"><el-input v-model="form.expressionSql" type="textarea" :rows="5" /></el-form-item><el-form-item label="基础表"><el-input v-model="form.baseTable" /></el-form-item>
      </template>
      <template v-else-if="resource==='relation'">
        <div class="form-grid"><el-form-item label="左表"><el-input v-model="form.leftTable" /></el-form-item><el-form-item label="右表"><el-input v-model="form.rightTable" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="Join 类型"><el-select v-model="form.joinType"><el-option label="INNER" value="INNER" /><el-option label="LEFT" value="LEFT" /></el-select></el-form-item><el-form-item label="基数"><el-select v-model="form.cardinality"><el-option label="一对多" value="ONE_TO_MANY" /><el-option label="多对一" value="MANY_TO_ONE" /><el-option label="一对一" value="ONE_TO_ONE" /></el-select></el-form-item></div>
        <el-form-item label="关联条件"><el-input v-model="form.joinCondition" placeholder="例如：customer.customer_id = sales_order.customer_id" /></el-form-item>
      </template>
      <template v-else-if="resource==='synonym'">
        <el-form-item label="数据域"><el-input v-model="form.domain" /></el-form-item><el-form-item label="标准业务词"><el-input v-model="form.businessTerm" /></el-form-item><el-form-item label="同义表达（逗号分隔）"><el-input v-model="form.synonyms" /></el-form-item><el-form-item label="映射目标"><el-input v-model="form.targetExpression" /></el-form-item>
      </template>
      <template v-else-if="resource==='example'">
        <el-form-item label="数据域"><el-input v-model="form.domain" /></el-form-item><el-form-item label="标准问题"><el-input v-model="form.question" /></el-form-item><el-form-item label="标准 SQL"><el-input v-model="form.sql" type="textarea" :rows="10" /></el-form-item><el-form-item label="说明"><el-input v-model="form.explanation" /></el-form-item>
      </template>
      <template v-else-if="resource==='golden'">
        <el-form-item label="数据域"><el-input v-model="form.domain" /></el-form-item><el-form-item label="问题"><el-input v-model="form.question" /></el-form-item><el-form-item label="期望 SQL（可选）"><el-input v-model="form.expectedSql" type="textarea" :rows="5" /></el-form-item><el-form-item label="期望结果 JSON（优先）"><el-input v-model="form.expectedResultJson" type="textarea" :rows="5" /></el-form-item>
      </template>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.el-select{width:100%}:deep(textarea){font-family:ui-monospace,SFMono-Regular,Consolas,monospace}@media(max-width:600px){.form-grid{grid-template-columns:1fr;gap:0}}</style>
