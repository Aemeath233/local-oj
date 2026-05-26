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
          <h3>📂 已选题目 ({{ selectedProblems.length }})</h3>
          <el-empty v-if="selectedProblems.length === 0" description="请在下方题库勾选题目" :image-size="60" />
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
              placeholder="搜索题目名或Slug..."
              clearable
              style="width: 200px;"
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
                <span class="db-slug">{{ p.slug }}</span>
              </div>
              <el-tag size="small">{{ p.difficulty }}</el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import {
  fetchAdminContest,
  createContest,
  updateContest,
  fetchAdminProblems
} from '../api/http'
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
    p => String(p.id).includes(q) || p.title.toLowerCase().includes(q) || p.slug.toLowerCase().includes(q)
  )
})

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
</style>
