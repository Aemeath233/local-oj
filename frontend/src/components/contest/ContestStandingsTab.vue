<template>
  <div class="panel" style="padding: 20px;">
    <!-- Freeze Warning Banner -->
    <div v-if="isBoardFrozen" class="freeze-warning-banner" :class="{ 'is-admin': isAdmin }">
      <template v-if="isAdmin">
        <span class="icon">🛡️</span>
        <div class="banner-body">
          <h4>管理员视图</h4>
          <p>您正在查看实时完整排行榜（普通参赛选手目前只能看到封榜前的数据，封榜时长为 <b>{{ contest.freezeDurationMinutes }}</b> 分钟）。</p>
        </div>
      </template>
      <template v-else>
        <span class="icon">⚠️</span>
        <div class="banner-body">
          <h4>排行榜已封榜！</h4>
          <p>当前比赛已进入封榜阶段（比赛结束前 <b>{{ contest.freezeDurationMinutes }}</b> 分钟已停止公开更新榜单）。正式完整榜单将在比赛结束后揭晓，祝各位选手取得佳绩！</p>
        </div>
      </template>
    </div>

    <div class="standings-toolbar">
      <el-input
        v-model="standingsSearch"
        placeholder="搜索参赛人..."
        clearable
        style="width: 260px;"
        :prefix-icon="Search"
      />
      <el-button :icon="Refresh" @click="$emit('refresh')" :loading="loading">刷新榜单</el-button>
      <el-button type="success" :icon="Download" @click="$emit('export')" :loading="exporting">导出排行榜</el-button>
    </div>
    <el-table v-loading="loading" :data="filteredStandings" border class="standings-table">
      <el-table-column label="Rank" width="80" align="center" fixed>
        <template #default="{ row }">
          <div class="rank-badge" :class="'rank-' + row.rank">
            {{ row.rank }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="参赛选手" min-width="160" fixed>
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="24" :src="row.avatarUrl">{{ row.username.slice(0, 1).toUpperCase() }}</el-avatar>
            <span class="user-display">{{ row.displayName || row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <!-- If ACM format: show Solved count and Penalty -->
      <template v-if="contest.type === 'ACM'">
        <el-table-column prop="acceptedCount" label="Solved" width="90" align="center">
          <template #default="{ row }">
            <strong class="solved-bold">{{ row.acceptedCount }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="totalPenaltyMinutes" label="Penalty" width="100" align="center">
          <template #default="{ row }">
            <span class="penalty-text">{{ row.totalPenaltyMinutes }}</span>
          </template>
        </el-table-column>
      </template>
      <!-- If OI format: show Total Score and no Penalty -->
      <template v-else>
        <el-table-column prop="totalScore" label="总分" width="100" align="center">
          <template #default="{ row }">
            <strong class="oi-score-bold" style="color: var(--el-color-warning); font-size: 1.15rem;">{{ row.totalScore ?? 0 }}</strong>
          </template>
        </el-table-column>
      </template>

      <!-- Dynamically render one column for each contest problem -->
      <el-table-column
        v-for="p in problems"
        :key="p.id"
        :label="p.sequenceCode"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <div v-if="row.problemDetails[p.id]">
            <!-- ACM format individual cell -->
            <div
              v-if="contest.type === 'ACM'"
              class="standing-cell"
              :class="{
                'cell-ac': row.problemDetails[p.id].accepted,
                'cell-failed': !row.problemDetails[p.id].accepted && row.problemDetails[p.id].failedAttempts > 0,
                'cell-first': row.problemDetails[p.id].firstToSolve
              }"
            >
              <div class="cell-status">
                <span v-if="row.problemDetails[p.id].accepted">
                  +{{ row.problemDetails[p.id].failedAttempts > 0 ? row.problemDetails[p.id].failedAttempts : '' }}
                </span>
                <span v-else-if="row.problemDetails[p.id].failedAttempts > 0">
                  -{{ row.problemDetails[p.id].failedAttempts }}
                </span>
              </div>
              <div v-if="row.problemDetails[p.id].accepted" class="cell-time">
                {{ row.problemDetails[p.id].acElapsedMinutes }}'
              </div>
              <el-tooltip v-if="row.problemDetails[p.id].firstToSolve" content="全场首杀 (First to Solve)" placement="top">
                <span class="first-solve-star">⭐</span>
              </el-tooltip>
            </div>
            <!-- OI format individual cell -->
            <div
              v-else
              class="standing-cell"
              :class="{
                'cell-ac': row.problemDetails[p.id].score === 100,
                'cell-oi-partial': row.problemDetails[p.id].score > 0 && row.problemDetails[p.id].score < 100,
                'cell-failed': row.problemDetails[p.id].score === 0
              }"
            >
              <div class="cell-status oi-score-text">
                {{ row.problemDetails[p.id].score ?? 0 }}
              </div>
            </div>
          </div>
          <div v-else class="standing-cell cell-empty">-</div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import type { Contest, ContestProblemDetail, ContestStandingsRow } from '../../types'

const props = defineProps<{
  contest: Contest
  problems: ContestProblemDetail[]
  standings: ContestStandingsRow[]
  loading: boolean
  exporting: boolean
  isAdmin: boolean
  isBoardFrozen: boolean
}>()

defineEmits<{
  (e: 'refresh'): void
  (e: 'export'): void
}>()

const standingsSearch = ref('')

const filteredStandings = computed(() => {
  const q = standingsSearch.value.trim().toLowerCase()
  if (!q) return props.standings
  return props.standings.filter(
    row => row.username.toLowerCase().includes(q) || (row.displayName && row.displayName.toLowerCase().includes(q))
  )
})
</script>

<style scoped>
.freeze-warning-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(234, 179, 8, 0.1);
  border: 1px solid rgba(234, 179, 8, 0.3);
  padding: 16px 20px;
  border-radius: var(--radius-md);
  margin-bottom: 20px;
  color: var(--el-color-warning);
}
.freeze-warning-banner.is-admin {
  background: rgba(14, 165, 233, 0.1);
  border-color: rgba(14, 165, 233, 0.3);
  color: var(--el-color-primary);
}
.freeze-warning-banner .icon {
  font-size: 24px;
}
.freeze-warning-banner h4 {
  margin: 0 0 4px 0;
  font-size: 15px;
}
.freeze-warning-banner p {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}
.standings-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-display {
  font-weight: 500;
  color: var(--text-primary);
}
.rank-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: bold;
  font-size: 13px;
}
.rank-1 { background: #ffd700; color: #000; }
.rank-2 { background: #c0c0c0; color: #000; }
.rank-3 { background: #cd7f32; color: #000; }
.solved-bold { font-size: 15px; }
.penalty-text { font-size: 13px; color: var(--text-secondary); }
.standing-cell {
  position: relative;
  height: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-radius: 4px;
  font-size: 13px;
}
.cell-ac { background: rgba(34, 197, 94, 0.15); color: var(--el-color-success); font-weight: bold; }
.cell-failed { background: rgba(239, 68, 68, 0.1); color: var(--el-color-danger); }
.cell-first { background: rgba(16, 185, 129, 0.25); border: 1px solid var(--el-color-success); }
.cell-oi-partial { background: rgba(245, 158, 11, 0.15); color: var(--el-color-warning); font-weight: bold; }
.cell-time { font-size: 11px; opacity: 0.8; margin-top: 2px; }
.first-solve-star {
  position: absolute;
  top: -6px;
  right: -6px;
  font-size: 12px;
}
</style>
