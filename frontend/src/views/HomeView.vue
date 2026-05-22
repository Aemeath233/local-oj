<template>
  <section class="page-stack">
    <div class="home-hero panel">
      <div>
        <h1>Local Judge</h1>
        <p>今日练习、题库入口和排行榜概览</p>
      </div>
      <RouterLink to="/problems">
        <el-button type="primary" :icon="ArrowRight">进入题库</el-button>
      </RouterLink>
    </div>

    <div class="home-grid">
      <section class="panel daily-panel">
        <div class="panel-toolbar">
          <div>
            <h2>每日一题</h2>
            <p class="muted">{{ todayText }}</p>
          </div>
        </div>
        <div v-if="dailyProblem" class="daily-problem">
          <el-tag size="small">{{ dailyProblem.difficulty }}</el-tag>
          <h3>{{ dailyProblem.title }}</h3>
          <p class="muted">#{{ dailyProblem.id }} · {{ dailyProblem.slug }}</p>
          <div class="tag-list">
            <el-tag v-for="tag in splitTags(dailyProblem.tags)" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
          <RouterLink :to="`/problems/${dailyProblem.id}`">
            <el-button type="primary" :icon="ArrowRight">开始</el-button>
          </RouterLink>
        </div>
        <div v-else class="empty-state">暂无可见题目</div>
      </section>

      <section class="panel table-panel">
        <div class="panel-toolbar">
          <div>
            <h2>排行榜</h2>
            <p class="muted">Top 5</p>
          </div>
          <RouterLink to="/leaderboard">
            <el-button :icon="ArrowRight" circle />
          </RouterLink>
        </div>
        <el-table :data="leaderboard" row-key="userId">
          <el-table-column prop="rank" label="#" width="60" />
          <el-table-column label="用户" min-width="150">
            <template #default="{ row }">
              {{ row.displayName || row.username }}
            </template>
          </el-table-column>
          <el-table-column prop="acceptedCount" label="AC" width="70" />
          <el-table-column prop="submissionCount" label="提交" width="80" />
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { fetchLeaderboard, fetchProblems } from '../api/http'
import type { LeaderboardRow, ProblemSummary } from '../types'

const problems = ref<ProblemSummary[]>([])
const leaderboard = ref<LeaderboardRow[]>([])

const todayText = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

const dailyProblem = computed(() => {
  if (problems.value.length === 0) return null
  const now = new Date()
  const start = new Date(now.getFullYear(), 0, 0)
  const day = Math.floor((now.getTime() - start.getTime()) / 86400000)
  return problems.value[day % problems.value.length]
})

onMounted(async () => {
  const [problemData, leaderboardData] = await Promise.all([
    fetchProblems(),
    fetchLeaderboard(5)
  ])
  problems.value = problemData
  leaderboard.value = leaderboardData
})

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}
</script>
