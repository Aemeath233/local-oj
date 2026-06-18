<template>
  <aside class="submit-panel panel">
    <div class="submit-toolbar">
      <el-select v-model="localLanguage" class="language-select">
        <el-option label="C++20 (O2)" value="CPP" />
        <el-option label="C++20 (O3)" value="CPP_O3" />
        <el-option label="C17 (O2)" value="C" />
        <el-option label="Python 3.12" value="PYTHON" />
        <el-option label="PyPy 3" value="PYPY3" />
        <el-option label="Java 21" value="JAVA" />
      </el-select>
      <el-button :icon="Brush" :loading="formatting" @click="$emit('format')">简单整理</el-button>
      <el-button :loading="selfTesting" :disabled="cooldownSeconds > 0" @click="$emit('run-custom-test')">
        <template #icon>
          <span v-if="cooldownSeconds > 0" class="cooldown-num-icon">{{ cooldownSeconds }}</span>
          <el-icon v-else><VideoPlay /></el-icon>
        </template>
        自测
      </el-button>
      <el-button type="primary" :loading="submitting" :disabled="cooldownSeconds > 0" @click="$emit('submit')">
        <template #icon>
          <span v-if="cooldownSeconds > 0" class="cooldown-num-icon white-num">{{ cooldownSeconds }}</span>
          <el-icon v-else><Upload /></el-icon>
        </template>
        提交
      </el-button>
    </div>
    
    <CodeEditor ref="codeEditorRef" v-model="localSourceCode" :language="localLanguage" />

    <!-- Collapsible Self-test Console -->
    <ProblemSelfTestConsole
      ref="selfTestConsoleRef"
      v-model="localSelfTestInput"
      :self-testing="selfTesting"
      :self-test-error="selfTestError"
      :self-test-result="selfTestResult"
      @clear="$emit('clear-self-test')"
    />

    <!-- Mini Submissions list for current problem and current user -->
    <ProblemMiniSubmissions
      :submissions="submissions"
      :submissions-loading="submissionsLoading"
      @refresh="$emit('refresh-submissions')"
      @click-submission="(sub) => $emit('open-submission', sub)"
    />

    <el-alert v-if="message" :title="message" type="success" show-icon :closable="false" style="margin-top: 8px;" />
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Upload, VideoPlay, Brush } from '@element-plus/icons-vue'
import CodeEditor from './CodeEditor.vue'
import ProblemSelfTestConsole from './problem/ProblemSelfTestConsole.vue'
import ProblemMiniSubmissions from './problem/ProblemMiniSubmissions.vue'
import type { Language, SelfTestResult, SubmissionSummary } from '../types'

const props = defineProps<{
  language: Language
  sourceCode: string
  formatting: boolean
  selfTesting: boolean
  submitting: boolean
  cooldownSeconds: number
  selfTestInput: string
  selfTestError: string
  selfTestResult: SelfTestResult | null
  submissions: SubmissionSummary[]
  submissionsLoading: boolean
  message: string
}>()

const emit = defineEmits<{
  (e: 'update:language', val: Language): void
  (e: 'update:sourceCode', val: string): void
  (e: 'update:selfTestInput', val: string): void
  (e: 'format'): void
  (e: 'run-custom-test'): void
  (e: 'submit'): void
  (e: 'clear-self-test'): void
  (e: 'refresh-submissions'): void
  (e: 'open-submission', sub: SubmissionSummary): void
}>()

const localLanguage = computed({
  get: () => props.language,
  set: (val) => emit('update:language', val)
})

const localSourceCode = computed({
  get: () => props.sourceCode,
  set: (val) => emit('update:sourceCode', val)
})

const localSelfTestInput = computed({
  get: () => props.selfTestInput,
  set: (val) => emit('update:selfTestInput', val)
})

const codeEditorRef = ref<InstanceType<typeof CodeEditor> | null>(null)
const selfTestConsoleRef = ref<InstanceType<typeof ProblemSelfTestConsole> | null>(null)

defineExpose({
  codeEditorRef,
  selfTestConsoleRef
})
</script>

<style scoped>
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
  .submit-panel {
    width: 100% !important;
    flex: none !important;
    height: auto !important;
    overflow-y: visible !important;
  }
}
</style>
