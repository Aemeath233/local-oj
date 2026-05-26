<template>
  <section class="page-stack">
    <AdminNav />

    <!-- DLQ Alert Panel -->
    <div v-if="dlqStats.size > 0" class="panel alert-panel dlq-alert-panel">
      <div class="alert-content">
        <el-icon class="alert-icon"><Warning /></el-icon>
        <div class="alert-text">
          <h3>检测到死信队列中有异常任务 ({{ dlqStats.size }} 个)</h3>
          <p>评测机在重试 3 次后仍遭遇系统级异常，任务已被隔离至死信队列。这通常意味着 Sandbox (go-judge) 服务离线或配置出错。</p>
        </div>
      </div>
      <div class="alert-actions">
        <el-button type="primary" size="small" @click="inspectDlq">查看详情</el-button>
        <el-button type="success" size="small" :loading="requeuing" @click="handleRequeueDlq">重新评测</el-button>
        <el-button type="danger" size="small" plain @click="handleClearDlq">清空死信</el-button>
      </div>
    </div>

    <div class="page-heading">
      <div>
        <h1>系统日志与诊断</h1>
        <p>控制系统日志级别，并安全下载文本日志文件进行诊断分析</p>
      </div>
    </div>

    <!-- Logs Controls Panel Grid -->
    <div class="logs-grid">
      <!-- Card 1: Dynamic Logging Toggle -->
      <div class="panel logs-card">
        <div class="card-header">
          <el-icon class="card-icon"><Setting /></el-icon>
          <h3>日志记录开关</h3>
        </div>
        <p class="card-desc">
          关闭日志记录可最大化 OJ 系统判题吞吐率，完全避免高并发插入对数据库和磁盘带来的 IO 额外负担。系统发生异常或需要排错时，可随时开启进行行为审计与问题排查。
        </p>
        <div class="switch-container">
          <span class="switch-label">系统日志全局收集：</span>
          <el-switch
            v-model="logEnabled"
            :loading="toggling"
            active-text="开启日志收集"
            inactive-text="关闭日志收集 (推荐高性能)"
            @change="handleToggleChange"
          />
        </div>
      </div>

      <!-- Card 2: Logs File Downloads -->
      <div class="panel logs-card">
        <div class="card-header">
          <el-icon class="card-icon"><Download /></el-icon>
          <h3>日志文件下载</h3>
        </div>
        <p class="card-desc">
          系统流日志以本地物理文件形式安全持久保存在共享数据卷中。数据庞大时亦不会拖慢网页加载速度，您可以随时下载以下文件的纯文本 `.log` 文件，以便使用多行全文检索工具进行高效的分析诊断。
        </p>
        <div class="downloads-container">
          <div class="log-file-row">
            <div class="file-info">
              <span class="file-name">backend.log</span>
              <span class="file-type-badge backend-badge">后端服务日志</span>
            </div>
            <div class="actions">
              <el-button
                type="primary"
                :icon="Download"
                :loading="downloadingBackend"
                class="action-btn"
                @click="handleDownload('backend')"
              >
                下载
              </el-button>
              <el-button
                type="danger"
                plain
                :icon="Delete"
                class="action-btn"
                @click="handleClearPrompt('backend')"
              >
                清空
              </el-button>
            </div>
          </div>

          <div class="log-file-row">
            <div class="file-info">
              <span class="file-name">judge-worker.log</span>
              <span class="file-type-badge worker-badge">判题机服务日志</span>
            </div>
            <div class="actions">
              <el-button
                type="warning"
                :icon="Download"
                :loading="downloadingWorker"
                class="action-btn"
                @click="handleDownload('worker')"
              >
                下载
              </el-button>
              <el-button
                type="danger"
                plain
                :icon="Delete"
                class="action-btn"
                @click="handleClearPrompt('worker')"
              >
                清空
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- DLQ Inspection Dialog -->
    <el-dialog v-model="dlqDialogOpen" title="死信队列任务详情" width="680px" class="glass-dialog">
      <div class="dlq-dialog-body">
        <div class="dlq-items-list">
          <div v-for="(item, index) in dlqStats.items" :key="index" class="dlq-item">
            <div class="dlq-item-header">
              <span class="badge">任务 #{{ index + 1 }}</span>
              <span class="payload-raw-title">原始 Payload</span>
            </div>
            <pre class="dlq-item-pre">{{ item }}</pre>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dlqDialogOpen = false">关闭</el-button>
        <el-button type="danger" plain @click="handleClearDlq">清空死信</el-button>
        <el-button type="success" :loading="requeuing" @click="handleRequeueDlq">重新评测</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Warning, Download, Setting, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminDlq, clearAdminDlq, requeueAdminDlq, fetchLogToggle, updateLogToggle, downloadAdminLog, clearAdminLog } from '../api/http'

const logEnabled = ref(true)
const toggling = ref(false)
const downloadingBackend = ref(false)
const downloadingWorker = ref(false)

const dlqStats = ref<{ size: number; items: string[] }>({ size: 0, items: [] })
const requeuing = ref(false)
const dlqDialogOpen = ref(false)

async function loadDlqStats() {
  try {
    dlqStats.value = await fetchAdminDlq()
  } catch (err) {
    console.error('Failed to load DLQ stats', err)
  }
}

