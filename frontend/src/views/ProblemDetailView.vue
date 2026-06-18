<template>
  <div class="problem-page-container" v-loading="loading">
    <div v-if="hasError" style="margin: 40px auto; max-width: 400px; text-align: center;">
      <el-empty description="加载题目失败或题目不存在/无权限访问">
        <el-button type="primary" @click="router.push('/problems')">返回题目列表</el-button>
      </el-empty>
    </div>
    <ProblemWorkspaceLayout v-else-if="problem" ref="problemLayoutRef">
      <template #left>
        <el-tabs v-model="activeLeftTab" class="statement-tabs" style="flex: 1; display: flex; flex-direction: column;">
          <!-- Tab 1: Problem Description -->
          <el-tab-pane label="题目描述" name="statement" style="padding-top: 10px;">
            <ProblemStatementTab
              :problem="problem"
              :reader-font-size="readerFontSize"
              :reader-font-family="readerFontFamily"
              @fill-self-test="fillSelfTest"
            />

            <!-- If mobile, show a nice info block about writing code on PC -->
            <el-card v-if="isMobile" class="mobile-warning-card" style="margin-top: 15px;">
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
          </el-tab-pane>

          <!-- Tab 2: Editorials & Solutions Board -->
          <el-tab-pane label="题解" name="solutions" style="padding-top: 10px;">
            <ProblemSolutionsTab :problem="problem" />
          </el-tab-pane>
        </el-tabs>
      </template>

      <template #right>
        <ProblemSubmitPanel
          ref="submitPanelRef"
          v-model:language="language"
          v-model:sourceCode="sourceCode"
          v-model:selfTestInput="selfTestInput"
          :formatting="formatting"
          :self-testing="selfTesting"
          :submitting="submitting"
          :cooldown-seconds="cooldownSeconds"
          :self-test-error="selfTestError"
          :self-test-result="selfTestResult"
          :submissions="submissions"
          :submissions-loading="submissionsLoading"
          :message="message"
          @format="handleFormat"
          @run-custom-test="runCustomTest"
          @submit="submit"
          @clear-self-test="clearSelfTest"
          @refresh-submissions="loadSubmissions"
          @open-submission="openSubmissionDetail"
        />
      </template>
    </ProblemWorkspaceLayout>

    <!-- Submission Details Drawer -->
    <SubmissionDetailDrawer v-model="drawerVisible" :detail="selectedSubmission" :current-code="sourceCode" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import ProblemStatementTab from '../components/problem/ProblemStatementTab.vue'
import ProblemSolutionsTab from '../components/problem/ProblemSolutionsTab.vue'
import ProblemSubmitPanel from '../components/ProblemSubmitPanel.vue'
import { fetchProblem } from '../api/problem'
import { runSelfTest, submitSolution, fetchSubmission, fetchSubmissions } from '../api/submission'
import { formatCode } from '../api/system'
import { useAuthStore } from '../stores/auth'
import { useSubmissionSse } from '../composables/useSubmissionSse'
import { firstProblemSampleInput } from '../utils/problemSamples'
import type { Language, ProblemDetail, SelfTestResult, SubmissionSummary, SubmissionDetail } from '../types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

import ProblemWorkspaceLayout from '../components/problem/ProblemWorkspaceLayout.vue'

const problemLayoutRef = ref<any>(null)
const submitPanelRef = ref<any>(null)
const isMobile = computed(() => problemLayoutRef.value?.isMobile ?? false)

const loading = ref(false)
const submitting = ref(false)
const selfTesting = ref(false)
const message = ref('')
const cooldownSeconds = ref(0)
let cooldownTimer: any = null

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
const problemId = computed(() => Number(route.params.id))
const language = ref<Language>((localStorage.getItem('coderushoj.editor.defaultLanguage') as Language) || 'CPP')
const sourceCode = ref(templateFor(language.value))

