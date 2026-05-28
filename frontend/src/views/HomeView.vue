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
      <HomeDailyProblemCard />

      <!-- Leaderboard Card -->
      <HomeLeaderboardCard @rank-calculated="handleRankCalculated" />

      <!-- Left Column: Recent Submissions -->
      <HomeSubmissionsFeed />

      <!-- Recent Contests Panel -->
      <HomeContestsFeed />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { fetchUserStats } from '../api/http'
import { useAuthStore } from '../stores/auth'
import HomeDailyProblemCard from '../components/home/HomeDailyProblemCard.vue'
import HomeLeaderboardCard from '../components/home/HomeLeaderboardCard.vue'
import HomeSubmissionsFeed from '../components/home/HomeSubmissionsFeed.vue'
import HomeContestsFeed from '../components/home/HomeContestsFeed.vue'

const auth = useAuthStore()

const streakDays = ref(0)
const todayAcCount = ref(0)
const solvedTotal = ref(0)
const userRank = ref('暂无')

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
  try {
    const statsData = await fetchUserStats()
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
  } catch (error: any) {
    console.error('Failed to load user stats for hero banner', error)
  }
})

function handleRankCalculated(rank: string) {
  userRank.value = rank
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
  box-sizing: border-box;
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
  color: #fff;
}

.hero-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(13, 148, 136, 0.6);
}

.home-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

@media (max-width: 992px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
  .home-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
}
</style>
