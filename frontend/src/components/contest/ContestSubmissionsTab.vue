<template>
  <div class="panel">
    <div class="submissions-toolbar">
      <el-button :icon="Refresh" @click="$emit('refresh')" :loading="loading">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="submissions" row-key="id">
      <el-table-column prop="id" label="提交 ID" width="100" />
      <el-table-column v-if="showUserColumn" label="用户" min-width="150">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="20" :src="row.avatarUrl">{{ row.username ? row.username.slice(0, 1).toUpperCase() : 'U' }}</el-avatar>
            <span class="user-display">{{ row.displayName || row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评测状态" width="150">
        <template #default="{ row }">
          <VerdictTag :status="row.status" :verdict="row.verdict" />
        </template>
      </el-table-column>
      <el-table-column label="题目" min-width="180">
        <template #default="{ row }">
          <span>{{ problemCodeMap[row.problemId] }} - {{ problemTitleMap[row.problemId] || row.problemId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="language" label="语言" width="100" />
      <el-table-column label="运行时间" width="110">
        <template #default="{ row }">
          {{ row.status === 'FINISHED' ? `${row.timeMs ?? 0} ms` : '--' }}
        </template>
      </el-table-column>
      <el-table-column label="运行内存" width="110">
        <template #default="{ row }">
          {{ row.status === 'FINISHED' ? `${Math.round((row.memoryKb ?? 0) / 10.24) / 100} MB` : '--' }}
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="180">
        <template #default="{ row }">
          {{ formatFullTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column width="90" align="right">
        <template #default="{ row }">
          <el-button circle :icon="DocumentCopy" @click="$emit('open-detail', row)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { Refresh, DocumentCopy } from '@element-plus/icons-vue'
import VerdictTag from '../VerdictTag.vue'
import type { SubmissionSummary } from '../../types'

const props = defineProps<{
  submissions: SubmissionSummary[]
  loading: boolean
  showUserColumn: boolean
  problemCodeMap: Record<number, string>
  problemTitleMap: Record<number, string>
}>()

defineEmits<{
  (e: 'refresh'): void
  (e: 'open-detail', row: SubmissionSummary): void
}>()

function formatFullTime(timeStr: string) {
  return new Date(timeStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.submissions-toolbar {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: flex-end;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-display {
  font-weight: 500;
  color: var(--text-primary);
}
</style>
