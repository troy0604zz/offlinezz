<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DataRow, TrainingResource } from '../../types/training'

const props = defineProps<{ modelValue: boolean; resource: TrainingResource | null; saving: boolean; row?: DataRow | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [resource: TrainingResource, form: DataRow] }>()
const form = ref<Record<string, unknown>>({})
const editing = computed(() => Boolean(props.row?.id))
const names: Record<TrainingResource, string> = { document: '知识文档', schema: 'DDL / Schema', metric: '业务指标', relation: '表关系', synonym: '同义词', example: '标准 SQL 案例', golden: '黄金问题' }
const title = computed(() => `${editing.value ? '编辑' : '新增'}${props.resource ? names[props.resource] : ''}`)
const defaults: Record<TrainingResource, Record<string, unknown>> = {
  document: { fileName: '', status: 'PUBLISHED' },
  schema: { name: '', dialect: 'Oracle 19c', ddlText: '', description: '' },
  metric: { code: '', name: '', description: '', expressionSql: '', baseTable: '' },
  relation: { leftTable: '', rightTable: '', joinType: 'LEFT', joinCondition: '', cardinality: 'ONE_TO_MANY' },
  synonym: { businessTerm: '', synonyms: '', targetExpression: '' },
  example: { question: '', sql: '', explanation: '' },
  golden: { question: '', expectedSql: '', expectedResultJson: '' },
}
const rowKeys: Record<TrainingResource, Record<string, string>> = {
  document: { fileName: 'file_name', status: 'status' },
  schema: { name: 'name', dialect: 'dialect', ddlText: 'ddl_text', description: 'description' },
  metric: { code: 'code', name: 'name', description: 'description', expressionSql: 'expression_sql', baseTable: 'base_table' },
  relation: { leftTable: 'left_table', rightTable: 'right_table', joinType: 'join_type', joinCondition: 'join_condition', cardinality: 'cardinality' },
  synonym: { businessTerm: 'business_term', synonyms: 'synonyms', targetExpression: 'target_expression' },
  example: { question: 'question', sql: 'sql_text', explanation: 'explanation' },
  golden: { question: 'question', expectedSql: 'expected_sql', expectedResultJson: 'expected_result_json' },
}

watch(() => [props.modelValue, props.resource, props.row], () => {
  if (!props.modelValue || !props.resource) return
  const value = { ...defaults[props.resource] }
  if (props.row) Object.entries(rowKeys[props.resource]).forEach(([target, source]) => { if (props.row?.[source] != null) value[target] = props.row[source] })
  form.value = value
}, { deep: true })
function save(): void { if (props.resource) emit('save', props.resource, form.value) }
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(720px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top">
      <template v-if="resource==='document'">
        <el-form-item label="显示名称"><el-input v-model="form.fileName" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="已发布" value="PUBLISHED" /><el-option label="已停用" value="DISABLED" /></el-select></el-form-item>
      </template>
      <template v-else-if="resource==='schema'">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="数据库方言"><el-select v-model="form.dialect"><el-option label="Oracle 19c" value="Oracle 19c" /></el-select></el-form-item>
        <el-form-item label="完整 DDL"><el-input v-model="form.ddlText" type="textarea" :rows="12" /></el-form-item><el-form-item label="业务说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </template>
      <template v-else-if="resource==='metric'">
        <div class="form-grid"><el-form-item label="指标编码"><el-input v-model="form.code" /></el-form-item><el-form-item label="业务名称"><el-input v-model="form.name" /></el-form-item></div>
        <el-form-item label="指标说明"><el-input v-model="form.description" /></el-form-item><el-form-item label="SQL 表达式"><el-input v-model="form.expressionSql" type="textarea" :rows="5" /></el-form-item><el-form-item label="基础表"><el-input v-model="form.baseTable" /></el-form-item>
      </template>
      <template v-else-if="resource==='relation'">
        <div class="form-grid"><el-form-item label="左表"><el-input v-model="form.leftTable" /></el-form-item><el-form-item label="右表"><el-input v-model="form.rightTable" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="Join 类型"><el-select v-model="form.joinType"><el-option label="INNER" value="INNER" /><el-option label="LEFT" value="LEFT" /></el-select></el-form-item><el-form-item label="基数"><el-select v-model="form.cardinality"><el-option label="一对多" value="ONE_TO_MANY" /><el-option label="多对一" value="MANY_TO_ONE" /><el-option label="一对一" value="ONE_TO_ONE" /></el-select></el-form-item></div>
        <el-form-item label="关联条件"><el-input v-model="form.joinCondition" /></el-form-item>
      </template>
      <template v-else-if="resource==='synonym'">
        <el-form-item label="标准业务词"><el-input v-model="form.businessTerm" /></el-form-item><el-form-item label="同义表达（逗号分隔）"><el-input v-model="form.synonyms" /></el-form-item><el-form-item label="映射目标"><el-input v-model="form.targetExpression" /></el-form-item>
      </template>
      <template v-else-if="resource==='example'">
        <el-form-item label="标准问题"><el-input v-model="form.question" /></el-form-item><el-form-item label="标准 SQL"><el-input v-model="form.sql" type="textarea" :rows="10" /></el-form-item><el-form-item label="说明"><el-input v-model="form.explanation" /></el-form-item>
      </template>
      <template v-else-if="resource==='golden'">
        <el-form-item label="问题"><el-input v-model="form.question" /></el-form-item><el-form-item label="期望 SQL（可选）"><el-input v-model="form.expectedSql" type="textarea" :rows="5" /></el-form-item><el-form-item label="期望结果 JSON（优先）"><el-input v-model="form.expectedResultJson" type="textarea" :rows="5" /></el-form-item>
      </template>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<style scoped>.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.el-select{width:100%}:deep(textarea){font-family:ui-monospace,SFMono-Regular,Consolas,monospace}@media(max-width:600px){.form-grid{grid-template-columns:1fr;gap:0}}</style>
