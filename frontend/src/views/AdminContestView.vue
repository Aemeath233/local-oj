<template>
  <section class="page-stack">
    <AdminNav />
    <div class="page-heading">
      <h1>{{ isEdit ? '编辑比赛' : '新建比赛' }}</h1>
      <el-button @click="router.push('/admin/contests')">返回列表</el-button>
    </div>

    <div v-loading="loading" class="admin-contest-grid">
      <!-- Form Panel -->
      <div class="panel form-panel">
        <h2>⚙️ 比赛基本设置</h2>
        <el-divider />

        <el-form :model="form" label-position="top">
          <el-form-item label="比赛标题" required>
            <el-input v-model="form.title" placeholder="例如：2026年春季期末排位赛" />
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间" required>
                <el-date-picker
                  v-model="form.startTime"
                  type="datetime"
                  placeholder="选择开始日期时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束时间" required>
                <el-date-picker
                  v-model="form.endTime"
                  type="datetime"
                  placeholder="选择结束日期时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="比赛可见性">
            <el-switch
              v-model="form.visible"
              active-text="公开可见 (学生可自由参与)"
              inactive-text="仅管理员可见 (隐藏测试中)"
            />
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用比赛封榜">
                <el-switch
                  v-model="enableFreeze"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item v-if="enableFreeze" label="封榜时长 (比赛结束前多少分钟进行封榜)" required>
                <el-input-number
                  v-model="form.freezeDurationMinutes"
                  :min="1"
                  :max="2400"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="比赛赛制" required>
            <el-radio-group v-model="form.type">
              <el-radio-button value="ACM">ACM 赛制</el-radio-button>
              <el-radio-button value="OI">OI 赛制</el-radio-button>
            </el-radio-group>
            <div style="font-size: 0.8rem; color: var(--el-text-color-secondary); margin-top: 6px;">
              <span v-if="form.type === 'ACM'">💡 ACM 赛制：即时查看评测结果，以通过题数、罚时进行排名。</span>
              <span v-else>💡 OI 赛制：即时查看测试点得分，以各题最高得分之和进行排名。</span>
            </div>
          </el-form-item>

          <el-form-item label="比赛说明及规则" required>
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="12"
              placeholder="输入比赛须知、罚时规则、注意事项等（支持 Markdown）"
            />
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button type="primary" size="large" :loading="saving" @click="save">保存比赛</el-button>
        </div>
      </div>

      <!-- Problem Selection Panel -->
      <div class="panel selection-panel">
        <h2>📚 题目挑选与排序</h2>
        <el-divider />
        <p class="muted-note">从下方题库列表中勾选题目，拖拽或调整以排序。题目将自动以 A, B, C... 编号展示给选手。</p>

        <!-- Selected problems list -->
        <div class="selected-problems-list">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
            <h3 style="margin: 0;">📂 已选题目 ({{ selectedProblems.length }})</h3>
            <el-button type="info" size="small" :icon="Upload" @click="openImportDialog">导入并加入本场比赛</el-button>
          </div>
          <el-empty v-if="selectedProblems.length === 0" description="请在下方题库勾选题目，或直接点击上方导入并加入" :image-size="60" />
          <div v-else class="selected-items">
            <div v-for="(p, index) in selectedProblems" :key="p.id" class="selected-problem-item">
              <div class="item-left">
                <el-tag effect="dark" type="info" class="seq-tag">{{ getSequenceCode(index) }}</el-tag>
                <span class="item-title">{{ p.title }}</span>
                <span class="item-slug">({{ p.slug }})</span>
              </div>
              <div class="item-actions">
                <el-button :disabled="index === 0" size="small" circle @click="moveUp(index)">▲</el-button>
                <el-button :disabled="index === selectedProblems.length - 1" size="small" circle @click="moveDown(index)">▼</el-button>
                <el-button type="danger" size="small" circle @click="removeProblem(p.id)">×</el-button>
              </div>
            </div>
          </div>
        </div>

        <el-divider />

        <!-- System problems catalog list -->
        <div class="db-problems-selector">
          <div class="selector-header">
            <h3>🔍 系统题库</h3>
            <el-input
              v-model="problemSearch"
              placeholder="搜索题目、Slug或标签..."
              clearable
              style="width: 210px;"
            />
          </div>

          <div class="db-list">
            <div
              v-for="p in filteredDbProblems"
              :key="p.id"
              class="db-problem-row"
              :class="{ 'row-selected': isSelected(p.id) }"
              @click="toggleProblem(p)"
            >
              <el-checkbox :model-value="isSelected(p.id)" @click.stop="toggleProblem(p)" />
              <div class="db-item-meta">
                <span class="db-title">{{ p.title }}</span>
                <div class="db-slug-row">
                  <span class="db-slug">{{ p.slug }}</span>
                  <div v-if="p.tags" class="db-tags">
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
              </div>
              <el-tag size="small">{{ p.difficulty }}</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Import Problems Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入题目并加入本场比赛"
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Upload,
  UploadFilled,
  Delete,
  Download
} from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminContest, createContest, updateContest, fetchAdminProblems, http, importProblemPackage } from '../api/http'
import { getTagColor } from '../utils/tag'
import type { AdminProblemSummary } from '../types'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const contestId = computed(() => Number(route.params.id))

