<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>题库</h1>
        <p>{{ problems.length }} 道题目</p>
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
          placeholder="搜索序号、标题或题面内容"
          @keyup.enter="load"
          @clear="load"
        />
        <el-segmented v-model="statusFilter" :options="statusOptions" @change="load" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="problems" row-key="id" @row-click="openProblem">
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.solveStatus)" size="small" effect="light">
              {{ statusLabel(row.solveStatus) }}
            </el-tag>
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
            <el-tag size="small">{{ row.difficulty }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag v-for="tag in splitTags(row.tags)" :key="tag" size="small" effect="plain">
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
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, Refresh, Search } from '@element-plus/icons-vue'
import { fetchProblems } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { ProblemStatus, ProblemSummary } from '../types'

const router = useRouter()
const auth = useAuthStore()
const problems = ref<ProblemSummary[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<ProblemStatus | ''>('')
let searchTimer: number | undefined

const statusOptions = [
  { label: '全部', value: '' },
  { label: '未尝试', value: 'UNATTEMPTED' },
  { label: '尝试过', value: 'ATTEMPTED' },
  { label: '已通过', value: 'ACCEPTED' }
]

onMounted(load)

watch(keyword, () => {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
  searchTimer = window.setTimeout(load, 350)
})

async function load() {
  loading.value = true
  try {
    problems.value = await fetchProblems({
      q: keyword.value.trim() || undefined,
      status: statusFilter.value
    })
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
