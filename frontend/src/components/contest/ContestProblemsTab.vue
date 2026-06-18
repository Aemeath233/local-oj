<template>
  <div class="panel">
    <el-table :data="problems" row-key="id" @row-click="openProblem">
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.id)" size="small" effect="light">
            {{ statusLabel(row.id) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sequenceCode" label="序号" width="90" align="center" />
      <el-table-column prop="title" label="题目名称" min-width="260">
        <template #default="{ row }">
          <div class="problem-title-cell">
            <span class="title-text">{{ row.title }}</span>
            <span class="slug-text">{{ row.slug }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="difficulty" label="难度" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.difficulty }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通过比例" width="180" align="center">
        <template #default="{ row }">
          <div class="progress-wrapper">
            <span class="ratio-text">{{ row.acceptedCount }} / {{ row.submissionCount }}</span>
            <el-progress
              :percentage="row.submissionCount > 0 ? Math.round((row.acceptedCount / row.submissionCount) * 100) : 0"
              :show-text="false"
              stroke-width="4"
              status="success"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column width="100" align="right">
        <template #default="{ row }">
          <el-button circle :icon="ArrowRight" @click.stop="openProblem(row)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import type { ContestProblemDetail } from '../../types'

const props = defineProps<{
  problems: ContestProblemDetail[]
  contestId: number
  solvedProblemIds: Set<number>
  attemptedProblemIds: Set<number>
}>()

const router = useRouter()

function openProblem(row: ContestProblemDetail) {
  router.push(`/contests/${props.contestId}/problems/${row.id}`)
}

function statusLabel(problemId: number) {
  if (props.solvedProblemIds.has(problemId)) return '已通过'
  if (props.attemptedProblemIds.has(problemId)) return '尝试过'
  return '未尝试'
}

function statusType(problemId: number) {
  if (props.solvedProblemIds.has(problemId)) return 'success'
  if (props.attemptedProblemIds.has(problemId)) return 'warning'
  return 'info'
}
</script>

<style scoped>
.problem-title-cell {
  display: flex;
  flex-direction: column;
}
.title-text {
  font-weight: 500;
  color: var(--text-primary);
}
.slug-text {
  font-size: 12px;
  color: var(--text-muted);
}
.progress-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ratio-text {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
