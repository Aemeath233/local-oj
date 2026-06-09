<template>
  <div class="problem-page-container" v-loading="loading">
    <div class="workspace-header panel">
      <div class="header-left">
        <el-button size="small" :icon="ArrowLeft" @click="router.push(`/contests/${contestId}`)">返回比赛控制台</el-button>
        <span class="header-divider">/</span>
        <span class="contest-title" v-if="problem">{{ problemCode }} - {{ problem.title }}</span>
      </div>
      <div class="header-right">
        <span v-if="problem" :class="'difficulty-badge ' + (problem.difficulty || 'Easy').toLowerCase()">{{ problem.difficulty }}</span>
        <span class="limits" v-if="problem">{{ problem.timeLimitMs }} ms / {{ Math.round(problem.memoryLimitKb / 1024) }} MB</span>
      </div>
    </div>

    <section class="problem-layout" ref="problemLayoutRef">
      <!-- Left side: Statement -->
      <article v-if="problem" class="statement panel" style="display: flex; flex-direction: column; position: relative; overflow: hidden; padding: 24px 8px 24px 24px;" :style="leftStyle">
        <!-- Maximize Button -->
        <el-tooltip v-if="!isMobile" :content="isMaximized ? '还原布局' : '放大题面'" placement="top">
          <el-button
            class="maximize-btn"
            circle
            :icon="isMaximized ? ScaleToOriginal : FullScreen"
            @click="toggleMaximize"
            size="small"
          />
        </el-tooltip>

        <div class="statement-scroll-area">
          <section class="statement-body" :style="{ fontSize: readerFontSize + 'px', fontFamily: readerFontFamily }">
            <MarkdownView :source="statementMarkdown" />
          </section>

          <!-- If mobile, show a nice info block about writing code on PC -->
          <el-card v-if="isMobile" class="mobile-warning-card" style="margin-top: 20px; margin-bottom: 15px;">
            <div style="display: flex; gap: 15px; align-items: flex-start;">
              <el-icon style="font-size: 24px; color: var(--el-color-warning); margin-top: 2px;"><Monitor /></el-icon>
              <div>
                <h3 style="margin: 0 0 8px 0; font-size: 16px;">建议使用电脑端</h3>
                <p style="margin: 0 0 12px 0; font-size: 14px; color: var(--el-text-color-secondary); line-height: 1.5;">
                  本系统支持在电脑端进行代码编写、调试与提交。建议在电脑浏览器打开当前链接以获得最佳答题体验。
                </p>
                <el-button type="primary" size="small" @click="copyLink">复制题目链接</el-button>
              </div>
            </div>
          </el-card>

          <section v-if="sampleCases.length > 0" class="sample-list">
            <h2>样例</h2>
            <div v-for="(sample, index) in sampleCases" :key="index" class="sample-block">
              <h3>样例 {{ index + 1 }}</h3>
              <div class="sample-grid">
                <div>
                  <div class="sample-header-row">
                    <h4>输入</h4>
                    <div class="sample-actions">
                      <el-button
                        v-if="sample.inputText"
                        type="primary"
                        link
                        size="small"
                        :icon="DocumentCopy"
                        @click="copyText(sample.inputText, '输入已复制')"
                      >
                        复制
                      </el-button>
                      <el-button
                        v-if="sample.inputText && !isMobile"
                        type="success"
                        link
                        size="small"
                        :icon="VideoPlay"
                        @click="fillSelfTest(sample.inputText)"
                      >
                        填入自测
                      </el-button>
                    </div>
                  </div>
                  <pre :class="{ empty: !sample.inputText }">{{ sample.inputText || '无输入' }}</pre>
                </div>
                <div>
                  <div class="sample-header-row">
                    <h4>输出</h4>
                    <div class="sample-actions">
                      <el-button
                        v-if="sample.expectedOutput"
                        type="primary"
                        link
                        size="small"
                        :icon="DocumentCopy"
                        @click="copyText(sample.expectedOutput, '输出已复制')"
                      >
                        复制
                      </el-button>
                    </div>
                  </div>
                  <pre :class="{ empty: !sample.expectedOutput }">{{ sample.expectedOutput || '无输出' }}</pre>
                </div>
              </div>
            </div>
          </section>
        </div>
      </article>

      <!-- Drag Resizable Divider -->
      <div v-if="problem && !isMobile && !isMaximized" class="resize-divider" @mousedown="startDrag">
        <div class="resize-divider-line"></div>
      </div>

      <!-- Right side: Code Editor & Submissions & Custom Stdin Test Console -->
      <aside v-if="!isMobile" v-show="!isMaximized" class="sidebar" :style="rightStyle">
        <!-- Editor Header -->
        <div class="editor-bar panel">
          <div class="bar-left">
            <span class="editor-title">编写代码</span>
            <el-select v-model="language" size="small" style="width: 140px;">
              <el-option label="C17 (O2)" value="C" />
              <el-option label="C++20 (O2)" value="CPP" />
              <el-option label="C++20 (O3)" value="CPP_O3" />
              <el-option label="Java (JDK 21)" value="JAVA" />
              <el-option label="Python 3.12" value="PYTHON" />
              <el-option label="PyPy 3" value="PYPY3" />
            </el-select>
          </div>
          <div class="bar-right">
            <el-button :icon="Brush" :loading="formatting" size="small" @click="handleFormat">简单整理</el-button>
            <el-button :loading="selfTesting" :disabled="cooldownSeconds > 0" size="small" @click="runCustomTest">
              <template #icon>
                <span v-if="cooldownSeconds > 0" class="cooldown-num-icon">{{ cooldownSeconds }}</span>
                <el-icon v-else><Cpu /></el-icon>
              </template>
              自测
            </el-button>
            <el-button type="primary" :loading="submitting" :disabled="cooldownSeconds > 0" size="small" @click="submit">
              <template #icon>
                <span v-if="cooldownSeconds > 0" class="cooldown-num-icon white-num">{{ cooldownSeconds }}</span>
                <el-icon v-else><Upload /></el-icon>
              </template>
              提交
            </el-button>
          </div>
        </div>

        <!-- Monaco Editor Integration Container -->
        <div class="editor-container panel">
          <CodeEditor ref="codeEditorRef" v-model="sourceCode" :language="language" />
        </div>

        <!-- Collapsible Console Area -->
        <section class="console-panel panel" :class="{ expanded: consoleExpanded }">
          <div class="console-header" @click="consoleExpanded = !consoleExpanded">
            <div class="header-title">
              <el-icon><Cpu /></el-icon>
              <span>控制台</span>
              <el-tag v-if="selfTestResult" size="small" type="info">已运行</el-tag>
            </div>
            <el-button :icon="consoleExpanded ? ArrowDown : ArrowUp" link size="small" />
          </div>

          <div v-show="consoleExpanded" class="console-body">
            <el-tabs v-model="activeConsoleTab">
              <el-tab-pane label="自测输入" name="input">
                <el-input
                  v-model="selfTestInput"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入自定义测试数据..."
                  resize="none"
                />
              </el-tab-pane>
              <el-tab-pane label="运行结果" name="result">
                <div v-if="selfTesting" class="self-test-loading" v-loading="true" element-loading-text="正在运行中..." />
                <div v-else-if="selfTestError" class="self-test-result-wrapper">
                  <el-alert :title="selfTestError" type="error" show-icon :closable="false" />
                </div>
                <div v-else-if="selfTestResult" class="self-test-result">
                  <div class="self-test-head" style="padding-bottom: 10px; margin-bottom: 4px; border-bottom: 1px solid var(--border-light);">
                    <span class="self-test-meta" style="font-size: 13px; color: var(--text-muted); font-family: var(--font-sans), sans-serif;">
                      运行时间：<span style="color: var(--text-primary); font-weight: 600; font-family: var(--font-mono), monospace;">{{ selfTestResult.timeMs }} ms</span>
                      <span style="color: var(--border-color); margin: 0 12px;">|</span>
                      占用内存：<span style="color: var(--text-primary); font-weight: 600; font-family: var(--font-mono), monospace;">{{ selfTestResult.memoryKb }} KB</span>
                    </span>
                  </div>
                  <div class="case-output-grid">
                    <div>
                      <h3>stdout (标准输出)</h3>
                      <pre :class="{ empty: !selfTestResult.stdout }">{{ selfTestResult.stdout || '无输出' }}</pre>
                    </div>
                    <div>
                      <h3>stderr / message (错误及消息)</h3>
                      <pre :class="{ empty: !selfTestMessage }">{{ selfTestMessage || '无消息' }}</pre>
                    </div>
                  </div>
                </div>
                <div v-else class="self-test-placeholder">
                  请在“自测输入”中填写数据，然后点击右上角“自测”按钮运行程序。
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </section>

        <!-- Scoped Submission History for current Problem inside current Contest -->
        <div class="mini-submissions-panel">
          <div class="mini-panel-header">
            <span>本题提交记录</span>
            <el-button :icon="Refresh" link size="small" :loading="submissionsLoading" @click="loadSubmissions">刷新</el-button>
          </div>
          <div v-if="submissionsLoading && submissions.length === 0" class="mini-loading">
            <el-icon class="is-loading" style="margin-right: 6px;"><Loading /></el-icon> 加载中...
          </div>
          <div v-else-if="submissions.length === 0" class="mini-empty">
            暂无提交记录
          </div>
          <div v-else class="mini-submissions-list">
            <div
              v-for="sub in submissions"
              :key="sub.id"
              class="mini-submission-item"
              @click="openSubmissionDetail(sub)"
            >
              <div class="mini-sub-left">
                <span class="mini-sub-id">#{{ sub.id }}</span>
                <VerdictTag :status="sub.status" :verdict="sub.verdict" />
              </div>
              <div class="mini-sub-right">
                <span class="mini-sub-meta" v-if="sub.status === 'FINISHED'">
                  {{ sub.timeMs ?? 0 }}ms / {{ sub.memoryKb ?? 0 }}KB
                </span>
                <span class="mini-sub-time">{{ formatRelativeTime(sub.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </section>

    <!-- Submission Details Drawer -->
    <SubmissionDetailDrawer v-model="drawerVisible" :detail="selectedSubmission" :current-code="sourceCode" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Upload, VideoPlay, ArrowLeft, ArrowUp, ArrowDown, Cpu, DocumentCopy, Refresh, Loading, Brush, Monitor, FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CodeEditor from '../components/CodeEditor.vue'
import MarkdownView from '../components/MarkdownView.vue'
import VerdictTag from '../components/VerdictTag.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import {
  fetchContest,
  fetchContestProblem,
  fetchContestProblems,
  runSelfTest,
  submitContestSolution,
  fetchSubmission,
  fetchContestSubmissions,
  fetchContestRegistration,
  formatCode,
  requestSseTicket
} from '../api/http'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import { useAuthStore } from '../stores/auth'
import { parseSubmissionUpdate } from '../utils/submissionEvents'
import type { Language, ProblemDetail, SelfTestResult, SubmissionDetail, SubmissionSummary } from '../types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const problemLayoutRef = ref<HTMLElement | null>(null)
const leftWidthPercent = ref(50)
let startX = 0
let startWidthPercent = 0

// Check if mobile or desktop split screen is active
const isWideScreen = ref(window.innerWidth >= 1041)
const isMobile = ref(window.innerWidth <= 768)
const isMaximized = ref(false)

function handleResize() {
  isWideScreen.value = window.innerWidth >= 1041
  isMobile.value = window.innerWidth <= 768
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
  setTimeout(() => {
    window.dispatchEvent(new Event('resize'))
  }, 100)
}

const leftStyle = computed(() => {
  if (isMaximized.value) {
    return {
      width: '100%',
      flex: '0 0 100%'
    }
  }
  if (!isWideScreen.value || isMobile.value) return {}
  return {
    width: `${leftWidthPercent.value}%`,
    flex: `0 0 ${leftWidthPercent.value}%`
  }
})

const rightStyle = computed(() => {
  if (isMaximized.value) {
    return {
      display: 'none'
    }
  }
  if (!isWideScreen.value || isMobile.value) return {}
  return {
    width: `${100 - leftWidthPercent.value}%`,
    flex: `0 0 ${100 - leftWidthPercent.value}%`
  }
})

function copyLink() {
  navigator.clipboard.writeText(window.location.href)
  ElMessage.success('链接已复制到剪贴板，快去电脑上打开吧！')
}

function startDrag(event: MouseEvent) {
  event.preventDefault()
  startX = event.clientX
  startWidthPercent = leftWidthPercent.value
  
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  
  window.addEventListener('mousemove', doDrag)
  window.addEventListener('mouseup', stopDrag)
}

function doDrag(event: MouseEvent) {
  if (!problemLayoutRef.value) return
  const containerWidth = problemLayoutRef.value.getBoundingClientRect().width
  if (containerWidth === 0) return
  
  const deltaX = event.clientX - startX
  const deltaPercent = (deltaX / containerWidth) * 100
  let newPercent = startWidthPercent + deltaPercent
  
  if (newPercent < 20) newPercent = 20
  if (newPercent > 80) newPercent = 80
  
  leftWidthPercent.value = newPercent
}

function stopDrag() {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  
  window.removeEventListener('mousemove', doDrag)
  window.removeEventListener('mouseup', stopDrag)
  
  // Force Monaco to recalculate layout
  window.dispatchEvent(new Event('resize'))
}

const contestId = computed(() => Number(route.params.contestId))
const problemId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const selfTesting = ref(false)
const message = ref('')
const cooldownSeconds = ref(0)
let cooldownTimer: any = null

const codeEditorRef = ref<any>(null)
const formatting = ref(false)

async function handleFormat() {
  if (!sourceCode.value || sourceCode.value.trim() === '') {
    ElMessage.warning('代码不能为空')
    return
  }
  formatting.value = true
  try {
    const formatted = await formatCode(language.value, sourceCode.value)
    if (codeEditorRef.value?.setValuePreservingHistory) {
      codeEditorRef.value.setValuePreservingHistory(formatted)
    } else {
      sourceCode.value = formatted
    }
    ElMessage.success('代码整理成功')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '整理失败')
  } finally {
    formatting.value = false
  }
}

function startCooldown() {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
  cooldownSeconds.value = 5
  cooldownTimer = window.setInterval(() => {
    cooldownSeconds.value--
    if (cooldownSeconds.value <= 0) {
      if (cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }
  }, 1000)
}
const selfTestInput = ref('')
const selfTestError = ref('')
const selfTestResult = ref<SelfTestResult | null>(null)
const problem = ref<ProblemDetail | null>(null)
const problemCode = ref('')

const language = ref<Language>((localStorage.getItem('coderushoj.editor.defaultLanguage') as Language) || 'CPP')
const sourceCode = ref(templateFor(language.value))

// Reader Typography Configuration States
const readerFontSize = ref(Number(localStorage.getItem('coderushoj.reader.fontSize')) || 15)
const readerFontFamily = ref(localStorage.getItem('coderushoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

const statementMarkdown = computed(() => {
  return problem.value?.description || ''
})
const sampleCases = computed(() => {
  if (!problem.value) {
    return []
  }
  return problem.value.samples
})
const selfTestMessage = computed(() => {
  const result = selfTestResult.value
  if (!result) return ''
  
  let msg = result.stderr || result.message || ''
  
  // If the program had an execution error, display the error verdict as a clear header
  if (result.verdict && result.verdict !== 'AC') {
    const verdictNameMap: Record<string, string> = {
      CE: '编译错误 (Compile Error)',
      RE: '运行错误 (Runtime Error)',
      TLE: '超出时间限制 (Time Limit Exceeded)',
      MLE: '超出内存限制 (Memory Limit Exceeded)',
      OLE: '超出输出限制 (Output Limit Exceeded)',
      IE: '内部错误 (Internal Error)',
      WA: '答案错误 (Wrong Answer)'
    }
    const errorTitle = verdictNameMap[result.verdict] || result.verdict
    const header = `[错误] ${errorTitle}\n`
    if (!msg.includes(errorTitle)) {
      msg = header + (msg ? '\n' + msg : '')
    }
  }
  return msg
})

// UX & Layout reactive variables
const consoleExpanded = ref(false)
const activeConsoleTab = ref('input')
const submissions = ref<SubmissionSummary[]>([])
const submissionsLoading = ref(false)
const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
let submissionsTimerId: number | undefined

const isSwitchingLanguage = ref(false)

watch(language, (newLang, oldLang) => {
  if (oldLang && problemId.value && contestId.value) {
    localStorage.setItem(`coderushoj.draft.contest.${contestId.value}.${problemId.value}.${oldLang}`, sourceCode.value)
  }
  isSwitchingLanguage.value = true
  sourceCode.value = templateFor(newLang)
  isSwitchingLanguage.value = false
})

watch(sourceCode, (newCode) => {
  if (isSwitchingLanguage.value) return
  if (problemId.value && contestId.value) {
    localStorage.setItem(`coderushoj.draft.contest.${contestId.value}.${problemId.value}.${language.value}`, newCode)
  }
})

async function initProblem() {
  loading.value = true
  try {
    // Check if the user is registered
    const contest = await fetchContest(contestId.value)
    const regStatus = await fetchContestRegistration(contestId.value)
    const contestEnded = new Date(contest.endTime).getTime() <= Date.now()
    const isAllowed = authStore.isAdmin || contestEnded || !!(regStatus && regStatus.registered)
    if (!isAllowed) {
      ElMessage.warning('您尚未报名此场比赛，请先报名参战！')
      router.replace(`/contests/${contestId.value}`)
      return
    }

    problem.value = await fetchContestProblem(contestId.value, problemId.value)

    // Resolve problem sequence letter code (like A, B, C)
    const allContestProblems = await fetchContestProblems(contestId.value)
    const match = allContestProblems.find(p => p.id === problemId.value)
    if (match) {
      problemCode.value = match.sequenceCode
    }

    await loadSubmissions()
    clearSelfTest()

    // Auto populate the first sample case input if available
    if (problem.value?.samples && problem.value.samples.length > 0) {
      selfTestInput.value = problem.value.samples[0].inputText
    } else {
      selfTestInput.value = ''
    }
  } catch (error: any) {
    console.error('Failed to initialize problem details', error)
    const errorMsg = error.response?.data?.message || error.message || ''
    if (errorMsg.includes('请先报名') || errorMsg.includes('报名') || error.response?.status === 400 || error.response?.status === 403) {
      ElMessage.warning('您尚未报名此场比赛，请先报名参战！')
      router.replace(`/contests/${contestId.value}`)
    } else {
      ElMessage.error(errorMsg || '加载题目失败')
    }
  } finally {
    loading.value = false
  }
}

let eventSource: EventSource | null = null
let reconnectTimeout: any = null
let isConnecting = false

async function connectSse() {
  if (eventSource || isConnecting) return
  if (!authStore.token) return

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
          if (update.problemId !== undefined && update.problemId !== problemId.value) return
          if (update.contestId !== undefined && update.contestId !== null && update.contestId !== contestId.value) return
          const subId = update.submissionId
          const status = update.status
          const verdict = update.verdict || null

          const existingIndex = submissions.value.findIndex(s => s.id === subId)
          if (existingIndex !== -1) {
            submissions.value[existingIndex].status = status as any
            submissions.value[existingIndex].verdict = verdict as any
          }

          // Auto-update drawer if it's currently showing one of our submissions
          if (drawerVisible.value && selectedSubmission.value && selectedSubmission.value.submission.id === subId) {
            fetchSubmission(subId).then(detail => {
              if (selectedSubmission.value && selectedSubmission.value.submission.id === subId) {
                const curStatus = selectedSubmission.value.submission.status
                const curCaseCount = selectedSubmission.value.cases?.length || 0
                const newStatus = detail.submission.status
                const newCaseCount = detail.cases?.length || 0

                // Guard: Do not overwrite with older state (e.g. finished -> running, or fewer cases)
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
            loadSubmissions(true)
          }
        }
      } catch (err) {
        console.error('Failed to handle SSE message', err)
      }
    })

    eventSource.onerror = (err) => {
      console.error('SSE connection error, scheduled reconnect in 1s:', err)
      disconnectSse()
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      reconnectTimeout = setTimeout(() => {
        connectSse()
      }, 1000)
    }
  } catch (error) {
    console.error('Failed to fetch SSE ticket, scheduled reconnect in 1s:', error)
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
  window.addEventListener('resize', handleResize)
  await initProblem()
  connectSse()
  // Auto-refresh submissions status every 3 seconds to track pending runs!
  submissionsTimerId = window.setInterval(async () => {
    const hasPending = submissions.value.some(s => s.status === 'PENDING' || s.status === 'RUNNING')
    if (hasPending) {
      await loadSubmissions(true)
    }
  }, 3000)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disconnectSse()
  if (submissionsTimerId) {
    window.clearInterval(submissionsTimerId)
  }
  if (cooldownTimer) {
    window.clearInterval(cooldownTimer)
  }
})

watch(problemId, async () => {
  await initProblem()
  sourceCode.value = templateFor(language.value)
})

async function loadSubmissions(isSilent = false) {
  if (!isSilent) {
    submissionsLoading.value = true
  }
  try {
    const list = await fetchContestSubmissions(contestId.value)
    // Filter specifically for this problem
    submissions.value = list.filter(s => s.problemId === problemId.value).slice(0, 10)

    // Auto-update drawer if it's currently showing one of our submissions
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

async function submit() {
  if (!sourceCode.value || sourceCode.value.trim() === '') {
    ElMessage.warning('代码不能为空')
    return
  }
  submitting.value = true
  message.value = ''
  try {
    const submission = await submitContestSolution(contestId.value, problemId.value, language.value, sourceCode.value)
    message.value = `提交 #${submission.id} 已入队`
    ElMessage.success(`提交 #${submission.id} 已入队`)
    startCooldown()

    await loadSubmissions()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '提交失败')
    if (error.response?.data?.message?.includes('频繁')) {
      startCooldown()
    }
  } finally {
    submitting.value = false
  }
}

async function runCustomTest() {
  if (!sourceCode.value || sourceCode.value.trim() === '') {
    ElMessage.warning('代码不能为空')
    return
  }
  consoleExpanded.value = true
  activeConsoleTab.value = 'result'
  selfTesting.value = true
  selfTestError.value = ''
  selfTestResult.value = null
  try {
    selfTestResult.value = await runSelfTest(problemId.value, language.value, sourceCode.value, selfTestInput.value, contestId.value)
    startCooldown()
  } catch (error: any) {
    selfTestError.value = error.response?.data?.message || '自测运行失败'
    if (error.response?.data?.message?.includes('频繁')) {
      startCooldown()
    }
  } finally {
    selfTesting.value = false
  }
}

function clearSelfTest() {
  selfTestResult.value = null
  selfTestError.value = ''
}

async function copyText(text: string, successMsg = '复制成功') {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(successMsg)
  } catch (err) {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success(successMsg)
    } catch (fallbackErr) {
      ElMessage.error('复制失败，请手动复制')
    }
    document.body.removeChild(textarea)
  }
}

function fillSelfTest(text: string) {
  selfTestInput.value = text
  consoleExpanded.value = true
  activeConsoleTab.value = 'input'
  ElMessage.success('已填入自测输入')
}

function templateFor(value: Language) {
  const draft = localStorage.getItem(`coderushoj.draft.contest.${contestId.value}.${problemId.value}.${value}`)
  if (draft !== null) {
    return draft
  }
  return ''
}
</script>

<style scoped>
.problem-page-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 100%;
  margin: 0;
  padding: 0;
}
@media (min-width: 1041px) {
  .problem-page-container {
    height: 100%;
    padding: 16px;
    box-sizing: border-box;
  }
}
.workspace-header {
  padding: 10px 16px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-divider {
  color: var(--el-text-color-placeholder);
}
.contest-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.limits {
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
}
.problem-layout {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 12px;
  flex-grow: 1;
  overflow: hidden;
  height: 0;
}
@media (min-width: 1041px) {
  .problem-layout {
    display: flex;
    gap: 0px;
    align-items: stretch;
  }
}
.statement {
  overflow: hidden;
  padding: 24px 8px 24px 24px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}
.statement-scroll-area {
  flex: 1;
  overflow-y: auto;
  padding-right: 16px;
}
.maximize-btn {
  position: absolute;
  top: 16px;
  right: 24px;
  z-index: 10;
}
/* Resize Divider rules */
.resize-divider {
  width: 14px;
  cursor: col-resize;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 10;
  transition: background-color 0.2s ease;
}
.resize-divider:hover,
.resize-divider:active {
  background-color: var(--border-light);
}
.resize-divider-line {
  width: 2px;
  height: 40px;
  background: var(--border-color);
  border-radius: 1px;
}
.resize-divider:hover .resize-divider-line,
.resize-divider:active .resize-divider-line {
  background: var(--el-color-primary);
}
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  overflow-x: hidden;
}
.editor-bar {
  padding: 10px 16px;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.editor-title {
  font-weight: 650;
  font-size: 0.95rem;
}
.cooldown-num-icon {
  font-family: var(--font-mono), monospace;
  font-weight: bold;
  font-size: 0.8rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.08);
  color: var(--el-text-color-regular);
}

