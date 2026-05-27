<template>
  <section class="page-stack" v-loading="loading">
    <AdminNav />

    <div class="breadcrumb-bar">
      <el-button link :icon="ArrowLeft" @click="router.push('/admin/training')">返回题单列表</el-button>
    </div>

    <!-- Top Pane: Edit Metadata -->
    <div v-if="set" class="panel workspace-card">
      <div class="panel-toolbar">
        <h2>题单基本属性配置</h2>
        <el-button type="success" :icon="Check" :loading="savingMetadata" @click="saveMetadata">
          保存属性修改
        </el-button>
      </div>

      <el-form :model="metadataForm" label-position="top" class="metadata-form">
        <div class="form-row">
          <el-form-item label="题单标题" required class="flex-grow">
            <el-input v-model="metadataForm.title" placeholder="请输入题单名称" />
          </el-form-item>
          <el-form-item label="可见状态" class="visible-switch">
            <el-switch
              v-model="metadataForm.visible"
              active-text="公开 (普通用户可见)"
              inactive-text="隐藏 (仅管理员可见)"
            />
          </el-form-item>
        </div>

        <el-form-item label="题单介绍 (支持 Markdown)">
          <el-tabs type="border-card" class="markdown-tabs">
            <el-tab-pane label="编辑 Markdown">
              <el-input
                v-model="metadataForm.description"
                type="textarea"
                :rows="6"
                placeholder="在此输入题单的引导词、学习大纲和要求..."
              />
            </el-tab-pane>
            <el-tab-pane label="实时预览">
              <div class="markdown-preview">
                <MarkdownView :source="metadataForm.description || '*暂无描述预览内容*'" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-form-item>
      </el-form>
    </div>

    <!-- Bottom Pane: Problems Management -->
    <div class="panel problems-card">
      <div class="panel-toolbar problem-toolbar">
        <h2>题单包含的题目 (共 {{ problems.length }} 道)</h2>
        <div class="toolbar-actions">
          <el-button type="warning" :icon="Link" @click="openLinkDialog">关联已有题目</el-button>
          <el-button type="info" :icon="Upload" @click="openImportDialog">导入并加入本题单</el-button>
          <el-button type="primary" :icon="Plus" @click="createNewProblemInSet">
            创建并加入本题单
          </el-button>
        </div>
      </div>

      <el-empty v-if="problems.length === 0" description="本题单目前没有任何题目，请在右上方选择关联或新建题目！" />

      <el-table v-else :data="problems" row-key="id">
        <el-table-column prop="id" label="#" width="80" />
        
        <el-table-column prop="title" label="题目名称" min-width="220">
          <template #default="{ row }">
            <div class="problem-title">{{ row.title }}</div>
            <div class="muted">{{ row.slug }}</div>
          </template>
        </el-table-column>

        <el-table-column prop="difficulty" label="难度" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.difficulty }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="可见状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.visible ? 'success' : 'info'" size="small">
              {{ row.visible ? '公开' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="排序调整" width="180">
          <template #default="{ $index }">
            <el-button-group>
              <el-button
                :disabled="$index === 0"
                size="small"
                :icon="CaretTop"
                @click="moveProblem($index, -1)"
              />
              <el-button
                :disabled="$index === problems.length - 1"
                size="small"
                :icon="CaretBottom"
                @click="moveProblem($index, 1)"
              />
            </el-button-group>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="unlinkProblemPrompt(row.id)">解除关联</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Link Existing Problems Dialog -->
    <el-dialog
      v-model="linkDialogVisible"
      title="关联已有题目"
      width="640px"
      :close-on-click-modal="false"
    >
      <div class="search-bar" style="margin-bottom: 16px;">
        <el-input
          v-model="searchProblemQuery"
          placeholder="搜索题目 ID、名称、缩略名、标签..."
          clearable
          :prefix-icon="Search"
        />
      </div>

      <div v-loading="loadingLibrary" class="library-container" style="max-height: 360px; overflow-y: auto;">
        <el-empty v-if="filteredLibraryProblems.length === 0" description="未找到可关联的题目" />
        
        <el-checkbox-group v-model="selectedLibraryProblems" class="problem-checkbox-group">
          <div
            v-for="p in filteredLibraryProblems"
            :key="p.id"
            class="problem-checkbox-item"
          >
            <el-checkbox :value="p.id">
              <span class="lib-id">#{{ p.id }}</span>
              <div class="lib-meta">
                <span class="lib-title">{{ p.title }}</span>
                <div v-if="p.tags" class="lib-tags">
                  <el-tag
                    v-for="tag in splitTags(p.tags)"
                    :key="tag"
                    size="small"
                    :color="getTagColor(tag) + '20'"
                    :style="{ borderColor: getTagColor(tag), color: getTagColor(tag) }"
                    class="mini-tag"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                </div>
              </div>
              <el-tag size="small" type="info" class="lib-slug">{{ p.slug }}</el-tag>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>

      <template #footer>
        <span class="selected-count">已选中 {{ selectedLibraryProblems.length }} 道题目</span>
        <div class="dialog-footer-actions">
          <el-button @click="linkDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="linking"
            :disabled="selectedLibraryProblems.length === 0"
            @click="submitLink"
          >
            确认关联
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Import Problems Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入题目并加入本题单"
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
            开始批量导入并加入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Plus,
  Link,
  Check,
  Search,
  CaretTop,
  CaretBottom,
  Upload,
  UploadFilled,
  Delete,
  Download
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import MarkdownView from '../components/MarkdownView.vue'
import { http, fetchAdminProblems, importProblemPackage } from '../api/http'
import { getTagColor } from '../utils/tag'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const loading = ref(false)
const set = ref<any>(null)
const problems = ref<any[]>([])

// Metadata states
const savingMetadata = ref(false)
const metadataForm = ref({
  title: '',
  description: '',
  visible: true
})

// Link Dialog states
const linkDialogVisible = ref(false)
const loadingLibrary = ref(false)
const linking = ref(false)
const searchProblemQuery = ref('')
const libraryProblems = ref<any[]>([])
const selectedLibraryProblems = ref<number[]>([])

const filteredLibraryProblems = computed(() => {
  const currentIds = new Set(problems.value.map(p => p.id))
  // Filter out problems already in the set
  const pool = libraryProblems.value.filter(p => !currentIds.has(p.id))
  
  const query = searchProblemQuery.value.trim().toLowerCase()
  if (!query) return pool
  
  return pool.filter(
    p => p.title.toLowerCase().includes(query) ||
         p.slug.toLowerCase().includes(query) ||
         String(p.id).includes(query) ||
         (p.tags && p.tags.toLowerCase().includes(query))
  )
})

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

// Import Dialog states
const importDialogVisible = ref(false)
const importingPackage = ref(false)
const importFilesList = ref<any[]>([])

const hasPendingOrFailedFiles = computed(() => {
  return importFilesList.value.some(f => f.status === 'pending' || f.status === 'failed')
})

function openImportDialog() {
  resetImportState()
  importDialogVisible.value = true
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
  const newlyImportedIds: number[] = []

  for (const file of importFilesList.value) {
    if (file.status === 'success') continue
    
    file.status = 'uploading'
    file.errorMsg = ''
    
    try {
      const res = await importProblemPackage(file.raw)
      file.status = 'success'
      successCount++
      if (res && res.problem && res.problem.id) {
        newlyImportedIds.push(res.problem.id)
      }
    } catch (error: any) {
      file.status = 'failed'
      file.errorMsg = error.response?.data?.message || '导入失败，请检查包结构'
      failCount++
    }
  }

  // Auto-link newly imported problems
  if (newlyImportedIds.length > 0) {
    try {
      await http.post(`/admin/training/${props.id}/link`, {
        problemIds: newlyImportedIds
      })
      ElMessage.success(`成功将 ${newlyImportedIds.length} 道导入的题目加入本题单！`)
    } catch (linkErr) {
      console.error('Failed to auto-link imported problems', linkErr)
      ElMessage.warning('题目导入成功，但自动关联至题单失败，请手动在“关联已有题目”中添加。')
    }
  }

  importingPackage.value = false
  
  if (successCount > 0) {
    ElMessage.success(`批量导入并关联完成！成功 ${successCount} 个，失败 ${failCount} 个`)
    importDialogVisible.value = false
    await load()
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

async function downloadExamplePackage() {
  try {
    const response = await http.get('/admin/problems/example-package', {
      responseType: 'blob'
    })
    const blob = new Blob([response.data], { type: 'application/zip' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'example-problem-package.zip'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (err) {
    ElMessage.error('下载示例包失败')
  }
}

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    const resSet = await http.get(`/admin/training/${props.id}`)
    const resProblems = await http.get(`/admin/training/${props.id}/problems`)
    
    if (resSet.data && resSet.data.data) {
      set.value = resSet.data.data
      metadataForm.value = {
        title: set.value.title,
        description: set.value.description || '',
        visible: set.value.visible
      }
    }
    if (resProblems.data && resProblems.data.data) {
      problems.value = resProblems.data.data
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载详情失败')
    router.push('/admin/training')
  } finally {
    loading.value = false
  }
}

async function saveMetadata() {
  if (!metadataForm.value.title.trim()) {
    ElMessage.warning('题单标题不能为空')
    return
  }
  savingMetadata.value = true
  try {
    await http.put(`/admin/training/${props.id}`, metadataForm.value)
    ElMessage.success('题单属性修改成功')
    await load()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '属性更新失败')
  } finally {
    savingMetadata.value = false
  }
}

async function openLinkDialog() {
  selectedLibraryProblems.value = []
  searchProblemQuery.value = ''
  linkDialogVisible.value = true
  loadingLibrary.value = true
  try {
    libraryProblems.value = await fetchAdminProblems()
  } catch (err) {
    console.error('Failed to load library problems', err)
  } finally {
    loadingLibrary.value = false
  }
}

async function submitLink() {
  if (selectedLibraryProblems.value.length === 0) return
  linking.value = true
  try {
    await http.post(`/admin/training/${props.id}/link`, {
      problemIds: selectedLibraryProblems.value
    })
    ElMessage.success('题目关联成功！')
    linkDialogVisible.value = false
    await load()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '关联失败')
  } finally {
    linking.value = false
  }
}

function createNewProblemInSet() {
  router.push({
    path: '/admin/problems/new',
    query: { autolinkTrainingId: props.id }
  })
}

async function unlinkProblemPrompt(problemId: number) {
  ElMessageBox.confirm(
    '确定要把这道题目从当前题单中移出吗？（此操作不会删除原题）',
    '提示',
    {
      confirmButtonText: '确定移出',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await http.post(`/admin/training/${props.id}/unlink`, {
        problemIds: [problemId]
      })
      ElMessage.success('已移出该题单')
      await load()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '解除关联失败')
    }
  }).catch(() => {})
}

async function moveProblem(index: number, direction: number) {
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= problems.value.length) return
  
  // Swap elements
  const temp = problems.value[index]
  problems.value[index] = problems.value[targetIndex]
  problems.value[targetIndex] = temp
  
  const orderedIds = problems.value.map(p => p.id)
  try {
    await http.put(`/admin/training/${props.id}/reorder`, {
      problemIds: orderedIds
    })
    // Reload local sequenced items
    await load()
  } catch (err: any) {
    ElMessage.error('调整排序失败')
  }
}
</script>

<style scoped>
.breadcrumb-bar {
  margin-bottom: 12px;
}
.workspace-card {
  padding: 24px;
}
.problems-card {
  margin-top: 18px;
}
.metadata-form {
  margin-top: 18px;
}
.form-row {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.flex-grow {
  flex: 1;
  min-width: 240px;
}
.visible-switch {
  width: 240px;
  margin-top: 6px;
}
.markdown-tabs {
  border-radius: 8px;
  overflow: hidden;
}
.markdown-preview {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fafbfc;
  min-height: 140px;
  max-height: 320px;
  overflow-y: auto;
}
.problem-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.toolbar-actions {
  display: flex;
  gap: 10px;
}
.problem-title {
  font-weight: 600;
  color: #1e293b;
}
.problem-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.problem-checkbox-item {
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  padding: 10px 14px;
  background: #f8fafc;
  transition: all 0.2s ease;
}
.problem-checkbox-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}
.problem-checkbox-item :deep(.el-checkbox) {
  display: flex;
  align-items: center;
  width: 100%;
  height: 100%;
}
.problem-checkbox-item :deep(.el-checkbox__label) {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  width: 100%;
}
.lib-id {
  font-family: monospace;
  font-weight: 600;
  color: #64748b;
  width: 40px;
}
.lib-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.lib-title {
  font-weight: 600;
  color: #1e293b;
}
.lib-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.mini-tag {
  font-size: 10px;
  height: 18px;
  line-height: 16px;
  padding: 0 6px;
  font-weight: 500;
  border-radius: 4px;
  background-color: transparent !important;
}
.lib-slug {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0;
}
.dialog-footer-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
.selected-count {
  float: left;
  font-size: 13px;
  color: #64748b;
  margin-top: 8px;
}

/* ZIP Import Dialog Styles */
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
  .zip-dialog-footer {
    flex-wrap: wrap;
  }
}
</style>
