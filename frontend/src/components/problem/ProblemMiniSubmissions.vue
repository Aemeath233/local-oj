<template>
  <div class="mini-submissions-panel">
    <div class="mini-panel-header">
      <span>我的提交记录</span>
      <el-button :icon="Refresh" link size="small" :loading="submissionsLoading" @click="emit('refresh')">刷新</el-button>
    </div>
    <div v-if="submissionsLoading && submissions.length === 0" class="mini-loading">
      <el-icon class="is-loading" style="margin-right: 6px;"><Loading /></el-icon> 加载中...
    </div>
    <div v-else-if="submissions.length === 0" class="mini-empty">
      暂无提交记录
    </div>
    <div v-else class="mini-submissions-list">
      <transition-group name="list">
        <div
          v-for="sub in submissions"
          :key="sub.id"
          class="mini-submission-item"
          @click="emit('click-submission', sub)"
        >
          <div class="mini-sub-left">
            <span class="mini-sub-id">#{{ sub.id }}</span>
            <VerdictTag :status="sub.status" :verdict="sub.verdict" />
          </div>
          <div class="mini-sub-right">
            <span class="mini-sub-meta" v-if="sub.status === 'FINISHED'">
              {{ sub.timeMs ?? 0 }}ms / {{ sub.memoryKb ?? 0 }}KB
            </span>
            <span class="mini-sub-time">{{ formatRelativeTime(sub.createdAt) }}</span>
          </div>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Refresh, Loading } from '@element-plus/icons-vue'
import { formatRelativeTime } from '../../utils/time'
import type { SubmissionSummary } from '../../types'
import VerdictTag from '../VerdictTag.vue'

defineProps<{
  submissions: SubmissionSummary[]
  submissionsLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'click-submission', sub: SubmissionSummary): void
}>()
</script>

<style scoped>
.mini-submissions-panel {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  margin-top: 14px;
  overflow: hidden;
  box-sizing: border-box;
  text-align: left;
}

.mini-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-app);
  border-bottom: 1px solid var(--border-color);
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-primary);
}

.mini-loading {
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.mini-empty {
  padding: 24px;
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.mini-submissions-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 6px;
  box-sizing: border-box;
}

.mini-submission-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 4px;
}

.mini-submission-item:last-child {
  margin-bottom: 0;
}

.mini-submission-item:hover {
  background: var(--bg-app);
}

.mini-sub-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mini-sub-id {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-muted);
  font-family: var(--font-mono), monospace;
}

.mini-sub-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.mini-sub-meta {
  font-size: 0.78rem;
  color: var(--text-secondary);
  font-family: var(--font-mono), monospace;
  font-weight: 600;
}

.mini-sub-time {
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* Transitions */
.list-enter-active,
.list-leave-active {
  transition: all 0.3s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.list-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
