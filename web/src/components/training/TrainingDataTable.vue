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
  editable?: boolean
  deletable?: boolean
  deletingId?: string | number | null
}>()
defineEmits<{ action: [row: DataRow]; edit: [row: DataRow]; delete: [row: DataRow] }>()

function isDeleting(row: DataRow, deletingId: string | number | null | undefined): boolean {
  return deletingId !== null && deletingId !== undefined && String(row.id) === String(deletingId)
}
</script>

<template>
  <el-table :data="rows" stripe empty-text="暂无数据">
    <el-table-column v-for="column in columns" :key="column.key" :prop="column.key" :label="column.label" :min-width="column.minWidth" :width="column.width" :show-overflow-tooltip="column.overflow">
      <template #default="scope">
        <el-tag v-if="column.key === 'status'" size="small" effect="plain">{{ scope.row[column.key] }}</el-tag>
        <span v-else>{{ scope.row[column.key] }}</span>
      </template>
    </el-table-column>
    <el-table-column v-if="actionLabel || editable || deletable" label="操作" width="210" fixed="right">
      <template #default="scope">
        <el-button v-if="actionLabel && (!actionVisible || actionVisible(scope.row))" link type="primary" :disabled="deletingId !== null && deletingId !== undefined" @click="$emit('action', scope.row)">{{ actionLabel }}</el-button>
        <el-button v-if="editable" link :disabled="deletingId !== null && deletingId !== undefined" @click="$emit('edit', scope.row)">编辑</el-button>
        <el-button v-if="deletable" link type="danger" :loading="isDeleting(scope.row, deletingId)" :disabled="deletingId !== null && deletingId !== undefined && !isDeleting(scope.row, deletingId)" @click="$emit('delete', scope.row)">{{ isDeleting(scope.row, deletingId) ? '删除中' : '删除' }}</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
