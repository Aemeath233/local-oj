<template>
  <div class="tab-content backup-tab" v-loading="loading">
    <div v-if="backupData">
      <!-- Database Connection Meta Info -->
      <div class="backup-meta-grid">
        <div class="meta-card">
          <div class="meta-item">
            <span class="lbl">数据库 Host:</span>
            <span class="val"><code>{{ backupData.host }}</code></span>
          </div>
          <div class="meta-item">
            <span class="lbl">数据库 Port:</span>
            <span class="val"><code>{{ backupData.port }}</code></span>
          </div>
        </div>
        <div class="meta-card">
          <div class="meta-item">
            <span class="lbl">备份数据库库名:</span>
            <span class="val"><code>{{ backupData.database }}</code></span>
          </div>
          <div class="meta-item">
            <span class="lbl">连接认证用户名:</span>
            <span class="val"><code>{{ backupData.username }}</code></span>
          </div>
        </div>
      </div>

      <!-- Commands sections grouped by Environment -->
      <div class="commands-list">
        <!-- Group 1: Local Server Environment -->
        <div class="command-group-card">
          <div class="group-header">
            <span class="badge host">本地快捷连接</span>
            <h3>本机命令行模式 (推荐)</h3>
          </div>
          <p class="group-desc">当直接在数据库运行的本机服务器终端执行备份恢复时，可直接省略 Host 和 Port 选项，运行如下简化指令：</p>
          
          <!-- Local Backup -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag backup">一键备份数据库 (Backup)</span>
              <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.localBackupCommand)">复制备份命令</el-button>
            </div>
            <div class="code-container">
              <pre><code>{{ backupData.localBackupCommand }}</code></pre>
            </div>
          </div>

          <!-- Local Restore -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag restore">一键恢复数据库 (Restore)</span>
              <el-button size="small" type="danger" link :icon="DocumentCopy" @click="copyText(backupData.localRestoreCommand)">复制恢复命令</el-button>
            </div>
            <p class="sub-desc"><span class="warn-label">警告：</span>恢复操作为覆盖写，会覆盖当前同名数据库的数据表，请在恢复前再次确认备份 SQL 的文件名！</p>
            <div class="code-container">
              <pre><code>{{ backupData.localRestoreCommand }}</code></pre>
            </div>
          </div>
        </div>

        <!-- Group 2: Remote/Explicit TCP Connection -->
        <div class="command-group-card">
          <div class="group-header">
            <span class="badge docker">网络连接模式</span>
            <h3>TCP/IP 显式连接模式</h3>
          </div>
          <p class="group-desc">适合异地/外部服务器备份还原或需要显式指定 Host 与 Port 参数的本地客户端场景：</p>

          <!-- Remote Backup -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag backup">远程备份数据库 (Backup)</span>
              <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.remoteBackupCommand)">复制备份命令</el-button>
            </div>
            <div class="code-container">
              <pre><code>{{ backupData.remoteBackupCommand }}</code></pre>
            </div>
          </div>

          <!-- Remote Restore -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag restore">远程恢复数据库 (Restore)</span>
              <el-button size="small" type="danger" link :icon="DocumentCopy" @click="copyText(backupData.remoteRestoreCommand)">复制恢复命令</el-button>
            </div>
            <p class="sub-desc"><span class="warn-label">警告：</span>数据还原覆盖操作。执行后，终端会提示输入密码，请配合输入数据库管理员密码确认：</p>
            <div class="code-container">
              <pre><code>{{ backupData.remoteRestoreCommand }}</code></pre>
            </div>
          </div>
        </div>
      </div>

      <!-- Group 3: One-click Full Site Backup & Migration -->
      <div class="command-group-card">
        <div class="group-header">
          <span class="badge host">全站打包备份</span>
          <h3>全站一键备份与迁移</h3>
        </div>
        <p class="group-desc">打包导出当前系统的完整数据包（包含全部数据库结构与内容、题目的评测数据、用户头像等），生成一个 <code>.zip</code> 压缩文件。导入备份时会覆盖当前系统的所有数据，请谨慎操作。</p>

        <div class="backup-actions">
          <el-button type="primary" :icon="Download" :loading="exporting" @click="handleFullExport">
            全站数据打包并下载 (Export)
          </el-button>

          <el-upload
            action=""
            :before-upload="beforeFullImport"
            :show-file-list="false"
            accept=".zip"
            style="display: inline-block; margin-left: 12px;"
          >
            <el-button type="danger" :icon="Upload" :loading="importing">
              导入备份还原全站 (Import)
            </el-button>
          </el-upload>
        </div>
      </div>

      <!-- Maintenance Guidelines -->
      <div class="maintenance-box mt-4">
        <div class="section-title">
          <el-icon><InfoFilled /></el-icon> 运维维护及灾备恢复最佳实践建议
        </div>
        <ul class="guideline-list">
          <li v-for="(rec, idx) in backupData.recommendations" :key="idx">
            <span class="bullet">{{ Number(idx) + 1 }}</span>
            <span class="text">{{ rec }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { DocumentCopy, InfoFilled, Download, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchBackupInfo, http } from '../../api/http'

const loading = ref(false)
const exporting = ref(false)
const importing = ref(false)
const backupData = ref<any>(null)

onMounted(async () => {
  loading.value = true
  try {
    backupData.value = await fetchBackupInfo()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '加载数据库备份配置失败')
  } finally {
    loading.value = false
  }
})

