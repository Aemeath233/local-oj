<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>提交巡检</h1>
        <p>最近 {{ submissions.length }} 条提交</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="RefreshRight" :loading="requeueing" @click="requeueAll">补发未完成</el-button>
      </div>
    </div>

    <section class="panel table-panel">
      <el-table v-loading="loading" :data="submissions" row-key="id" @row-click="openDetail">
        <el-table-column prop="id" label="#" width="76" />
        <el-table-column label="题目" min-width="190">
          <template #default="{ row }">
            <span>{{ row.problemTitle || `#${row.problemId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <span>{{ row.displayName || row.username || `#${row.userId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="96" />
        <el-table-column label="结果" width="126">
          <template #default="{ row }">
            <VerdictTag :status="row.status" :verdict="row.verdict" />
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分数" width="86" />
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
        <el-table-column label="提交时间" min-width="150">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.createdAt) }}</div>
            <div class="muted">{{ formatRelativeTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="" width="104">
          <template #default="{ row }">
            <el-button :icon="RefreshRight" circle @click.stop="rejudge(row.id)" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <SubmissionDetailDrawer v-model="drawerOpen" :detail="selected" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import SubmissionDetailDrawer from '../components/SubmissionDetailDrawer.vue'
import VerdictTag from '../components/VerdictTag.vue'
import { fetchAdminSubmissions, rejudgeSubmission, requeueUnfinishedSubmissions } from '../api/admin'
import { fetchSubmission } from '../api/submission'
import { formatDateTime, formatRelativeTime } from '../utils/time'
import type { AdminSubmissionSummary, SubmissionDetail } from '../types'

const loading = ref(false)
const requeueing = ref(false)
const submissions = ref<AdminSubmissionSummary[]>([])
const selected = ref<SubmissionDetail | null>(null)
const drawerOpen = ref(false)

async function load() {
  loading.value = true
  try {
    submissions.value = await fetchAdminSubmissions(100)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: AdminSubmissionSummary) {
  selected.value = await fetchSubmission(row.id)
  drawerOpen.value = true
}

async function rejudge(id: number) {
  await rejudgeSubmission(id)
  ElMessage.success(`提交 #${id} 已重新入队`)
  await load()
}

async function requeueAll() {
  requeueing.value = true
  try {
    const result = await requeueUnfinishedSubmissions()
    ElMessage.success(`已补发 ${result.queued} 条`)
    await load()
  } finally {
    requeueing.value = false
  }
}

function formatMemory(kb: number) {
  if (kb >= 1024) {
    return (kb / 1024).toFixed(1) + ' MB'
  }
  return kb + ' KB'
}

onMounted(load)
</script>
