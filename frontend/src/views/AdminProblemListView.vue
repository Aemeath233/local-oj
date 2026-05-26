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
        <el-table-column prop="difficulty" label="难度" width="110">
          <template #default="{ row }">
            <span :class="'difficulty-badge ' + (row.difficulty || 'Easy').toLowerCase()">
              {{ row.difficulty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="tag in splitTags(row.tags)"
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
          </template>
        </el-table-column>
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

    <el-dialog
      v-model="importDialogVisible"
      title="批量导入题目 ZIP 包"
      width="860px"
      destroy-on-close
      @close="resetImportState"
    >
      <div class="zip-import-dialog-body">
        <el-upload
          class="zip-drop"
          drag
          action=""
          :auto-upload="false"
          multiple
          accept=".zip"
          :show-file-list="false"
          :on-change="onFileSelected"
        >
          <el-icon class="zip-drop-icon"><UploadFilled /></el-icon>
          <div class="zip-drop-title">
            拖入多个标准题目 ZIP 包，或点击选择多个文件
          </div>
          <div class="zip-drop-subtitle">可同时选择多道题目的 ZIP 包进行批量静默导入。</div>
          <template #tip>
            <div class="zip-upload-tip">
              单个题目包大小限制 128 MB。每个包内需包含 <code>config.yml</code>、<code>statement.md</code> 以及 <code>cases/</code> 测试用例文件夹。
            </div>
          </template>
        </el-upload>

        <!-- Selected Files List -->
        <div v-if="importFilesList.length > 0" class="zip-selected-files-list">
          <div class="list-header">
            <h3>待导入列表 ({{ importFilesList.length }})</h3>
            <el-button link type="danger" :disabled="importingPackage" @click="clearFileList">清空列表</el-button>
          </div>
          <div class="selected-files-container">
            <div v-for="(file, index) in importFilesList" :key="index" class="batch-file-row">
              <div class="file-meta">
                <span class="file-name" :title="file.name">{{ file.name }}</span>
                <span class="file-size">{{ formatSize(file.size) }}</span>
              </div>
              <div class="file-status-group">
                <el-tag v-if="file.status === 'pending'" type="info" size="small">待导入</el-tag>
                <el-tag v-else-if="file.status === 'uploading'" type="primary" size="small">正在解析...</el-tag>
                <el-tag v-else-if="file.status === 'success'" type="success" size="small">导入成功</el-tag>
                <el-tag v-else-if="file.status === 'failed'" type="danger" size="small" :title="file.errorMsg">导入失败</el-tag>
                
                <span v-if="file.status === 'failed'" class="error-msg-detail">{{ file.errorMsg }}</span>
                
                <el-button
                  v-if="file.status !== 'uploading' && file.status !== 'success'"
                  link
                  type="danger"
                  :disabled="importingPackage"
                  :icon="Delete"
                  class="remove-file-btn"
                  @click="removeFileFromList(index)"
                />
              </div>
            </div>
          </div>
        </div>

        <section v-if="importFilesList.length === 0" class="zip-format-hint">
          <div class="zip-format-copy">
            <h3>标准题目包结构</h3>
            <p>每个 ZIP 只创建一道题。题面统一写在 Markdown 文件中，测试点由同名输入输出文件自动配对。</p>
          </div>
          <pre class="zip-tree">problem-package.zip
├── config.yml
├── statement.md
└── cases/
    ├── 1.in
    ├── 1.out
    ├── 2.in
    └── 2.out</pre>
        </section>
      </div>
      <template #footer>
        <div class="dialog-footer zip-dialog-footer">
          <el-button :icon="Download" @click="downloadExamplePackage">下载示例包</el-button>
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="importingPackage"
            :disabled="importFilesList.length === 0 || !hasPendingOrFailedFiles"
            @click="startBatchImport"
          >
            开始批量导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Upload, UploadFilled, View } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { deleteProblem, fetchAdminProblems, http, importProblemPackage, setProblemVisibility } from '../api/http'
import { getTagColor } from '../utils/tag'
import type { AdminProblemSummary } from '../types'

const router = useRouter()
const loading = ref(false)
const importingPackage = ref(false)
const visibilityUpdating = ref<number | null>(null)
const problems = ref<AdminProblemSummary[]>([])

const importDialogVisible = ref(false)
const importFilesList = ref<any[]>([])

const hasPendingOrFailedFiles = computed(() => {
  return importFilesList.value.some(f => f.status === 'pending' || f.status === 'failed')
})

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
    const file = uploadFile.raw as File
    if (!file.name.toLowerCase().endsWith('.zip')) {
      ElMessage.warning(`文件「${file.name}」非 .zip 格式，已忽略`)
      return
    }
    
    // Prevent duplicate selection by name
    if (importFilesList.value.some(f => f.name === file.name)) {
      return
    }

    importFilesList.value.push({
      name: file.name,
      size: file.size,
      raw: file,
      status: 'pending',
      errorMsg: ''
    })
  }
}

