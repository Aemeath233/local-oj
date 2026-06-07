<template>
  <section class="page-stack" v-loading="loading">
    <div class="breadcrumb-bar">
      <el-button link :icon="ArrowLeft" @click="router.push('/training')">返回专项练习</el-button>
    </div>

    <div v-if="set" class="training-header panel">
      <div class="header-main">
        <h1 class="header-title">{{ set.title }}</h1>
        <div class="header-meta-row">
          <span class="meta-item">
            <el-icon class="meta-icon"><User /></el-icon>
            创建人：<strong class="author-name">{{ set.creatorNickname || '管理员' }}</strong>
          </span>
          <span class="meta-item">
            <el-icon class="meta-icon"><Calendar /></el-icon>
            创建时间：{{ formatDate(set.createdAt) }}
          </span>
        </div>
        <p class="header-desc">{{ set.description || '暂无详细描述。' }}</p>
      </div>
      <div class="header-stats">
        <div class="stat-circle">
          <el-progress
            type="circle"
            :percentage="progressPercentage"
            :status="progressPercentage === 100 ? 'success' : undefined"
            :width="96"
          />
        </div>
        <div class="stat-text">
          <span class="stat-num">{{ set.solvedProblems }} / {{ set.totalProblems }}</span>
          <span class="stat-label">已通关题目数</span>
        </div>
      </div>
    </div>

    <!-- Problems Table Panel -->
    <div class="panel">
      <div class="panel-toolbar">
        <h2>题单包含的题目</h2>
      </div>

      <el-table :data="problems" row-key="id" @row-click="openProblem">
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span :class="'status-badge ' + (row.solveStatus || 'UNATTEMPTED').toLowerCase()">
              {{ statusLabel(row.solveStatus) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="id" label="#" width="80" />

        <el-table-column prop="title" label="题目" min-width="220">
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

        <el-table-column width="90" align="right">
          <template #default="{ row }">
            <el-button :icon="ArrowRight" circle @click.stop="router.push(`/problems/${row.id}`)" />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, User, Calendar } from '@element-plus/icons-vue'
import { http } from '../api/http'
import { getTagColor } from '../utils/tag'
import type { ProblemStatus } from '../types'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const loading = ref(false)
const set = ref<any>(null)
const problems = ref<any[]>([])

const progressPercentage = computed(() => {
  if (!set.value || set.value.totalProblems === 0) return 0
  return Math.round((set.value.solvedProblems / set.value.totalProblems) * 100)
})

onMounted(async () => {
  await load()
})

async function load() {
  loading.value = true
  try {
    const resSet = await http.get(`/training/${props.id}`)
    const resProblems = await http.get(`/training/${props.id}/problems`)
    
    if (resSet.data && resSet.data.data) {
      set.value = resSet.data.data
    }
    if (resProblems.data && resProblems.data.data) {
      problems.value = resProblems.data.data
      
      // Update solved count in set based on return data
      if (set.value) {
        set.value.totalProblems = problems.value.length
        set.value.solvedProblems = problems.value.filter(p => p.solveStatus === 'ACCEPTED').length
      }
    }
  } catch (err) {
    console.error('Failed to load training details', err)
  } finally {
    loading.value = false
  }
}

function openProblem(row: any) {
  router.push(`/problems/${row.id}`)
}

function statusLabel(status?: ProblemStatus) {
  if (status === 'ACCEPTED') return '已通过'
  if (status === 'ATTEMPTED') return '尝试过'
  return '未尝试'
}

function statusType(status?: ProblemStatus) {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'ATTEMPTED') return 'warning'
  return 'info'
}

function splitTags(tags?: string) {
  return (tags || '')
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '未知时间'
  const date = new Date(dateStr)
  return `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.breadcrumb-bar {
  margin-bottom: 12px;
}
.training-header {
  padding: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  background: var(--bg-surface);
}
.header-main {
  flex: 1;
  min-width: 0;
}
.header-title {
  margin: 0 0 10px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}
.header-desc {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}
.header-meta-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}
.meta-icon {
  font-size: 14px;
}
.author-name {
  color: var(--text-primary);
  font-weight: 600;
}
.header-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--bg-app);
  border-radius: 12px;
  padding: 16px 20px;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}
.stat-circle {
  flex-shrink: 0;
}
.stat-text {
  display: flex;
  flex-direction: column;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  font-weight: 500;
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
.problem-title {
  font-weight: 600;
  color: var(--text-primary);
}
@media (max-width: 768px) {
  .training-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-stats {
    width: 100%;
    justify-content: center;
  }
}
</style>
