<script setup lang="ts">
defineProps<{ modelValue: string; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; submit: [] }>()

const examples = [
  '查询2026年华东区域每月净销售额',
  '客户净销售额排名 Top10',
  '各区域销售额是多少？',
  '今年成交客户数量是多少？',
]
</script>

<template>
  <section>
    <div class="composer">
      <textarea :value="modelValue" placeholder="例如：查询 2026 年华东区域每月净销售额" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" @keydown.ctrl.enter.prevent="emit('submit')" />
      <div class="composer__footer">
        <span>按 Ctrl + Enter 快速提交</span>
        <el-button type="primary" :loading="loading" :disabled="!modelValue.trim()" @click="emit('submit')">开始分析</el-button>
      </div>
    </div>
    <div class="examples" aria-label="示例问题">
      <span>试试这样问</span>
      <button v-for="example in examples" :key="example" @click="emit('update:modelValue', example)">{{ example }}</button>
    </div>
  </section>
</template>

<style scoped>
.composer{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);box-shadow:var(--shadow-sm);overflow:hidden;transition:.18s ease}.composer:focus-within{border-color:#91b4e8;box-shadow:0 0 0 3px #2f6fed12}.composer textarea{width:100%;height:126px;padding:24px;border:0;outline:0;resize:vertical;color:var(--text-strong);font:16px/1.7 inherit}.composer textarea::placeholder{color:#a8b2bf}.composer__footer{min-height:60px;padding:10px 14px 10px 22px;border-top:1px solid var(--border-light);display:flex;align-items:center;justify-content:space-between;color:var(--text-subtle);font-size:12px}.examples{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:14px 0 26px}.examples>span{margin-right:2px;color:var(--text-subtle);font-size:12px}.examples button{padding:7px 12px;border:1px solid var(--border);border-radius:999px;background:var(--surface);color:var(--text-muted);font:12px inherit;cursor:pointer}.examples button:hover{border-color:#9ab7df;color:var(--primary);background:var(--primary-soft)}
</style>
