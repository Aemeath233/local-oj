<template>
  <section class="page-stack animate-fade-in">
    <div class="page-heading">
      <div>
        <h1>排行榜</h1>
        <p>按 AC 题数、提交次数和最后 AC 时间排序</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <!-- My Rank Card -->
    <div v-if="myRankRow" class="my-rank-panel panel">
      <div class="my-rank-header">
        <span class="my-rank-tag">MY RANKING</span>
        <h3>我的排名情况</h3>
      </div>
      <div class="my-rank-content">
        <div class="my-rank-left">
          <div class="rank-number-box" :class="myRankRow.rank <= 3 ? 'rank-pos-' + myRankRow.rank : 'rank-pos-other'">
            <span class="rank-num">{{ myRankRow.rank }}</span>
          </div>
          <div class="my-rank-user">
            <el-avatar :size="38" :src="myRankRow.avatarUrl">
              {{ userFallback(myRankRow) }}
            </el-avatar>
            <div class="user-details">
              <div class="user-nick">{{ myRankRow.displayName || myRankRow.username }}</div>
              <div v-if="[myRankRow.studentNo, myRankRow.major].filter(Boolean).length > 0" class="user-sub">
                {{ [myRankRow.studentNo, myRankRow.major].filter(Boolean).join(' · ') }}
              </div>
            </div>
          </div>
        </div>
        <div class="my-rank-stats">
          <div class="my-rank-stat-item">
            <span class="stat-lbl">AC 题数</span>
            <strong class="stat-val">{{ myRankRow.acceptedCount }}</strong>
          </div>
          <div class="my-rank-stat-divider" />
          <div class="my-rank-stat-item">
            <span class="stat-lbl">总提交数</span>
            <strong class="stat-val">{{ myRankRow.submissionCount }}</strong>
          </div>
          <div class="my-rank-stat-divider" />
          <div class="my-rank-stat-item">
            <span class="stat-lbl">最后 AC</span>
            <strong class="stat-val">{{ formatDateTime(myRankRow.lastAcceptedAt) || '暂无通过记录' }}</strong>
          </div>
        </div>
      </div>
    </div>

    <section class="panel table-panel">
      <el-table v-loading="loading" :data="pagedRows" row-key="userId">
        <el-table-column label="#" width="80">
          <template #default="{ row }">
            <div class="rank-number-box" :class="row.rank <= 3 ? 'rank-pos-' + row.rank : ''">
              <span class="rank-num">{{ row.rank }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上周变化" width="100">
          <template #default="{ row }">
            <div :class="['rank-change-box', getRankChange(row).type]">
              <span class="change-icon">{{ getRankChange(row).icon }}</span>
              <span class="change-text">{{ getRankChange(row).text }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <router-link :to="`/user/${row.userId}`" class="user-profile-link">
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
            </router-link>
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

      <!-- Pagination Footer -->
      <div v-if="rows.length > pageSize" class="table-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="rows.length"
          layout="total, prev, pager, next"
          background
        />
        <div class="custom-jumper">
          <span class="jumper-label">前往</span>
          <el-input-number
            v-model="jumpPage"
            :min="1"
            :max="Math.ceil(rows.length / pageSize)"
            :controls="false"
            size="small"
            class="jumper-input"
            @keyup.enter="handleJump"
          />
          <span class="jumper-label">页</span>
          <el-button size="small" type="primary" class="jumper-btn" @click="handleJump">跳转</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onActivated, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchLeaderboard, fetchMyRank } from '../api/http'
import { formatDateTime } from '../utils/time'
import { shouldRefreshSection, forceUpdateSectionVersion } from '../utils/versionCheck'
import { useAuthStore } from '../stores/auth'
import type { LeaderboardRow } from '../types'

const auth = useAuthStore()
const loading = ref(false)
const rows = ref<LeaderboardRow[]>([])
const myRankRow = ref<LeaderboardRow | null>(null)

const currentPage = ref(1)
const pageSize = ref(20)
const jumpPage = ref(1)

watch(currentPage, (val) => {
  jumpPage.value = val
})

function handleJump() {
  const maxPage = Math.ceil(rows.value.length / pageSize.value)
  if (jumpPage.value && jumpPage.value >= 1 && jumpPage.value <= maxPage) {
    currentPage.value = jumpPage.value
  }
}

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = currentPage.value * pageSize.value
  return rows.value.slice(start, end)
})

