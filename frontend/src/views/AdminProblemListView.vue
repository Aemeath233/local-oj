<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>题目管理</h1>
        <p>{{ problems.length }} 道题目</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="Upload" @click="importDialogVisible = true">导入 ZIP</el-button>
        <RouterLink to="/admin/problems/new">
          <el-button type="primary" :icon="Plus">新建</el-button>
        </RouterLink>
      </div>
    </div>

    <section class="panel table-panel">
      <el-table v-loading="loading" :data="problems" row-key="id">
        <el-table-column prop="id" label="#" width="76" />
        <el-table-column label="题目" min-width="240">
          <template #default="{ row }">
            <div class="problem-title">{{ row.title }}</div>
            <div class="muted">{{ row.slug }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="110" />
        <el-table-column prop="tags" label="标签" min-width="160" />
        <el-table-column label="测试点" width="96">
          <template #default="{ row }">
            <span>{{ row.testCaseCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交" width="96">
          <template #default="{ row }">
            <span>{{ row.submissionCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可见" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.visible"
              :loading="visibilityUpdating === row.id"
              @change="onVisibilityChange(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button :icon="View" circle @click="router.push(`/problems/${row.id}`)" title="查看" />
            <el-button :icon="Edit" circle type="primary" @click="router.push(`/admin/problems/${row.id}`)" title="编辑" />
            <el-button :icon="Delete" circle type="danger" @click="handleDelete(row)" title="删除" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ZIP Package Import Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入题目 ZIP 包"
      width="580px"
      destroy-on-close
      @close="resetImportState"
    >
      <div class="zip-import-dialog-body" style="padding: 10px 0;">
        <el-upload
          drag
          action=""
          :auto-upload="false"
          :limit="1"
          accept=".zip"
          :on-change="onFileSelected"
          :on-remove="onFileRemoved"
          :file-list="selectedFileList"
        >
          <el-icon class="el-icon--upload" style="font-size: 48px; color: #0f766e; margin-bottom: 12px;"><upload-filled /></el-icon>
          <div class="el-upload__text" style="font-size: 14px; color: #475569;">
            将题目 ZIP 压缩包拖到此处，或 <em style="color: #0f766e; font-weight: 600; font-style: normal; cursor: pointer;">点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip" style="text-align: center; margin-top: 8px; color: #94a3b8; font-size: 12px;">
              仅支持上传 <code>.zip</code> 格式的标准题目导入包，文件大小不超过 50MB
            </div>
          </template>
        </el-upload>

        <div class="zip-format-hint panel" style="margin-top: 18px; padding: 14px; background: #fafbfc; border-color: #e2e8f0; border-radius: 6px;">
          <h4 style="margin: 0 0 8px; font-size: 13.5px; color: #1e293b; font-weight: 650; display: flex; align-items: center; gap: 6px;">
            📌 标准题目包格式规范
          </h4>
          <ul style="margin: 0; padding-left: 18px; font-size: 12px; color: #64748b; line-height: 1.7;">
            <li><code>config.yml</code> / <code>config.yaml</code>: YAML 格式配置，包含题目 Slug、名称、限制与样例</li>
            <li><code>statement.md</code> / <code>problem.md</code>: Markdown 格式题面主体内容</li>
            <li><code>cases/</code>: 评测点文件夹，含相同基名的 <code>.in</code> 和 <code>.out/.ans</code> 测试点文件</li>
          </ul>
          <div style="margin-top: 14px; border-top: 1px dashed #e2e8f0; padding-top: 12px; display: flex; align-items: center; justify-content: space-between;">
            <span style="font-size: 12px; color: #94a3b8;">不清楚具体格式？</span>
            <el-link type="primary" :underline="false" style="font-size: 12.5px; font-weight: 600; color: #0f766e;" @click="downloadExamplePackage">
              📥 下载标准示例题目包
            </el-link>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer" style="display: flex; justify-content: flex-end; gap: 10px;">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            color="#0f766e"
            style="border: none;"
            :loading="importingPackage"
            :disabled="!selectedFile"
            @click="startImport"
          >
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, Upload, View, UploadFilled, Delete } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminProblems, importProblemPackage, setProblemVisibility, http, deleteProblem } from '../api/http'
import type { AdminProblemSummary } from '../types'

const router = useRouter()
const loading = ref(false)
const importingPackage = ref(false)
const visibilityUpdating = ref<number | null>(null)
const problems = ref<AdminProblemSummary[]>([])

// ZIP Package Import Dialog states
const importDialogVisible = ref(false)
const selectedFile = ref<File | null>(null)
const selectedFileList = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    problems.value = await fetchAdminProblems()
  } finally {
    loading.value = false
  }
}

async function handleDelete(row: AdminProblemSummary) {
  try {
    await ElMessageBox.confirm(
      `确定要永久删除题目「${row.title}」吗？此操作将同时清空该题目的所有评测点、历史提交记录及题解，且不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        buttonSize: 'default',
        confirmButtonClass: 'el-button--danger'
      }
    )
    loading.value = true
    await deleteProblem(row.id)
    ElMessage.success('题目删除成功')
    await load()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除题目失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

async function updateVisibility(problem: AdminProblemSummary, visible: boolean) {
  visibilityUpdating.value = problem.id
  const previous = !visible
  try {
    const updated = await setProblemVisibility(problem.id, visible)
    Object.assign(problem, updated)
    ElMessage.success(visible ? '题目已显示' : '题目已隐藏')
  } catch (error) {
    problem.visible = previous
    throw error
  } finally {
    visibilityUpdating.value = null
  }
}

function onVisibilityChange(problem: AdminProblemSummary, value: string | number | boolean) {
  updateVisibility(problem, Boolean(value))
}

function onFileSelected(uploadFile: any) {
  if (uploadFile && uploadFile.raw) {
    selectedFile.value = uploadFile.raw as File
    selectedFileList.value = [uploadFile]
  }
}

function onFileRemoved() {
  selectedFile.value = null
  selectedFileList.value = []
}

function resetImportState() {
  selectedFile.value = null
  selectedFileList.value = []
}

async function startImport() {
  if (!selectedFile.value) {
    return
  }
  importingPackage.value = true
  try {
    const detail = await importProblemPackage(selectedFile.value)
    ElMessage.success('题目包已成功导入！')
    importDialogVisible.value = false
    await router.push(`/admin/problems/${detail.problem.id}`)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '导入题目包失败，请检查文件格式')
  } finally {
    importingPackage.value = false
  }
}

async function downloadExamplePackage() {
  try {
    const response = await http.get('/admin/problems/example-package', {
      responseType: 'blob'
    })
    const blob = new Blob([response.data], { type: 'application/zip' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'example-package.zip'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (error) {
    ElMessage.error('下载示例包失败，请重试')
  }
}

onMounted(load)
</script>

<style scoped>
.hidden-file-input {
  display: none;
}
</style>
