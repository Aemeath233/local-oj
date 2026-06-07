<template>
  <div class="tab-content storage-tab" v-loading="loading">
    <div v-if="storageData">
      <!-- Storage Dashboard Gauge -->
      <div class="storage-dashboard-grid">
        <div class="storage-card gauge">
          <div class="gauge-header">
            <span>已用数据空间统计</span>
            <el-button size="small" type="primary" link :icon="Refresh" @click="loadStorageStats">刷新占用</el-button>
          </div>
          <div class="gauge-body">
            <div class="size-display">
              <span class="num">{{ formatBytes(storageData.totalSpaceBytes).split(' ')[0] }}</span>
              <span class="unit">{{ formatBytes(storageData.totalSpaceBytes).split(' ')[1] }}</span>
            </div>
            <el-progress
              :percentage="storageUsedPercentage"
              :stroke-width="12"
              status="success"
              class="space-progress"
            />
            <div class="space-details">
              <span>评测点磁盘占用：{{ formatBytes(storageData.totalSpaceBytes) }}</span>
              <span>系统可用剩余空间：{{ formatBytes(storageData.freeSpaceBytes) }}</span>
            </div>
          </div>
        </div>

        <div class="storage-card info">
          <h3>评测数据管理提示</h3>
          <p>测试数据存放在后台容器 `/data/problems/{problemId}/cases/` 目录中。</p>
          <div class="orphaned-warning-box" :class="{ 'has-orphans': orphanedDirectoriesCount > 0 }">
            <div class="warning-title">
              <el-icon><Warning /></el-icon>
              <span>孤立测试文件夹：{{ orphanedDirectoriesCount }} 个</span>
            </div>
            <p v-if="orphanedDirectoriesCount > 0">
              存在由于题目被删除而在磁盘残留的孤立数据。您可以立即一键清理它们以释放磁盘空间。
            </p>
            <p v-else>文件系统表现健康，未在磁盘检测到任何已删题目的无用残留文件夹。</p>
            <el-button
              v-if="orphanedDirectoriesCount > 0"
              type="danger"
              size="small"
              :loading="cleaningOrphans"
              :icon="Delete"
              @click="triggerOrphansCleanup"
            >
              一键物理清理孤立目录
            </el-button>
          </div>
        </div>
      </div>

      <!-- Problem Cases Table -->
      <div class="problems-storage-table mt-4">
        <div class="section-title">
          <el-icon><Files /></el-icon> 题目评测点文件空间占用排名
        </div>
        <el-table :data="storageData.problemStats" stripe style="width: 100%">
          <el-table-column prop="problemId" label="ID" width="100" align="center" />
          <el-table-column prop="title" label="题目名称" min-width="250">
            <template #default="scope">
              <span :class="{ 'text-danger font-bold': scope.row.orphaned }">
                {{ scope.row.title }}
                <el-tag v-if="scope.row.orphaned" type="danger" size="small" class="ml-2">孤立未关联目录</el-tag>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="slug" label="Slug标识" width="150" />
          <el-table-column prop="fileCount" label="测试文件个数 (.in / .out)" width="180" align="center">
            <template #default="scope">
              {{ scope.row.fileCount }} 个文件
            </template>
          </el-table-column>
          <el-table-column prop="totalSizeBytes" label="物理占用空间" width="180" align="right">
            <template #default="scope">
              <span class="font-mono">{{ formatBytes(scope.row.totalSizeBytes) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Delete, Files, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchStorageStats, cleanupOrphanedCases } from '../../api/http'

const loading = ref(false)
const storageData = ref<any>(null)
const cleaningOrphans = ref(false)

const storageUsedPercentage = computed(() => {
  if (!storageData.value || storageData.value.totalSpaceBytes === 0) return 0
  const total = storageData.value.totalSpaceBytes + storageData.value.freeSpaceBytes
  return Math.min(100, Math.round((storageData.value.totalSpaceBytes / total) * 100))
})

const orphanedDirectoriesCount = computed(() => {
  if (!storageData.value || !storageData.value.problemStats) return 0
  return storageData.value.problemStats.filter((p: any) => p.orphaned).length
})

onMounted(loadStorageStats)

async function loadStorageStats() {
  loading.value = true
  try {
    storageData.value = await fetchStorageStats()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '更新空间统计失败')
  } finally {
    loading.value = false
  }
}

async function triggerOrphansCleanup() {
  cleaningOrphans.value = true
  try {
    const res = await cleanupOrphanedCases()
    ElMessage.success(`孤立测试点目录清理成功！共释放了 ${res.count} 个无用历史残留目录。`)
    await loadStorageStats()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '清理残留目录失败')
  } finally {
    cleaningOrphans.value = false
  }
}

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.tab-content {
  padding: 1.5rem 0;
}

.storage-dashboard-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 1.5rem;
}
@media (max-width: 768px) {
  .storage-dashboard-grid {
    grid-template-columns: 1fr;
  }
}

.storage-card {
  background: var(--bg-muted);
  padding: 1.5rem;
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.storage-card.gauge {
  display: flex;
  flex-direction: column;
}

.gauge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 1.5rem;
}

.gauge-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.size-display {
  text-align: center;
  margin-bottom: 1rem;
}

.size-display .num {
  font-size: 2.8rem;
  font-weight: 700;
  color: var(--el-color-success);
}

.size-display .unit {
  font-size: 1.2rem;
  font-weight: 600;
  margin-left: 4px;
  color: var(--el-text-color-regular);
}

.space-progress {
  margin-bottom: 1.25rem;
}

.space-details {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
}

.orphaned-warning-box {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.03);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.orphaned-warning-box.has-orphans {
  background: rgba(239, 68, 68, 0.03);
  border: 1px solid rgba(239, 68, 68, 0.2);
}

html.dark .orphaned-warning-box {
  background: rgba(16, 185, 129, 0.08) !important;
  border-color: rgba(16, 185, 129, 0.25) !important;
}

html.dark .orphaned-warning-box.has-orphans {
  background: rgba(239, 68, 68, 0.08) !important;
  border-color: rgba(239, 68, 68, 0.25) !important;
}

.warning-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: var(--el-color-success);
}

.has-orphans .warning-title {
  color: var(--el-color-danger);
}

.orphaned-warning-box p {
  margin: 0 0 1rem 0;
  font-size: 0.85rem;
  color: var(--el-text-color-regular);
  line-height: 1.4;
}

.problems-storage-table {
  background: transparent;
  border-radius: 8px;
  overflow: hidden;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.05rem;
  font-weight: 600;
  margin-bottom: 1rem;
}

.mt-4 {
  margin-top: 1.5rem;
}

.ml-2 {
  margin-left: 0.5rem;
}

.text-danger {
  color: var(--el-color-danger);
}

.font-bold {
  font-weight: bold;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
