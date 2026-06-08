<template>
  <div class="tab-pane-container">
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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DocumentCopy, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTagColor } from '../../utils/tag'
import type { ProblemDetail } from '../../types'
import MarkdownView from '../MarkdownView.vue'

const props = defineProps<{
  problem: ProblemDetail
  readerFontSize: number
  readerFontFamily: string
}>()

const emit = defineEmits<{
  (e: 'fill-self-test', input: string): void
}>()

const statementMarkdown = computed(() => {
  return props.problem?.description || ''
})

const sampleCases = computed(() => {
  return props.problem?.samples || []
})

const problemTags = computed(() => {
  return (props.problem?.tags || '')
    .split(/[,，]/)
    .map(t => t.trim())
    .filter(t => t.length > 0)
})

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
  emit('fill-self-test', text)
}
</script>

<style scoped>
.tab-pane-container {
  display: flex;
  flex-direction: column;
}

.statement-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--border-light);
  padding-bottom: 10px;
  margin-bottom: 14px;
}
.statement-header h1 {
  font-size: 1.6rem;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0 0 6px 0;
  text-align: left;
}

.statement-header p {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-family: var(--font-mono), monospace;
  margin: 0;
  text-align: left;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.premium-tag {
  font-weight: 600;
  border-radius: 6px;
}

.difficulty-badge {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 8px;
  text-transform: uppercase;
}

.difficulty-badge.easy {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.difficulty-badge.medium {
  background: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

.difficulty-badge.hard {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.statement-body {
  margin-top: 0 !important;
  line-height: 1.65;
  color: var(--text-secondary);
  margin-bottom: 30px;
  text-align: left;
}

.sample-list {
  margin-top: 10px;
}

.sample-list h2 {
  font-size: 1.2rem;
  font-weight: 750;
  color: var(--text-primary);
  margin: 0 0 16px 0;
  text-align: left;
}

.sample-block {
  margin-bottom: 20px;
}

.sample-block h3 {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-secondary);
  margin: 0 0 10px 0;
  text-align: left;
}

.sample-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 768px) {
  .sample-grid {
    grid-template-columns: 1fr;
  }
}

.sample-grid > div {
  display: flex;
  flex-direction: column;
}

.sample-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.sample-header-row h4 {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  margin: 0;
}

.sample-actions {
  display: flex;
  gap: 8px;
}

.sample-grid pre {
  margin: 0;
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px 14px;
  font-family: var(--font-mono), monospace;
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  overflow-x: auto;
  text-align: left;
}

.sample-grid pre.empty {
  color: var(--text-muted);
  font-style: italic;
}
</style>
