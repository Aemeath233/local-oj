<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>题库</h1>
        <p>{{ totalProblems }} 道题目</p>
      </div>
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
          style="max-width: 260px"
        />

        <el-select v-model="sortBy" style="width: 140px">
          <el-option
            :label="sortField === 'ID' ? `创建时间 ${sortOrder === 'ASC' ? '↑' : '↓'}` : '创建时间'"
            :value="sortField === 'ID' ? `ID_${sortOrder}` : 'ID_DESC'"
            @click="handleOptionClick('ID')"
          />
          <el-option
            :label="sortField === 'DIFFICULTY' ? `难度 ${sortOrder === 'ASC' ? '↑' : '↓'}` : '难度'"
            :value="sortField === 'DIFFICULTY' ? `DIFFICULTY_${sortOrder}` : 'DIFFICULTY_ASC'"
            @click="handleOptionClick('DIFFICULTY')"
          />
          <el-option
            :label="sortField === 'AC_RATE' ? `通过率 ${sortOrder === 'ASC' ? '↑' : '↓'}` : '通过率'"
            :value="sortField === 'AC_RATE' ? `AC_RATE_${sortOrder}` : 'AC_RATE_DESC'"
            @click="handleOptionClick('AC_RATE')"
          />
        </el-select>

        <el-segmented v-model="statusFilter" :options="statusOptions" @change="handleFilterChange" style="margin-left: auto;" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-skeleton :loading="loading && problems.length === 0" animated>
        <template #template>
          <div style="padding: 12px 20px;">
            <el-skeleton-item variant="rect" style="height: 40px; border-radius: 8px; margin-bottom: 12px;" v-for="i in 10" :key="i" />
          </div>
        </template>
        <template #default>
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
        </template>
      </el-skeleton>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalProblems"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="load"
          @current-change="load"
        />
        <div class="custom-jumper" v-if="totalProblems > 0">
          <span class="jumper-label">前往</span>
          <el-input-number
            v-model="jumpPage"
            :min="1"
            :max="Math.ceil(totalProblems / pageSize)"
            :controls="false"
            size="small"
            class="jumper-input"
            @keyup.enter="handleJump"
          />
          <span class="jumper-label">页</span>
          <el-button size="small" type="primary" class="jumper-btn" @click="handleJump">跳转</el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onActivated, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, Refresh, Search } from '@element-plus/icons-vue'
import { fetchProblems } from '../api/problem'
import { fetchProblemTags } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { getTagColor } from '../utils/tag'
import { shouldRefreshSection, forceUpdateSectionVersion } from '../utils/versionCheck'
import type { ProblemStatus, ProblemSummary, ProblemTag } from '../types'

const router = useRouter()
const auth = useAuthStore()
const problems = ref<ProblemSummary[]>([])
const totalProblems = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const jumpPage = ref(1)

watch(currentPage, (val) => {
  jumpPage.value = val
})

function handleJump() {
  const maxPage = Math.ceil(totalProblems.value / pageSize.value)
  if (jumpPage.value && jumpPage.value >= 1 && jumpPage.value <= maxPage) {
    currentPage.value = jumpPage.value
    load()
  }
}
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<ProblemStatus | ''>('')
const sortBy = ref('ID_DESC')
const sortField = ref('ID')
const sortOrder = ref('DESC')

function handleOptionClick(field: string) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'ASC' ? 'DESC' : 'ASC'
  } else {
    sortField.value = field
    sortOrder.value = (field === 'AC_RATE' || field === 'ID') ? 'DESC' : 'ASC'
  }
  sortBy.value = `${sortField.value}_${sortOrder.value}`
  handleFilterChange()
}
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
  forceUpdateSectionVersion('problems')
})

onActivated(async () => {
  if (await shouldRefreshSection('problems')) {
    await load(true)
  }
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

async function load(isSilent: boolean = false) {
  if (!isSilent) {
    loading.value = true
  }
  try {
    if (!isSilent) {
      forceUpdateSectionVersion('problems')
    }
    const q = keyword.value.trim()
    const result = await fetchProblems({
      q: q || undefined,
      status: statusFilter.value,
      page: currentPage.value,
      pageSize: pageSize.value,
      sortBy: sortBy.value
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
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.custom-jumper {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.jumper-input {
  width: 50px !important;
}

.jumper-input :deep(.el-input__inner) {
  text-align: center;
  padding: 0 4px;
}

.jumper-btn {
  margin-left: 2px;
}
</style>
