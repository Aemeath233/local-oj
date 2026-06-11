<template>
  <section class="statement-content" :style="contentStyle">
    <template v-for="(segment, index) in segments" :key="index">
      <MarkdownView
        v-if="segment.type === 'markdown'"
        class="statement-markdown"
        :source="segment.content"
      />
      <div v-else class="sample-block">
        <div class="sample-grid">
          <div>
            <div class="sample-header-row">
              <h4>输入</h4>
              <div class="sample-actions">
                <el-button
                  v-if="segment.sample.inputText"
                  type="primary"
                  link
                  size="small"
                  :icon="DocumentCopy"
                  @click="copyText(segment.sample.inputText, '输入已复制')"
                >
                  复制
                </el-button>
                <el-button
                  v-if="segment.sample.inputText && canFillSelfTest"
                  type="success"
                  link
                  size="small"
                  :icon="VideoPlay"
                  @click="fillSelfTest(segment.sample.inputText)"
                >
                  填入自测
                </el-button>
              </div>
            </div>
            <pre :class="{ empty: !segment.sample.inputText }">{{ segment.sample.inputText || '无输入' }}</pre>
          </div>
          <div>
            <div class="sample-header-row">
              <h4>输出</h4>
              <div class="sample-actions">
                <el-button
                  v-if="segment.sample.expectedOutput"
                  type="primary"
                  link
                  size="small"
                  :icon="DocumentCopy"
                  @click="copyText(segment.sample.expectedOutput, '输出已复制')"
                >
                  复制
                </el-button>
              </div>
            </div>
            <pre :class="{ empty: !segment.sample.expectedOutput }">{{ segment.sample.expectedOutput || '无输出' }}</pre>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DocumentCopy, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownView from '../MarkdownView.vue'
import { parseProblemStatement } from '../../utils/problemSamples'

const props = withDefaults(defineProps<{
  source?: string | null
  readerFontSize?: number
  readerFontFamily?: string
  enableSelfTestFill?: boolean
}>(), {
  readerFontSize: 15,
  readerFontFamily: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif",
  enableSelfTestFill: true
})

const emit = defineEmits<{
  (e: 'fill-self-test', input: string): void
}>()

const segments = computed(() => parseProblemStatement(props.source))

const contentStyle = computed(() => ({
  fontSize: props.readerFontSize + 'px',
  fontFamily: props.readerFontFamily
}))

const canFillSelfTest = computed(() => props.enableSelfTestFill)

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
.statement-content {
  line-height: 1.65;
  color: var(--text-secondary);
  text-align: left;
}

.statement-markdown + .sample-block,
.sample-block + .statement-markdown {
  margin-top: 18px;
}

.sample-block {
  margin: 12px 0 22px;
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
  color: var(--text-muted, var(--el-text-color-secondary));
  margin: 0;
}

.sample-actions {
  display: flex;
  gap: 8px;
}

.sample-grid pre {
  margin: 0;
  background: var(--bg-app, var(--el-fill-color-light));
  border: 1px solid var(--border-color, var(--el-border-color-light));
  border-radius: 8px;
  padding: 12px 14px;
  font-family: var(--font-mono, monospace), monospace;
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--text-primary, var(--el-text-color-primary));
  white-space: pre-wrap;
  word-break: break-all;
  overflow-x: auto;
  text-align: left;
  min-height: 48px;
}

.sample-grid pre.empty {
  color: var(--text-muted, var(--el-text-color-placeholder));
  font-style: italic;
}
</style>
