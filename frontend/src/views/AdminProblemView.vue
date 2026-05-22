<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading" style="display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h1>{{ isEdit ? '编辑题目' : '新建题目' }}</h1>
        <p>配置题目基本信息、Markdown 题面与测试数据。</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 10px;">
        <el-button @click="router.push('/admin/problems')">返回列表</el-button>
        <el-button type="success" :icon="Check" :loading="saving" @click="save">保存并发布</el-button>
      </div>
    </div>

    <div class="admin-problem-layout">
      <!-- Sidebar Menu on the left -->
      <div class="problem-sidebar panel">
        <div class="sidebar-header">
          <h3>编辑步骤</h3>
        </div>
        <div class="sidebar-menu">
          <div
            class="sidebar-item"
            :class="{ active: activeStep === 0 }"
            @click="activeStep = 0"
          >
            <el-icon><InfoFilled /></el-icon>
            <span>1. 基本信息</span>
          </div>
          <div
            class="sidebar-item"
            :class="{ active: activeStep === 1 }"
            @click="activeStep = 1"
          >
            <el-icon><Document /></el-icon>
            <span>2. 题面内容</span>
          </div>
          <div
            class="sidebar-item"
            :class="{ active: activeStep === 2 }"
            @click="activeStep = 2"
          >
            <el-icon><Files /></el-icon>
            <span>3. 测试数据</span>
          </div>
        </div>

        <div class="sidebar-footer">
          <el-button
            type="success"
            class="sidebar-save-btn"
            :icon="Check"
            :loading="saving"
            @click="save"
          >
            保存并发布
          </el-button>
        </div>
      </div>

      <!-- Form Content on the right -->
      <div class="problem-form-container">
        <el-form class="admin-form panel" :model="form" label-position="top" style="padding: 24px;">

          <!-- Step 1: Basic Information -->
          <div v-show="activeStep === 0" class="step-content">
            <div class="form-grid">
              <el-form-item label="Slug (唯一缩略名 / URL 标识)">
                <el-input v-model="form.slug" placeholder="例如: a-plus-b" />
              </el-form-item>
              <el-form-item label="标题">
                <el-input v-model="form.title" placeholder="请输入题目显示名称" />
              </el-form-item>
              <el-form-item label="难度">
                <el-select v-model="form.difficulty">
                  <el-option label="Easy (简单)" value="Easy" />
                  <el-option label="Medium (中等)" value="Medium" />
                  <el-option label="Hard (困难)" value="Hard" />
                </el-select>
              </el-form-item>
              <el-form-item label="标签">
                <div class="tag-input-area">
                  <el-tag
                    v-for="tag in selectedTags"
                    :key="tag"
                    closable
                    effect="plain"
                    @close="removeTag(tag)"
                    style="margin-right: 6px; margin-bottom: 4px;"
                  >{{ tag }}</el-tag>
                  <el-input
                    v-model="tagInput"
                    placeholder="输入标签后按 Enter"
                    size="small"
                    style="width: 160px;"
                    @keydown.enter.prevent="addTag"
                  />
                </div>
              </el-form-item>
              <el-form-item label="时间限制 ms">
                <el-input-number v-model="form.timeLimitMs" :min="100" :step="100" />
              </el-form-item>
              <el-form-item label="内存限制 KB">
                <el-input-number v-model="form.memoryLimitKb" :min="16384" :step="16384" />
              </el-form-item>
            </div>
            <el-form-item label="是否可见" style="margin-top: 14px;">
              <el-switch v-model="form.visible" active-text="公开（学生在题库中可见）" />
            </el-form-item>
          </div>

          <!-- Step 2: Problem Statement (Markdown) -->
          <div v-show="activeStep === 1" class="step-content">
            <el-tabs v-model="statementTab" class="statement-tabs">
              <el-tab-pane label="编辑 Markdown" name="edit">
                <el-form-item label="题面 Markdown" label-position="top">
                  <el-input
                    v-model="form.description"
                    type="textarea"
                    :autosize="{ minRows: 14, maxRows: 30 }"
                    placeholder="在此输入题目的详细描述。支持标准的 Markdown 格式，您可以直接把题目描述、输入格式、输出格式和样例写成一个完整的 Markdown 文档。"
                  />
                </el-form-item>
              </el-tab-pane>
              <el-tab-pane label="实时预览" name="preview">
                <div class="statement-preview" style="border: 1px solid #d8dee6; padding: 20px; border-radius: 6px; background: #fafbfc; min-height: 320px; max-height: 520px; overflow-y: auto;">
                  <MarkdownView :source="form.description || '*暂无预览内容，请点击编辑标签页输入题面 Markdown。*'" />
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- Step 3: Test Cases -->
          <div v-show="activeStep === 2" class="step-content">
            <div class="testcase-heading">
              <h2>测试数据包导入</h2>
              <div class="testcase-actions">
                <input
                  ref="caseFileInput"
                  class="visually-hidden"
                  type="file"
                  multiple
                  accept=".in,.out,.ans"
                  @change="onFilesSelected"
                />
                <el-button type="warning" :icon="Upload" :loading="importing" @click="openCasePicker">
                  选择本地 .in/.out 文件
                </el-button>
              </div>
            </div>

            <div v-if="form.testCases.length === 0" class="empty-state" style="border: 1px dashed #cbd5e1; padding: 48px 0; text-align: center; border-radius: 8px; margin-top: 14px;">
              <p style="color: #64748b; font-weight: 550; margin-bottom: 8px;">暂无测试点数据</p>
              <p style="font-size: 13px; color: #94a3b8; max-width: 500px; margin: 0 auto;">
                支持一次多选并上传您的评测输入文件（如 <code>1.in</code>）与对应的预期输出文件（如 <code>1.out</code> 或 <code>1.ans</code>）。系统会自动配对并在此生成测试点表格。
              </p>
            </div>

            <div v-else class="case-table" style="margin-top: 14px;">
              <div class="case-row case-row-header">
                <span>测试点名称</span>
                <span>输入文件</span>
                <span>预期输出</span>
                <span>分数占比</span>
                <span>样例标记</span>
                <span>操作</span>
              </div>
              <div v-for="(testCase, index) in form.testCases" :key="index" class="case-row">
                <span class="case-name" style="font-family: monospace; font-weight: 600;">{{ testCase.name || testCase.caseName || `case-${index + 1}` }}</span>
                <span>{{ testCase.inputFile }} · {{ formatSize(testCase.inputSize) }}</span>
                <span>{{ testCase.outputFile }} · {{ formatSize(testCase.outputSize) }}</span>
                <el-input-number v-model="testCase.score" :min="0" :max="100" />
                <el-switch v-model="testCase.sample" active-text="样例" />
                <el-button :icon="Delete" circle type="danger" plain @click="removeCase(index)" />
              </div>
            </div>
          </div>

          <!-- Step Navigation Button Bar -->
          <div class="form-actions" style="margin-top: 24px; border-top: 1px solid #f1f5f9; padding-top: 18px; display: flex; justify-content: space-between;">
            <div>
              <el-button v-if="activeStep > 0" @click="prevStep">上一步</el-button>
            </div>
            <div>
              <el-button v-if="activeStep < 2" type="primary" @click="nextStep">下一步</el-button>
              <el-button v-else type="success" :icon="Check" :loading="saving" @click="save">保存并发布</el-button>
            </div>
          </div>

        </el-form>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Delete, Upload, InfoFilled, Document, Files } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import MarkdownView from '../components/MarkdownView.vue'
