<template>
  <section class="panel ranking-panel" v-loading="loading">
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
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Trophy, ArrowRight } from '@element-plus/icons-vue'
import { fetchLeaderboard } from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import type { LeaderboardRow } from '../../types'

const emit = defineEmits<{
  (e: 'rank-calculated', rank: string): void
}>()

const auth = useAuthStore()
const loading = ref(false)
const leaderboard = ref<LeaderboardRow[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const leaderboardData = await fetchLeaderboard(100)
    if (leaderboardData) {
      leaderboard.value = leaderboardData.slice(0, 5)
      
      if (auth.user) {
        const myRankIndex = leaderboardData.findIndex((row) => row.userId === auth.user?.id)
        const rankString = myRankIndex !== -1 ? `No.${myRankIndex + 1}` : '暂无'
        emit('rank-calculated', rankString)
      }
    }
  } catch (error: any) {
    console.error('Failed to load leaderboard', error)
  } finally {
    loading.value = false
  }
})
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

.rank-icon {
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fef3c7;
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
  align-items: center;
  gap: 16px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.stat-value {
  font-size: 0.95rem;
  font-weight: 750;
  color: var(--text-primary);
  font-family: var(--font-mono), monospace;
}

.stat-label {
  font-size: 0.68rem;
  color: var(--text-muted);
  font-weight: 600;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
}
</style>
