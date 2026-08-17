<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'
</script>

<template>
  <div class="forbidden page-container">
    <el-icon :size="42"><WarningFilled /></el-icon>
    <h1>当前账号没有访问权限</h1>
    <p>该功能未包含在你的角色权限中。你可以返回已有模块，或联系管理员调整角色。</p>
    <el-button type="primary" @click="$router.push('/')">返回工作台</el-button>
  </div>
</template>

<style scoped>
.forbidden{min-height:calc(100vh - 64px);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}.forbidden .el-icon{color:var(--warning)}.forbidden h1{margin:20px 0 10px;font-size:24px}.forbidden p{max-width:520px;margin:0 0 24px;color:var(--text-muted);line-height:1.7}
</style>
