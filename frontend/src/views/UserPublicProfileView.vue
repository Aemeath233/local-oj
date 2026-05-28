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
            </div>
          </div>
        </div>
      </div>

      <!-- Solved Stats & Heatmap Grid -->
      <section class="stats-dashboard-grid">
        <!-- Solved Problems Donut Chart -->
        <div class="donut-chart-card panel">
          <h3 class="panel-title">做题统计</h3>
          <div class="donut-content">
            <div class="donut-chart-wrapper">
              <svg width="120" height="120" viewBox="0 0 100 100" class="donut-svg">
                <!-- Background Track -->
                <circle cx="50" cy="50" r="42" fill="transparent" stroke="#f1f5f9" stroke-width="8" />
                <!-- Active Progress Ring -->
                <circle
                  cx="50"
                  cy="50"
                  r="42"
                  fill="transparent"
                  stroke="#0f766e"
                  stroke-width="8"
                  :stroke-dasharray="263.89"
                  :stroke-dashoffset="donutStrokeOffset"
                  transform="rotate(-90 50 50)"
                  stroke-linecap="round"
                  class="donut-ring-active"
                />
                <!-- Central Text -->
                <text x="50" y="48" text-anchor="middle" class="donut-total" font-size="16" font-weight="bold" fill="#0f766e">
                  {{ solvedTotal }}
                </text>
                <text x="50" y="66" text-anchor="middle" class="donut-label" font-size="8" fill="#64748b" font-weight="600">
                  已解决 / {{ problemsTotal }}
                </text>
              </svg>
            </div>

            <div class="difficulty-list">
              <!-- Easy -->
              <div class="difficulty-row">
                <div class="difficulty-header">
                  <span class="difficulty-badge easy">简单</span>
                  <span class="difficulty-count">
                    <strong>{{ profile.stats.difficultyDistribution.easySolved }}</strong> / {{ profile.stats.difficultyDistribution.easyTotal }}
                  </span>
                  <span class="difficulty-pct">{{ getPercentage(profile.stats.difficultyDistribution.easySolved, profile.stats.difficultyDistribution.easyTotal) }}%</span>
                </div>
                <div class="progress-bar-track">
                  <div
                    class="progress-bar-fill easy"
                    :style="{ width: getPercentage(profile.stats.difficultyDistribution.easySolved, profile.stats.difficultyDistribution.easyTotal) + '%' }"
                  ></div>
                </div>
              </div>

              <!-- Medium -->
              <div class="difficulty-row">
                <div class="difficulty-header">
                  <span class="difficulty-badge medium">中等</span>
                  <span class="difficulty-count">
                    <strong>{{ profile.stats.difficultyDistribution.mediumSolved }}</strong> / {{ profile.stats.difficultyDistribution.mediumTotal }}
                  </span>
                  <span class="difficulty-pct">{{ getPercentage(profile.stats.difficultyDistribution.mediumSolved, profile.stats.difficultyDistribution.mediumTotal) }}%</span>
                </div>
                <div class="progress-bar-track">
                  <div
                    class="progress-bar-fill medium"
                    :style="{ width: getPercentage(profile.stats.difficultyDistribution.mediumSolved, profile.stats.difficultyDistribution.mediumTotal) + '%' }"
                  ></div>
                </div>
              </div>

              <!-- Hard -->
              <div class="difficulty-row">
                <div class="difficulty-header">
                  <span class="difficulty-badge hard">困难</span>
                  <span class="difficulty-count">
                    <strong>{{ profile.stats.difficultyDistribution.hardSolved }}</strong> / {{ profile.stats.difficultyDistribution.hardTotal }}
                  </span>
                  <span class="difficulty-pct">{{ getPercentage(profile.stats.difficultyDistribution.hardSolved, profile.stats.difficultyDistribution.hardTotal) }}%</span>
                </div>
                <div class="progress-bar-track">
                  <div
                    class="progress-bar-fill hard"
                    :style="{ width: getPercentage(profile.stats.difficultyDistribution.hardSolved, profile.stats.difficultyDistribution.hardTotal) + '%' }"
                  ></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Heatmap Grid Panel -->
        <div class="heatmap-card panel">
          <div class="heatmap-header">
            <h3 class="panel-title">提交冷热图</h3>
            <div class="heatmap-stats">
              <div class="heatmap-stat-item">
                <span class="heatmap-stat-label">过去一年提交</span>
                <strong class="heatmap-stat-value">{{ heatmapTotalSubmissions }}</strong>
              </div>
              <div class="heatmap-stat-item">
                <span class="heatmap-stat-label">AC 通过数</span>
                <strong class="heatmap-stat-value ac">{{ heatmapTotalAc }}</strong>
              </div>
              <div class="heatmap-stat-item">
                <span class="heatmap-stat-label">通过率</span>
                <strong class="heatmap-stat-value">{{ heatmapAcRate }}</strong>
              </div>
              <div class="heatmap-stat-item">
                <span class="heatmap-stat-label">活跃天数</span>
                <strong class="heatmap-stat-value">{{ heatmapActiveDays }} 天</strong>
              </div>
            </div>
          </div>

          <div class="heatmap-container">
            <svg viewBox="0 0 740 135" width="100%" class="heatmap-svg">
              <!-- Month Labels -->
              <text
                v-for="(label, lIndex) in monthLabels"
                :key="'m-' + lIndex"
                :x="label.x"
                y="12"
                class="heatmap-label"
                font-size="9"
                fill="#64748b"
              >
                {{ label.text }}
              </text>

              <!-- Weekday Labels -->
              <text x="0" y="38" class="heatmap-label" font-size="9" fill="#64748b">周一</text>
              <text x="0" y="64" class="heatmap-label" font-size="9" fill="#64748b">周三</text>
              <text x="0" y="90" class="heatmap-label" font-size="9" fill="#64748b">周五</text>

              <!-- Grid Cells -->
              <g v-for="(week, wIndex) in weeks" :key="'w-' + wIndex">
                <template v-for="(day, dIndex) in week" :key="'d-' + dIndex">
                  <rect
                    v-if="day.level >= 0"
                    :x="30 + wIndex * 13"
                    :y="20 + dIndex * 13"
                    width="10"
                    height="10"
                    rx="2"
                    ry="2"
                    :class="['heatmap-cell', `level-${day.level}`]"
                    @mouseenter="showTooltip($event, day)"
                    @mouseleave="hideTooltip"
                  />
                </template>
              </g>

              <!-- Legend -->
              <g transform="translate(580, 118)">
                <text x="0" y="9" class="heatmap-label" font-size="9" fill="#64748b">少</text>
                <rect x="18" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-0" />
                <rect x="31" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-1" />
                <rect x="44" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-2" />
                <rect x="57" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-3" />
                <rect x="70" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-4" />
                <text x="86" y="9" class="heatmap-label" font-size="9" fill="#64748b">多</text>
              </g>
            </svg>
            <Teleport to="body">
              <Transition name="fade-fast">
                <div
                  v-show="tooltipVisible"
                  class="custom-heatmap-tooltip"
                  :style="tooltipStyle"
                >
                  {{ tooltipContent }}
                  <div class="tooltip-arrow"></div>
                </div>
              </Transition>
            </Teleport>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Notebook } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchPublicProfile } from '../api/http'
