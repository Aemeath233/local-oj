<template>
  <div class="tab-pane-container">
    <!-- Locked State overlay -->
    <div v-if="problem.solveStatus !== 'ACCEPTED'" class="solutions-locked-overlay">
      <div class="lock-icon-container">
        <el-icon class="lock-icon"><Lock /></el-icon>
      </div>
      <h3>题解已锁定</h3>
      <p>您需要先通过（AC）该题目，才能查看或发布题解！</p>
      <div class="lock-accent-line"></div>
    </div>

    <!-- Solutions list (unlocked) -->
    <div v-else class="sol-tab-content" v-loading="loading">
      <div class="sol-tab-header">
        <span class="sol-count">共 {{ solutionsList.length }} 篇题解</span>
        <el-button type="primary" size="small" @click="startWritingSolution">
          {{ hasMySolution ? '编辑我的题解' : '写题解' }}
        </el-button>
      </div>

      <div v-if="solutionsList.length === 0" class="sol-empty">
        <el-icon style="font-size: 36px; color: #c0c4cc; margin-bottom: 8px;"><Notebook /></el-icon>
        <p>暂无题解，快来发布第一篇吧！</p>
      </div>

      <div v-else class="sol-card-list">
        <div
          v-for="sol in solutionsList"
          :key="sol.id"
          class="sol-card"
          @click="openSolutionDrawer(sol.id)"
        >
          <div class="sol-card-top">
            <span class="sol-card-title">{{ sol.title }}</span>
            <el-tag v-if="sol.userId === authStore.user?.id" type="success" size="small" effect="plain">我的</el-tag>
          </div>
          <div class="sol-card-bottom">
            <div class="sol-card-author" @click.stop="navigateToUser(sol.userId)">
              <el-avatar :size="18" :src="sol.avatarUrl">{{ (sol.displayName || sol.username || 'U').slice(0, 1) }}</el-avatar>
              <span class="author-name-link">{{ sol.displayName || sol.username }}</span>
            </div>
            <span class="sol-card-time">{{ formatRelativeTime(sol.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Solution Detail Drawer -->
    <el-drawer
      v-model="solutionDrawerVisible"
      title=""
      direction="rtl"
      size="640px"
      :append-to-body="true"
      class="solution-drawer"
    >
      <template #header>
        <div class="drawer-header-content">
          <h3>{{ selectedSolutionDetail?.title }}</h3>
          <div class="drawer-header-actions">
            <el-button
              v-if="selectedSolutionDetail && (selectedSolutionDetail.userId === authStore.user?.id || authStore.isAdmin)"
              type="danger"
              link
              size="small"
              @click="deleteSolutionPrompt(selectedSolutionDetail!.id)"
            >删除</el-button>
          </div>
        </div>
      </template>
      <div v-if="selectedSolutionDetail" class="drawer-solution-body">
        <div class="drawer-sol-meta">
          <el-avatar :size="28" :src="selectedSolutionDetail.avatarUrl" style="cursor: pointer;" @click="navigateToUser(selectedSolutionDetail.userId)">
            {{ (selectedSolutionDetail.displayName || selectedSolutionDetail.username || 'U').slice(0, 1) }}
          </el-avatar>
          <div>
            <strong style="cursor: pointer;" class="author-name-link" @click="navigateToUser(selectedSolutionDetail.userId)">
              {{ selectedSolutionDetail.displayName || selectedSolutionDetail.username }}
            </strong>
            <div class="muted" style="font-size: 12px;">发表于 {{ formatDateTime(selectedSolutionDetail.createdAt) }}</div>
          </div>
        </div>
        <el-divider style="margin: 16px 0;" />
        <div class="drawer-sol-content">
          <MarkdownView :source="selectedSolutionDetail.content" />
        </div>
      </div>
      <div v-else style="text-align: center; color: #999; padding-top: 40px;">加载中...</div>
    </el-drawer>

    <!-- Solution Editor Dialog -->
    <el-dialog
      v-model="solutionEditorVisible"
      :title="hasMySolution ? '编辑我的题解' : '发布新题解'"
      width="780px"
      :close-on-click-modal="false"
      :append-to-body="true"
      top="6vh"
    >
      <el-input
        v-model="solutionForm.title"
        placeholder="请输入题解标题..."
        maxlength="100"
        show-word-limit
        style="margin-bottom: 16px;"
      />
      <div class="dialog-editor-split">
        <div class="dialog-editor-left">
          <div class="dialog-pane-label">Markdown</div>
          <el-input
            v-model="solutionForm.content"
            type="textarea"
            :rows="18"
            placeholder="请使用 Markdown 编写题解，分享您的思路、算法复杂度及代码实现..."
            resize="none"
          />
        </div>
        <div class="dialog-editor-right">
          <div class="dialog-pane-label">预览</div>
          <div class="dialog-preview-body">
            <MarkdownView :source="solutionForm.content || '*暂无预览内容*'" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="solutionEditorVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSolution" @click="submitSolutionForm">保存并发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Notebook } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import {
  fetchProblemSolutions,
  fetchProblemSolutionDetail,
  saveProblemSolution,
  deleteProblemSolution
} from '../../api/http'
import { formatDateTime, formatRelativeTime } from '../../utils/time'
import type { ProblemDetail, ProblemSolutionSummary, ProblemSolutionDetail } from '../../types'
import MarkdownView from '../MarkdownView.vue'

const props = defineProps<{
  problem: ProblemDetail
}>()

const authStore = useAuthStore()
const router = useRouter()

function navigateToUser(userId: number) {
  router.push(`/user/${userId}`)
}

const loading = ref(false)
const solutionsList = ref<ProblemSolutionSummary[]>([])
const selectedSolutionDetail = ref<ProblemSolutionDetail | null>(null)
const solutionDrawerVisible = ref(false)
const solutionEditorVisible = ref(false)
const savingSolution = ref(false)

const solutionForm = ref({
  title: '',
  content: ''
})

const hasMySolution = computed(() => {
  return solutionsList.value.some(sol => sol.userId === authStore.user?.id)
})

onMounted(async () => {
  await loadSolutions()
})

async function loadSolutions() {
  if (!props.problem || props.problem.solveStatus !== 'ACCEPTED') return
  loading.value = true
  try {
    solutionsList.value = await fetchProblemSolutions(props.problem.id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载题解失败')
  } finally {
    loading.value = false
  }
}

async function openSolutionDrawer(id: number) {
  solutionDrawerVisible.value = true
  selectedSolutionDetail.value = null
  try {
    selectedSolutionDetail.value = await fetchProblemSolutionDetail(props.problem.id, id)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载题解详情失败')
  }
}

function startWritingSolution() {
  const mySol = solutionsList.value.find(sol => sol.userId === authStore.user?.id)
  if (mySol) {
    solutionForm.value.title = mySol.title
    fetchProblemSolutionDetail(props.problem.id, mySol.id).then(detail => {
      solutionForm.value.content = detail.content
    })
  } else {
    solutionForm.value.title = ''
    solutionForm.value.content = ''
  }
  solutionEditorVisible.value = true
}

async function submitSolutionForm() {
  if (!solutionForm.value.title.trim() || !solutionForm.value.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  savingSolution.value = true
  try {
    await saveProblemSolution(props.problem.id, solutionForm.value.title, solutionForm.value.content)
    ElMessage.success('题解保存成功！')
    solutionEditorVisible.value = false
    await loadSolutions()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '发布题解失败')
  } finally {
    savingSolution.value = false
  }
}

async function deleteSolutionPrompt(id: number) {
  try {
    await deleteProblemSolution(props.problem.id, id)
    ElMessage.success('题解已删除')
    solutionDrawerVisible.value = false
    selectedSolutionDetail.value = null
    await loadSolutions()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除题解失败')
  }
}
</script>

<style scoped>
.tab-pane-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.solutions-locked-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.07);
}

.lock-icon-container {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(245, 158, 11, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.lock-icon {
  font-size: 32px;
  color: #d97706;
}

.solutions-locked-overlay h3 {
  font-size: 1.3rem;
  font-weight: 750;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.solutions-locked-overlay p {
  font-size: 0.9rem;
  color: var(--text-muted);
  margin: 0 0 20px 0;
}

.lock-accent-line {
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: #d97706;
}

.sol-tab-content {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.sol-tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sol-count {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-muted);
}

.sol-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: var(--text-muted);
}

.sol-empty p {
  margin: 0;
  font-size: 0.88rem;
}

.sol-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sol-card {
  padding: 14px 18px;
  border-radius: var(--radius-md);
  background: var(--bg-app);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
}

.sol-card:hover {
  border-color: var(--border-hover);
  background: var(--bg-surface);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.sol-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sol-card-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-primary);
}

.sol-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sol-card-author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sol-card-author span {
  font-size: 0.82rem;
  color: var(--text-secondary);
  font-weight: 600;
}

.sol-card-time {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.drawer-header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 24px;
}

.drawer-header-content h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 750;
  color: var(--text-primary);
  text-align: left;
}

.drawer-sol-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
}

.drawer-sol-meta strong {
  font-size: 0.95rem;
  color: var(--text-primary);
  display: block;
}

.drawer-sol-meta .muted {
  color: var(--text-muted);
}

.drawer-sol-content {
  line-height: 1.6;
  color: var(--text-secondary);
  text-align: left;
}

.dialog-editor-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 8px;
}

.dialog-pane-label {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-muted);
  margin-bottom: 8px;
  text-align: left;
}

.dialog-preview-body {
  height: 420px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  overflow-y: auto;
  background: var(--bg-app);
  box-sizing: border-box;
  text-align: left;
}

.author-name-link {
  transition: color 0.15s ease;
}
.author-name-link:hover {
  color: var(--primary) !important;
  text-decoration: underline;
}
</style>