async function loadToggleStatus() {
  try {
    const res = await fetchLogToggle()
    logEnabled.value = res.enabled
  } catch (err) {
    console.error('Failed to load log toggle status', err)
  }
}

async function handleToggleChange(val: boolean | string | number) {
  const isEnabled = !!val
  toggling.value = true
  try {
    await updateLogToggle(isEnabled)
    ElMessage.success(`系统日志全局收集已成功${isEnabled ? '开启' : '关闭'}`)
  } catch (err) {
    logEnabled.value = !isEnabled // Revert on failure
    ElMessage.error('切换日志状态失败')
  } finally {
    toggling.value = false
  }
}

async function handleDownload(type: 'backend' | 'worker') {
  if (type === 'backend') {
    downloadingBackend.value = true
  } else {
    downloadingWorker.value = true
  }
  try {
    await downloadAdminLog(type)
    ElMessage.success('日志文件下载成功！')
  } catch (err: any) {
    console.error('Download log failed', err)
    ElMessage.error('日志文件暂无或下载失败')
  } finally {
    if (type === 'backend') {
      downloadingBackend.value = false
    } else {
      downloadingWorker.value = false
    }
  }
}

async function handleClearPrompt(type: 'backend' | 'worker') {
  const label = type === 'worker' ? '判题机服务日志 (judge-worker.log)' : '后端服务日志 (backend.log)'
  try {
    await ElMessageBox.confirm(`确定要清空 ${label} 吗？清空后文件内容将被截断为0字节，历史日志将彻底消失且无法恢复。`, '危险提示', {
      type: 'warning',
      confirmButtonText: '确定清空',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    await clearAdminLog(type)
    ElMessage.success('日志文件已清空！')
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('清空日志失败')
    }
  }
}

async function load() {
  await loadToggleStatus()
  await loadDlqStats()
}

function inspectDlq() {
  dlqDialogOpen.value = true
}

async function handleClearDlq() {
  try {
    await ElMessageBox.confirm('确定要清空死信队列吗？此操作将丢弃所有被隔离的任务，无法撤销。', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await clearAdminDlq()
    ElMessage.success('已清空死信队列')
    dlqDialogOpen.value = false
    await loadDlqStats()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

async function handleRequeueDlq() {
  requeuing.value = true
  try {
    const res = await requeueAdminDlq()
    ElMessage.success(`成功重新评测 ${res.requeued} 个任务，系统将开始重新计算`)
    dlqDialogOpen.value = false
    await load()
  } catch (err) {
    ElMessage.error('操作失败')
  } finally {
    requeuing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dlq-alert-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(230, 162, 60, 0.08);
  border: 1px solid rgba(230, 162, 60, 0.25);
  backdrop-filter: blur(12px);
  padding: 1.25rem 1.5rem;
  border-radius: 12px;
  margin-bottom: 1.5rem;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  animation: slideDown 0.3s ease-out;
}

.alert-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.alert-icon {
  font-size: 2rem;
  color: var(--el-color-warning);
  margin-top: 0.15rem;
}

.alert-text h3 {
  margin: 0 0 0.25rem 0;
  font-size: 1.05rem;
  color: var(--el-color-warning);
  font-weight: 600;
}

.alert-text p {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-regular);
  line-height: 1.5;
}

.alert-actions {
  display: flex;
  gap: 0.75rem;
  flex-shrink: 0;
}

.logs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  margin-top: 15px;
}

.logs-card {
  padding: 24px;
  background: #ffffff;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.card-icon {
  font-size: 1.5rem;
  color: #6366f1;
}

.card-header h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: #1e293b;
}

.card-desc {
  font-size: 0.88rem;
  color: #64748b;
  line-height: 1.6;
  margin: 0 0 24px 0;
  flex: 1;
}

.switch-container {
  display: flex;
  align-items: center;
  background: #f8fafc;
  padding: 16px 20px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.switch-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: #334155;
  margin-right: 12px;
}

.downloads-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-file-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 16px 20px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.log-file-row:hover {
  border-color: #cbd5e1;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
}

.file-name {
  font-size: 0.95rem;
  font-weight: 600;
  color: #1e293b;
  font-family: var(--font-mono), monospace;
}

.file-type-badge {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 4px;
  width: fit-content;
  font-weight: 500;
}

.backend-badge {
  background: #e0f2fe;
  color: #0369a1;
}

.worker-badge {
  background: #fef3c7;
  color: #b45309;
}

.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action-btn {
  font-weight: 600;
  margin-left: 0 !important;
}

.dlq-dialog-body {
  max-height: 480px;
  overflow-y: auto;
}

.dlq-items-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.dlq-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 1rem;
}

.dlq-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.dlq-item-header .badge {
  font-size: 0.75rem;
  padding: 0.15rem 0.4rem;
  background: rgba(230, 162, 60, 0.15);
  color: var(--el-color-warning);
  border: 1px solid rgba(230, 162, 60, 0.25);
  border-radius: 4px;
  font-weight: bold;
}

.payload-raw-title {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.dlq-item-pre {
  margin: 0;
  background: rgba(0, 0, 0, 0.25) !important;
  color: #a9b7c6;
  font-family: var(--font-mono);
  font-size: 0.85rem;
  padding: 0.75rem;
  border-radius: 6px;
  overflow-x: auto;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
