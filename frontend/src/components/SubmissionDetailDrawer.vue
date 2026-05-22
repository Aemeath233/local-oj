<template>
  <el-drawer :model-value="modelValue" :title="drawerTitle" size="64%" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="detail" class="drawer-stack submission-detail">
      <div class="detail-line">
        <div>
          <strong>#{{ detail.submission.id }}</strong>
          <p class="muted">{{ detail.problem?.title || `题目 #${detail.submission.problemId}` }}</p>
        </div>
        <VerdictTag :status="detail.submission.status" :verdict="detail.submission.verdict" />
      </div>

      <div class="submission-summary-grid">
        <div>
          <span>语言</span>
          <strong>{{ detail.submission.language }}</strong>
        </div>
        <div>
          <span>分数</span>
          <strong>{{ detail.submission.score }}</strong>
        </div>
        <div>
          <span>时间</span>
          <strong>{{ detail.submission.timeMs ?? '-' }} ms</strong>
        </div>
        <div>
          <span>内存</span>
          <strong>{{ detail.submission.memoryKb ?? '-' }} KB</strong>
        </div>
      </div>

      <el-alert
        v-if="detail.submission.errorMessage"
        :title="detail.submission.errorMessage"
        type="warning"
        show-icon
        :closable="false"
      />

      <section>
        <h2>测试点</h2>
        <el-table :data="detail.cases" row-key="id" size="small">
          <el-table-column type="expand" width="44">
            <template #default="{ row }">
              <div class="case-output-grid">
                <div>
                  <h3>stdout</h3>
                  <pre :class="{ empty: !row.stdoutText }">{{ row.stdoutText || '无输出' }}</pre>
                </div>
                <div>
                  <h3>stderr / message</h3>
                  <pre :class="{ empty: !caseMessage(row) }">{{ caseMessage(row) || '无消息' }}</pre>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="caseIndex" label="#" width="70" />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <VerdictTag status="FINISHED" :verdict="row.verdict" />
            </template>
          </el-table-column>
          <el-table-column label="时间" width="110">
            <template #default="{ row }">{{ row.timeMs ?? 0 }} ms</template>
          </el-table-column>
          <el-table-column label="内存" width="120">
            <template #default="{ row }">{{ row.memoryKb ?? 0 }} KB</template>
          </el-table-column>
          <el-table-column label="消息" min-width="180">
            <template #default="{ row }">
              <span class="line-clamp">{{ caseMessage(row) || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section>
        <h2>源码</h2>
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
import { computed } from 'vue'
import VerdictTag from './VerdictTag.vue'
import CodeEditor from './CodeEditor.vue'
import type { SubmissionCaseResult, SubmissionDetail } from '../types'

const props = defineProps<{
  modelValue: boolean
  detail: SubmissionDetail | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const drawerTitle = computed(() => {
  if (!props.detail) {
    return '提交详情'
  }
  return `提交详情 #${props.detail.submission.id}`
})

function caseMessage(row: SubmissionCaseResult) {
  return row.message || row.stderrText || ''
}
</script>

<style scoped>
.source-editor-container {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  height: 480px;
  margin-top: 12px;
}
</style>