const loading = ref(false)
const saving = ref(false)

const enableFreeze = ref(false)

const form = ref({
  title: '',
  description: '',
  startTime: null as Date | null,
  endTime: null as Date | null,
  visible: true,
  type: 'ACM' as 'ACM' | 'OI',
  freezeDurationMinutes: 0
})

const dbProblems = ref<AdminProblemSummary[]>([])
const selectedProblems = ref<AdminProblemSummary[]>([])
const problemSearch = ref('')

const filteredDbProblems = computed(() => {
  const q = problemSearch.value.trim().toLowerCase()
  if (!q) return dbProblems.value
  return dbProblems.value.filter(
    p => String(p.id).includes(q) ||
         p.title.toLowerCase().includes(q) ||
         p.slug.toLowerCase().includes(q) ||
         (p.tags && p.tags.toLowerCase().includes(q))
  )
})

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    // 1. Fetch available problems in OJ database
    dbProblems.value = await fetchAdminProblems()

    // 2. Fetch edit contest data if modifying
    if (isEdit.value) {
      const detail = await fetchAdminContest(contestId.value)
      form.value = {
        title: detail.contest.title,
        description: detail.contest.description || '',
        startTime: new Date(detail.contest.startTime),
        endTime: new Date(detail.contest.endTime),
        visible: detail.contest.visible,
        type: detail.contest.type || 'ACM',
        freezeDurationMinutes: detail.contest.freezeDurationMinutes || 0
      }
      enableFreeze.value = (detail.contest.freezeDurationMinutes || 0) > 0

      // Map matching problem objects in sequence
      const problemMap = new Map<number, AdminProblemSummary>()
      dbProblems.value.forEach(p => problemMap.set(p.id, p))

      const mapped: AdminProblemSummary[] = []
      detail.problemIds.forEach(id => {
        const match = problemMap.get(id)
        if (match) mapped.push(match)
      })
      selectedProblems.value = mapped
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('加载比赛数据失败')
  } finally {
    loading.value = false
  }
}

function isSelected(problemId: number) {
  return selectedProblems.value.some(p => p.id === problemId)
}

function toggleProblem(p: AdminProblemSummary) {
  const index = selectedProblems.value.findIndex(item => item.id === p.id)
  if (index === -1) {
    selectedProblems.value.push(p)
  } else {
    selectedProblems.value.splice(index, 1)
  }
}

function removeProblem(problemId: number) {
  selectedProblems.value = selectedProblems.value.filter(p => p.id !== problemId)
}

function moveUp(index: number) {
  if (index === 0) return
  const temp = selectedProblems.value[index]
  selectedProblems.value[index] = selectedProblems.value[index - 1]
  selectedProblems.value[index - 1] = temp
}

function moveDown(index: number) {
  if (index === selectedProblems.value.length - 1) return
  const temp = selectedProblems.value[index]
  selectedProblems.value[index] = selectedProblems.value[index + 1]
  selectedProblems.value[index + 1] = temp
}

function getSequenceCode(index: number) {
  let code = ''
  let temp = index
  while (temp >= 0) {
    code = String.fromCharCode(65 + (temp % 26)) + code
    temp = Math.floor(temp / 26) - 1
  }
  return code
}

async function save() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入比赛标题')
    return
  }
  if (!form.value.startTime || !form.value.endTime) {
    ElMessage.warning('请选择起止时间')
    return
  }
  if (form.value.startTime >= form.value.endTime) {
    ElMessage.warning('开始时间必须早于结束时间')
    return
  }
  if (!form.value.description.trim()) {
    ElMessage.warning('请输入比赛说明或说明规则')
    return
  }
  if (selectedProblems.value.length === 0) {
    ElMessage.warning('请至少选择一道比赛题目')
    return
  }

  saving.value = true
  try {
    const payload = {
      title: form.value.title.trim(),
      description: form.value.description.trim(),
      startTime: form.value.startTime.toISOString(),
      endTime: form.value.endTime.toISOString(),
      visible: form.value.visible,
      type: form.value.type,
      freezeDurationMinutes: enableFreeze.value ? form.value.freezeDurationMinutes : 0,
      problemIds: selectedProblems.value.map(p => p.id)
    }

    if (isEdit.value) {
      await updateContest(contestId.value, payload)
      ElMessage.success('比赛修改已保存')
    } else {
      await createContest(payload)
      ElMessage.success('比赛创建成功')
    }
    router.push('/admin/contests')
  } catch (err: any) {
    console.error(err)
    ElMessage.error(err.response?.data?.message || '保存比赛失败')
  } finally {
    saving.value = false
  }
}