onMounted(() => {
  load()
  forceUpdateSectionVersion('leaderboard')
})

onActivated(async () => {
  if (await shouldRefreshSection('leaderboard')) {
    await load(true)
  }
})

async function load(isSilent: boolean = false) {
  if (!isSilent) {
    loading.value = true
  }
  try {
    if (!isSilent) {
      forceUpdateSectionVersion('leaderboard')
    }
    rows.value = await fetchLeaderboard()
    myRankRow.value = auth.isLoggedIn ? await fetchMyRank() : null
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

function userFallback(row: LeaderboardRow) {
  return (row.displayName || row.username || 'U').slice(0, 1).toUpperCase()
}

function getRankChange(row: LeaderboardRow) {
  if (row.rankChange === undefined || row.rankChange === null) {
    return { type: 'unchanged', icon: '—', text: '不变' }
  }
  if (row.rankChange > 0) {
    return { type: 'up', icon: '▲', text: `${row.rankChange}` }
  } else if (row.rankChange < 0) {
    return { type: 'down', icon: '▼', text: `${Math.abs(row.rankChange)}` }
  } else {
    return { type: 'unchanged', icon: '—', text: '不变' }
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

/* ===== My Rank Card Styles ===== */
.my-rank-panel {
  background: linear-gradient(135deg, #f0fdfa 0%, var(--bg-surface) 100%);
  border: 1px solid #99f6e4;
  padding: 20px 24px;
  display: grid;
  gap: 16px;
  box-shadow: 0 4px 20px -2px rgba(13, 148, 136, 0.06);
}

.my-rank-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.my-rank-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 750;
  color: #0f172a;
}

.my-rank-tag {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
  background: #ccfbf1;
  color: #0d9488;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--font-mono), monospace;
}

.my-rank-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.my-rank-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.my-rank-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-nick {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.user-sub {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.my-rank-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  padding: 10px 20px;
  border-radius: 10px;
}

.my-rank-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-lbl {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
  margin-bottom: 2px;
}

.stat-val {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.my-rank-stat-divider {
  width: 1px;
  height: 24px;
  background: var(--border-color);
}

.rank-pos-other {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #cbd5e1;
}

/* ===== Pagination ===== */
.table-pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-top: 10px;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.custom-jumper {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.jumper-input {
  width: 50px !important;
}

.jumper-input :deep(.el-input__inner) {
  text-align: center;
  padding: 0 4px;
}

.jumper-btn {
  margin-left: 2px;
}

/* Dark mode overrides */
html.dark .my-rank-panel {
  background: linear-gradient(135deg, rgba(13, 148, 136, 0.08) 0%, var(--bg-surface) 100%) !important;
  border-color: rgba(13, 148, 136, 0.25) !important;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.2) !important;
}

html.dark .my-rank-tag {
  background: rgba(13, 148, 136, 0.2) !important;
  color: #2dd4bf !important;
}

html.dark .user-nick,
html.dark .stat-val,
html.dark .my-rank-header h3 {
  color: var(--text-primary) !important;
}

html.dark .rank-pos-other {
  background: var(--bg-muted) !important;
  color: var(--text-secondary) !important;
  border-color: var(--border-color) !important;
}

.user-profile-link {
  text-decoration: none;
  color: inherit;
  display: inline-block;
}
.user-profile-link:hover .user-nickname {
  color: var(--primary);
  text-decoration: underline;
}
</style>