function copyText(text: string) {
  if (!text) return
  navigator.clipboard.writeText(text)
    .then(() => {
      ElMessage.success('命令已成功复制到剪贴板！')
    })
    .catch(() => {
      ElMessage.error('复制失败，请手动选取代码块')
    })
}

async function handleFullExport() {
  exporting.value = true
  try {
    const token = localStorage.getItem('coderushoj.token')
    const response = await http.get('/admin/data/backup/export', {
      responseType: 'blob',
      headers: {
        Authorization: `Bearer ${token}`
      }
    })
    
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    
    const contentDisposition = response.headers['content-disposition']
    let filename = 'coderush_oj_backup.zip'
    if (contentDisposition) {
      const match = contentDisposition.match(/filename=(.+)/)
      if (match) filename = match[1]
    }
    
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('全站数据备份导出成功！')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '全站数据备份导出失败')
  } finally {
    exporting.value = false
  }
}

function beforeFullImport(file: File) {
  ElMessageBox.confirm(
    '此操作将彻底删除并覆盖当前系统的所有数据库表、题目测试数据以及用户头像，且不可逆！是否确定导入备份进行还原？',
    '高危恢复警告',
    {
      confirmButtonText: '确定覆盖还原',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    importing.value = true
    const formData = new FormData()
    formData.append('file', file)
    try {
      await http.post('/admin/data/backup/import', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      ElMessage({
        type: 'success',
        message: '全站数据备份导入成功！数据库与文件已还原，请刷新页面检查。',
        duration: 5000
      })
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '全站数据备份导入失败')
    } finally {
      importing.value = false
    }
  }).catch(() => {})
  return false
}
</script>

<style scoped>
.tab-content {
  padding: 1.5rem 0;
}

.backup-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.meta-card {
  background: var(--bg-muted);
  padding: 1.25rem;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
}

.meta-item .lbl {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.meta-item .val code {
  background: var(--bg-app);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  font-family: var(--font-mono, monospace);
  color: var(--primary);
}

.commands-list {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  margin-bottom: 2rem;
}

.command-group-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 1.75rem;
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.group-header h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-primary);
}

.group-desc {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.command-box {
  background: var(--bg-muted);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.box-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.action-tag {
  font-size: 0.8rem;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 4px;
}

.action-tag.backup {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.action-tag.restore {
  background: #fff5f5;
  color: #e53e3e;
  border: 1px solid #fed7d7;
}

.sub-desc {
  margin: 0 0 4px 0;
  font-size: 0.82rem;
  color: var(--text-secondary, #4b5563);
  line-height: 1.4;
}

.warn-label {
  color: #e53e3e;
  font-weight: 700;
}

.badge {
  font-size: 0.75rem;
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 600;
}

.badge.docker {
  background: #e0f2fe;
  color: #0284c7;
  border: 1px solid #bae6fd;
}

.badge.host {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.code-container {
  background: #0f172a !important; /* Enforce premium deep slate black background */
  border-radius: 6px;
  padding: 14px 18px;
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.2);
}

.code-container pre {
  margin: 0 !important;
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
}

.code-container code {
  color: #38bdf8 !important; /* Premium light cyan color for commands, 100% high contrast and visible */
  background: transparent !important;
  font-family: var(--font-mono, monospace) !important;
  font-size: 0.9rem !important;
  padding: 0 !important;
  border: none !important;
  text-shadow: 0 0 2px rgba(56, 189, 248, 0.2);
  white-space: pre-wrap;
  word-break: break-all;
}

.maintenance-box {
  background: #f0fdfa;
  border: 1px solid #ccfbf1;
  padding: 1.5rem;
  border-radius: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.05rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: #0d9488;
}

.guideline-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guideline-list li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.guideline-list .bullet {
  background: #0d9488;
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.guideline-list .text {
  font-size: 0.88rem;
  color: #334155;
  line-height: 1.5;
}

.mt-4 {
  margin-top: 1.5rem;
}

/* Dark Mode Badge and recommendation overrides */
html.dark .action-tag.backup {
  background: rgba(16, 185, 129, 0.1) !important;
  color: #34d399 !important;
  border-color: rgba(16, 185, 129, 0.25) !important;
}

html.dark .action-tag.restore {
  background: rgba(239, 68, 68, 0.1) !important;
  color: #f87171 !important;
  border-color: rgba(239, 68, 68, 0.25) !important;
}

html.dark .badge.docker {
  background: rgba(14, 165, 233, 0.1) !important;
  color: #38bdf8 !important;
  border-color: rgba(14, 165, 233, 0.25) !important;
}

html.dark .badge.host {
  background: rgba(34, 197, 94, 0.1) !important;
  color: #4ade80 !important;
  border-color: rgba(34, 197, 94, 0.25) !important;
}

html.dark .maintenance-box {
  background: rgba(13, 148, 136, 0.08) !important;
  border-color: rgba(13, 148, 136, 0.25) !important;
}

html.dark .maintenance-box .section-title {
  color: #2dd4bf !important;
}

html.dark .guideline-list .text {
  color: var(--text-secondary) !important;
}
</style>