// Reader Typography Configuration States
const readerFontSize = ref(Number(localStorage.getItem('coderushoj.reader.fontSize')) || 15)
const readerFontFamily = ref(localStorage.getItem('coderushoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

// UX & Layout reactive variables
const activeLeftTab = ref('statement')
const submissions = ref<SubmissionSummary[]>([])
const submissionsLoading = ref(false)
const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
const timer = ref<number | undefined>(undefined)

const isSwitchingLanguage = ref(false)

watch(language, (newLang, oldLang) => {
  if (oldLang && problemId.value) {
    localStorage.setItem(`coderushoj.draft.${problemId.value}.${oldLang}`, sourceCode.value)
  }
  isSwitchingLanguage.value = true
  sourceCode.value = templateFor(newLang)
  isSwitchingLanguage.value = false
})

watch(sourceCode, (newCode) => {
  if (isSwitchingLanguage.value) return
  if (problemId.value) {
    localStorage.setItem(`coderushoj.draft.${problemId.value}.${language.value}`, newCode)
  }
})

const hasError = ref(false)

async function initProblem(isSilent = false) {
  if (!isSilent) {
    loading.value = true
    hasError.value = false
  }
  try {
    problem.value = await fetchProblem(problemId.value)
    if (!isSilent) {
      clearSelfTest()
      
      selfTestInput.value = firstProblemSampleInput(problem.value?.description)
    }
  } catch (error) {
    console.error('Failed to initialize problem details', error)
    if (!isSilent) {
      hasError.value = true
    }
  } finally {
    if (!isSilent) {
      loading.value = false
    }
  }

  // Load submissions in the background without blocking the main page display
  if (!hasError.value) {
    loadSubmissions(isSilent)
  }
}

function copyLink() {
  navigator.clipboard.writeText(window.location.href)
  ElMessage.success('链接已复制到剪贴板，快去电脑上打开吧！')
}

const { connect: connectSse, disconnect: disconnectSse } = useSubmissionSse((update) => {
  if (update.problemId !== undefined && update.problemId !== problemId.value) return
  if (update.contestId !== undefined && update.contestId !== null) return
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

        // Guard: Do not overwrite with older state
        if (curStatus === 'FINISHED' && newStatus !== 'FINISHED') return
        if (newCaseCount < curCaseCount) return
        selectedSubmission.value = detail
      }
    }).catch(console.error)
  }

  if (status === 'FINISHED') {
    loadSubmissions(true)
    initProblem(true)
  }
}, () => authStore.token)

onMounted(async () => {
  // Listen for editor/reader preference updates locally
  window.addEventListener('coderushoj-preferences-saved', handlePrefUpdates)
  await initProblem()
  connectSse()
})

watch(problemId, async () => {
  await initProblem()
  sourceCode.value = templateFor(language.value)
})

onUnmounted(() => {
  window.removeEventListener('coderushoj-preferences-saved', handlePrefUpdates)
  disconnectSse()
  if (timer.value) {
    window.clearInterval(timer.value)
  }
  if (cooldownTimer) {
    window.clearInterval(cooldownTimer)
  }
})

