<template>
  <section class="panel contests-panel" v-loading="loading">
    <div class="panel-header">
      <div class="header-icon-box contest-icon">
        <el-icon><Timer /></el-icon>
      </div>
      <div>
        <h2>近期比赛</h2>
        <p class="muted">最新可见的三场比赛</p>
      </div>
      <RouterLink to="/contests" class="more-link">
        <el-button size="small" circle :icon="ArrowRight" />
      </RouterLink>
    </div>

    <div class="home-contest-list">
      <div v-if="contests.length === 0" class="empty-state">
        <el-empty description="当前无可见比赛" :image-size="40" />
      </div>
      <div
        v-for="c in contests.slice(0, 3)"
        :key="c.id"
        class="home-contest-item"
        @click="router.push(`/contests/${c.id}`)"
      >
        <div class="contest-item-left">
          <span class="contest-type-badge">{{ c.type }}</span>
          <span class="contest-title">{{ c.title }}</span>
        </div>
        <span :class="['contest-status-pill', getContestStatusClass(c)]">
          {{ getContestStatusText(c) }}
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { Timer, ArrowRight } from '@element-plus/icons-vue'
import { fetchContests } from '../../api/http'
import type { Contest } from '../../types'

const router = useRouter()
const loading = ref(false)
const contests = ref<Contest[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const contestsData = await fetchContests()
    contests.value = contestsData || []
  } catch (error: any) {
    console.error('Failed to load contests', error)
  } finally {
    loading.value = false
  }
})

function getContestStatusText(c: Contest) {
  const now = Date.now()
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  if (now < start) return '未开始'
  if (now > end) return '已结束'
  return '进行中'
}

function getContestStatusClass(c: Contest) {
  const now = Date.now()
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  if (now < start) return 'upcoming'
  if (now > end) return 'ended'
  return 'ongoing'
}
</script>

<style scoped>
.panel {
  border-radius: 16px !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: var(--shadow-sm) !important;
  background: var(--bg-surface) !important;
  padding: 24px !important;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.panel:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md) !important;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  position: relative;
}

.header-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.contest-icon {
  background: #faf5ff;
  color: #7c3aed;
  border: 1px solid #f3e8ff;
}

.panel-header h2 {
  font-size: 1.15rem;
  font-weight: 750;
  color: var(--text-primary);
  margin: 0;
  text-align: left;
}

.panel-header .muted {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 3px 0 0 0;
  font-weight: 500;
  text-align: left;
}

.more-link {
  margin-left: auto;
}

.home-contest-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-contest-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-radius: var(--radius-md);
  background: var(--bg-app);
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.home-contest-item:hover {
  border-color: var(--el-color-purple);
  background: var(--bg-surface);
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(124, 58, 237, 0.08);
}

.contest-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.contest-type-badge {
  font-size: 0.72rem;
  font-weight: 700;
  background: rgba(124, 58, 237, 0.1);
  color: #7c3aed;
  border: 1px solid rgba(124, 58, 237, 0.25);
  padding: 2px 8px;
  border-radius: 6px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.contest-title {
  font-size: 0.92rem;
  font-weight: 650;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contest-status-pill {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.contest-status-pill.upcoming {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.contest-status-pill.ongoing {
  background: #fef2f2;
  color: #ef4444;
  border: 1px solid #fecaca;
  animation: pulseLight 1.5s infinite alternate ease-in-out;
}

.contest-status-pill.ended {
  background: #f8fafc;
  color: #94a3b8;
  border: 1px solid #f1f5f9;
}

@keyframes pulseLight {
  from { opacity: 0.8; box-shadow: 0 0 0 rgba(239, 68, 68, 0); }
  to { opacity: 1; box-shadow: 0 0 8px rgba(239, 68, 68, 0.2); }
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
}
</style>
