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
        <el-table-column label="起止时间" width="300">
          <template #default="{ row }">
            <div class="time-range">
              <div><el-tag size="small" type="success">起</el-tag> {{ formatTime(row.startTime) }}</div>
              <div style="margin-top: 4px;"><el-tag size="small" type="danger">止</el-tag> {{ formatTime(row.endTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row)">
              {{ statusLabel(row) }}
            </el-tag>
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
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button :icon="View" circle @click="router.push(`/contests/${row.id}`)" title="查看选手视角" />
            <el-button :icon="Edit" circle type="primary" @click="router.push(`/admin/contests/${row.id}`)" title="编辑" />
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
import { Edit, Plus, Refresh, View, Delete } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminContests, updateContest, deleteContest } from '../api/http'
import type { Contest } from '../types'

const router = useRouter()
const loading = ref(false)
const visibilityUpdating = ref<number | null>(null)
const contests = ref<Contest[]>([])

async function load() {
  loading.value = true
  try {
    contests.value = await fetchAdminContests()
  } finally {
    loading.value = false
  }
}

async function updateVisibility(c: Contest, visible: boolean) {
  visibilityUpdating.value = c.id
  const previous = !visible
  try {
    const payload = {
      title: c.title,
      description: c.description,
      startTime: c.startTime,
      endTime: c.endTime,
      visible,
      problemIds: [] // Mapped relation doesn't need to change for visibility switch
    }
    // Fetch existing problemIds first
    const detail = await fetchAdminContests() // actually we can fetch individual if needed
    // Simple lazy backup: we retrieve the specific mapping before editing!
    // But since the backend requires problemIds inside request payload, we must keep it safe.
    // Let's call updateContest endpoint
    await updateContest(c.id, {
      title: c.title,
      description: c.description,
      startTime: c.startTime,
      endTime: c.endTime,
      visible,
      problemIds: [] // Passing empty is safe since we can also just fetch it, or let's fetch to avoid clearing problems!
    })
    ElMessage.success(visible ? '比赛已显示' : '比赛已隐藏')
  } catch (error) {
    c.visible = previous
    throw error
  } finally {
    visibilityUpdating.value = null
  }
}

function onVisibilityChange(c: Contest, value: string | number | boolean) {
  // Let's implement full fetch-to-update to avoid accidentally wiping out problems!
  // It is much safer! Let's fetch contest details first:
  updateVisibilitySafe(c, Boolean(value))
}

async function updateVisibilitySafe(c: Contest, visible: boolean) {
  visibilityUpdating.value = c.id
  const previous = !visible
  try {
    // Import helper dynamic fetch detail
    const detail = await router.resolve(`/admin/contests/${c.id}`)
    // Let's fetch the actual details of the contest including problemIds
    const response = await fetch(`/api/admin/contests/${c.id}`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('localoj.token')}` }
    })
    const json = await response.json()
    const problemIds = json.data.problemIds || []

    await updateContest(c.id, {
      title: c.title,
      description: c.description,
      startTime: c.startTime,
      endTime: c.endTime,
      visible,
      problemIds
    })
    ElMessage.success(visible ? '比赛已显示' : '比赛已隐藏')
  } catch (error) {
    c.visible = previous
    ElMessage.error('切换可见性失败')
  } finally {
    visibilityUpdating.value = null
  }
}

async function handleDelete(c: Contest) {
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

function getContestStatus(c: Contest): 'UPCOMING' | 'RUNNING' | 'FINISHED' {
  const start = new Date(c.startTime).getTime()
  const end = new Date(c.endTime).getTime()
  const now = new Date().getTime()

  if (now < start) return 'UPCOMING'
  if (now > end) return 'FINISHED'
  return 'RUNNING'
}

function statusLabel(c: Contest) {
  const status = getContestStatus(c)
  if (status === 'UPCOMING') return '未开始'
  if (status === 'RUNNING') return '进行中'
  return '已结束'
}

function statusType(c: Contest) {
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
</style>
