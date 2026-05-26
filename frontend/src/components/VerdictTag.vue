<template>
  <span :class="['verdict-badge-item', customClass]">
    <el-icon v-if="isPendingOrRunning" class="is-loading spin-icon"><Loading /></el-icon>
    <span :style="isPendingOrRunning ? 'margin-left: 6px' : ''">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { SubmissionStatus, Verdict } from '../types'

const props = defineProps<{
  status?: SubmissionStatus
  verdict?: Verdict
}>()

const isPendingOrRunning = computed(() => {
  return props.status === 'PENDING' || props.status === 'RUNNING'
})

const label = computed(() => {
  if (props.status === 'PENDING') return '排队中...'
  if (props.status === 'RUNNING') return '评测中...'
  return props.verdict ?? 'WAITING'
})

const customClass = computed(() => {
  if (props.status === 'PENDING') return 'status-pending'
  if (props.status === 'RUNNING') return 'status-running'
  if (props.verdict === 'AC') return 'verdict-ac'
  if (props.verdict === 'WA') return 'verdict-wa'
  if (props.verdict === 'CE') return 'verdict-ce'
  return 'verdict-other'
})
</script>

<style scoped>
.verdict-badge-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 700;
  border: 1px solid transparent;
  min-height: 24px;
  line-height: 1;
  letter-spacing: 0.02em;
}

.status-pending {
  background-color: #f1f5f9;
  border-color: #cbd5e1;
  color: #64748b;
}

.status-running {
  background-color: #eff6ff;
  border-color: #bfdbfe;
  color: #2563eb;
  animation: breathing 2s infinite ease-in-out;
}

.verdict-ac {
  background-color: #ecfdf5;
  border-color: #a7f3d0;
  color: #059669;
}

.verdict-wa {
  background-color: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

.verdict-ce {
  background-color: #fffbeb;
  border-color: #fde68a;
  color: #d97706;
}

.verdict-other {
  background-color: #fff1f2;
  border-color: #ffe4e6;
  color: #e11d48;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes breathing {
  0%, 100% { opacity: 0.8; transform: scale(0.97); }
  50% { opacity: 1; transform: scale(1); }
}
</style>
