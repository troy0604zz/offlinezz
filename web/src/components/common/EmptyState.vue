<script setup lang="ts">
import { DataAnalysis } from '@element-plus/icons-vue'

defineProps<{ title: string; description: string }>()
</script>

<template>
  <div class="empty-state">
    <el-icon :size="34"><DataAnalysis /></el-icon>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state{min-height:300px;border:1px dashed var(--border-strong);border-radius:var(--radius-lg);background:var(--surface);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:36px}.empty-state .el-icon{color:var(--primary)}.empty-state h3{margin:16px 0 8px;font-size:18px}.empty-state p{max-width:520px;margin:0 0 18px;color:var(--text-muted);line-height:1.7}
</style>
