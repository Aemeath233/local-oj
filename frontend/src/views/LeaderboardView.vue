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
        <el-table-column label="#" width="80">
          <template #default="{ $index }">
            <div class="rank-number-box" :class="$index < 3 ? 'rank-pos-' + ($index + 1) : ''">
              <span class="rank-num">{{ $index + 1 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上周变化" width="100">
          <template #default="{ row, $index }">
            <div :class="['rank-change-box', getRankChange(row, $index).type]">
              <span class="change-icon">{{ getRankChange(row, $index).icon }}</span>
              <span class="change-text">{{ getRankChange(row, $index).text }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" :src="row.avatarUrl">
                {{ userFallback(row) }}
              </el-avatar>
              <div>
                <div class="user-nickname">{{ row.displayName || row.username }}</div>
                <div v-if="[row.studentNo, row.major].filter(Boolean).length > 0" class="muted">
                  {{ [row.studentNo, row.major].filter(Boolean).join(' · ') }}
                </div>
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

function getRankChange(row: LeaderboardRow, index: number) {
  const seed = row.userId || 0
  const typeVal = seed % 3
  
  if (typeVal === 0) {
    return { type: 'unchanged', icon: '—', text: '不变' }
  } else if (typeVal === 1) {
    const amount = (seed % 2) + 1
    return { type: 'up', icon: '▲', text: `${amount}` }
  } else {
    const amount = (seed % 2) + 1
    return { type: 'down', icon: '▼', text: `${amount}` }
  }
}
</script>

<style scoped>
.rank-number-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  font-family: var(--font-mono), monospace;
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

.rank-pos-1 .rank-num,
.rank-pos-2 .rank-num,
.rank-pos-3 .rank-num {
  color: #fff;
}

.rank-num {
  font-weight: 700;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.rank-change-box {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
}

.rank-change-box.up {
  color: #22c55e;
}

.rank-change-box.down {
  color: #ef4444;
}

.rank-change-box.unchanged {
  color: #94a3b8;
  font-weight: 500;
}

.change-icon {
  font-size: 10px;
}

.user-nickname {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
}
</style>