// --- ZIP Import Problems and Associate Feature ---
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

  for (const file of importFilesList.value) {
    if (file.status === 'success') continue
    
    file.status = 'uploading'
    file.errorMsg = ''
    
    try {
      const res = await importProblemPackage(file.raw)
      file.status = 'success'
      successCount++
      
      if (res && res.problem && res.problem.id) {
        // Construct AdminProblemSummary object
        const newProb: AdminProblemSummary = {
          id: res.problem.id,
          title: res.problem.title,
          slug: res.problem.slug,
          difficulty: res.problem.difficulty || 'EASY',
          visible: res.problem.visible ?? true,
          tags: res.problem.tags || '',
          testCaseCount: res.testCases?.length || 0,
          submissionCount: 0,
          timeLimitMs: res.problem.timeLimitMs || 1000,
          memoryLimitKb: res.problem.memoryLimitKb || 262144
        }
        
        // Add to dbProblems if it doesn't already exist
        if (!dbProblems.value.some(p => p.id === newProb.id)) {
          dbProblems.value.push(newProb)
        }
        
        // Automatically select/associate this problem
        if (!selectedProblems.value.some(p => p.id === newProb.id)) {
          selectedProblems.value.push(newProb)
        }
      }
    } catch (error: any) {
      file.status = 'failed'
      file.errorMsg = error.response?.data?.message || '导入失败，请检查包结构'
      failCount++
    }
  }

  importingPackage.value = false
  
  if (successCount > 0) {
    ElMessage.success(`成功导入并关联了 ${successCount} 道题目！`)
    importDialogVisible.value = false
    resetImportState()
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
</script>

<style scoped>
.admin-contest-grid {
  display: grid;
  grid-template-columns: minmax(500px, 1.2fr) minmax(400px, 0.8fr);
  gap: 24px;
  align-items: start;
}
.form-panel,
.selection-panel {
  padding: 24px;
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
}
.panel h2 {
  margin: 0 0 10px 0;
  font-size: 1.2rem;
  font-weight: 650;
  color: var(--el-text-color-primary);
}
.muted-note {
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
  margin-top: 0;
}
.form-actions {
  margin-top: 24px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.selected-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
  margin-top: 12px;
}
.selected-problem-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}
.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.seq-tag {
  font-weight: bold;
  font-family: monospace;
}
.item-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--el-text-color-primary);
}
.item-slug {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}
.item-actions {
  display: flex;
  gap: 6px;
}

.db-problems-selector {
  margin-top: 16px;
}
.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.selector-header h3 {
  margin: 0;
  font-size: 0.95rem;
  color: var(--el-text-color-primary);
}
.db-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 8px;
  background: var(--el-fill-color-blank);
}
.db-problem-row {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  gap: 12px;
}
.db-problem-row:hover {
  background: var(--el-fill-color-light);
}
.row-selected {
  background: rgba(64, 158, 255, 0.08) !important;
}
.db-item-meta {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
}
.db-title {
  font-weight: 550;
  font-size: 0.85rem;
  color: var(--el-text-color-primary);
}
.db-slug {
  font-size: 0.75rem;
  color: var(--el-text-color-secondary);
}
.db-slug-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.db-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.mini-tag {
  font-size: 9px;
  height: 16px;
  line-height: 14px;
  padding: 0 4px;
  font-weight: 500;
  border-radius: 4px;
  background-color: transparent !important;
}

/* ZIP Import Dialog Styles */
.zip-import-dialog-body {
  display: grid;
  gap: 16px;
}
.zip-drop :deep(.el-upload-dragger) {
  padding: 30px 18px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px dashed var(--el-border-color);
}
.zip-drop :deep(.el-upload-dragger):hover {
  border-color: var(--el-color-primary);
}
.zip-drop-icon {
  margin-bottom: 10px;
  color: var(--el-color-primary);
  font-size: 42px;
}
.zip-drop-title {
  color: var(--el-text-color-primary);
  font-weight: 650;
  font-size: 1.05rem;
}
.zip-drop-subtitle,
.zip-upload-tip {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}
.zip-selected-files-list {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
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
  font-size: 0.95rem;
  font-weight: 650;
  color: var(--el-text-color-primary);
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
  background: var(--el-fill-color-light);
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
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
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}
.batch-file-row .file-size {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}
.file-status-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.error-msg-detail {
  font-size: 0.8rem;
  color: var(--el-color-danger);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.remove-file-btn {
  padding: 0;
  margin-left: 4px !important;
}
.zip-format-hint {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
}
.zip-format-copy h3 {
  margin: 0;
  font-size: 1.05rem;
  color: var(--el-text-color-primary);
}
.zip-format-copy p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}
.zip-tree {
  min-height: auto;
  margin: 0;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 10px 14px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.85rem;
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-light);
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
