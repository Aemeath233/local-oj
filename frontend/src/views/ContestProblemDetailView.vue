<template>
  <div class="problem-page-container" v-loading="loading">
    <div class="workspace-header panel">
      <div class="header-left">
        <el-button size="small" @click="router.push(`/contests/${contestId}`)">⬅️ 返回比赛控制台</el-button>
        <span class="header-divider">/</span>
        <span class="contest-title" v-if="problem">{{ problemCode }} - {{ problem.title }}</span>
      </div>
      <div class="header-right">
        <span v-if="problem" :class="'difficulty-badge ' + (problem.difficulty || 'Easy').toLowerCase()">{{ problem.difficulty }}</span>
        <span class="limits" v-if="problem">{{ problem.timeLimitMs }} ms / {{ Math.round(problem.memoryLimitKb / 1024) }} MB</span>
      </div>
    </div>

    <section class="problem-layout">
      <!-- Left side: Statement -->
      <article v-if="problem" class="statement panel">
        <section class="statement-body" :style="{ fontSize: readerFontSize + 'px', fontFamily: readerFontFamily }">
          <MarkdownView :source="statementMarkdown" />
        </section>

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
                      v-if="sample.inputText"
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
      </article>

      <!-- Right side: Code Editor & Submissions & Custom Stdin Test Console -->
      <aside class="sidebar">
        <!-- Editor Header -->
        <div class="editor-bar panel">
          <div class="bar-left">
            <span class="editor-title">✏️ 编写代码</span>
            <el-select v-model="language" size="small" style="width: 120px;">
              <el-option label="C (GCC)" value="C" />
              <el-option label="C++ (G++)" value="CPP" />
              <el-option label="Java (JDK 21)" value="JAVA" />
              <el-option label="Python 3" value="PYTHON" />
            </el-select>
          </div>
          <div class="bar-right">
            <el-button :loading="selfTesting" :icon="Cpu" @click="runCustomTest">自测</el-button>
            <el-button :loading="submitting" type="primary" :icon="Upload" @click="submit">提交</el-button>
          </div>
        </div>

        <!-- Monaco Editor Integration Container -->
        <div class="editor-container panel">
          <CodeEditor v-model="sourceCode" :language="language" />
        </div>

        <!-- Collapsible Console Area -->
        <section class="console-panel panel" :class="{ expanded: consoleExpanded }">
          <div class="console-header" @click="consoleExpanded = !consoleExpanded">
            <div class="header-title">
              <span>🛠️ 控制台</span>
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
                  <div class="self-test-head">
                    <VerdictTag status="FINISHED" :verdict="selfTestResult.verdict" />
                    <span class="self-test-meta">{{ selfTestResult.timeMs }} ms / {{ selfTestResult.memoryKb }} KB</span>
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
import { Upload, VideoPlay, ArrowUp, ArrowDown, Cpu, DocumentCopy, Refresh, Loading } from '@element-plus/icons-vue'
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
  fetchContestRegistration
} from '../api/http'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import { useAuthStore } from '../stores/auth'
import type { Language, ProblemDetail, SelfTestResult, Submission, SubmissionDetail } from '../types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const contestId = computed(() => Number(route.params.contestId))
const problemId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const selfTesting = ref(false)
const message = ref('')
const selfTestInput = ref('')
const selfTestError = ref('')
const selfTestResult = ref<SelfTestResult | null>(null)
const problem = ref<ProblemDetail | null>(null)
const problemCode = ref('')

const language = ref<Language>((localStorage.getItem('localoj.editor.defaultLanguage') as Language) || 'CPP')
const sourceCode = ref(templateFor(language.value))

// Reader Typography Configuration States
const readerFontSize = ref(Number(localStorage.getItem('localoj.reader.fontSize')) || 15)
const readerFontFamily = ref(localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

const statementMarkdown = computed(() => {
  return problem.value?.description || ''
})
const sampleCases = computed(() => {
  if (!problem.value) {
    return []
  }
  return problem.value.samples
})
const selfTestMessage = computed(() => selfTestResult.value?.stderr || selfTestResult.value?.message || '')

// UX & Layout reactive variables
const consoleExpanded = ref(false)
const activeConsoleTab = ref('input')
const submissions = ref<Submission[]>([])
const submissionsLoading = ref(false)
const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
let submissionsTimerId: number | undefined

watch(language, (value) => {
  sourceCode.value = templateFor(value)
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

onMounted(async () => {
  await initProblem()
  // Auto-refresh submissions status every 3 seconds to track pending runs!
  submissionsTimerId = window.setInterval(async () => {
    const hasPending = submissions.value.some(s => s.status === 'PENDING' || s.status === 'RUNNING')
    if (hasPending) {
      await loadSubmissions(true)
    }
  }, 3000)
})

onUnmounted(() => {
  if (submissionsTimerId) {
    window.clearInterval(submissionsTimerId)
  }
})

watch(problemId, async () => {
  await initProblem()
})

async function loadSubmissions(isSilent = false) {
  if (!isSilent) {
    submissionsLoading.value = true
  }
  try {
    const list = await fetchContestSubmissions(contestId.value)
    // Filter specifically for this problem
    submissions.value = list.filter(s => s.problemId === problemId.value)

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

async function submit() {
  submitting.value = true
  message.value = ''
  try {
    const submission = await submitContestSolution(contestId.value, problemId.value, language.value, sourceCode.value)
    message.value = `提交 #${submission.id} 已入队`
    ElMessage.success(`提交 #${submission.id} 已入队`)
    await loadSubmissions()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function runCustomTest() {
  consoleExpanded.value = true
  activeConsoleTab.value = 'result'
  selfTesting.value = true
  selfTestError.value = ''
  selfTestResult.value = null
  try {
    selfTestResult.value = await runSelfTest(problemId.value, language.value, sourceCode.value, selfTestInput.value, contestId.value)
  } catch (error: any) {
    selfTestError.value = error.response?.data?.message || '自测运行失败'
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
  const custom = localStorage.getItem(`localoj.template.${value}`)
  if (custom !== null) {
    return custom
  }
  if (value === 'PYTHON') {
    return 'a, b = map(int, input().split())\nprint(a + b)\n'
  }
  if (value === 'JAVA') {
    return 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        int a = scanner.nextInt();\n        int b = scanner.nextInt();\n        System.out.println(a + b);\n    }\n}\n'
  }
  if (value === 'C') {
    return '#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf("%d %d", &a, &b);\n    printf("%d\\n", a + b);\n    return 0;\n}\n'
  }
  return '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n\n    int a, b;\n    cin >> a >> b;\n    cout << a + b << "\\n";\n    return 0;\n}\n'
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
    grid-template-columns: 1fr 1fr;
  }
}
.statement {
  overflow-y: auto;
  padding: 24px;
  border-radius: 8px;
}
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
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
</style>
