<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>比赛列表</h1>
        <p>参与在线竞赛与算法测试</p>
      </div>
      <RouterLink v-if="auth.isAdmin" to="/admin/contests">
        <el-button :icon="ArrowRight" type="primary">管理比赛</el-button>
      </RouterLink>
    </div>

    <!-- Filter tabs -->
    <div class="contest-filter-bar">
      <div class="filter-tabs">
        <button
          v-for="tab in filterTabs"
          :key="tab.value"
          :class="['filter-tab', { active: activeFilter === tab.value }]"
          @click="activeFilter = tab.value"
        >
          <span class="tab-dot" :style="{ background: tab.color }" />
          {{ tab.label }}
          <span v-if="countByStatus(tab.value) > 0" class="tab-count">{{ countByStatus(tab.value) }}</span>
        </button>
      </div>
    </div>

    <div v-loading="loading" class="contest-list-container">
      <el-empty v-if="filteredContests.length === 0 && !loading" description="暂无比赛" />

      <TransitionGroup name="contest-item" tag="div" class="contest-list">
        <div
          v-for="c in filteredContests"
          :key="c.id"
          :class="['contest-row', `status-${getContestStatus(c).toLowerCase()}`]"
          @click="enterContest(c)"
        >
          <!-- Left accent strip -->
          <div class="accent-strip" />

          <!-- Main content -->
          <div class="contest-main">
            <div class="contest-top-line">
              <h3 class="contest-title">{{ c.title }}</h3>
              <div class="contest-badges">
                <span :class="['status-badge', `badge-${getContestStatus(c).toLowerCase()}`]">
                  <span class="badge-dot" />
                  {{ statusLabel(c) }}
                </span>
                <span class="type-badge">{{ c.type }}</span>
              </div>
            </div>

            <div class="contest-meta">
              <div class="meta-item">
                <svg class="meta-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clip-rule="evenodd"/>
                </svg>
                <span class="meta-label">开始</span>
                <span class="meta-value">{{ formatTime(c.startTime) }}</span>
              </div>
              <div class="meta-divider" />
              <div class="meta-item">
                <svg class="meta-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd"/>
                </svg>
                <span class="meta-label">时长</span>
                <span class="meta-value">{{ getDurationStr(c.startTime, c.endTime) }}</span>
              </div>
              <div class="meta-divider" />
              <div class="meta-item">
                <svg class="meta-icon" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clip-rule="evenodd"/>
                </svg>
                <span class="meta-label">结束</span>
                <span class="meta-value">{{ formatTime(c.endTime) }}</span>
              </div>
            </div>

            <!-- Running progress bar -->
            <div v-if="getContestStatus(c) === 'RUNNING'" class="live-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: getProgressPercentage(c) + '%' }" />
              </div>
              <div class="progress-labels">
                <span>已过 {{ getElapsedStr(c.startTime) }}</span>
                <span>剩余 {{ getRemainingStr(c.endTime) }}</span>
              </div>
            </div>
          </div>

          <!-- Right action area -->
          <div class="contest-action">
            <button class="action-btn">
              {{ getContestStatus(c) === 'UPCOMING' ? '查看' : '进入' }}
              <svg viewBox="0 0 20 20" fill="currentColor" class="action-arrow">
                <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/>
              </svg>
            </button>
          </div>
        </div>
      </TransitionGroup>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { fetchContests } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { Contest } from '../types'

const router = useRouter()
const auth = useAuthStore()
const contests = ref<Contest[]>([])
const loading = ref(false)
const nowRef = ref(new Date())
const activeFilter = ref<'ALL' | 'RUNNING' | 'UPCOMING' | 'FINISHED'>('ALL')
let timerId: number | undefined

const filterTabs = [
  { value: 'ALL' as const, label: '全部', color: '#6366f1' },
  { value: 'RUNNING' as const, label: '进行中', color: '#22c55e' },
  { value: 'UPCOMING' as const, label: '未开始', color: '#3b82f6' },
  { value: 'FINISHED' as const, label: '已结束', color: '#94a3b8' }
]

const filteredContests = computed(() => {
  if (activeFilter.value === 'ALL') return contests.value
  return contests.value.filter(c => getContestStatus(c) === activeFilter.value)
})

function countByStatus(status: string) {
  if (status === 'ALL') return contests.value.length
  return contests.value.filter(c => getContestStatus(c) === status).length
}

onMounted(() => {
  load()
  timerId = window.setInterval(() => {
    nowRef.value = new Date()
  }, 10000)
})

onUnmounted(() => {
  if (timerId) {
    window.clearInterval(timerId)
  }
})

async function load() {
  loading.value = true
  try {
    contests.value = await fetchContests()
  } finally {
    loading.value = false
  }
}

function enterContest(c: Contest) {
  router.push(`/contests/${c.id}`)
}

function getContestStatus(c: Contest): 'UPCOMING' | 'RUNNING' | 'FINISHED' {
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  const now = nowRef.value.getTime()

  if (now < start) return 'UPCOMING'
  if (now > end) return 'FINISHED'
  return 'RUNNING'
}

function statusLabel(c: Contest) {
  const status = getContestStatus(c)
  if (status === 'UPCOMING') return '未开始'
  if (status === 'RUNNING') return '进行中'
  return '已结束'
}

