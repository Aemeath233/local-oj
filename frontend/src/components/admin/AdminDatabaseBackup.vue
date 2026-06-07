<template>
  <div class="tab-content backup-tab" v-loading="loading">
    <div v-if="backupData">
      <!-- Database Connection Meta Info -->
      <div class="backup-meta-grid">
        <div class="meta-card">
          <div class="meta-item">
            <span class="lbl">后端容器内部 Host:</span>
            <span class="val"><code>{{ backupData.host }}</code></span>
          </div>
          <div class="meta-item">
            <span class="lbl">容器内置 Port:</span>
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
        <!-- Group 1: Docker Compose Container Environment -->
        <div class="command-group-card">
          <div class="group-header">
            <span class="badge docker">Docker 容器环境</span>
            <h3>Docker-Compose 容器运行模式 (推荐)</h3>
          </div>
          <p class="group-desc">最简单的备份恢复模式。直接在运行 Docker 的宿主机终端执行，命令会自动读取容器内部的环境变量密码，安全且无需输入明文：</p>
          
          <!-- Docker Backup -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag backup">一键备份数据库 (Backup)</span>
              <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.dockerBackupCommand)">复制备份命令</el-button>
            </div>
            <div class="code-container">
              <pre><code>{{ backupData.dockerBackupCommand }}</code></pre>
            </div>
          </div>

          <!-- Docker Restore -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag restore">一键恢复数据库 (Restore)</span>
              <el-button size="small" type="danger" link :icon="DocumentCopy" @click="copyText(backupData.dockerRestoreCommand)">复制恢复命令</el-button>
            </div>
            <p class="sub-desc"><span class="warn-label">警告：</span>恢复操作为覆盖写，会覆盖当前同名数据库的数据表，请在恢复前再次确认备份 SQL 的文件名！</p>
            <div class="code-container">
              <pre><code>{{ backupData.dockerRestoreCommand }}</code></pre>
            </div>
          </div>
        </div>

        <!-- Group 2: Exposed Port Local Client Environment -->
        <div class="command-group-card">
          <div class="group-header">
            <span class="badge host">宿主机外置端口</span>
            <h3>物理/外置客户端模式</h3>
          </div>
          <p class="group-desc">适合本地装有 MySQL/mysqldump 客户端或需要异地/外部服务器备份还原的情景（基于暴露的 3307 映射端口）：</p>

          <!-- Native Backup -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag backup">端口备份数据库 (Backup)</span>
              <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.nativeBackupCommand)">复制备份命令</el-button>
            </div>
            <div class="code-container">
              <pre><code>{{ backupData.nativeBackupCommand }}</code></pre>
            </div>
          </div>

          <!-- Native Restore -->
          <div class="command-box">
            <div class="box-header">
              <span class="action-tag restore">端口恢复数据库 (Restore)</span>
              <el-button size="small" type="danger" link :icon="DocumentCopy" @click="copyText(backupData.nativeRestoreCommand)">复制恢复命令</el-button>
            </div>
            <p class="sub-desc"><span class="warn-label">警告：</span>数据还原覆盖操作。执行后，终端会提示输入密码，请配合输入数据库管理员密码确认：</p>
            <div class="code-container">
              <pre><code>{{ backupData.nativeRestoreCommand }}</code></pre>
            </div>
          </div>
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
import { DocumentCopy, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchBackupInfo } from '../../api/http'

const loading = ref(false)
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
