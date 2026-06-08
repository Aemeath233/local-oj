<template>
  <div class="problem-page-container" v-loading="loading">
    <div v-if="hasError" style="margin: 40px auto; max-width: 400px; text-align: center;">
      <el-empty description="加载题目失败或题目不存在/无权限访问">
        <el-button type="primary" @click="router.push('/problems')">返回题目列表</el-button>
      </el-empty>
    </div>
    <section v-else-if="problem" class="problem-layout" ref="problemLayoutRef">
      <article class="statement panel" style="display: flex; flex-direction: column; position: relative;" :style="leftStyle">
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
      </article>

      <!-- Drag Resizable Divider -->
      <div v-if="!isMobile && !isMaximized" class="resize-divider" @mousedown="startDrag">
        <div class="resize-divider-line"></div>
      </div>

      <aside v-if="!isMobile" v-show="!isMaximized" class="submit-panel panel" :style="rightStyle">
        <div class="submit-toolbar">
          <el-select v-model="language" class="language-select">
            <el-option label="C++20 (O2)" value="CPP" />
            <el-option label="C++20 (O3)" value="CPP_O3" />
            <el-option label="C17 (O2)" value="C" />
            <el-option label="Python 3.12" value="PYTHON" />
            <el-option label="PyPy 3" value="PYPY3" />
            <el-option label="Java 21" value="JAVA" />
          </el-select>
          <el-button :icon="Brush" :loading="formatting" @click="handleFormat">格式化</el-button>
          <el-button :loading="selfTesting" :disabled="cooldownSeconds > 0" @click="runCustomTest">
            <template #icon>
              <span v-if="cooldownSeconds > 0" class="cooldown-num-icon">{{ cooldownSeconds }}</span>
              <el-icon v-else><VideoPlay /></el-icon>
            </template>
            自测
          </el-button>
          <el-button type="primary" :loading="submitting" :disabled="cooldownSeconds > 0" @click="submit">
            <template #icon>
              <span v-if="cooldownSeconds > 0" class="cooldown-num-icon white-num">{{ cooldownSeconds }}</span>
              <el-icon v-else><Upload /></el-icon>
            </template>
            提交
          </el-button>
        </div>
        <CodeEditor ref="codeEditorRef" v-model="sourceCode" :language="language" />

        <!-- Collapsible Self-test Console -->
        <ProblemSelfTestConsole
          ref="selfTestConsoleRef"
          v-model="selfTestInput"
          :self-testing="selfTesting"
          :self-test-error="selfTestError"
          :self-test-result="selfTestResult"
          @clear="clearSelfTest"
        />

        <!-- Mini Submissions list for current problem and current user -->
        <ProblemMiniSubmissions
          :submissions="submissions"
          :submissions-loading="submissionsLoading"
          @refresh="loadSubmissions"
          @click-submission="openSubmissionDetail"
        />

        <el-alert v-if="message" :title="message" type="success" show-icon :closable="false" style="margin-top: 8px;" />
      </aside>
    </section>

    <!-- Submission Details Drawer -->
    <SubmissionDetailDrawer v-model="drawerVisible" :detail="selectedSubmission" :current-code="sourceCode" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Upload, VideoPlay, Brush, Monitor, FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CodeEditor from '../components/CodeEditor.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import ProblemStatementTab from '../components/problem/ProblemStatementTab.vue'
import ProblemSolutionsTab from '../components/problem/ProblemSolutionsTab.vue'
import ProblemSelfTestConsole from '../components/problem/ProblemSelfTestConsole.vue'
import ProblemMiniSubmissions from '../components/problem/ProblemMiniSubmissions.vue'
import {
  fetchProblem,
  runSelfTest,
  submitSolution,
  fetchSubmission,
  fetchSubmissions,
  formatCode
} from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { Language, ProblemDetail, SelfTestResult, SubmissionSummary, SubmissionDetail } from '../types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const problemLayoutRef = ref<HTMLElement | null>(null)
const selfTestConsoleRef = ref<any>(null)
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
const language = ref<Language>((localStorage.getItem('localoj.editor.defaultLanguage') as Language) || 'CPP')
const sourceCode = ref(templateFor(language.value))

// Reader Typography Configuration States
const readerFontSize = ref(Number(localStorage.getItem('localoj.reader.fontSize')) || 15)
const readerFontFamily = ref(localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

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
    localStorage.setItem(`localoj.draft.${problemId.value}.${oldLang}`, sourceCode.value)
  }
  isSwitchingLanguage.value = true
  sourceCode.value = templateFor(newLang)
  isSwitchingLanguage.value = false
})

watch(sourceCode, (newCode) => {
  if (isSwitchingLanguage.value) return
  if (problemId.value) {
    localStorage.setItem(`localoj.draft.${problemId.value}.${language.value}`, newCode)
  }
})

const hasError = ref(false)

async function initProblem() {
  loading.value = true
  hasError.value = false
  try {
    problem.value = await fetchProblem(problemId.value)
    clearSelfTest()
    
    // Auto populate the first sample case input if available
    if (problem.value?.samples && problem.value.samples.length > 0) {
      selfTestInput.value = problem.value.samples[0].inputText
    } else {
      selfTestInput.value = ''
    }
  } catch (error) {
    console.error('Failed to initialize problem details', error)
    hasError.value = true
  } finally {
    loading.value = false
  }

  // Load submissions in the background without blocking the main page display
  if (!hasError.value) {
    loadSubmissions()
  }
}

function copyLink() {
  navigator.clipboard.writeText(window.location.href)
  ElMessage.success('链接已复制到剪贴板，快去电脑上打开吧！')
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  // Listen for editor/reader preference updates locally
  window.addEventListener('localoj-preferences-saved', handlePrefUpdates)
  await initProblem()
})

watch(problemId, async () => {
  await initProblem()
  sourceCode.value = templateFor(language.value)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('localoj-preferences-saved', handlePrefUpdates)
  if (timer.value) {
    window.clearInterval(timer.value)
  }
  if (cooldownTimer) {
    window.clearInterval(cooldownTimer)
  }
})

function handlePrefUpdates() {
  readerFontSize.value = Number(localStorage.getItem('localoj.reader.fontSize')) || 15
  readerFontFamily.value = localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif"
}

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
    ElMessage.success('代码格式化成功')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '格式化失败')
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
      await initProblem()
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
  selfTestConsoleRef.value?.expandInputTab()
  ElMessage.success('已填入自测输入')
}

function templateFor(value: Language) {
  const draft = localStorage.getItem(`localoj.draft.${problemId.value}.${value}`)
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
