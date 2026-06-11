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

    <ProblemStatementContent
      :source="statementMarkdown"
      :reader-font-size="readerFontSize"
      :reader-font-family="readerFontFamily"
      @fill-self-test="fillSelfTest"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getTagColor } from '../../utils/tag'
import type { ProblemDetail } from '../../types'
import ProblemStatementContent from './ProblemStatementContent.vue'

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

const problemTags = computed(() => {
  return (props.problem?.tags || '')
    .split(/[,，]/)
    .map(t => t.trim())
    .filter(t => t.length > 0)
})

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

</style>
