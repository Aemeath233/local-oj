<template>
  <div v-loading="loading" class="contest-detail-container">
    <div v-if="contest" class="contest-header-panel panel">
      <div class="header-main">
        <div class="title-section">
          <h1>{{ contest.title }}</h1>
          <div class="time-meta">
            <span class="time-meta-item">
              {{ formatTime(contest.startTime) }} ~ {{ formatTime(contest.endTime) }}
            </span>
            <span class="divider">|</span>
            <span class="time-meta-item">
              时长: {{ getDurationStr(contest.startTime, contest.endTime) }}
            </span>
          </div>
        </div>
        <div class="countdown-section" :class="timerClass">
          <span class="timer-label">{{ timerLabel }}</span>
          <span class="timer-value">{{ timerValue }}</span>
        </div>
      </div>
    </div>

    <el-tabs v-if="contest" v-model="activeTab" class="contest-tabs">
      <!-- Tab 1: Contest Details / Rules -->
      <el-tab-pane label="比赛说明" name="info">
        <div class="panel statement-card">
          <div class="statement-body">
            <MarkdownView :source="contest.description || '*出题人太懒了，没有填写任何比赛说明。*'" />
          </div>
        </div>

        <!-- Registration Prompt Banner -->
        <div v-if="!isRegisteredOrAdmin" class="registration-prompt-card panel" style="margin-top: 20px; padding: 28px; background: linear-gradient(135deg, #f0fdfa 0%, #ccfbf1 100%); border-color: #99f6e4; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);">
          <div style="display: flex; align-items: flex-start; gap: 18px;">
            <div style="font-size: 32px; line-height: 1;">💡</div>
            <div style="flex-grow: 1;">
              <h3 style="margin: 0 0 8px; font-size: 16px; color: #0f766e; font-weight: 700;">您尚未报名此场比赛</h3>
              <p style="margin: 0 0 14px; font-size: 13.5px; color: #115e59; line-height: 1.6;">
                本场评测比赛包含特定隐藏评测题目，只有<b>报名参赛</b>的用户才能查看题目列表、在线提交评测代码，并实时刷新 ICPC/OI 赛制排行榜单。
              </p>
              <div style="display: flex; align-items: center; gap: 20px;">
                <template v-if="!auth.isLoggedIn">
                  <el-button
                    type="primary"
                    color="#0f766e"
                    style="border: none; font-weight: 600; padding: 12px 24px; border-radius: 8px; font-size: 14px; box-shadow: 0 4px 10px rgba(15, 118, 110, 0.2);"
                    @click="router.push('/login')"
                  >
                    请先登录以报名参赛
                  </el-button>
                </template>
                <template v-else>
                  <el-button
                    v-if="registrationStatus?.canRegister"
                    type="primary"
                    color="#0f766e"
                    style="border: none; font-weight: 600; padding: 12px 24px; border-radius: 8px; font-size: 14px; box-shadow: 0 4px 10px rgba(15, 118, 110, 0.2);"
                    :loading="registering"
                    @click="handleRegister"
                  >
                    立即报名参战
                  </el-button>
                  <el-tag v-else type="info" size="large" style="font-weight: 600; padding: 6px 14px;">
                    报名通道已关闭 (比赛已结束)
                  </el-tag>
                </template>
                <span v-if="registrationStatus" style="font-size: 13px; color: #0d9488; font-weight: 550;">
                  🔥 目前已有 <span style="font-size: 15px; font-weight: 700; color: #0f766e;">{{ registrationStatus.registrationCount }}</span> 人报名参战
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Registered Info Banner -->
        <div v-else-if="!auth.isAdmin && registrationStatus?.registered" class="registration-success-card panel" style="margin-top: 20px; padding: 18px 24px; background: #f8fafc; border-color: #cbd5e1; border-radius: 8px; display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; align-items: center; gap: 10px; color: #475569; font-size: 13.5px;">
            <span>✅ 您已成功报名此场比赛</span>
            <span style="color: #cbd5e1;">|</span>
            <span style="color: #94a3b8; font-size: 12.5px;">报名时间: {{ formatFullTime(registrationStatus.registeredAt || '') }}</span>
          </div>
          <el-tag type="success" effect="light" style="font-weight: 600;">已参赛</el-tag>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Problems List -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" label="题目列表" name="problems">
        <div class="panel">
          <el-table :data="problems" row-key="id" @row-click="openProblem">
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.id)" size="small" effect="light">
                  {{ statusLabel(row.id) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sequenceCode" label="序号" width="90" align="center" />
            <el-table-column prop="title" label="题目名称" min-width="260">
              <template #default="{ row }">
                <div class="problem-title-cell">
                  <span class="title-text">{{ row.title }}</span>
                  <span class="slug-text">{{ row.slug }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.difficulty }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通过比例" width="180" align="center">
              <template #default="{ row }">
                <div class="progress-wrapper">
                  <span class="ratio-text">{{ row.acceptedCount }} / {{ row.submissionCount }}</span>
                  <el-progress
                    :percentage="row.submissionCount > 0 ? Math.round((row.acceptedCount / row.submissionCount) * 100) : 0"
                    :show-text="false"
                    stroke-width="4"
                    status="success"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column width="100" align="right">
              <template #default="{ row }">
                <el-button circle :icon="ArrowRight" @click.stop="router.push(`/contests/${contest.id}/problems/${row.id}`)" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Submissions -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" label="我的提交" name="submissions">
        <div class="panel">
          <div class="submissions-toolbar">
            <el-button :icon="Refresh" @click="loadSubmissions" :loading="submissionsLoading">刷新</el-button>
          </div>
          <el-table v-loading="submissionsLoading" :data="submissions" row-key="id">
            <el-table-column prop="id" label="提交 ID" width="100" />
            <el-table-column label="评测状态" width="150">
              <template #default="{ row }">
                <VerdictTag :status="row.status" :verdict="row.verdict" />
              </template>
            </el-table-column>
            <el-table-column label="题目" min-width="180">
              <template #default="{ row }">
                <span>{{ problemCodeMap[row.problemId] }} - {{ problemTitleMap[row.problemId] || row.problemId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="language" label="语言" width="100" />
            <el-table-column label="运行时间" width="110">
              <template #default="{ row }">
                {{ row.status === 'FINISHED' ? `${row.timeMs ?? 0} ms` : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="运行内存" width="110">
              <template #default="{ row }">
                {{ row.status === 'FINISHED' ? `${Math.round((row.memoryKb ?? 0) / 10.24) / 100} MB` : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">
                {{ formatFullTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column width="90" align="right">
              <template #default="{ row }">
                <el-button circle :icon="DocumentCopy" @click="openSubmissionDetail(row)" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- Tab 4: ICPC Standings -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" label="实时排名" name="standings">
        <div class="panel" style="padding: 20px;">
          <!-- Freeze Warning Banner -->
          <div v-if="isBoardFrozen" class="freeze-warning-banner" :class="{ 'is-admin': auth.isAdmin }">
            <template v-if="auth.isAdmin">
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
            <el-button :icon="Refresh" @click="loadStandings" :loading="standingsLoading">刷新榜单</el-button>
            <el-button type="success" :icon="Download" @click="exportStandings" :loading="exporting">导出排行榜</el-button>
          </div>
          <el-table v-loading="standingsLoading" :data="filteredStandings" border class="standings-table">
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
      </el-tab-pane>
    </el-tabs>

    <!-- Submission Details Drawer -->
    <SubmissionDetailDrawer v-model="drawerVisible" :detail="selectedSubmission" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Refresh, DocumentCopy, Search, Download } from '@element-plus/icons-vue'
import MarkdownView from '../components/MarkdownView.vue'
import VerdictTag from '../components/VerdictTag.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import {
  fetchContest,
  fetchContestProblems,
  fetchContestSubmissions,
  fetchContestLeaderboard,
  fetchSubmission,
  fetchContestRegistration,
  registerContest,
  downloadContestStandings
} from '../api/http'
import { useAuthStore } from '../stores/auth'
import type {
  Contest,
  ContestProblemDetail,
  Submission,
  ContestStandingsRow,
  SubmissionDetail,
  ContestRegistrationStatus
} from '../types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const contestId = computed(() => Number(route.params.id))

const loading = ref(false)
const registering = ref(false)
const registrationStatus = ref<ContestRegistrationStatus | null>(null)

const isRegisteredOrAdmin = computed(() => {
  return auth.isAdmin || timeState.value === 'FINISHED' || !!(registrationStatus.value && registrationStatus.value.registered)
})
const contest = ref<Contest | null>(null)
const problems = ref<ContestProblemDetail[]>([])
const submissions = ref<Submission[]>([])
const standings = ref<ContestStandingsRow[]>([])

const activeTab = ref('problems')
const standingsSearch = ref('')

const submissionsLoading = ref(false)
const standingsLoading = ref(false)

const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)

// live countdown variables
const nowRef = ref(new Date())
let countdownInterval: number | undefined
let submissionsInterval: number | undefined

// Map cache for faster lookup in Submissions tab
const problemCodeMap = computed(() => {
  const map: Record<number, string> = {}
  problems.value.forEach(p => {
    map[p.id] = p.sequenceCode
  })
  return map
})

const problemTitleMap = computed(() => {
  const map: Record<number, string> = {}
  problems.value.forEach(p => {
    map[p.id] = p.title
  })
  return map
})

// Client-calculated user status for problems
const solvedProblemIds = computed(() => {
  const set = new Set<number>()
  submissions.value.forEach(s => {
    if (s.verdict === 'AC') {
      set.add(s.problemId)
    }
  })
  return set
})

const attemptedProblemIds = computed(() => {
  const set = new Set<number>()
  submissions.value.forEach(s => {
    set.add(s.problemId)
  })
  return set
})

// live countdown computations
const timeState = computed(() => {
  if (!contest.value) return 'UPCOMING'
  const start = new Date(contest.value.startTime).getTime()
  const end = new Date(contest.value.endTime).getTime()
  const now = nowRef.value.getTime()

  if (now < start) return 'UPCOMING'
  if (now > end) return 'FINISHED'
  return 'RUNNING'
})

const isBoardFrozen = computed(() => {
  if (!contest.value || !contest.value.freezeDurationMinutes || contest.value.freezeDurationMinutes <= 0) {
    return false
  }
  const end = new Date(contest.value.endTime).getTime()
  const freezeStart = end - contest.value.freezeDurationMinutes * 60 * 1000
  const now = nowRef.value.getTime()
  return now >= freezeStart && now < end
})

const timerLabel = computed(() => {
  const state = timeState.value
  if (state === 'UPCOMING') return '距比赛开始'
  if (state === 'RUNNING') return '距比赛结束'
  return '比赛已结束'
})

const timerValue = ref('00:00:00')
const timerClass = computed(() => {
  const state = timeState.value
  if (state === 'UPCOMING') return 'timer-upcoming'
  if (state === 'RUNNING') return 'timer-running'
  return 'timer-finished'
})

// standins search filter
const filteredStandings = computed(() => {
  const q = standingsSearch.value.trim().toLowerCase()
  if (!q) return standings.value
  return standings.value.filter(
    row => row.username.toLowerCase().includes(q) || (row.displayName && row.displayName.toLowerCase().includes(q))
  )
})

onMounted(async () => {
  await loadAll()
  countdownInterval = window.setInterval(updateTimer, 1000)
})

onUnmounted(() => {
  if (countdownInterval) {
    window.clearInterval(countdownInterval)
  }
  if (submissionsInterval) {
    window.clearInterval(submissionsInterval)
  }
})

watch(activeTab, (tab) => {
  if (tab === 'submissions') {
    loadSubmissions()
  } else if (tab === 'standings') {
    loadStandings()
  }
})

// Poll submissions in ContestDetailView when tab is active and there's a pending run
watch(
  [activeTab, submissions],
  ([tab, list]) => {
    const hasRunning = list.some(s => s.status === 'PENDING' || s.status === 'RUNNING')
    if (tab === 'submissions' && hasRunning && !submissionsInterval) {
      submissionsInterval = window.setInterval(() => loadSubmissions(true), 3000)
    } else if ((tab !== 'submissions' || !hasRunning) && submissionsInterval) {
      window.clearInterval(submissionsInterval)
      submissionsInterval = undefined
    }
  },
  { deep: true }
)

watch(timeState, (newVal, oldVal) => {
  if (oldVal === 'UPCOMING' && newVal === 'RUNNING') {
    loadAll()
  }
})

async function loadAll() {
  loading.value = true
  try {
    contest.value = await fetchContest(contestId.value)
    // Fetch registration status
    registrationStatus.value = await fetchContestRegistration(contestId.value)

    // Fetch problems only if contest has started and user is registered or admin
    const start = new Date(contest.value.startTime).getTime()
    if (new Date().getTime() >= start) {
      if (isRegisteredOrAdmin.value) {
        problems.value = await fetchContestProblems(contestId.value)
        await loadSubmissions()

        const queryTab = route.query.tab as string
        if (queryTab && ['info', 'problems', 'submissions', 'standings'].includes(queryTab)) {
          activeTab.value = queryTab
        } else {
          activeTab.value = 'problems'
        }
      } else {
        activeTab.value = 'info'
      }
    } else {
      activeTab.value = 'info'
    }
    updateTimer()
  } catch (err) {
    console.error('Failed to load contest details', err)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  registering.value = true
  try {
    const status = await registerContest(contestId.value)
    registrationStatus.value = status
    ElMessage.success('报名成功！祝您在比赛中取得好成绩！')
    await loadAll()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '报名失败，请稍后重试')
  } finally {
    registering.value = false
  }
}

async function loadSubmissions(isSilent = false) {
  if (!isSilent) {
    submissionsLoading.value = true
  }
  try {
    submissions.value = await fetchContestSubmissions(contestId.value)

    // Auto-update drawer if open
    if (drawerVisible.value && selectedSubmission.value) {
      selectedSubmission.value = await fetchSubmission(selectedSubmission.value.submission.id)
    }
  } catch (err) {
    console.error(err)
  } finally {
    if (!isSilent) {
      submissionsLoading.value = false
    }
  }
}

async function loadStandings() {
  standingsLoading.value = true
  try {
    standings.value = await fetchContestLeaderboard(contestId.value)
  } catch (err) {
    console.error(err)
  } finally {
    standingsLoading.value = false
  }
}

const exporting = ref(false)

async function exportStandings() {
  if (!contest.value) return
  exporting.value = true
  try {
    const blob = await downloadContestStandings(contestId.value)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `contest-${contestId.value}-standings.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success('排行榜已成功导出为 CSV！')
  } catch (err) {
    console.error(err)
    ElMessage.error('导出排行榜失败，请稍后重试。')
  } finally {
    exporting.value = false
  }
}

async function openSubmissionDetail(row: Submission) {
  loading.value = true
  try {
    selectedSubmission.value = await fetchSubmission(row.id)
    drawerVisible.value = true
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

function updateTimer() {
  nowRef.value = new Date()
  if (!contest.value) return

  const start = new Date(contest.value.startTime).getTime()
  const end = new Date(contest.value.endTime).getTime()
  const now = nowRef.value.getTime()

  let diff = 0
  if (now < start) {
    diff = start - now
  } else if (now < end) {
    diff = end - now
  } else {
    timerValue.value = 'FINISHED'
    return
  }

  const secs = Math.floor((diff / 1000) % 60)
  const mins = Math.floor((diff / 60000) % 60)
  const hours = Math.floor(diff / 3600000)

  const hStr = hours.toString().padStart(2, '0')
  const mStr = mins.toString().padStart(2, '0')
  const sStr = secs.toString().padStart(2, '0')

  timerValue.value = `${hStr}:${mStr}:${sStr}`
}

function openProblem(row: ContestProblemDetail) {
  router.push(`/contests/${contestId.value}/problems/${row.id}`)
}

function statusLabel(problemId: number) {
  if (solvedProblemIds.value.has(problemId)) return '已通过'
  if (attemptedProblemIds.value.has(problemId)) return '尝试过'
  return '未尝试'
}

function statusType(problemId: number) {
  if (solvedProblemIds.value.has(problemId)) return 'success'
  if (attemptedProblemIds.value.has(problemId)) return 'warning'
  return 'info'
}

function formatTime(timeStr: string) {
  return new Date(timeStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatFullTime(timeStr: string) {
  return new Date(timeStr).toLocaleString('zh-CN')
}

function getDurationStr(startStr: string, endStr: string) {
  const start = new Date(startStr).getTime()
  const end = new Date(endStr).getTime()
  const diffMs = end - start
  const diffHours = Math.floor(diffMs / 3600000)
  const diffMins = Math.round((diffMs % 3600000) / 60000)

  if (diffHours === 0) return `${diffMins} 分钟`
  return `${diffHours} 小时 ${diffMins} 分钟`
}
</script>

<style scoped>
.contest-detail-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 12px 0 32px 0;
}
.contest-header-panel {
  padding: 24px;
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
}
.header-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
}
.title-section h1 {
  margin: 0 0 10px 0;
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.time-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.9rem;
  color: var(--el-text-color-secondary);
}
.time-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.meta-icon-img {
  width: 15px;
  height: 15px;
  object-fit: contain;
}
.divider {
  color: var(--el-border-color);
}
.countdown-section {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding: 12px 20px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  min-width: 160px;
}
.timer-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.timer-value {
  font-size: 1.6rem;
  font-family: monospace;
  font-weight: bold;
}
.timer-running .timer-value {
  color: var(--el-color-success);
  text-shadow: 0 0 8px rgba(103, 194, 58, 0.2);
  animation: pulse-timer 2s infinite alternate;
}
.timer-upcoming .timer-value {
  color: var(--el-color-primary);
}
.timer-finished .timer-value {
  color: var(--el-text-color-placeholder);
}

@keyframes pulse-timer {
  0% { opacity: 0.9; }
  100% { opacity: 1; }
}

.contest-tabs {
  background: transparent;
}

.statement-card {
  padding: 32px;
  border-radius: 12px;
  line-height: 1.6;
}

.problem-title-cell {
  display: flex;
  flex-direction: column;
}
.title-text {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 0.95rem;
}
.slug-text {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}
.progress-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 120px;
  margin: 0 auto;
}
.ratio-text {
  font-size: 0.8rem;
  color: var(--el-text-color-regular);
}

.submissions-toolbar,
.standings-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
}

.standings-table {
  border-radius: 8px;
  overflow: hidden;
}
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-weight: bold;
  font-size: 0.9rem;
}
.rank-1 {
  background: gold;
  color: #5c3b00;
}
.rank-2 {
  background: silver;
  color: #444;
}
.rank-3 {
  background: #cd7f32;
  color: #fff;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-display {
  font-weight: 550;
  color: var(--el-text-color-primary);
}
.solved-bold {
  color: var(--el-color-success);
  font-size: 1.1rem;
}
.penalty-text {
  font-family: monospace;
  color: var(--el-text-color-regular);
}

.standing-cell {
  width: 100%;
  height: 100%;
  padding: 6px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  font-family: monospace;
  font-size: 0.9rem;
  border-radius: 4px;
}
.cell-ac {
  background: rgba(103, 194, 58, 0.12) !important;
  color: var(--el-color-success);
  border: 1px solid rgba(103, 194, 58, 0.3);
}
.cell-failed {
  background: rgba(245, 108, 108, 0.12) !important;
  color: var(--el-color-danger);
  border: 1px solid rgba(245, 108, 108, 0.3);
}
.cell-first {
  background: rgba(230, 162, 60, 0.15) !important;
  color: #b88230;
  border: 1px solid rgba(230, 162, 60, 0.5) !important;
  box-shadow: 0 0 6px rgba(230, 162, 60, 0.2);
}
.cell-empty {
  color: var(--el-text-color-placeholder);
}
.cell-status {
  font-weight: bold;
}
.cell-time {
  font-size: 0.75rem;
  opacity: 0.85;
}
.first-solve-star {
  position: absolute;
  top: 2px;
  right: 2px;
  font-size: 0.75rem;
}

.cell-oi-partial {
  background: rgba(230, 162, 60, 0.12) !important;
  color: var(--el-color-warning);
  border: 1px solid rgba(230, 162, 60, 0.3);
}
.oi-score-text {
  font-weight: 700;
  font-size: 1rem;
}
.oi-score-bold {
  font-weight: bold;
}

.freeze-warning-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  line-height: 1.5;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.25);
}
.freeze-warning-banner.is-admin {
  background: rgba(14, 165, 233, 0.08);
  border: 1px solid rgba(14, 165, 233, 0.25);
}
.freeze-warning-banner .icon {
  font-size: 1.3rem;
  line-height: 1.2;
}
.freeze-warning-banner .banner-body h4 {
  margin: 0 0 4px 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: #b45309;
}
.freeze-warning-banner.is-admin .banner-body h4 {
  color: #0369a1;
}
.freeze-warning-banner .banner-body p {
  margin: 0;
  font-size: 0.85rem;
  color: #78350f;
}
.freeze-warning-banner.is-admin .banner-body p {
  color: #075985;
}
</style>
