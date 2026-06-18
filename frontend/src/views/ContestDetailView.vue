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
        <ContestInfoTab
          :contest="contest"
          :registration-status="registrationStatus"
          :is-registered-or-admin="isRegisteredOrAdmin"
          :registering="registering"
          @register="handleRegister"
        />
      </el-tab-pane>

      <!-- Tab 2: Problems List -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" label="题目列表" name="problems">
        <ContestProblemsTab
          :problems="problems"
          :contest-id="contest.id"
          :solved-problem-ids="solvedProblemIds"
          :attempted-problem-ids="attemptedProblemIds"
        />
      </el-tab-pane>

      <!-- Tab 3: Submissions -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" :label="submissionsTabLabel" name="submissions">
        <ContestSubmissionsTab
          :submissions="submissions"
          :loading="submissionsLoading"
          :show-user-column="showUserColumn"
          :problem-code-map="problemCodeMap"
          :problem-title-map="problemTitleMap"
          @refresh="loadSubmissions"
          @open-detail="openSubmissionDetail"
        />
      </el-tab-pane>

      <!-- Tab 4: ICPC Standings -->
      <el-tab-pane v-if="isRegisteredOrAdmin && timeState !== 'UPCOMING'" label="实时排名" name="standings">
        <ContestStandingsTab
          :contest="contest"
          :problems="problems"
          :standings="standings"
          :loading="standingsLoading"
          :exporting="exporting"
          :is-admin="auth.isAdmin"
          :is-board-frozen="isBoardFrozen"
          @refresh="loadStandings"
          @export="exportStandings"
        />
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
import ContestInfoTab from '../components/contest/ContestInfoTab.vue'
import ContestProblemsTab from '../components/contest/ContestProblemsTab.vue'
import ContestSubmissionsTab from '../components/contest/ContestSubmissionsTab.vue'
import ContestStandingsTab from '../components/contest/ContestStandingsTab.vue'
import { fetchContest, fetchContestProblems, fetchContestSubmissions, fetchContestLeaderboard, fetchContestRegistration, registerContest, downloadContestStandings } from '../api/contest'
import { fetchSubmission, requestSseTicket } from '../api/submission'
import { useAuthStore } from '../stores/auth'
import { parseSubmissionUpdate } from '../utils/submissionEvents'
import type {
  Contest,
  ContestProblemDetail,
  Submission,
  ContestStandingsRow,
  SubmissionDetail,
  ContestRegistrationStatus,
  SubmissionSummary
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
const submissions = ref<SubmissionSummary[]>([])
const standings = ref<ContestStandingsRow[]>([])

const activeTab = ref('problems')
const standingsSearch = ref('')

const submissionsTabLabel = computed(() => {
  return timeState.value === 'FINISHED' ? '提交记录' : '我的提交'
})

const showUserColumn = computed(() => {
  return auth.isAdmin || timeState.value === 'FINISHED'
})

const submissionsLoading = ref(false)
const standingsLoading = ref(false)

const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)

// live countdown variables
const nowRef = ref(new Date())
let countdownInterval: number | undefined

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

let eventSource: EventSource | null = null
let reconnectTimeout: any = null
let isConnecting = false

async function connectSse() {
  if (eventSource || isConnecting) return
  if (!auth.token) return

  isConnecting = true
  try {
    const ticket = await requestSseTicket()
    if (!isConnecting || eventSource) return

    const sseUrl = `/api/submissions/live?ticket=${encodeURIComponent(ticket)}`
    eventSource = new EventSource(sseUrl)

    eventSource.addEventListener('update', (event) => {
      try {
        const update = parseSubmissionUpdate(event.data)
        if (update) {
          if (update.contestId !== undefined && update.contestId !== null && update.contestId !== contestId.value) return
          if (update.contestId === null) return
          const subId = update.submissionId
          const status = update.status
          const verdict = update.verdict || null

          const existing = submissions.value.find(s => s.id === subId)
          if (existing) {
            existing.status = status as any
            existing.verdict = verdict as any
          } else {
            // If a new submission came in, silent refresh list
            loadSubmissions(true)
          }

          if (drawerVisible.value && selectedSubmission.value && selectedSubmission.value.submission.id === subId) {
            fetchSubmission(subId).then(detail => {
              if (selectedSubmission.value && selectedSubmission.value.submission.id === subId) {
                const curStatus = selectedSubmission.value.submission.status
                const curCaseCount = selectedSubmission.value.cases?.length || 0
                const newStatus = detail.submission.status
                const newCaseCount = detail.cases?.length || 0

                // Guard: Do not overwrite with older state
                if (curStatus === 'FINISHED' && newStatus !== 'FINISHED') {
                  return
                }
                if (newCaseCount < curCaseCount) {
                  return
                }
                selectedSubmission.value = detail
              }
            }).catch(console.error)
          }

          if (status === 'FINISHED') {
            // Silent refresh to populate time, memory, score
            loadSubmissions(true)
          }
        }
      } catch (err) {
        console.error('Failed to handle SSE message in ContestDetailView', err)
      }
    })

    eventSource.onerror = (err) => {
      console.error('SSE connection error in ContestDetailView, scheduled reconnect in 1s:', err)
      disconnectSse()
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      reconnectTimeout = setTimeout(() => {
        connectSse()
      }, 1000)
    }
  } catch (error) {
    console.error('Failed to fetch SSE ticket in ContestDetailView, scheduled reconnect in 1s:', error)
    if (reconnectTimeout) clearTimeout(reconnectTimeout)
    reconnectTimeout = setTimeout(() => {
      connectSse()
    }, 1000)
  } finally {
    isConnecting = false
  }
}

function disconnectSse() {
  isConnecting = false
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

onMounted(async () => {
  await loadAll()
  countdownInterval = window.setInterval(updateTimer, 1000)
  if (activeTab.value === 'submissions') {
    connectSse()
  }
})

onUnmounted(() => {
  if (countdownInterval) {
    window.clearInterval(countdownInterval)
  }
  disconnectSse()
})

watch(activeTab, (tab) => {
  if (tab === 'submissions') {
    loadSubmissions()
    connectSse()
  } else {
    disconnectSse()
    if (tab === 'standings') {
      loadStandings()
    }
  }
})

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

async function openSubmissionDetail(row: SubmissionSummary) {
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
