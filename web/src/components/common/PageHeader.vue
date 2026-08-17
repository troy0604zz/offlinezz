<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="page-header__actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header{min-height:112px;padding:26px 40px;background:var(--surface);border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:24px}.page-header h1{font-size:26px;line-height:1.25;margin:0 0 7px;color:var(--text-strong)}.page-header p{margin:0;color:var(--text-muted);font-size:14px}.page-header__actions{display:flex;align-items:center;gap:10px}@media(max-width:760px){.page-header{padding:20px 18px;align-items:flex-start;flex-direction:column}.page-header h1{font-size:22px}}
</style>