.white-num {
  background: rgba(255, 255, 255, 0.25) !important;
  color: #ffffff !important;
}
.editor-container {
  flex-grow: 1;
  min-height: 280px;
  border-radius: 8px;
  overflow: hidden;
}
.console-panel {
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  transition: max-height 0.25s ease;
  max-height: 48px;
}
.console-panel.expanded {
  max-height: 280px;
}
.console-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.console-body {
  padding: 0 16px 16px 16px;
  overflow-y: auto;
  flex-grow: 1;
}

.self-test-placeholder {
  padding: 32px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 0.85rem;
}
.self-test-loading {
  padding: 40px 0;
}
.self-test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.self-test-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.self-test-meta {
  font-size: 0.85rem;
  font-family: monospace;
  color: var(--el-text-color-secondary);
}
.case-output-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.case-output-grid h3 {
  margin: 0 0 6px 0;
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
}
.case-output-grid pre {
  margin: 0;
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-primary);
  font-family: monospace;
  font-size: 0.85rem;
  white-space: pre-wrap;
  word-break: break-all;
  height: 80px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
}
.case-output-grid pre.empty {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}

.statement-body {
  line-height: 1.6;
}
.statement-body h1, .statement-body h2, .statement-body h3 {
  color: var(--el-text-color-primary);
}

