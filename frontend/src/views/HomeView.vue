<template>
  <section class="page-stack">
    <!-- Hero Banner (Personalized Welcome Dashboard) -->
    <div class="home-hero">
      <div class="hero-content">
        <span class="hero-tag">PERSONAL WORKSPACE</span>
        <h1>欢迎回来，{{ auth.user?.displayName || auth.user?.username || '开发者' }}！</h1>
        <p class="hero-streak-text">
          🔥 今天是你连续刷题的第 <strong>{{ streakDays }}</strong> 天，保持专注，不断超越！
        </p>
        <div class="hero-stats-row">
          <span class="hero-stat-item">今日已写：<strong>{{ todayAcCount }}</strong> 题</span>
          <span class="hero-stat-divider">|</span>
          <span class="hero-stat-item">总共通过：<strong>{{ solvedTotal }}</strong> 题</span>
          <span class="hero-stat-divider">|</span>
          <span class="hero-stat-item">当前全站排名：<strong>{{ userRank }}</strong></span>
        </div>
      </div>
      <RouterLink to="/problems">
        <el-button type="primary" size="large" class="hero-btn" :icon="ArrowRight">
          进入题库
        </el-button>
      </RouterLink>
    </div>

    <!-- Main Grid: Four direct panel panels -->
    <div class="home-grid">
      <!-- Daily Problem Card -->
      <section class="panel daily-panel">
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

      <!-- Leaderboard Card -->
      <section class="panel ranking-panel">
        <div class="panel-header">
          <div class="header-icon-box rank-icon">
            <el-icon><Trophy /></el-icon>
          </div>
          <div>
            <h2>排行榜</h2>
            <p class="muted">Top 5 最佳榜单</p>
          </div>
          <RouterLink to="/leaderboard" class="more-link">
            <el-button size="small" circle :icon="ArrowRight" />
          </RouterLink>
        </div>

        <div class="leaderboard-list">
          <div v-if="leaderboard.length === 0" class="empty-state">
            <el-empty description="暂无排行数据" :image-size="60" />
          </div>
          <div
            v-for="(row, index) in leaderboard"
            :key="row.userId"
            class="leaderboard-item"
            :class="'rank-' + (index + 1)"
          >
            <div class="item-left">
              <div class="rank-number-box" :class="'rank-pos-' + (index + 1)">
                <span class="rank-num">{{ index + 1 }}</span>
              </div>
              <div class="user-info">
                <span class="user-name">{{ row.displayName || row.username }}</span>
                <span v-if="index === 0" class="top-tag">榜首</span>
              </div>
            </div>
            
            <div class="item-right">
              <div class="stat-box">
                <span class="stat-value">{{ row.acceptedCount }}</span>
                <span class="stat-label">AC</span>
              </div>
              <div class="stat-box">
                <span class="stat-value">{{ row.submissionCount }}</span>
                <span class="stat-label">提交</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Left Column: Recent Submissions -->
      <section class="panel submissions-panel">
        <div class="panel-header">
          <div class="header-icon-box sub-icon">
            <el-icon><Message /></el-icon>
          </div>
          <div>
            <h2>全站提交动态</h2>
            <p class="muted">最近 5 次提交结果实时展示</p>
          </div>
          <RouterLink to="/submissions" class="more-link">
            <el-button size="small" circle :icon="ArrowRight" />
          </RouterLink>
        </div>

        <div class="home-sub-list">
          <div v-if="recentSubmissions.length === 0" class="empty-state">
            <el-empty description="暂无公共提交记录" :image-size="50" />
          </div>
          <div
            v-for="sub in recentSubmissions.slice(0, 5)"
            :key="sub.id"
            class="home-sub-row"
          >
            <div class="sub-row-user">
              <el-avatar :size="22" :src="sub.avatarUrl">
                {{ (sub.displayName || sub.username || 'U').slice(0, 1).toUpperCase() }}
              </el-avatar>
              <span class="sub-user-nick">{{ sub.displayName || sub.username }}</span>
            </div>
            <div class="sub-row-problem">
              <RouterLink :to="`/problems/${sub.problemId}`" class="sub-prob-link">
                {{ sub.problemTitle || `题目 #${sub.problemId}` }}
              </RouterLink>
            </div>
            <div class="sub-row-lang">
              <span class="sub-lang-text">{{ sub.language }}</span>
            </div>
            <div class="sub-row-verdict">
              <VerdictTag :status="sub.status" :verdict="sub.verdict" />
            </div>
            <div class="sub-row-time">
              <span class="sub-time-text">{{ formatRelativeTime(sub.createdAt) }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Recent Contests Panel -->
      <section class="panel contests-panel">
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
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, Trophy, Calendar, Grid, Timer, Message } from '@element-plus/icons-vue'
import { fetchLeaderboard, fetchDailyProblem, fetchContests, fetchSubmissions, fetchUserStats } from '../api/http'
import { getTagColor } from '../utils/tag'
import { formatRelativeTime } from '../utils/time'
import type { LeaderboardRow, ProblemSummary, Contest, SubmissionSummary, UserStats } from '../types'
import VerdictTag from '../components/VerdictTag.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const dailyProblem = ref<ProblemSummary | null>(null)
const leaderboard = ref<LeaderboardRow[]>([])
const contests = ref<Contest[]>([])
const recentSubmissions = ref<SubmissionSummary[]>([])

