<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>题库</h1>
        <p>{{ totalProblems }} 道题目</p>
      </div>
      <RouterLink v-if="auth.isAdmin" to="/admin/problems">
        <el-button :icon="ArrowRight" type="primary">管理题目</el-button>
      </RouterLink>
    </div>

    <div class="panel">
      <div class="panel-toolbar problem-filters">
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          clearable
          placeholder="搜索序号、标题、标签或题面内容"
          @keyup.enter="load"
          @clear="load"
          style="max-width: 280px"
        />

        <el-segmented v-model="statusFilter" :options="statusOptions" @change="handleFilterChange" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="problems" row-key="id" @row-click="openProblem">
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
        <el-table-column label="限制" width="160">
          <template #default="{ row }">
            {{ row.timeLimitMs }} ms / {{ Math.round(row.memoryLimitKb / 1024) }} MB
          </template>
        </el-table-column>
        <el-table-column label="AC / 提交" width="130" align="center">
          <template #default="{ row }">
            <span style="font-family: var(--font-mono); font-size: 0.9rem; font-weight: 550; color: var(--text-secondary);">
              {{ row.acceptedCount ?? 0 }} / {{ row.submitCount ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column width="90" align="right">
          <template #default="{ row }">
            <el-button :icon="ArrowRight" circle @click.stop="router.push(`/problems/${row.id}`)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalProblems"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, Refresh, Search } from '@element-plus/icons-vue'
import { fetchProblems, fetchProblemTags } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { getTagColor } from '../utils/tag'
import type { ProblemStatus, ProblemSummary, ProblemTag } from '../types'

const router = useRouter()
const auth = useAuthStore()
const problems = ref<ProblemSummary[]>([])
const totalProblems = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<ProblemStatus | ''>('')
const allTags = ref<ProblemTag[]>([])
const statusOptions = [
  { label: '全部', value: '' },
  { label: '未尝试', value: 'UNATTEMPTED' },
  { label: '尝试过', value: 'ATTEMPTED' },
  { label: '已通过', value: 'ACCEPTED' }
]
let searchTimer: number | undefined

onMounted(async () => {
  await loadTags()
  await load()
})

watch(keyword, () => {
  currentPage.value = 1
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
  searchTimer = window.setTimeout(load, 350)
})

function handleFilterChange() {
  currentPage.value = 1
  load()
}

async function loadTags() {
  try {
    allTags.value = await fetchProblemTags()
  } catch (err) {
    console.error('Failed to load tags dictionary', err)
  }
}

async function load() {
  loading.value = true
  try {
    const q = keyword.value.trim()
    // If keyword matches any tag name, include those tags in the search
    const matchingTags = q
      ? allTags.value
          .filter(t => t.name.toLowerCase().includes(q.toLowerCase()))
          .map(t => t.name)
      : []
    const result = await fetchProblems({
      q: q || undefined,
      status: statusFilter.value,
      tags: matchingTags.length > 0 ? matchingTags.join(',') : undefined,
      page: currentPage.value,
      pageSize: pageSize.value
    })
    problems.value = result.list
    totalProblems.value = result.total
  } finally {
    loading.value = false
  }
}

function openProblem(row: ProblemSummary) {
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
</script>

<style scoped>
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

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