import type { PublicProfile } from '../types'

const route = useRoute()
const loading = ref(false)
const profile = ref<PublicProfile | null>(null)

// Virtual Tooltip handlers to prevent SVG hover flickering
const tooltipVisible = ref(false)
const tooltipContent = ref('')
const tooltipStyle = ref({
  left: '0px',
  top: '0px'
})

let activeCell: any = null
let hoverTimeout: number | undefined
let hideTimeout: number | undefined

interface HeatmapDay {
  date: Date
  dateString: string
  isFuture: boolean
  totalCount: number
  acCount: number
  level: number
}

const weeks = ref<HeatmapDay[][]>([])
const monthLabels = ref<Array<{ text: string; x: number }>>([])

const solvedTotal = computed(() => {
  if (!profile.value) return 0
  const d = profile.value.stats.difficultyDistribution
  return d.easySolved + d.mediumSolved + d.hardSolved
})

const problemsTotal = computed(() => {
  if (!profile.value) return 0
  const d = profile.value.stats.difficultyDistribution
  return d.easyTotal + d.mediumTotal + d.hardTotal
})

const donutStrokeOffset = computed(() => {
  const total = problemsTotal.value
  if (total === 0) return 263.89
  const solved = solvedTotal.value
  const pct = Math.min(1, solved / total)
  return 263.89 * (1 - pct)
})

const heatmapTotalSubmissions = computed(() => {
  if (!profile.value) return 0
  let sum = 0
  for (const date in profile.value.stats.heatmap) {
    sum += profile.value.stats.heatmap[date].totalCount
  }
  return sum
})

const heatmapTotalAc = computed(() => {
  if (!profile.value) return 0
  let sum = 0
  for (const date in profile.value.stats.heatmap) {
    sum += profile.value.stats.heatmap[date].acCount
  }
  return sum
})

const heatmapAcRate = computed(() => {
  if (heatmapTotalSubmissions.value === 0) return '0.0%'
  return ((heatmapTotalAc.value / heatmapTotalSubmissions.value) * 100).toFixed(1) + '%'
})