function formatTime(timeStr: string) {
  const date = new Date(timeStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hour}:${min}`
}

function getDurationStr(startStr: string, endStr: string) {
  const start = new Date(startStr).getTime()
  const end = new Date(endStr).getTime()
  const diffMs = end - start
  const diffHours = Math.floor(diffMs / 3600000)
  const diffMins = Math.round((diffMs % 3600000) / 60000)

  if (diffHours === 0) return `${diffMins} 分钟`
  if (diffMins === 0) return `${diffHours} 小时`
  return `${diffHours} 小时 ${diffMins} 分钟`
}

function getProgressPercentage(c: Contest) {
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  const now = nowRef.value.getTime()

  if (now <= start) return 0
  if (now >= end) return 100
  return Math.round(((now - start) / (end - start)) * 100)
}

function getElapsedStr(startStr: string) {
  const start = new Date(startStr).getTime()
  const now = nowRef.value.getTime()
  const diff = now - start

  if (diff <= 0) return '0 分钟'
  const hours = Math.floor(diff / 3600000)
  const mins = Math.floor((diff % 3600000) / 60000)
  if (hours === 0) return `${mins} 分钟`
  return `${hours} 小时 ${mins} 分钟`
}

function getRemainingStr(endStr: string) {
  const end = new Date(endStr).getTime()
  const now = nowRef.value.getTime()
  const diff = end - now

  if (diff <= 0) return '0 分钟'
  const hours = Math.floor(diff / 3600000)
  const mins = Math.floor((diff % 3600000) / 60000)
  if (hours === 0) return `${mins} 分钟`
  return `${hours} 小时 ${mins} 分钟`
}
</script>

<style scoped>
/* ===== Filter bar ===== */
.contest-filter-bar {
  margin-bottom: 20px;
}

.filter-tabs {
  display: flex;
  gap: 6px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 4px;
  width: fit-content;
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: none;
  background: transparent;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
}

.filter-tab:hover {
  background: #f1f5f9;
  color: #334155;
}

.filter-tab.active {
  background: #f0f4ff;
  color: #4f46e5;
  font-weight: 600;
}

.tab-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tab-count {
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}

.filter-tab.active .tab-count {
  background: #ddd6fe;
  color: #4f46e5;
}

/* ===== Contest list ===== */
.contest-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contest-row {
  display: flex;
  align-items: stretch;
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.contest-row:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
  transform: translateX(4px);
}

.contest-row:active {
  transform: translateX(2px);
}

/* ===== Left accent strip ===== */
.accent-strip {
  width: 4px;
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.contest-row:hover .accent-strip {
  width: 5px;
}

.status-running .accent-strip {
  background: linear-gradient(180deg, #22c55e, #16a34a);
}

.status-upcoming .accent-strip {
  background: linear-gradient(180deg, #3b82f6, #2563eb);
}

.status-finished .accent-strip {
  background: linear-gradient(180deg, #cbd5e1, #94a3b8);
}

/* ===== Main content area ===== */
.contest-main {
  flex: 1;
  padding: 18px 20px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.contest-top-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.contest-title {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #1e293b;
  line-height: 1.4;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contest-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* ===== Status badge ===== */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.badge-running {
  background: #dcfce7;
  color: #15803d;
}

.badge-running .badge-dot {
  background: #22c55e;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.25);
  animation: pulse-green 2s ease-in-out infinite;
}

@keyframes pulse-green {
  0%, 100% { box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.25); }
  50% { box-shadow: 0 0 0 5px rgba(34, 197, 94, 0.1); }
}

.badge-upcoming {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge-upcoming .badge-dot {
  background: #3b82f6;
}

.badge-finished {
  background: #f1f5f9;
  color: #64748b;
}

.badge-finished .badge-dot {
  background: #94a3b8;
}

.type-badge {
  padding: 3px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  background: #f8fafc;
  color: #475569;
  border: 1px solid #e2e8f0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

/* ===== Meta row ===== */
.contest-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #64748b;
}

.meta-icon {
  width: 14px;
  height: 14px;
  color: #94a3b8;
  flex-shrink: 0;
}

.meta-label {
  color: #94a3b8;
  font-weight: 500;
  font-size: 12px;
}

.meta-value {
  color: #475569;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.meta-divider {
  width: 1px;
  height: 14px;
  background: #e2e8f0;
  flex-shrink: 0;
}

/* ===== Live progress bar ===== */
.live-progress {
  margin-top: 2px;
}

.progress-track {
  height: 4px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #22c55e, #4ade80);
  border-radius: 4px;
  transition: width 1s ease;
  position: relative;
}

.progress-fill::after {
  content: '';
  position: absolute;
  right: 0;
  top: -1px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

/* ===== Right action ===== */
.contest-action {
  display: flex;
  align-items: center;
  padding: 0 20px;
  flex-shrink: 0;
  border-left: 1px solid #f1f5f9;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 18px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.action-btn:hover {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #fff;
}

.action-arrow {
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
}

.contest-row:hover .action-arrow {
  transform: translateX(2px);
}

.status-running .action-btn {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #15803d;
}

.status-running .action-btn:hover {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}

/* ===== Transition animations ===== */
.contest-item-enter-active {
  transition: all 0.35s ease;
}

.contest-item-leave-active {
  transition: all 0.25s ease;
}

.contest-item-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.contest-item-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* ===== Responsive ===== */
@media (max-width: 640px) {
  .contest-row {
    flex-direction: column;
  }

  .accent-strip {
    width: 100% !important;
    height: 3px;
  }

  .contest-action {
    border-left: none;
    border-top: 1px solid #f1f5f9;
    padding: 12px 20px;
    justify-content: stretch;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }

  .contest-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .meta-divider {
    display: none;
  }
}
</style>
