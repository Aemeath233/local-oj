<template>
  <section class="panel daily-panel" v-loading="loading">
    <div class="panel-header">
      <div class="header-icon-box daily-icon">
        <el-icon><Calendar /></el-icon>
      </div>
      <div>
        <h2>每日一题</h2>
        <p class="muted">{{ todayText }}</p>
      </div>
    </div>
    
    <div v-if="dailyProblem" class="daily-problem-card">
      <div class="card-top">
        <span class="difficulty-badge" :class="dailyProblem.difficulty.toLowerCase()">
          {{ dailyProblem.difficulty }}
        </span>
        <span class="problem-id">#{{ dailyProblem.id }}</span>
      </div>
      
      <h3 class="problem-title">{{ dailyProblem.title }}</h3>
      <p class="problem-slug">{{ dailyProblem.slug }}</p>
      
      <div class="tag-list">
        <span
          v-for="tag in splitTags(dailyProblem.tags)"
          :key="tag"
          class="custom-tag-pill"
          :style="{ '--tag-color': getTagColor(tag) }"
        >
          {{ tag }}
        </span>
      </div>

      <!-- Daily Stats Block -->
      <div class="daily-stats">
        <div class="daily-stat-item">
          <span class="ds-label">全站通过率</span>
          <strong class="ds-value">{{ calculateAcRate(dailyProblem) }}</strong>
        </div>
        <div class="daily-stat-item">
          <span class="ds-label">总提交数</span>
          <strong class="ds-value">{{ dailyProblem.submitCount || 0 }} 次</strong>
        </div>
        <div class="daily-stat-item">
          <span class="ds-label">通过人数</span>
          <strong class="ds-value">{{ dailyProblem.acceptedCount || 0 }} 人</strong>
        </div>
      </div>
      
      <div class="card-footer">
        <RouterLink :to="`/problems/${dailyProblem.id}`">
          <el-button type="primary" size="large" class="start-btn" :icon="ArrowRight">
            开始挑战
          </el-button>
        </RouterLink>
      </div>
    </div>
    <div v-else class="empty-state">
      <el-empty description="今日暂无可见题目" :image-size="60" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Calendar, ArrowRight } from '@element-plus/icons-vue'
import { fetchDailyProblem } from '../../api/http'
import { getTagColor } from '../../utils/tag'
import type { ProblemSummary } from '../../types'

const loading = ref(false)
const dailyProblem = ref<ProblemSummary | null>(null)

const todayText = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

onMounted(async () => {
  loading.value = true
  try {
    dailyProblem.value = await fetchDailyProblem()
  } catch (error: any) {
    console.error('Failed to load daily problem', error)
  } finally {
    loading.value = false
  }
})

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function calculateAcRate(problem: ProblemSummary) {
  if (!problem.submitCount || problem.submitCount === 0) return '0.0%'
  const count = problem.acceptedCount || 0
  return ((count / problem.submitCount) * 100).toFixed(1) + '%'
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

.daily-icon {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #dcfce7;
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

.daily-panel {
  display: flex;
  flex-direction: column;
}

.daily-problem-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  align-items: flex-start;
  position: relative;
  overflow: hidden;
  text-align: left;
}

.daily-problem-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: #10b981;
}

.card-top {
  display: flex;
  justify-content: space-between;
  width: 100%;
  align-items: center;
}

.difficulty-badge {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
  text-transform: uppercase;
}

.difficulty-badge.easy {
  background: #ecfdf5;
  color: #10b981;
}

.difficulty-badge.medium {
  background: #fffbeb;
  color: #f59e0b;
}

.difficulty-badge.hard {
  background: #fef2f2;
  color: #ef4444;
}

.problem-id {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--text-muted);
  font-family: var(--font-mono), monospace;
}

.problem-title {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--text-primary);
  margin: 14px 0 4px 0;
}

.problem-slug {
  font-size: 0.82rem;
  color: var(--text-muted);
  font-family: var(--font-mono), monospace;
  margin: 0 0 14px 0;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}

.custom-tag-pill {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--tag-color);
  background: color-mix(in srgb, var(--tag-color) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--tag-color) 25%, transparent);
  padding: 3px 8px;
  border-radius: 6px;
}

.daily-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: auto;
  margin-bottom: 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  width: 100%;
  box-sizing: border-box;
}

.daily-stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  flex: 1;
}

.ds-label {
  font-size: 0.72rem;
  color: var(--text-muted);
  font-weight: 600;
}

.ds-value {
  font-size: 0.95rem;
  color: var(--text-primary);
  font-weight: 700;
  margin-top: 2px;
  font-family: var(--font-mono), monospace;
}

.card-footer {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.start-btn {
  font-weight: 600;
  background: #0f172a;
  border: none;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.2);
  transition: all 0.2s ease;
  color: #fff;
}

.start-btn:hover {
  background: #1e293b;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 0;
}
</style>
