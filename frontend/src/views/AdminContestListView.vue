<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>比赛管理</h1>
        <p>创建和安排在线程序设计比赛</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <RouterLink to="/admin/contests/new">
          <el-button type="primary" :icon="Plus">新建比赛</el-button>
        </RouterLink>
      </div>
    </div>

    <section class="panel table-panel">
      <el-table v-loading="loading" :data="contests" row-key="id">
        <el-table-column prop="id" label="#" width="76" />
        <el-table-column prop="title" label="比赛标题" min-width="220" />
        <el-table-column prop="type" label="赛制" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">
            <span class="time-range">{{ formatTime(row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">
            <span class="time-range">{{ formatTime(row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span :class="['status-badge', getContestStatus(row) === 'RUNNING' ? 'accepted' : getContestStatus(row) === 'UPCOMING' ? 'attempted' : 'unattempted']">
              {{ statusLabel(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="规模" width="210">
          <template #default="{ row }">
            <div class="contest-metrics">
              <span>{{ row.problemCount }} 题</span>
              <span>{{ row.registrationCount }} 人</span>
              <span>{{ row.submissionCount }} 次</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可见" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.visible"
              :loading="visibilityUpdating === row.id"
              @change="onVisibilityChange(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button :icon="View" circle @click="router.push(`/contests/${row.id}`)" title="查看选手视角" />
            <el-button :icon="Edit" circle type="primary" @click="router.push(`/admin/contests/${row.id}`)" title="编辑" />
            <el-button :icon="Checked" circle type="warning" @click="router.push(`/admin/contests/${row.id}/plagiarism`)" title="代码查重" />
            <el-button :icon="Delete" circle type="danger" @click="handleDelete(row)" title="删除" />
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, View, Delete, Checked } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminContests, setContestVisibility, deleteContest } from '../api/http'
import type { AdminContestSummary } from '../types'

const router = useRouter()
const loading = ref(false)
const visibilityUpdating = ref<number | null>(null)
const contests = ref<AdminContestSummary[]>([])

async function load() {
  loading.value = true
  try {
    contests.value = await fetchAdminContests()
  } finally {
    loading.value = false
  }
}

function onVisibilityChange(c: AdminContestSummary, value: string | number | boolean) {
  updateVisibilitySafe(c, Boolean(value))
}

async function updateVisibilitySafe(c: AdminContestSummary, visible: boolean) {
  visibilityUpdating.value = c.id
  const previous = !visible
  try {
    await setContestVisibility(c.id, visible)
    ElMessage.success(visible ? '比赛已显示' : '比赛已隐藏')
    await load()
  } catch (error) {
    c.visible = previous
    ElMessage.error('切换可见性失败')
  } finally {
    visibilityUpdating.value = null
  }
}

async function handleDelete(c: AdminContestSummary) {
  try {
    await ElMessageBox.confirm(`确定删除比赛「${c.title}」吗？此操作不可逆！`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    loading.value = true
    await deleteContest(c.id)
    ElMessage.success('比赛已删除')
    await load()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  } finally {
    loading.value = false
  }
}

function getContestStatus(c: AdminContestSummary): 'UPCOMING' | 'RUNNING' | 'FINISHED' {
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  const now = new Date().getTime()

  if (now < start) return 'UPCOMING'
  if (now > end) return 'FINISHED'
  return 'RUNNING'
}

function statusLabel(c: AdminContestSummary) {
  const status = getContestStatus(c)
  if (status === 'UPCOMING') return '未开始'
  if (status === 'RUNNING') return '进行中'
  return '已结束'
}

function statusType(c: AdminContestSummary) {
  const status = getContestStatus(c)
  if (status === 'UPCOMING') return 'primary'
  if (status === 'RUNNING') return 'success'
  return 'info'
}

function formatTime(timeStr: string) {
  return new Date(timeStr).toLocaleString('zh-CN')
}

onMounted(load)
</script>

<style scoped>
.time-range {
  font-family: monospace;
  font-size: 0.85rem;
  color: var(--el-text-color-regular);
}

.contest-metrics {
  display: flex;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 0.85rem;
}
</style>
