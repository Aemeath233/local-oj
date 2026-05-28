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
  selfTest?: boolean
}>()

const isPendingOrRunning = computed(() => {
  return props.status === 'PENDING' || props.status === 'RUNNING'
})

const label = computed(() => {
  if (props.status === 'PENDING') return '排队中...'
  if (props.status === 'RUNNING') return '评测中...'
  if (props.selfTest && props.verdict === 'AC') return '运行成功'
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
  padding: 6px 14px;
  border-radius: 99px; /* Capsule shape */
  font-size: 0.8rem;
  font-weight: 700;
  border: 1px solid transparent;
  backdrop-filter: blur(8px);
  min-height: 28px;
  line-height: 1;
  letter-spacing: 0.04em;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: status-pop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.status-pending {
  background-color: rgba(241, 245, 249, 0.6);
  border-color: rgba(203, 213, 225, 0.8);
  color: #475569;
  box-shadow: 0 0 12px rgba(100, 116, 139, 0.08);
}

.status-running {
  background-color: rgba(239, 246, 255, 0.7);
  border-color: rgba(147, 197, 253, 0.85);
  color: #1d4ed8;
  box-shadow: 0 0 16px rgba(37, 99, 235, 0.15);
  animation: breathing 1.8s infinite ease-in-out, status-pop 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.verdict-ac {
  background-color: rgba(236, 253, 245, 0.75);
  border-color: rgba(167, 243, 208, 0.9);
  color: #047857;
  box-shadow: 0 0 16px rgba(16, 185, 129, 0.2);
}

.verdict-wa {
  background-color: rgba(254, 242, 242, 0.75);
  border-color: rgba(254, 202, 202, 0.9);
  color: #b91c1c;
  box-shadow: 0 0 16px rgba(239, 68, 68, 0.15);
}

.verdict-ce {
  background-color: rgba(255, 251, 235, 0.75);
  border-color: rgba(253, 230, 138, 0.9);
  color: #b45309;
  box-shadow: 0 0 16px rgba(245, 158, 11, 0.15);
}

.verdict-other {
  background-color: rgba(255, 241, 242, 0.75);
  border-color: rgba(255, 228, 230, 0.9);
  color: #be123c;
  box-shadow: 0 0 16px rgba(225, 29, 72, 0.15);
}

.spin-icon {
  animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes breathing {
  0%, 100% { 
    box-shadow: 0 0 8px rgba(37, 99, 235, 0.1); 
    transform: scale(0.98); 
  }
  50% { 
    box-shadow: 0 0 20px rgba(37, 99, 235, 0.3); 
    transform: scale(1.02); 
  }
}

@keyframes status-pop {
  0% {
    transform: scale(0.85);
    opacity: 0.5;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
