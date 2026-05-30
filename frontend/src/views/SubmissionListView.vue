<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>提交记录</h1>
        <p>最近 100 条</p>
      </div>
      <el-button :icon="Refresh" circle :loading="loading" @click="load" />
    </div>

    <!-- Interactive Search and Filter Bar -->
    <div class="filter-card-bar">
      <!-- Search User -->
      <div class="filter-search-box">
        <el-input
          v-model="filterUser"
          placeholder="搜索提交者用户名或昵称..."
          clearable
          :prefix-icon="Search"
        />
      </div>
 
      <!-- Filter by Language -->
      <div>
        <el-select v-model="filterLanguage" placeholder="所有语言" clearable style="width: 180px;">
          <el-option label="C++20 (O2)" value="CPP" />
          <el-option label="C++20 (O3)" value="CPP_O3" />
          <el-option label="C17 (O2)" value="C" />
          <el-option label="Python 3.12" value="PYTHON" />
          <el-option label="PyPy 3" value="PYPY3" />
          <el-option label="Java 21" value="JAVA" />
        </el-select>
      </div>
 
      <!-- Filter by Verdict -->
      <div>
        <el-select v-model="filterVerdict" placeholder="所有结果" clearable style="width: 150px;">
          <el-option label="Accepted (AC)" value="AC" />
          <el-option label="Wrong Answer (WA)" value="WA" />
          <el-option label="Time Limit Exceeded (TLE)" value="TLE" />
          <el-option label="Memory Limit Exceeded (MLE)" value="MLE" />
          <el-option label="Output Limit Exceeded (OLE)" value="OLE" />
          <el-option label="Runtime Error (RE)" value="RE" />
          <el-option label="Compilation Error (CE)" value="CE" />
          <el-option label="Internal Error (IE)" value="IE" />
          <el-option label="Pending" value="PENDING" />
          <el-option label="Running" value="RUNNING" />
        </el-select>
      </div>
 
      <!-- Reset filters -->
      <el-button v-if="hasFilters" type="warning" plain @click="resetFilters">重置筛选</el-button>
    </div>

    <div class="panel">
      <el-table v-loading="loading" :data="filteredSubmissions" row-key="id" @row-click="openDetail">
        <el-table-column prop="id" label="#" width="90" />
        <el-table-column label="提交者" min-width="150">
          <template #default="{ row }">
            <router-link :to="`/user/${row.userId}`" class="user-link-cell" @click.stop>
              <div class="user-cell">
                <el-avatar :size="26" :src="row.avatarUrl">
                  {{ userFallback(row) }}
                </el-avatar>
                <div>
                  <div style="font-weight: 600;" class="user-nickname">{{ row.displayName || row.username || `用户 #${row.userId}` }}</div>
                </div>
              </div>
            </router-link>
          </template>
        </el-table-column>
        <el-table-column label="题目" min-width="180">
          <template #default="{ row }">
            <div class="problem-link-cell" @click.stop="goToProblem(row.problemId)">
              <div class="problem-title-text">{{ row.problemTitle || `题目 #${row.problemId}` }}</div>
              <div class="muted">#{{ row.problemId }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="110" />
        <el-table-column label="结果" width="130">
          <template #default="{ row }">
            <VerdictTag :status="row.status" :verdict="row.verdict" />
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分数" width="90" />
        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">
            <span style="font-variant-numeric: tabular-nums;">{{ row.timeMs != null ? row.timeMs + ' ms' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内存" width="110" align="right">
          <template #default="{ row }">
            <span style="font-variant-numeric: tabular-nums;">{{ row.memoryKb != null ? formatMemory(row.memoryKb) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.createdAt) }}</div>
            <div class="muted">{{ formatRelativeTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <SubmissionDetailDrawer v-model="drawerVisible" :detail="selected" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
import { fetchSubmission, fetchSubmissions } from '../api/http'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import { ElMessage } from 'element-plus'
import VerdictTag from '../components/VerdictTag.vue'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import { shouldRefreshSection, forceUpdateSectionVersion } from '../utils/versionCheck'
import type { SubmissionDetail, SubmissionSummary } from '../types'

const loading = ref(false)
const submissions = ref<SubmissionSummary[]>([])
const selected = ref<SubmissionDetail | null>(null)
const drawerVisible = ref(false)
let timer: number | undefined

// Filter states
const filterUser = ref('')
const filterLanguage = ref('')
const filterVerdict = ref('')

const hasFilters = computed(() => {
  return filterUser.value.trim() !== '' || filterLanguage.value !== '' || filterVerdict.value !== ''
})

const filteredSubmissions = computed(() => {
  return submissions.value.filter((sub) => {
    // 1. Filter by User
    if (filterUser.value.trim() !== '') {
      const query = filterUser.value.toLowerCase().trim()
      const username = (sub.username || '').toLowerCase()
      const displayName = (sub.displayName || '').toLowerCase()
      const userId = `用户 #${sub.userId}`
      if (!username.includes(query) && !displayName.includes(query) && !userId.includes(query)) {
        return false
      }
    }

    // 2. Filter by Language
    if (filterLanguage.value !== '' && filterLanguage.value !== null) {
      if (sub.language !== filterLanguage.value) {
        return false
      }
    }

    // 3. Filter by Verdict
    if (filterVerdict.value !== '' && filterVerdict.value !== null) {
      if (filterVerdict.value === 'PENDING' || filterVerdict.value === 'RUNNING') {
        if (sub.status !== filterVerdict.value) {
          return false
        }
      } else {
        if (sub.status !== 'FINISHED' || sub.verdict !== filterVerdict.value) {
          return false
        }
      }
    }

    return true
  })
})

function resetFilters() {
  filterUser.value = ''
  filterLanguage.value = ''
  filterVerdict.value = ''
}

function startPolling() {
  if (timer) return
  timer = window.setInterval(async () => {
    if (await shouldRefreshSection('submissions')) {
      load(true)
    }
  }, 15000)
}

function stopPolling() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

onMounted(async () => {
  await load()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

onActivated(async () => {
  if (await shouldRefreshSection('submissions')) {
    await load(true)
  }
  startPolling()
})

onDeactivated(() => {
  stopPolling()
})

async function load(isSilent = false) {
  if (!isSilent) {
    loading.value = true
  }
  try {
    if (!isSilent) {
      forceUpdateSectionVersion('submissions')
    }
    submissions.value = await fetchSubmissions()
    if (drawerVisible.value && selected.value) {
      selected.value = await fetchSubmission(selected.value.submission.id)
    }
  } finally {
    if (!isSilent) {
      loading.value = false
    }
  }
}

async function openDetail(row: SubmissionSummary) {
  try {
    selected.value = await fetchSubmission(row.id)
    drawerVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取提交详情失败')
  }
}

const router = useRouter()

function goToProblem(problemId: number) {
  router.push(`/problems/${problemId}`)
}

function userFallback(row: SubmissionSummary) {
  return (row.displayName || row.username || 'U').slice(0, 1).toUpperCase()
}

function formatMemory(kb: number) {
  if (kb >= 1024) {
    return (kb / 1024).toFixed(1) + ' MB'
  }
  return kb + ' KB'
}
</script>

<style scoped>
.problem-link-cell {
  cursor: pointer;
  display: inline-flex;
  flex-direction: column;
}

.problem-link-cell:hover .problem-title-text {
  color: var(--primary);
  text-decoration: underline;
}

.problem-title-text {
  font-weight: 650;
  color: var(--text-primary);
  transition: color 0.15s ease;
}

.user-link-cell {
  text-decoration: none;
  color: inherit;
  display: inline-block;
}
.user-link-cell:hover .user-nickname {
  color: var(--primary);
  text-decoration: underline;
}
</style>
