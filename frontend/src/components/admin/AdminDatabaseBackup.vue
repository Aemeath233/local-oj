<template>
  <div class="tab-content backup-tab" v-loading="loading">
    <div v-if="backupData">
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

      <!-- Commands sections -->
      <div class="commands-list">
        <!-- Command Box 1 -->
        <div class="command-box">
          <div class="box-header">
            <span class="badge docker">Docker 容器环境</span>
            <h4>通过宿主机控制台一键备份 (推荐)</h4>
            <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.dockerBackupCommand)">一键复制</el-button>
          </div>
          <p class="desc">在部署了 Docker-Compose 的宿主机终端直接运行。命令会读取 MySQL 容器内的密码环境变量，不会在页面展示明文密码：</p>
          <div class="code-container">
            <pre><code>{{ backupData.dockerBackupCommand }}</code></pre>
          </div>
        </div>

        <!-- Command Box 2 -->
        <div class="command-box">
          <div class="box-header">
            <span class="badge host">宿主机外置端口</span>
            <h4>通过 3307 映射端口备份</h4>
            <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.nativeBackupCommand)">一键复制</el-button>
          </div>
          <p class="desc">在外部局域网或宿主机控制台直接基于暴露的 3307 端口调取客户端工具输出。运行命令后需要输入数据库密码：</p>
          <div class="code-container">
            <pre><code>{{ backupData.nativeBackupCommand }}</code></pre>
          </div>
        </div>

        <!-- Command Box 3 -->
        <div class="command-box">
          <div class="box-header">
            <span class="badge danger">高危还原</span>
            <h4>数据恢复/还原指令 (Restore)</h4>
            <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(backupData.dockerRestoreCommand)">一键复制</el-button>
          </div>
          <p class="desc">当发生系统损坏时，将之前备份的 SQL 文件还原覆盖写入 Docker 容器数据库：</p>
          <div class="code-container">
            <pre><code>{{ backupData.dockerRestoreCommand }}</code></pre>
          </div>
        </div>
      </div>

      <!-- Maintenance Guidelines -->
      <div class="maintenance-box mt-4">
        <div class="section-title">
          <el-icon><InfoFilled /></el-icon> 运维维护最佳实践建议
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
  background: var(--bg-card, #fafafa);
  padding: 1.25rem;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
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
  background: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-light);
  font-family: var(--font-mono, monospace);
  color: var(--el-color-primary);
}

.commands-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.command-box {
  background: #fafbfc;
  border: 1px solid #d8dee6;
  border-radius: 8px;
  padding: 1.25rem;
}

.box-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}

.box-header h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.box-header .el-button {
  margin-left: auto;
}

.badge {
  font-size: 0.75rem;
  padding: 2px 8px;
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

.badge.danger {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.command-box .desc {
  margin: 0 0 10px 0;
  font-size: 0.88rem;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.code-container {
  background: #0f172a;
  border-radius: 6px;
  padding: 12px 16px;
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.code-container pre {
  margin: 0;
}

.code-container code {
  color: #e2e8f0;
  font-family: var(--font-mono, monospace);
  font-size: 0.88rem;
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
</style>