function removeFileFromList(index: number) {
  importFilesList.value.splice(index, 1)
}

function clearFileList() {
  importFilesList.value = []
}

function resetImportState() {
  importFilesList.value = []
  importingPackage.value = false
}

async function startBatchImport() {
  if (importFilesList.value.length === 0) return
  
  importingPackage.value = true
  let successCount = 0
  let failCount = 0

  for (const file of importFilesList.value) {
    if (file.status === 'success') continue
    
    file.status = 'uploading'
    file.errorMsg = ''
    
    try {
      await importProblemPackage(file.raw)
      file.status = 'success'
      successCount++
    } catch (error: any) {
      file.status = 'failed'
      file.errorMsg = error.response?.data?.message || '导入失败，请检查包结构'
      failCount++
    }
  }

  importingPackage.value = false
  
  if (successCount > 0) {
    ElMessage.success(`批量导入完成！成功 ${successCount} 个，失败 ${failCount} 个`)
    await load() // Reload problem list
  } else if (failCount > 0) {
    ElMessage.error(`批量导入失败，共 ${failCount} 个题目包导入出错，请检查原因`)
  }
}

function formatSize(bytes?: number) {
  const size = bytes || 0
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${size} B`
}

function formatMemory(kb?: number) {
  const size = kb || 0
  if (size >= 1024) {
    return `${(size / 1024).toFixed(0)} MB`
  }
  return `${size} KB`
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

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

onMounted(load)
</script>

<style scoped>
.zip-import-dialog-body {
  display: grid;
  gap: 16px;
}

.zip-drop :deep(.el-upload-dragger) {
  padding: 30px 18px;
  border-radius: 8px;
  background: #fbfcfd;
}

.zip-drop-icon {
  margin-bottom: 10px;
  color: #0f766e;
  font-size: 42px;
}

.zip-drop-title {
  color: #1f2937;
  font-weight: 650;
}

.zip-drop-subtitle,
.zip-upload-tip {
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
}

.zip-selected-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: #ffffff;
}

.zip-selected-files-list {
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
  display: grid;
  gap: 12px;
}

.zip-selected-files-list .list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.zip-selected-files-list h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: #1e293b;
}

.selected-files-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
}

.batch-file-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.batch-file-row .file-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.batch-file-row .file-size {
  font-size: 0.8rem;
  color: #64748b;
}

.file-status-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.error-msg-detail {
  font-size: 0.8rem;
  color: #ef4444;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file-btn {
  padding: 0;
  margin-left: 4px !important;
}

.zip-preview,
.zip-format-hint {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  background: #ffffff;
}

.zip-preview-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.zip-preview-heading h3,
.zip-format-copy h3 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.zip-preview-heading p,
.zip-format-copy p {
  margin: 5px 0 0;
  color: #667085;
}

.zip-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.zip-summary-grid > div {
  display: grid;
  gap: 5px;
  min-height: 72px;
  padding: 11px;
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  background: #fbfcfd;
}

.zip-summary-grid span {
  color: #667085;
  font-size: 12px;
}

.zip-summary-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zip-warning {
  margin: 0;
}

.zip-warning-list {
  display: grid;
  gap: 4px;
}

.zip-tree {
  min-height: auto;
  margin: 0;
}

.zip-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 720px) {
  .zip-summary-grid {
    grid-template-columns: 1fr;
  }

  .zip-dialog-footer {
    flex-wrap: wrap;
  }
}

.premium-tag {
  font-weight: 500;
  border-radius: 6px;
  font-size: 0.8rem;
  padding: 0.15rem 0.5rem;
  background-color: transparent !important;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