import {
  createProblem,
  fetchAdminProblem,
  importTestCaseFiles,
  updateProblem
} from '../api/http'
import type { CreateProblemPayload } from '../api/http'
import type { AdminProblemDetail } from '../types'

interface TestCaseForm {
  uploadToken?: string
  name?: string
  caseName?: string
  inputFile: string
  outputFile: string
  inputSize: number
  outputSize: number
  score: number
  sample: boolean
}

interface ProblemForm {
  slug: string
  title: string
  description: string
  timeLimitMs: number
  memoryLimitKb: number
  difficulty: string
  visible: boolean
  testCases: TestCaseForm[]
}

const route = useRoute()
const router = useRouter()
const saving = ref(false)
const importing = ref(false)
const statementTab = ref('edit')
const caseFileInput = ref<HTMLInputElement | null>(null)
const selectedTags = ref<string[]>([])
const tagInput = ref('')

const problemId = computed(() => {
  const idStr = route.params.id
  return idStr ? Number(idStr) : null
})
const isEdit = computed(() => problemId.value !== null)

// Steps wizard state control
const activeStep = ref(0)

function nextStep() {
  if (activeStep.value < 2) {
    activeStep.value++
  }
}

function prevStep() {
  if (activeStep.value > 0) {
    activeStep.value--
  }
}



const form = reactive<ProblemForm>({
  slug: '',
  title: '',
  description: '',
  timeLimitMs: 1000,
  memoryLimitKb: 262144,
  difficulty: 'Easy',
  visible: true,
  testCases: []
})

