<script setup lang="ts">
import type { DataRow } from '../../types/training'

export interface TableColumn {
  key: string
  label: string
  minWidth?: number
  width?: number
  overflow?: boolean
}

defineProps<{
  rows: DataRow[]
  columns: TableColumn[]
  actionLabel?: string
  actionVisible?: (row: DataRow) => boolean
}>()
defineEmits<{ action: [row: DataRow] }>()
</script>

<template>
  <el-table :data="rows" stripe empty-text="暂无数据">
    <el-table-column v-for="column in columns" :key="column.key" :prop="column.key" :label="column.label" :min-width="column.minWidth" :width="column.width" :show-overflow-tooltip="column.overflow">
      <template #default="scope">
        <el-tag v-if="column.key === 'status'" size="small" effect="plain">{{ scope.row[column.key] }}</el-tag>
        <span v-else>{{ scope.row[column.key] }}</span>
      </template>
    </el-table-column>
    <el-table-column v-if="actionLabel" label="操作" width="120" fixed="right">
      <template #default="scope"><el-button v-if="!actionVisible || actionVisible(scope.row)" link type="primary" @click="$emit('action', scope.row)">{{ actionLabel }}</el-button></template>
    </el-table-column>
  </el-table>
</template>
