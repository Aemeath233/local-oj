<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="70%"
    @update:model-value="emit('update:modelValue', $event)"
    destroy-on-close
    custom-class="premium-drawer"
  >
    <div v-if="detail" class="drawer-content submission-detail-premium">

      <!-- Part 1: Verdict & Personalized Suggestion Banner -->
      <div :class="['verdict-banner', verdictBannerClass]">
        <div class="banner-body">
          <div class="banner-left">
            <VerdictTag :status="detail.submission.status" :verdict="detail.submission.verdict" />
            <div class="banner-text">
              <h2>{{ verdictBannerTitle }}</h2>
              <p>{{ verdictBannerTip }}</p>
            </div>
          </div>
          <div class="banner-score">
            <span class="score-label">得分</span>
            <span class="score-value">{{ detail.submission.score }}</span>
          </div>
        </div>
      </div>

      <!-- Part 2: Quick Specs Metrics Grid (Frosted Glass Style) -->
      <div class="metrics-grid">
        <div class="metric-card">
          <el-icon class="metric-icon"><Cpu /></el-icon>
          <div class="metric-info">
            <span>编程语言</span>
            <strong>{{ detail.submission.language }}</strong>
          </div>
        </div>
        <div class="metric-card">
          <el-icon class="metric-icon"><Clock /></el-icon>
          <div class="metric-info">
            <span>运行耗时</span>
            <strong>{{ detail.submission.timeMs != null ? detail.submission.timeMs + ' ms' : '-' }}</strong>
          </div>
        </div>
        <div class="metric-card">
          <el-icon class="metric-icon"><Odometer /></el-icon>
          <div class="metric-info">
            <span>内存消耗</span>
            <strong>{{ detail.submission.memoryKb != null ? formatMemory(detail.submission.memoryKb) : '-' }}</strong>
          </div>
        </div>
        <div class="metric-card">
          <el-icon class="metric-icon"><Notebook /></el-icon>
          <div class="metric-info">
            <span>评测题目</span>
            <strong class="text-ellipsis">{{ detail.problem?.title || `题目 #${detail.submission.problemId}` }}</strong>
          </div>
        </div>
      </div>

      <!-- Part 3: Compiler/Runtime Error Log Hacker Console -->
      <div v-if="detail.submission.errorMessage" class="terminal-card">
        <div class="terminal-header">
          <div class="terminal-dots">
            <span class="dot red"></span>
            <span class="dot yellow"></span>
            <span class="dot green"></span>
          </div>
          <span class="terminal-title">COMPILATION ERROR & DIAGNOSTICS</span>
          <div class="terminal-actions" style="display: flex; gap: 8px; align-items: center;">
            <el-button
              v-if="hasLongError"
              size="small"
              type="info"
              plain
              @click="showAllError = !showAllError"
            >
              {{ showAllError ? '收起日志' : '展开全部' }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :icon="DocumentCopy"
              @click="copyText(detail.submission.errorMessage || '', '错误日志复制成功')"
            >
              复制日志
            </el-button>
          </div>
        </div>
        <div class="terminal-body">
          <pre class="terminal-pre">{{ displayedError }}</pre>
        </div>
      </div>

      <!-- Part 4: Sleek Test Case Grid showing all metrics at a glance -->
      <section v-if="detail.cases && detail.cases.length > 0" class="cases-section">
        <div class="section-title-bar">
          <h2>测试点分析 ({{ detail.cases.length }} 个测试点)</h2>
        </div>

        <div class="cases-grid">
          <div
            v-for="c in detail.cases"
            :key="c.id"
            :class="['case-status-card', cardStatusClass(c.verdict)]"
          >
            <div class="case-card-top">
              <span class="case-index">测试点 #{{ c.caseIndex }}</span>
              <VerdictTag status="FINISHED" :verdict="c.verdict" size="small" />
            </div>
            <div class="case-card-metrics">
              <div class="metric-item">
                <el-icon><Clock /></el-icon>
                <span>{{ c.timeMs ?? 0 }} ms</span>
              </div>
              <div class="metric-item">
                <el-icon><Odometer /></el-icon>
                <span>{{ c.memoryKb != null ? formatMemory(c.memoryKb) : '0 KB' }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Part 5: Code Editor Section -->
      <section class="source-section">
        <div class="source-section-header">
          <div class="title-with-icon">
            <el-icon class="header-icon"><View /></el-icon>
            <h2>提交源码</h2>
          </div>
          <div class="editor-actions">
            <el-button
              type="primary"
              size="small"
              :icon="DocumentCopy"
              @click="copyText(detail.submission.sourceCode || '', '源码复制成功')"
            >
              复制代码
            </el-button>
          </div>
        </div>

        <div class="source-editor-container">
          <CodeEditor
            :model-value="detail.submission.sourceCode || ''"
            :language="detail.submission.language"
            :read-only="true"
          />
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Cpu, Clock, Odometer, Notebook, View, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VerdictTag from './VerdictTag.vue'
import CodeEditor from './CodeEditor.vue'
import type { SubmissionDetail, Verdict } from '../types'

const props = defineProps<{
  modelValue: boolean
  detail: SubmissionDetail | null
  currentCode?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const showAllError = ref(false)

const errorLines = computed(() => {
  const msg = props.detail?.submission?.errorMessage
  if (!msg) return []
  return msg.split('\n')
})

const hasLongError = computed(() => {
  return errorLines.value.length > 25
})

const displayedError = computed(() => {
  const msg = props.detail?.submission?.errorMessage
  if (!msg) return ''
  if (showAllError.value || !hasLongError.value) {
    return msg
  }
  return errorLines.value.slice(0, 25).join('\n') + `\n\n[... 已省略余下 ${errorLines.value.length - 25} 行，可点击上方“展开全部”或复制完整日志 ...]`
})

watch(() => props.detail?.submission?.id, () => {
  showAllError.value = false
})

const drawerTitle = computed(() => {
  if (!props.detail) return '提交详情'
  return `提交详情 #${props.detail.submission.id}`
})

// Verdict banner classes and text configurations
const verdictBannerClass = computed(() => {
  const v = props.detail?.submission.verdict
  const status = props.detail?.submission.status
  if (status && status !== 'FINISHED') return 'banner-judging'
  if (v === 'AC') return 'banner-ac'
  if (v === 'WA') return 'banner-wa'
  if (v === 'CE') return 'banner-ce'
  return 'banner-other'
})

const verdictBannerTitle = computed(() => {
  const status = props.detail?.submission.status
  const v = props.detail?.submission.verdict
  if (status === 'PENDING') return '排队待评中...'
  if (status === 'RUNNING') return '评测正忙碌...'

  if (v === 'AC') return 'ACCEPTED! 完美通过'
  if (v === 'WA') return 'WRONG ANSWER 答案错误'
  if (v === 'TLE') return 'TIME LIMIT EXCEEDED 运行超时'
  if (v === 'MLE') return 'MEMORY LIMIT EXCEEDED 内存超限'
  if (v === 'OLE') return 'OUTPUT LIMIT EXCEEDED 输出超限'
  if (v === 'RE') return 'RUNTIME ERROR 运行错误'
  if (v === 'CE') return 'COMPILATION ERROR 编译失败'
  return 'JUDGE ERROR 系统内部错误'
})

const verdictBannerTip = computed(() => {
  const status = props.detail?.submission.status
  const v = props.detail?.submission.verdict
  if (status === 'PENDING') return '您的提交已经在队列中排队，沙箱环境正在为其分配独立计算资源...'
  if (status === 'RUNNING') return '正在加载系统测试集，并发沙箱已启动，请耐心等待返回...'

  if (v === 'AC') return '恭喜您！您的代码已顺利通过该题目的所有评测数据集，完美通关！🎉'
  if (v === 'WA') return '您的程序输出与预期输出不一致。别灰心，可以尝试自测特殊边界值，或检查数组越界与精度损失等问题。'
  if (v === 'TLE') return '程序运行耗时超过了题目规定的时限。请检查是否有无限循环，或尝试使用更低时间复杂度的算法。'
  if (v === 'MLE') return '程序占用的物理内存空间超出了上限。请避免开辟过大的全局数组或多余的静态分配。'
  if (v === 'OLE') return '程序输出了冗余的测试点信息。请检查是否忘记关闭临时调试打印。'
  if (v === 'RE') return '程序运行时崩溃或发生严重异常（例如：除零错误、空指针、或栈溢出等）。'
  if (v === 'CE') return '编译器在生成可执行程序时发生语法错误或库缺失。请仔细核查下方的详细编译器错误报告。'
  return '评测引擎沙箱分配失败或内部模块抛出异常，如非程序格式问题，请及时联系超级管理员处理。'
})

function cardStatusClass(v: Verdict) {
  if (v === 'AC') return 'card-ac'
  if (v === 'WA') return 'card-wa'
  if (v === 'CE') return 'card-ce'
  if (v === 'TLE' || v === 'MLE') return 'card-tle'
  return 'card-other'
}

function formatMemory(kb: number) {
  if (kb >= 1024) {
    return (kb / 1024).toFixed(1) + ' MB'
  }
  return kb + ' KB'
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
</script>

<style scoped>
.submission-detail-premium {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 10px;
}

/* Verdict banner styles */
.verdict-banner {
  border-radius: 12px;
  padding: 20px 24px;
  color: #ffffff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.banner-judging {
  background: linear-gradient(135deg, #7b8893 0%, #4a5664 100%);
}

.banner-ac {
  background: linear-gradient(135deg, #52c41a 0%, #2f8e00 100%);
  box-shadow: 0 6px 20px rgba(82, 196, 26, 0.25);
}

.banner-wa {
  background: linear-gradient(135deg, #ff4d4f 0%, #cf1322 100%);
  box-shadow: 0 6px 20px rgba(245, 108, 108, 0.25);
}

.banner-ce {
  background: linear-gradient(135deg, #faad14 0%, #d48806 100%);
  box-shadow: 0 6px 20px rgba(250, 173, 20, 0.25);
}

.banner-other {
  background: linear-gradient(135deg, #f5222d 0%, #a8071a 100%);
  box-shadow: 0 6px 20px rgba(245, 34, 45, 0.25);
}

.banner-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.banner-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex: 1;
  min-width: 280px;
}

.banner-text h2 {
  font-size: 20px;
  margin: 0 0 6px 0;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.banner-text p {
  font-size: 13px;
  margin: 0;
  opacity: 0.9;
  line-height: 1.5;
}

.banner-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(4px);
  padding: 8px 18px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  min-width: 80px;
}

.score-label {
  font-size: 11px;
  text-transform: uppercase;
  opacity: 0.8;
  letter-spacing: 1px;
}

.score-value {
  font-size: 32px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  margin-top: 2px;
}

/* Specs Metrics grid */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.metric-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.25s ease;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
  border-color: #cbd5e1;
}

.metric-icon {
  font-size: 24px;
  color: #64748b;
  background: #edf2f7;
  padding: 8px;
  border-radius: 8px;
}

.metric-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.metric-info span {
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metric-info strong {
  font-size: 14px;
  color: #1e293b;
  margin-top: 2px;
  font-weight: 600;
}

.text-ellipsis {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Hacker Terminal Card */
.terminal-card {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.terminal-header {
  background: #1e293b;
  padding: 8px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #334155;
}

.terminal-dots {
  display: flex;
  gap: 6px;
}

.terminal-dots .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.terminal-dots .dot.red { background-color: #ef4444; }
.terminal-dots .dot.yellow { background-color: #eab308; }
.terminal-dots .dot.green { background-color: #22c55e; }

.terminal-title {
  color: #94a3b8;
  font-size: 11px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
  letter-spacing: 1px;
}

.terminal-body {
  padding: 16px;
  max-height: 280px;
  overflow-y: auto;
}

/* Custom dark scrollbar for the hacker terminal */
.terminal-body::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.terminal-body::-webkit-scrollbar-track {
  background: #0f172a;
}
.terminal-body::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 4px;
}
.terminal-body::-webkit-scrollbar-thumb:hover {
  background: #475569;
}

.terminal-pre {
  color: #f1f5f9;
  background: transparent !important;
  border: none !important;
  min-height: auto !important;
  padding: 0 !important;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Cases analysis grid */
.cases-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
}

.section-title-bar {
  margin-bottom: 16px;
}

.section-title-bar h2 {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.cases-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.case-status-card {
  border-radius: 10px;
  border: 1.5px solid transparent;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s ease;
  background: #f8fafc;
  border-color: #e2e8f0;
}

.case-status-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.case-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.case-index {
  font-size: 13px;
  font-weight: 750;
  color: #334155;
}

.case-card-metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  padding-top: 8px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.metric-item .el-icon {
  font-size: 13px;
  color: #94a3b8;
}

/* Card color themes */
.card-ac {
  background-color: #f0fdf4;
  border-color: #bbf7d0;
}
.card-ac .case-index {
  color: #15803d;
}

.card-wa {
  background-color: #fef2f2;
  border-color: #fecaca;
}
.card-wa .case-index {
  color: #b91c1c;
}

.card-ce {
  background-color: #fffbeb;
  border-color: #fde68a;
}
.card-ce .case-index {
  color: #b45309;
}

.card-tle {
  background-color: #fff7ed;
  border-color: #ffedd5;
}
.card-tle .case-index {
  color: #c2410c;
}

.card-other {
  background-color: #fafafa;
  border-color: #e4e4e7;
}
.card-other .case-index {
  color: #52525b;
}

/* Source code view */
.source-section {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-top: 10px;
}

.source-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 16px;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-with-icon h2 {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.header-icon {
  font-size: 20px;
  color: #475569;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.source-editor-container {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  overflow: hidden;
  height: 480px;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02);
}
</style>
