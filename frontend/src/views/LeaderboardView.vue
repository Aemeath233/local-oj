<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>排行榜</h1>
        <p>按 AC 题数、提交次数和最后 AC 时间排序</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <section class="panel table-panel">
      <el-table v-loading="loading" :data="rows" row-key="userId">
        <el-table-column prop="rank" label="#" width="80" />
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" :src="row.avatarUrl">
                {{ userFallback(row) }}
              </el-avatar>
              <div>
                <div>{{ row.displayName || row.username }}</div>
                <div class="muted">{{ [row.studentNo, row.major].filter(Boolean).join(' · ') || row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="acceptedCount" label="AC 题数" width="120" />
        <el-table-column prop="submissionCount" label="提交次数" width="120" />
        <el-table-column label="最后 AC" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.lastAcceptedAt) }}
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchLeaderboard } from '../api/http'
import { formatDateTime } from '../utils/time'
import type { LeaderboardRow } from '../types'

const loading = ref(false)
const rows = ref<LeaderboardRow[]>([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchLeaderboard()
  } finally {
    loading.value = false
  }
}

function userFallback(row: LeaderboardRow) {
  return (row.displayName || row.username || 'U').slice(0, 1).toUpperCase()
}
</script>