.sample-list {
  margin-top: 24px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 20px;
}
.sample-list h2 {
  margin: 0 0 16px 0;
  font-size: 1.25rem;
}
.sample-block {
  margin-bottom: 20px;
}
.sample-block h3 {
  font-size: 0.95rem;
  margin: 0 0 8px 0;
  color: var(--el-text-color-regular);
}
.sample-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.sample-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.sample-header-row h4 {
  margin: 0;
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}
.sample-actions {
  display: flex;
  gap: 8px;
}
.sample-grid pre {
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-family: monospace;
  font-size: 0.9rem;
  white-space: pre-wrap;
  border: 1px solid var(--el-border-color-light);
  min-height: 48px;
  color: var(--el-text-color-primary);
}

.mini-submissions-panel {
  padding: 16px;
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mini-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 650;
  font-size: 0.9rem;
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 8px;
}
.mini-loading,
.mini-empty {
  padding: 20px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 0.85rem;
}
.mini-submissions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
  overflow-x: hidden;
}
.mini-submission-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s ease;
  border: 1px solid transparent;
}
.mini-submission-item:hover {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
}
.mini-sub-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mini-sub-id {
  font-family: monospace;
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
}
.mini-sub-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.mini-sub-meta {
  font-size: 0.75rem;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}
.mini-sub-time {
  font-size: 0.75rem;
  color: var(--el-text-color-placeholder);
}
@media (max-width: 768px) {
  .problem-layout {
    grid-template-columns: 1fr !important;
    overflow-y: auto !important;
    height: auto !important;
  }
  .statement {
    overflow-y: visible !important;
  }
  .problem-page-container {
    height: auto !important;
  }
}
</style>