function handlePrefUpdates() {
  readerFontSize.value = Number(localStorage.getItem('coderushoj.reader.fontSize')) || 15
  readerFontFamily.value = localStorage.getItem('coderushoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif"
}

const formatting = ref(false)

async function handleFormat() {
  if (!sourceCode.value || sourceCode.value.trim() === '') {
    ElMessage.warning('代码不能为空')
    return
  }
  formatting.value = true
  try {
    const formatted = await formatCode(language.value, sourceCode.value)
    if (submitPanelRef.value?.codeEditorRef?.setValuePreservingHistory) {
      submitPanelRef.value.codeEditorRef.setValuePreservingHistory(formatted)
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

// Poll submission statuses when there is pending or running submissions
watch(
  submissions,
  async (newSubmissions, oldSubmissions) => {
    const hasRunning = newSubmissions.some(
      (sub) => sub.status === 'PENDING' || sub.status === 'RUNNING'
    )
    if (hasRunning && !timer.value) {
      timer.value = window.setInterval(() => loadSubmissions(true), 3000)
    } else if (!hasRunning && timer.value) {
      window.clearInterval(timer.value)
      timer.value = undefined
    }

    // Automatically reload problem if a judge has finished to reactively update solved lock status
    const runningIds = new Set(
      (oldSubmissions || [])
        .filter(sub => sub.status === 'PENDING' || sub.status === 'RUNNING')
        .map(sub => sub.id)
    )
    const justFinished = newSubmissions.some(
      sub => runningIds.has(sub.id) && sub.status === 'FINISHED'
    )
    if (justFinished) {
      await initProblem(true)
    }
  },
  { deep: true }
)

async function loadSubmissions(isSilent = false) {
  if (!isSilent) {
    submissionsLoading.value = true
  }
  try {
    const res = await fetchSubmissions({
      problemId: problemId.value,
      mine: true
    })
    submissions.value = res.slice(0, 10)

    // Auto-update drawer if it's currently showing one of our submissions
    if (drawerVisible.value && selectedSubmission.value) {
      selectedSubmission.value = await fetchSubmission(selectedSubmission.value.submission.id)
    }
  } catch (error) {
    console.error('Failed to load submission history', error)
  } finally {
    if (!isSilent) {
      submissionsLoading.value = false
    }
  }
}

async function openSubmissionDetail(row: SubmissionSummary) {
  selectedSubmission.value = await fetchSubmission(row.id)
  drawerVisible.value = true
}

async function submit() {
  if (!sourceCode.value || sourceCode.value.trim() === '') {
    ElMessage.warning('代码不能为空')
    return
  }
  submitting.value = true
  message.value = ''
  try {
    const submission = await submitSolution(problemId.value, language.value, sourceCode.value)
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
  selfTesting.value = true
  selfTestError.value = ''
  selfTestResult.value = null
  try {
    selfTestResult.value = await runSelfTest(problemId.value, language.value, sourceCode.value, selfTestInput.value)
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

function fillSelfTest(text: string) {
  selfTestInput.value = text
  submitPanelRef.value?.selfTestConsoleRef?.expandInputTab()
  ElMessage.success('已填入自测输入')
}

function templateFor(value: Language) {
  const draft = localStorage.getItem(`coderushoj.draft.${problemId.value}.${value}`)
  if (draft !== null) {
    return draft
  }
  return ''
}
</script>

<style scoped>
.problem-page-container {
  height: calc(100vh - 120px);
  min-height: 500px;
  display: flex;
  flex-direction: column;
}

.problem-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 0px;
  position: relative;
  align-items: stretch;
}

.statement {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  overflow-y: hidden;
  box-sizing: border-box;
  position: relative;
}

.maximize-btn {
  position: absolute;
  top: 15px;
  right: 20px;
  z-index: 10;
}

.statement-tabs :deep(.el-tabs__header) {
  margin-bottom: 0px;
}
.statement-tabs :deep(.el-tabs__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.statement-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

@media (min-width: 1041px) {
  .statement-tabs :deep(.el-tab-pane) {
    overflow-y: auto;
    padding-right: 8px;
  }
}

.submit-panel {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
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

.submit-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  align-items: center;
}

.language-select {
  width: 150px;
}

.cooldown-num-icon {
  font-family: var(--font-mono), monospace;
  font-weight: bold;
  font-size: 0.82rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.08);
  color: var(--el-text-color-regular);
}

.white-num {
  background: rgba(255, 255, 255, 0.25) !important;
  color: #ffffff !important;
}

@media (max-width: 1040px) {
  .problem-layout {
    flex-direction: column;
    overflow-y: auto;
  }
  .statement,
  .submit-panel {
    width: 100% !important;
    flex: none !important;
    height: auto !important;
    overflow-y: visible !important;
  }
  .resize-divider {
    display: none;
  }
  .problem-page-container {
    height: auto;
  }
}
</style>