onMounted(async () => {
  if (isEdit.value && problemId.value) {
    try {
      const detail = await fetchAdminProblem(problemId.value)
      form.slug = detail.problem.slug
      form.title = detail.problem.title
      form.description = buildEditableStatement(detail)
      form.timeLimitMs = detail.problem.timeLimitMs
      form.memoryLimitKb = detail.problem.memoryLimitKb
      form.difficulty = detail.problem.difficulty
      selectedTags.value = splitTags(detail.problem.tags)
      form.visible = detail.problem.visible
      form.testCases = detail.testCases.map((tc, index) => ({
        name: tc.caseName || tc.name || `case-${index + 1}`,
        caseName: tc.caseName,
        inputFile: tc.inputFile || '',
        outputFile: tc.outputFile || '',
        inputSize: tc.inputSize ?? tc.inputText?.length ?? 0,
        outputSize: tc.outputSize ?? tc.expectedOutput?.length ?? 0,
        score: tc.score,
        sample: Boolean(tc.sample)
      }))
    } catch (e) {
      ElMessage.error('获取题目详情失败')
      router.push('/admin/problems')
    }
  }
})

function removeCase(index: number) {
  form.testCases.splice(index, 1)
}

function openCasePicker() {
  caseFileInput.value?.click()
}

async function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.length === 0) return
  importing.value = true
  try {
    const cases = await importTestCaseFiles(files)
    if (cases && cases.length > 0) {
      form.testCases = cases.map((testCase) => ({
        uploadToken: testCase.uploadToken,
        name: testCase.name,
        inputFile: testCase.inputFile,
        outputFile: testCase.outputFile,
        inputSize: testCase.inputSize,
        outputSize: testCase.outputSize,
        score: testCase.score,
        sample: Boolean(testCase.sample)
      }))
      ElMessage.success(`成功导入 ${cases.length} 个测试点！`)
    } else {
      ElMessage.warning('未找到配对的 .in 和 .out/.ans 文件')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '导入测试点失败，请检查文件格式')
  } finally {
    importing.value = false
    input.value = ''
  }
}

async function save() {
  if (form.testCases.length === 0) {
    ElMessage.warning('请先上传测试点文件')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && problemId.value) {
      await updateProblem(problemId.value, buildPayload())
      ElMessage.success('保存修改成功！')
      router.push(`/problems/${problemId.value}`)
    } else {
      const detail = await createProblem(buildPayload())
      ElMessage.success('创建题目成功！')
      router.push(`/problems/${detail.problem.id}`)
    }
  } catch (error) {
    // Handled by request interceptor / UI alert
  } finally {
    saving.value = false
  }
}



function buildPayload(): CreateProblemPayload {
  return {
    slug: form.slug,
    title: form.title,
    description: form.description,
    timeLimitMs: form.timeLimitMs,
    memoryLimitKb: form.memoryLimitKb,
    difficulty: form.difficulty,
    tags: selectedTags.value.join(', '),
    visible: form.visible,
    testCases: form.testCases
  }
}

function buildEditableStatement(detail: AdminProblemDetail) {
  return detail.problem.description?.trimEnd() || ''
}

function formatSize(bytes?: number) {
  const size = bytes || 0
  if (size < 1024) {
    return `${size} B`
  }
  return `${(size / 1024).toFixed(1)} KB`
}

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function addTag() {
  const name = tagInput.value.trim()
  if (name && !selectedTags.value.includes(name)) {
    selectedTags.value.push(name)
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  selectedTags.value = selectedTags.value.filter(t => t !== tag)
}
</script>

<style scoped>
.admin-problem-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  margin-top: 18px;
}

.problem-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  overflow: hidden;
  position: sticky;
  top: 24px;
}

.sidebar-header {
  padding: 16px 20px;
  border-bottom: 1px solid #eef2f6;
  background: #fafbfc;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 15px;
  color: #1f2937;
  font-weight: 600;
}

.sidebar-menu {
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 6px;
  color: #4b5563;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.sidebar-item:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.sidebar-item.active {
  background: #e7f5f2;
  color: #0f766e;
}

.sidebar-item .el-icon {
  font-size: 18px;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #eef2f6;
  background: #fafbfc;
}

.sidebar-save-btn {
  width: 100%;
}

.problem-form-container {
  flex-grow: 1;
  min-width: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.step-content {
  animation: fadeIn 0.2s ease-in-out;
}

.testcase-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 12px;
}

.testcase-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.case-table {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.case-row {
  display: grid;
  grid-template-columns: 140px minmax(120px, 1fr) minmax(120px, 1fr) 140px 100px 60px;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #ffffff;
  border-bottom: 1px solid #f1f5f9;
}

.case-row:last-child {
  border-bottom: none;
}

.case-row-header {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  font-size: 13px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tag-input-area {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 4px 0;
}
</style>
