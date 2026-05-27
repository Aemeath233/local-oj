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
          placeholder="搜索题目名称、缩略名..."
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
              <span class="lib-title">{{ p.title }}</span>
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
  CaretBottom
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import MarkdownView from '../components/MarkdownView.vue'
import { http, fetchAdminProblems } from '../api/http'

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
    p => p.title.toLowerCase().includes(query) || p.slug.toLowerCase().includes(query) || String(p.id).includes(query)
  )
})

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
.lib-title {
  font-weight: 600;
  color: #1e293b;
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
</style>