const heatmapActiveDays = computed(() => {
  if (!profile.value) return 0
  let count = 0
  for (const date in profile.value.stats.heatmap) {
    if (profile.value.stats.heatmap[date].totalCount > 0) {
      count++
    }
  }
  return count
})

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
    if (data && data.stats) {
      generateHeatmapData(data.stats.heatmap)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取用户主页信息失败')
  } finally {
    loading.value = false
  }
}

function showTooltip(event: MouseEvent, day: any) {
  const rectEl = event.currentTarget as SVGRectElement
  if (!rectEl) return

  if (hideTimeout) {
    clearTimeout(hideTimeout)
    hideTimeout = undefined
  }
  if (activeCell === day) return
  activeCell = day
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
  }
  hoverTimeout = window.setTimeout(() => {
    const rectBounds = rectEl.getBoundingClientRect()
    const left = rectBounds.left + window.scrollX + rectBounds.width / 2
    const top = rectBounds.top + window.scrollY - 8
    
    tooltipStyle.value = {
      left: `${left}px`,
      top: `${top}px`
    }

    tooltipContent.value = getTooltipContent(day)
    tooltipVisible.value = true
  }, 20)
}

function hideTooltip() {
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = undefined
  }
  activeCell = null
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
  hideTimeout = window.setTimeout(() => {
    tooltipVisible.value = false
  }, 40)
}

function getPercentage(solved: number, total: number) {
  if (total === 0) return 0
  return Math.round((solved / total) * 100)
}

function getTooltipContent(day: any) {
  if (day.totalCount === 0) {
    return `${day.dateString} : 无提交记录`
  }
  return `${day.dateString} : 提交 ${day.totalCount} 次，通过 AC ${day.acCount} 次`
}

function formatDate(d: Date) {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getContributionLevel(total: number, ac: number, isFuture: boolean) {
  if (isFuture) return -1
  if (total === 0) return 0
  if (ac === 0) return 1
  if (ac <= 2) return 2
  if (ac <= 5) return 3
  return 4
}

function generateHeatmapData(heatmapData: Record<string, { totalCount: number; acCount: number }>) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const lastSaturday = new Date(today)
  lastSaturday.setDate(today.getDate() + (6 - today.getDay()))

  const gridStartDate = new Date(lastSaturday)
  gridStartDate.setDate(lastSaturday.getDate() - 370)

  const tempWeeks: any[] = []
  for (let w = 0; w < 53; w++) {
    const weekDays: any[] = []
    for (let d = 0; d < 7; d++) {
      const date = new Date(gridStartDate)
      date.setDate(gridStartDate.getDate() + (w * 7 + d))

      const dateString = formatDate(date)
      const isFuture = date > today
      const statsObj = heatmapData[dateString] || { totalCount: 0, acCount: 0 }

      weekDays.push({
        date,
        dateString,
        isFuture,
        totalCount: statsObj.totalCount,
        acCount: statsObj.acCount,
        level: getContributionLevel(statsObj.totalCount, statsObj.acCount, isFuture)
      })
    }
    tempWeeks.push(weekDays)
  }
  weeks.value = tempWeeks
  generateMonthLabels()
}

function generateMonthLabels() {
  const labels: Array<{ text: string; x: number }> = []
  let lastMonth = -1

  for (let w = 0; w < 53; w++) {
    const week = weeks.value[w]
    if (!week || week.length === 0) continue
    const sundayDate = week[0].date
    const m = sundayDate.getMonth()

    if (w === 0 || m !== lastMonth) {
      const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
      labels.push({
        text: monthNames[m],
        x: 30 + w * 13
      })
      lastMonth = m
    }
  }

  if (labels.length >= 2 && labels[1].x - labels[0].x < 26) {
    labels.shift()
  }

  monthLabels.value = labels
}

onUnmounted(() => {
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
  }
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
})
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
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
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

.heatmap-svg {
  min-width: 700px;
}

.heatmap-cell {
  fill: #f1f5f9;
  transition: fill 0.15s ease;
  cursor: pointer;
}

.heatmap-cell:hover {
  stroke: #0f172a;
  stroke-width: 1.5;
}

.heatmap-cell.level-0 {
  fill: #f1f5f9;
}
 
.heatmap-cell.level-1 {
  fill: #ccfbf1;
}
 
.heatmap-cell.level-2 {
  fill: #5eead4;
}
 
.heatmap-cell.level-3 {
  fill: #0d9488;
}
 
.heatmap-cell.level-4 {
  fill: #115e59;
}

.heatmap-label {
  user-select: none;
  font-family: var(--el-font-family);
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
</style>
