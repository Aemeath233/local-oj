<template>
  <div class="problem-page-container" v-loading="loading">
    <section class="problem-layout" ref="problemLayoutRef">
      <article v-if="problem" class="statement panel" style="display: flex; flex-direction: column;" :style="leftStyle">
        <el-tabs v-model="activeLeftTab" class="statement-tabs" style="flex: 1; display: flex; flex-direction: column;">
          <!-- Tab 1: Problem Description -->
          <el-tab-pane label="题目描述" name="statement" style="padding-top: 10px;">
            <div class="statement-header">
              <div>
                <h1>{{ problem.title }}</h1>
                <p>{{ problem.slug }} · {{ problem.timeLimitMs }} ms · {{ Math.round(problem.memoryLimitKb / 1024) }} MB</p>
                <div v-if="problemTags.length > 0" class="tag-list" style="margin-top: 8px;">
                  <el-tag
                    v-for="tag in problemTags"
                    :key="tag"
                    size="small"
                    :color="getTagColor(tag) + '20'"
                    :style="{ borderColor: getTagColor(tag), color: getTagColor(tag) }"
                    class="premium-tag"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                </div>
              </div>
              <span :class="'difficulty-badge ' + (problem.difficulty || 'Easy').toLowerCase()">{{ problem.difficulty }}</span>
            </div>

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
          </el-tab-pane>

          <!-- Tab 2: Editorials & Solutions Board -->
          <el-tab-pane label="题解" name="solutions" style="padding-top: 10px;">
            <!-- Locked State overlay -->
            <div v-if="problem.solveStatus !== 'ACCEPTED'" class="solutions-locked-overlay">
              <div class="lock-icon-container">
                <el-icon class="lock-icon"><Lock /></el-icon>
              </div>
              <h3>题解已锁定</h3>
              <p>您需要先通过（AC）该题目，才能查看或发布题解！</p>
              <div class="lock-accent-line"></div>
            </div>

            <!-- Solutions list (unlocked) -->
            <div v-else class="sol-tab-content">
              <div class="sol-tab-header">
                <span class="sol-count">共 {{ solutionsList.length }} 篇题解</span>
                <el-button type="primary" size="small" @click="startWritingSolution">
                  {{ hasMySolution ? '编辑我的题解' : '写题解' }}
                </el-button>
              </div>

              <div v-if="solutionsList.length === 0" class="sol-empty">
                <el-icon style="font-size: 36px; color: #c0c4cc; margin-bottom: 8px;"><Notebook /></el-icon>
                <p>暂无题解，快来发布第一篇吧！</p>
              </div>

              <div v-else class="sol-card-list">
                <div
                  v-for="sol in solutionsList"
                  :key="sol.id"
                  class="sol-card"
                  @click="openSolutionDrawer(sol.id)"
                >
                  <div class="sol-card-top">
                    <span class="sol-card-title">{{ sol.title }}</span>
                    <el-tag v-if="sol.userId === authStore.user?.id" type="success" size="small" effect="plain">我的</el-tag>
                  </div>
                  <div class="sol-card-bottom">
                    <div class="sol-card-author">
                      <el-avatar :size="18" :src="sol.avatarUrl">{{ (sol.displayName || sol.username || 'U').slice(0, 1) }}</el-avatar>
                      <span>{{ sol.displayName || sol.username }}</span>
                    </div>
                    <span class="sol-card-time">{{ formatRelativeTime(sol.updatedAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>

        <!-- Solution Detail Drawer -->
        <el-drawer
          v-model="solutionDrawerVisible"
          title=""
          direction="rtl"
          size="640px"
          :append-to-body="true"
          class="solution-drawer"
        >
          <template #header>
            <div class="drawer-header-content">
              <h3>{{ selectedSolutionDetail?.title }}</h3>
              <div class="drawer-header-actions">
                <el-button
                  v-if="selectedSolutionDetail && (selectedSolutionDetail.userId === authStore.user?.id || authStore.isAdmin)"
                  type="danger"
                  link
                  size="small"
                  @click="deleteSolutionPrompt(selectedSolutionDetail!.id)"
                >删除</el-button>
              </div>
            </div>
          </template>
          <div v-if="selectedSolutionDetail" class="drawer-solution-body">
            <div class="drawer-sol-meta">
              <el-avatar :size="28" :src="selectedSolutionDetail.avatarUrl">
                {{ (selectedSolutionDetail.displayName || selectedSolutionDetail.username || 'U').slice(0, 1) }}
              </el-avatar>
              <div>
                <strong>{{ selectedSolutionDetail.displayName || selectedSolutionDetail.username }}</strong>
                <div class="muted" style="font-size: 12px;">发表于 {{ formatDateTime(selectedSolutionDetail.createdAt) }}</div>
              </div>
            </div>
            <el-divider style="margin: 16px 0;" />
            <div class="drawer-sol-content">
              <MarkdownView :source="selectedSolutionDetail.content" />
            </div>
          </div>
          <div v-else style="text-align: center; color: #999; padding-top: 40px;">加载中...</div>
        </el-drawer>

        <!-- Solution Editor Dialog -->
        <el-dialog
          v-model="solutionEditorVisible"
          :title="hasMySolution ? '编辑我的题解' : '发布新题解'"
          width="780px"
          :close-on-click-modal="false"
          :append-to-body="true"
          top="6vh"
        >
          <el-input
            v-model="solutionForm.title"
            placeholder="请输入题解标题..."
            maxlength="100"
            show-word-limit
            style="margin-bottom: 16px;"
          />
          <div class="dialog-editor-split">
            <div class="dialog-editor-left">
              <div class="dialog-pane-label">Markdown</div>
              <el-input
                v-model="solutionForm.content"
                type="textarea"
                :rows="18"
                placeholder="请使用 Markdown 编写题解，分享您的思路、算法复杂度及代码实现..."
                resize="none"
              />
            </div>
            <div class="dialog-editor-right">
              <div class="dialog-pane-label">预览</div>
              <div class="dialog-preview-body">
                <MarkdownView :source="solutionForm.content || '*暂无预览内容*'" />
              </div>
            </div>
          </div>
          <template #footer>
            <el-button @click="solutionEditorVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingSolution" @click="submitSolutionForm">保存并发布</el-button>
          </template>
        </el-dialog>
      </article>

      <!-- Drag Resizable Divider -->
      <div v-if="problem" class="resize-divider" @mousedown="startDrag">
        <div class="resize-divider-line"></div>
      </div>

      <aside class="submit-panel panel" :style="rightStyle">
        <div class="submit-toolbar">
          <el-select v-model="language" class="language-select">
            <el-option label="C++20 (O2)" value="CPP" />
            <el-option label="C++20 (O3)" value="CPP_O3" />
            <el-option label="C" value="C" />
            <el-option label="Python 3.12" value="PYTHON" />
            <el-option label="PyPy 3" value="PYPY3" />
            <el-option label="Java 21" value="JAVA" />
          </el-select>
          <el-button :icon="VideoPlay" :loading="selfTesting" :disabled="cooldownSeconds > 0" @click="runCustomTest">
            {{ cooldownSeconds > 0 ? `自测 (${cooldownSeconds}s)` : '自测' }}
          </el-button>
          <el-button :icon="Upload" type="primary" :loading="submitting" :disabled="cooldownSeconds > 0" @click="submit">
            {{ cooldownSeconds > 0 ? `提交 (${cooldownSeconds}s)` : '提交' }}
          </el-button>
        </div>
        <CodeEditor v-model="sourceCode" :language="language" />

        <!-- Collapsible Self-test Console -->
        <section class="console-section" :class="{ 'is-expanded': consoleExpanded }">
          <div class="console-header" @click="consoleExpanded = !consoleExpanded">
            <span class="console-title">
              <el-icon><Cpu /></el-icon>
              自测控制台
            </span>
            <div class="console-actions">
              <el-button v-if="selfTestResult || selfTestError" type="primary" link size="small" @click.stop="clearSelfTest">清空结果</el-button>
              <el-icon class="toggle-arrow" :class="{ 'is-rotated': consoleExpanded }"><ArrowUp /></el-icon>
            </div>
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

        <!-- Mini Submissions list for current problem and current user -->
        <div class="mini-submissions-panel">
          <div class="mini-panel-header">
            <span>我的提交记录</span>
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
import { Upload, VideoPlay, ArrowUp, ArrowDown, Cpu, DocumentCopy, Refresh, Loading, Lock, Notebook } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CodeEditor from '../components/CodeEditor.vue'
import MarkdownView from '../components/MarkdownView.vue'
import VerdictTag from '../components/VerdictTag.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import {
  fetchProblem,
  runSelfTest,
  submitSolution,
  fetchSubmission,
  fetchSubmissions,
  fetchProblemSolutions,
  fetchProblemSolutionDetail,
  saveProblemSolution,
  deleteProblemSolution
} from '../api/http'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import { getTagColor } from '../utils/tag'
import { useAuthStore } from '../stores/auth'
import type { Language, ProblemDetail, SelfTestResult, SubmissionSummary, SubmissionDetail, ProblemSolutionSummary, ProblemSolutionDetail } from '../types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const problemLayoutRef = ref<HTMLElement | null>(null)
const leftWidthPercent = ref(50)
let startX = 0
let startWidthPercent = 0

// Check if mobile or desktop split screen is active
const isWideScreen = ref(window.innerWidth >= 1041)
function handleResize() {
  isWideScreen.value = window.innerWidth >= 1041
}

const leftStyle = computed(() => {
  if (!isWideScreen.value) return {}
  return {
    width: `${leftWidthPercent.value}%`,
    flex: `0 0 ${leftWidthPercent.value}%`
  }
})

const rightStyle = computed(() => {
  if (!isWideScreen.value) return {}
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
const language = ref<Language>((localStorage.getItem('localoj.editor.defaultLanguage') as Language) || 'CPP')
const sourceCode = ref(templateFor(language.value))

// Reader Typography Configuration States
const readerFontSize = ref(Number(localStorage.getItem('localoj.reader.fontSize')) || 15)
const readerFontFamily = ref(localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

const problemId = computed(() => Number(route.params.id))
const statementMarkdown = computed(() => {
  return problem.value?.description || ''
})
const sampleCases = computed(() => {
  if (!problem.value) {
    return []
  }
  return problem.value.samples
})
const problemTags = computed(() => {
  return (problem.value?.tags || '')
    .split(/[,，]/)
    .map(t => t.trim())
    .filter(t => t.length > 0)
})
const selfTestMessage = computed(() => selfTestResult.value?.stderr || selfTestResult.value?.message || '')

// UX & Layout reactive variables
const consoleExpanded = ref(false)
const activeConsoleTab = ref('input')
const submissions = ref<SubmissionSummary[]>([])
const submissionsLoading = ref(false)
const drawerVisible = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
const timer = ref<number | undefined>(undefined)

watch(language, (value) => {
  sourceCode.value = templateFor(value)
})

async function initProblem() {
  loading.value = true
  try {
    problem.value = await fetchProblem(problemId.value)
    await loadSubmissions()
    clearSelfTest()
  } catch (error) {
    console.error('Failed to initialize problem details', error)
  } finally {
    loading.value = false
  }
}

// Solutions workspace variables
const activeLeftTab = ref('statement')
const solutionsList = ref<ProblemSolutionSummary[]>([])
const selectedSolutionDetail = ref<ProblemSolutionDetail | null>(null)
const solutionDrawerVisible = ref(false)
const solutionEditorVisible = ref(false)
const savingSolution = ref(false)
const solutionForm = ref({
  title: '',
  content: ''
})

const hasMySolution = computed(() => {
  return solutionsList.value.some(sol => sol.userId === authStore.user?.id)
})

watch(activeLeftTab, (newTab) => {
  if (newTab === 'solutions') {
    loadSolutions()
  }
})

async function loadSolutions() {
  if (!problem.value || problem.value.solveStatus !== 'ACCEPTED') return
  try {
    solutionsList.value = await fetchProblemSolutions(problem.value.id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载题解失败')
  }
}

async function openSolutionDrawer(id: number) {
  solutionDrawerVisible.value = true
  selectedSolutionDetail.value = null
  try {
    selectedSolutionDetail.value = await fetchProblemSolutionDetail(problem.value!.id, id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载题解详情失败')
  }
}

function startWritingSolution() {
  const mySol = solutionsList.value.find(sol => sol.userId === authStore.user?.id)
  if (mySol) {
    solutionForm.value.title = mySol.title
    fetchProblemSolutionDetail(problem.value!.id, mySol.id).then(detail => {
      solutionForm.value.content = detail.content
    })
  } else {
    solutionForm.value.title = ''
    solutionForm.value.content = ''
  }
  solutionEditorVisible.value = true
}

async function submitSolutionForm() {
  if (!solutionForm.value.title.trim() || !solutionForm.value.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  savingSolution.value = true
  try {
    await saveProblemSolution(problem.value!.id, solutionForm.value.title, solutionForm.value.content)
    ElMessage.success('题解保存成功！')
    solutionEditorVisible.value = false
    solutionsList.value = await fetchProblemSolutions(problem.value!.id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '发布题解失败')
  } finally {
    savingSolution.value = false
  }
}

async function deleteSolutionPrompt(id: number) {
  try {
    await deleteProblemSolution(problem.value!.id, id)
    ElMessage.success('题解已删除')
    solutionDrawerVisible.value = false
    selectedSolutionDetail.value = null
    solutionsList.value = await fetchProblemSolutions(problem.value!.id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除题解失败')
  }
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await initProblem()
})

watch(problemId, async () => {
  await initProblem()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (timer.value) {
    window.clearInterval(timer.value)
  }
  if (cooldownTimer) {
    window.clearInterval(cooldownTimer)
  }
})

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
      if (activeLeftTab.value === 'solutions') {
        loadSolutions()
      }
    }
  },
  { deep: true }
)

async function loadSubmissions(isSilent = false) {
  if (!isSilent) {
    submissionsLoading.value = true
  }
  try {
    const allSubmissions = await fetchSubmissions()
    const currentUserId = authStore.user?.id
    submissions.value = allSubmissions.filter(
      (sub) => Number(sub.problemId) === Number(problemId.value) && Number(sub.userId) === Number(currentUserId)
    )

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
  consoleExpanded.value = true
  activeConsoleTab.value = 'result'
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
  if (value === 'PYTHON' || value === 'PYPY3') {
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

/* Solutions Tab styles */
.solutions-locked-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--el-text-color-primary);
  margin-top: 15px;
  text-align: center;
  padding: 30px;
}
.lock-icon-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: rgba(245, 108, 108, 0.15);
  border: 2px dashed #f56c6c;
  color: #f56c6c;
  margin-bottom: 18px;
}
.lock-icon {
  font-size: 32px;
  animation: shake 2s infinite ease-in-out;
}
.solutions-locked-overlay h3 {
  font-size: 20px;
  margin-bottom: 8px;
  font-weight: 600;
}
.solutions-locked-overlay p {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  max-width: 320px;
  line-height: 1.5;
}

@keyframes shake {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

/* Solutions Tab - Clean card list */
.sol-tab-content {
  padding: 4px 0;
}

.sol-tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sol-count {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.sol-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  color: #94a3b8;
  font-size: 14px;
}

.sol-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sol-card {
  padding: 14px 16px;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: #fff;
}

.sol-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}

.sol-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.sol-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.sol-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sol-card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.sol-card-time {
  font-size: 11px;
  color: #94a3b8;
}

/* Solution Drawer */
.drawer-header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.drawer-header-content h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  color: #1e293b;
  line-height: 1.4;
}

.drawer-sol-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-sol-meta strong {
  font-size: 14px;
  color: #1e293b;
}

.drawer-sol-content {
  font-size: 15px;
  line-height: 1.75;
}

/* Solution Editor Dialog */
.dialog-editor-split {
  display: flex;
  gap: 16px;
  height: 480px;
}

.dialog-editor-left,
.dialog-editor-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.dialog-pane-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.dialog-editor-left :deep(.el-textarea) {
  flex: 1;
}

.dialog-editor-left :deep(.el-textarea__inner) {
  height: 100% !important;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  background: #f8fafc;
  border-radius: 8px;
}

.dialog-preview-body {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
  background: #fff;
  font-size: 14px;
  line-height: 1.7;
}

.premium-tag {
  font-weight: 500;
  border-radius: 6px;
  font-size: 0.8rem;
  padding: 0.15rem 0.5rem;
  background-color: transparent !important;
}
</style>