const streakDays = ref(0)
const todayAcCount = ref(0)
const solvedTotal = ref(0)
const userRank = ref('暂无')

const todayText = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

function formatDate(d: Date) {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function calculateStreak(heatmap: Record<string, { totalCount: number; acCount: number }>) {
  let streak = 0
  const current = new Date()
  current.setHours(0, 0, 0, 0)
  
  const todayStr = formatDate(current)
  const hasToday = (heatmap[todayStr]?.totalCount || 0) > 0
  
  if (!hasToday) {
    current.setDate(current.getDate() - 1)
  }
  
  while (true) {
    const dateStr = formatDate(current)
    if ((heatmap[dateStr]?.totalCount || 0) > 0) {
      streak++
      current.setDate(current.getDate() - 1)
    } else {
      break
    }
  }
  
  return streak
}

onMounted(async () => {
  const [dailyData, leaderboardData, contestsData, submissionsData, statsData] = await Promise.all([
    fetchDailyProblem(),
    fetchLeaderboard(100), // Fetch up to 100 ranked users to calculate personal rank
    fetchContests(),
    fetchSubmissions(),
    fetchUserStats().catch(() => null) // Suppress failed stats fetch gracefully
  ])
  
  dailyProblem.value = dailyData
  leaderboard.value = (leaderboardData || []).slice(0, 5) // Display top 5
  contests.value = contestsData || []
  recentSubmissions.value = submissionsData || []

  // Calculate personal rank on the leaderboard
  if (leaderboardData && auth.user) {
    const myRankIndex = leaderboardData.findIndex((row) => row.userId === auth.user?.id)
    userRank.value = myRankIndex !== -1 ? `No.${myRankIndex + 1}` : '暂无'
  }

  // Parse user stats if available
  if (statsData) {
    solvedTotal.value = 
      (statsData.difficultyDistribution?.easySolved || 0) + 
      (statsData.difficultyDistribution?.mediumSolved || 0) + 
      (statsData.difficultyDistribution?.hardSolved || 0)

    const todayStr = formatDate(new Date())
    if (statsData.heatmap) {
      todayAcCount.value = statsData.heatmap[todayStr]?.acCount || 0
      streakDays.value = calculateStreak(statsData.heatmap)
    }
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
/* Hero Banner Styling */
.home-hero {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
  position: relative;
  overflow: hidden;
  padding: 30px 40px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 10px 30px -10px rgba(15, 23, 42, 0.3);
  margin-bottom: 20px;
}

.home-hero::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(13, 148, 136, 0.12) 0%, transparent 60%);
  pointer-events: none;
  animation: glowMove 8s infinite alternate ease-in-out;
}

@keyframes glowMove {
  0% { transform: translate(-10%, -10%); }
  100% { transform: translate(10%, 10%); }
}

.hero-content {
  position: relative;
  z-index: 2;
  text-align: left;
}

.hero-tag {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.15em;
  color: #0d9488;
  background: rgba(13, 148, 136, 0.15);
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(13, 148, 136, 0.25);
  text-transform: uppercase;
}

.home-hero h1 {
  font-size: 2.2rem;
  font-weight: 850;
  color: #ffffff;
  margin: 14px 0 6px 0;
  letter-spacing: -0.02em;
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-version {
  font-size: 0.85rem;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.08);
  color: #cbd5e1;
  padding: 2px 8px;
  border-radius: 6px;
  vertical-align: middle;
}

.hero-streak-text {
  font-size: 1.05rem;
  color: #cbd5e1 !important;
  margin: 8px 0 14px 0 !important;
  font-weight: 500;
}

.hero-streak-text strong {
  color: #f59e0b;
  font-size: 1.25rem;
  font-family: var(--font-mono), monospace;
}

.hero-stats-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.hero-stat-item {
  font-size: 0.95rem;
  color: #94a3b8;
}

.hero-stat-item strong {
  color: #ffffff;
  font-family: var(--font-mono), monospace;
  font-size: 1.05rem;
}

.hero-stat-divider {
  color: rgba(255, 255, 255, 0.15);
  font-size: 0.95rem;
  user-select: none;
}

.hero-btn {
  position: relative;
  z-index: 2;
  font-weight: 600;
  background: linear-gradient(135deg, #0d9488 0%, #0f766e 100%);
  border: none;
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.4);
  transition: all 0.2s ease;
}

.hero-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(13, 148, 136, 0.6);
}

/* Home Grid Layout */
.home-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.daily-panel {
  display: flex;
  flex-direction: column;
}

.daily-panel .daily-problem-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* Panel Header Premium styling */
.panel {
  border-radius: 16px !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: var(--shadow-sm) !important;
  background: var(--bg-surface) !important;
  padding: 24px !important;
  transition: all 0.3s ease;
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

.rank-icon {
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fef3c7;
}

.nav-icon {
  background: #f0f9ff;
  color: #0284c7;
  border: 1px solid #e0f2fe;
}

.contest-icon {
  background: #faf5ff;
  color: #7c3aed;
  border: 1px solid #f3e8ff;
}

.sub-icon {
  background: #f0fdfa;
  color: #0d9488;
  border: 1px solid #ccfbf1;
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

/* Daily Problem Card Premium styling */
.daily-problem-card {
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
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
}

.start-btn:hover {
  background: #1e293b;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);
}



/* Leaderboard Premium styling */
.leaderboard-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.leaderboard-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-radius: 10px;
  background: var(--bg-app);
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;
  position: relative;
}

.leaderboard-item:hover {
  background: var(--bg-surface);
  border-color: var(--border-hover);
  box-shadow: var(--shadow-sm);
  transform: translateX(4px);
}

.item-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank-number-box {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  font-family: var(--font-mono), monospace;
  transition: all 0.2s ease;
}

.rank-pos-1 {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.3);
}

.rank-pos-2 {
  background: linear-gradient(135deg, #cbd5e1, #94a3b8);
  color: #fff;
  box-shadow: 0 2px 8px rgba(148, 163, 184, 0.3);
}

.rank-pos-3 {
  background: linear-gradient(135deg, #fdba74, #f97316);
  color: #fff;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.2);
}

.rank-num {
  font-weight: 700;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.rank-pos-1 .rank-num,
.rank-pos-2 .rank-num,
.rank-pos-3 .rank-num {
  color: #fff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-weight: 650;
  color: var(--text-secondary);
  font-size: 0.92rem;
}

.top-tag {
  font-size: 0.65rem;
  font-weight: 700;
  color: #b45309;
  background: #fef3c7;
  padding: 1px 6px;
  border-radius: 4px;
}

.item-right {
  display: flex;
  gap: 20px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 48px;
}

.stat-value {
  font-size: 1rem;
  font-weight: 750;
  color: var(--text-primary);
  font-family: var(--font-mono), monospace;
}

.stat-box .stat-label {
  font-size: 0.72rem;
  color: var(--text-disabled);
  font-weight: 600;
  margin-top: 1px;
}

.rank-1 {
  background: linear-gradient(90deg, #fefdf0 0%, var(--bg-app) 100%);
  border-color: #fef3c7;
}

html.dark .rank-1 {
  background: linear-gradient(90deg, rgba(217, 119, 6, 0.12) 0%, rgba(30, 41, 59, 0.2) 100%) !important;
  border-color: rgba(217, 119, 6, 0.3) !important;
}

.rank-1::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 32px;
  background: #d97706;
  border-radius: 0 4px 4px 0;
}

/* Recent Contests */
.home-contest-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.home-contest-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-app);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s ease;
}

.home-contest-item:hover {
  background: var(--bg-surface);
  border-color: var(--primary);
  box-shadow: var(--shadow-sm);
  transform: translateX(4px);
}

.contest-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.contest-type-badge {
  font-size: 0.68rem;
  font-weight: 700;
  color: #7c3aed;
  background: #f3e8ff;
  padding: 2px 6px;
  border-radius: 4px;
}

.home-contest-item .contest-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contest-status-pill {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
}

.contest-status-pill.ongoing {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.contest-status-pill.upcoming {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}

.contest-status-pill.ended {
  background: #f1f5f9;
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

/* Recent Submissions Feed */
.home-sub-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.home-sub-row {
  display: grid;
  grid-template-columns: 180px 1fr 100px 120px 120px;
  align-items: center;
  padding: 10px 16px;
  background: var(--bg-app);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  text-align: left;
}

.sub-row-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sub-user-nick {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-row-problem {
  min-width: 0;
}

.sub-prob-link {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-primary);
}

.sub-prob-link:hover {
  color: var(--primary);
  text-decoration: underline;
}

.sub-lang-text {
  font-size: 0.8rem;
  font-family: var(--font-mono);
  color: var(--text-muted);
}

.sub-time-text {
  font-size: 0.8rem;
  color: var(--text-disabled);
  text-align: right;
  display: block;
}

.empty-state {
  padding: 30px 0;
}

@media (max-width: 1040px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
  .home-sub-row {
    grid-template-columns: 1fr 1fr 1fr;
    gap: 8px;
  }
  .sub-row-lang, .sub-row-time {
    display: none;
  }
}
</style>
