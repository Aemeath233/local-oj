<template>
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
            v-model="localInput"
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
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Cpu, ArrowUp } from '@element-plus/icons-vue'
import type { SelfTestResult } from '../../types'

const props = defineProps<{
  modelValue: string // binds to selfTestInput
  selfTesting: boolean
  selfTestError: string
  selfTestResult: SelfTestResult | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'clear'): void
}>()

const consoleExpanded = ref(false)
const activeConsoleTab = ref('input')

const localInput = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const selfTestMessage = computed(() => {
  const result = props.selfTestResult
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

// Automatically expand console and switch to results tab when testing starts or outputs arrive
watch(() => props.selfTesting, (newTesting) => {
  if (newTesting) {
    consoleExpanded.value = true
    activeConsoleTab.value = 'result'
  }
})

watch(() => props.selfTestResult, (newResult) => {
  if (newResult) {
    consoleExpanded.value = true
    activeConsoleTab.value = 'result'
  }
})

watch(() => props.selfTestError, (newError) => {
  if (newError) {
    consoleExpanded.value = true
    activeConsoleTab.value = 'result'
  }
})

function clearSelfTest() {
  emit('clear')
}

// Offer external control method to expand console
defineExpose({
  expandInputTab: () => {
    consoleExpanded.value = true
    activeConsoleTab.value = 'input'
  }
})
</script>

<style scoped>
.console-section {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  margin-top: 14px;
  overflow: hidden;
  box-sizing: border-box;
  text-align: left;
}

.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-app);
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
}

.console-section.is-expanded .console-header {
  border-bottom-color: var(--border-color);
}

.console-title {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.console-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-arrow {
  font-size: 0.85rem;
  color: var(--text-muted);
  transition: transform 0.25s ease;
}

.toggle-arrow.is-rotated {
  transform: rotate(180deg);
}

.console-body {
  padding: 14px;
  background: var(--bg-surface);
}

.self-test-loading {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.self-test-result-wrapper {
  padding: 10px 0;
}

.self-test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.self-test-head {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border-light);
  padding-bottom: 8px;
}

.self-test-meta {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-family: var(--font-mono), monospace;
  font-weight: 600;
}

.case-output-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 768px) {
  .case-output-grid {
    grid-template-columns: 1fr;
  }
}

.case-output-grid h3 {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-muted);
  margin: 0 0 6px 0;
}

.case-output-grid pre {
  margin: 0;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 10px 12px;
  font-family: var(--font-mono), monospace;
  font-size: 0.82rem;
  line-height: 1.4;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  height: 140px;
  overflow-y: auto;
  box-sizing: border-box;
}

.case-output-grid pre.empty {
  color: var(--text-muted);
  font-style: italic;
}

.self-test-placeholder {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  padding: 0 20px;
}
</style>
