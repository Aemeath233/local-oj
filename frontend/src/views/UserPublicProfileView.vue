<template>
  <div class="page-stack" v-loading="loading">
    <div class="public-profile-container" v-if="profile">
      <!-- Premium User Card Header -->
      <div class="premium-profile-card panel">
        <div class="profile-header-content">
          <el-avatar :size="80" :src="profile.avatarUrl" class="profile-avatar">
            {{ userFallback }}
          </el-avatar>
          <div class="profile-meta-info">
            <div class="nickname-row">
              <h2>{{ profile.displayName || profile.username }}</h2>
              <el-tag v-if="profile.role === 'SUPER_ADMIN'" class="role-tag super-admin" effect="dark">
                超级管理员
              </el-tag>
              <el-tag v-else-if="profile.role === 'ADMIN'" class="role-tag admin" type="warning" effect="dark">
                管理员
              </el-tag>
              <el-tag v-else class="role-tag student" type="success" effect="dark">
                学生
              </el-tag>
            </div>
            <div class="major-info">
              <el-icon class="major-icon"><Notebook /></el-icon>
              <span>专业：{{ profile.major || '未填写专业' }}</span>
              <el-divider direction="vertical" />
              <el-icon class="major-icon"><Calendar /></el-icon>
              <span>注册时间：{{ formatDateTime(profile.createdAt) }}</span>
              <el-divider direction="vertical" v-if="profile.lastActiveAt" />
              <el-icon class="major-icon" v-if="profile.lastActiveAt"><Clock /></el-icon>
              <span v-if="profile.lastActiveAt">最近在线：{{ formatRelativeTime(profile.lastActiveAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Solved Stats & Heatmap Grid -->
      <section class="stats-dashboard-grid">
        <!-- Solved Problems Donut Chart -->
        <!-- Solved Problems Donut Chart -->
        <UserStatsDonut :difficulty="profile.stats.difficultyDistribution" />

        <!-- Heatmap Grid Panel -->
        <UserHeatmap :heatmap-data="profile.stats.heatmap" />
      </section>
      <!-- Recent Submissions Panel -->
      <div class="panel recent-submissions-card" style="margin-top: 24px;">
        <h3 class="panel-title" style="margin-bottom: 16px;">最近提交记录</h3>
        <el-table :data="profile.recentSubmissions" row-key="id" empty-text="暂无提交记录" @row-click="openDetail" style="width: 100%;">
          <el-table-column prop="id" label="#" width="90" />
          <el-table-column label="题目" min-width="200">
            <template #default="{ row }">
              <div class="problem-link-cell" @click.stop="goToProblem(row.problemId)">
                <span class="problem-title-text">{{ row.problemTitle || `题目 #${row.problemId}` }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="language" label="语言" width="120" />
          <el-table-column label="结果" width="140">
            <template #default="{ row }">
              <VerdictTag :status="row.status" :verdict="row.verdict" />
            </template>
          </el-table-column>
          <el-table-column prop="score" label="分数" width="90" />
          <el-table-column label="耗时" width="110" align="right">
            <template #default="{ row }">
              <span style="font-variant-numeric: tabular-nums;">{{ row.timeMs != null ? row.timeMs + ' ms' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="内存" width="120" align="right">
            <template #default="{ row }">
              <span style="font-variant-numeric: tabular-nums;">{{ row.memoryKb != null ? formatMemory(row.memoryKb) : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="160">
            <template #default="{ row }">
              <div>{{ formatDateTime(row.createdAt) }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <SubmissionDetailDrawer v-model="drawerVisible" :detail="selectedSubmission" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Notebook, Calendar, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchPublicProfile } from '../api/profile'
import { fetchSubmission } from '../api/submission'
import type { PublicProfile } from '../types'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import VerdictTag from '../components/VerdictTag.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import UserStatsDonut from '../components/profile/UserStatsDonut.vue'
import UserHeatmap from '../components/profile/UserHeatmap.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const drawerVisible = ref(false)
const selectedSubmission = ref<any>(null)

function goToProblem(problemId: number) {
  router.push(`/problems/${problemId}`)
}

async function openDetail(row: any) {
  try {
    selectedSubmission.value = await fetchSubmission(row.id)
    drawerVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取提交详情失败')
  }
}

function formatMemory(kb: number) {
  if (kb >= 1024) {
    return (kb / 1024).toFixed(1) + ' MB'
  }
  return kb + ' KB'
}
const profile = ref<PublicProfile | null>(null)



const userFallback = computed(() => {
  if (!profile.value) return '?'
  return (profile.value.displayName || profile.value.username || 'U').substring(0, 1).toUpperCase()
})

const userIdParam = computed(() => Number(route.params.id))

watch(userIdParam, () => {
  loadProfile()
})

onMounted(() => {
  loadProfile()
})

async function loadProfile() {
  if (!userIdParam.value) return
  loading.value = true
  try {
    const data = await fetchPublicProfile(userIdParam.value)
    profile.value = data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取用户主页信息失败')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.public-profile-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 0 30px 0;
}

/* Premium User Card Header */
.premium-profile-card {
  padding: 24px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

.profile-header-content {
  display: flex;
  align-items: center;
  gap: 24px;
}

.profile-avatar {
  border: 3px solid var(--border-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.profile-meta-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nickname-row h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 750;
  color: var(--el-text-color-primary);
}

.username-sub {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.major-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-top: 6px;
  flex-wrap: wrap;
}

.major-icon {
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

/* Role Badges */
.role-tag.super-admin {
  background: linear-gradient(135deg, #ff4e50 0%, #f9d423 100%);
  border: none;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(249, 212, 35, 0.2);
}

.role-tag.admin {
  background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%);
  border: none;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(109, 213, 237, 0.2);
}

.role-tag.student {
  font-weight: 500;
}

/* Stats dashboard styling */
.stats-dashboard-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.panel-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 12px;
}

.donut-chart-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
}

.donut-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  flex: 1;
  justify-content: center;
}

.donut-chart-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
}

.donut-svg {
  transform: rotate(0deg);
}

.donut-ring-active {
  transition: stroke-dashoffset 0.6s ease-out;
}

.donut-total {
  font-family: var(--el-font-family);
}

.donut-label {
  font-family: var(--el-font-family);
  letter-spacing: 0.5px;
}

.difficulty-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.difficulty-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.difficulty-header {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.difficulty-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  margin-right: 8px;
  line-height: 1;
}

.difficulty-badge.easy {
  background-color: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.difficulty-badge.medium {
  background-color: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

.difficulty-badge.hard {
  background-color: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.difficulty-count {
  color: var(--el-text-color-secondary);
}

.difficulty-count strong {
  color: var(--el-text-color-primary);
}

.difficulty-pct {
  margin-left: auto;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.progress-bar-track {
  height: 6px;
  background-color: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
  width: 100%;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-bar-fill.easy {
  background-color: #22c55e;
}

.progress-bar-fill.medium {
  background-color: #f59e0b;
}

.progress-bar-fill.hard {
  background-color: #ef4444;
}

/* Heatmap Card styling */
.heatmap-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
}

.heatmap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 16px;
  padding-bottom: 12px;
  flex-wrap: wrap;
  gap: 12px;
}

.heatmap-header .panel-title {
  margin: 0;
  border-bottom: none;
  padding-bottom: 0;
}

.heatmap-stats {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}

.heatmap-stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.heatmap-stat-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.heatmap-stat-value {
  font-size: 15px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.heatmap-stat-value.ac {
  color: #16a34a;
}

.heatmap-container {
  position: relative;
  overflow-x: auto;
  padding: 4px 0;
  flex: 1;
  display: flex;
  align-items: center;
}

@media (max-width: 992px) {
  .stats-dashboard-grid {
    grid-template-columns: 1fr;
  }
}

.custom-heatmap-tooltip {
  position: absolute;
  transform: translate(-50%, -100%);
  pointer-events: none;
  z-index: 100;
  background: #0f172a;
  color: #ffffff;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.tooltip-arrow {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%) rotate(45deg);
  width: 8px;
  height: 8px;
  background: #0f172a;
  border-right: 1px solid rgba(255, 255, 255, 0.15);
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}

.fade-fast-enter-active,
.fade-fast-leave-active {
  transition: opacity 0.12s ease;
}
.fade-fast-enter-from,
.fade-fast-leave-to {
  opacity: 0;
}
.recent-submissions-card {
  padding: 18px;
}
.problem-link-cell {
  cursor: pointer;
  display: inline-flex;
}
.problem-link-cell:hover .problem-title-text {
  color: var(--primary);
  text-decoration: underline;
}
.problem-title-text {
  font-weight: 650;
  color: var(--text-primary);
  transition: color 0.15s ease;
}
</style>
