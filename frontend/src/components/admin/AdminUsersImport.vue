<template>
  <div class="tab-content users-tab" v-loading="loading">
    <div class="action-card">
      <div class="card-info">
        <h3>批量导入学生与管理员账号</h3>
        <p>通过上传标准格式的 CSV 文件批量注册账号。如果密码列留空，系统将自动为每个用户生成随机默认密码并在此展示。</p>
      </div>
      <el-button type="success" :icon="Download" @click="downloadCsvTemplate">下载 CSV 导入模板</el-button>
    </div>

    <div class="upload-area">
      <el-upload
        drag
        action=""
        :auto-upload="false"
        :show-file-list="false"
        accept=".csv"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将 CSV 文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只能上传 .csv 格式的文件。请保证用户名和邮箱的唯一性。
          </div>
        </template>
      </el-upload>
    </div>

    <!-- Import Result Summary -->
    <div v-if="importResult" class="import-result-section">
      <el-divider>导入结果报告</el-divider>

      <div class="result-stats-grid">
        <div class="stat-card total">
          <div class="num">{{ importResult.total }}</div>
          <div class="lbl">读取数据总行数</div>
        </div>
        <div class="stat-card success">
          <div class="num">{{ importResult.successCount }}</div>
          <div class="lbl">成功导入用户数</div>
        </div>
        <div class="stat-card failed">
          <div class="num">{{ importResult.failedCount }}</div>
          <div class="lbl">失败跳过行数</div>
        </div>
      </div>

      <!-- Errors Table -->
      <div v-if="importResult.errors && importResult.errors.length > 0" class="result-sub-section errors-list">
        <div class="section-title text-danger">
          <el-icon><Warning /></el-icon> 导入失败明细 (共 {{ importResult.errors.length }} 条错误)
        </div>
        <el-table :data="importResult.errors" stripe style="width: 100%" max-height="300">
          <el-table-column prop="row" label="Excel/CSV行号" width="120" align="center" />
          <el-table-column prop="username" label="用户名" width="180" />
          <el-table-column prop="reason" label="失败原因" min-width="250">
            <template #default="scope">
              <el-tag type="danger" size="small">{{ scope.row.reason }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Success Table -->
      <div v-if="importResult.users && importResult.users.length > 0" class="result-sub-section success-list">
        <div class="section-title text-success">
          <el-icon><Check /></el-icon> 成功导入用户明细 (共 {{ importResult.users.length }} 个账号)
        </div>
        <el-table :data="importResult.users" stripe style="width: 100%" max-height="400">
          <el-table-column prop="username" label="用户名" width="150" />
          <el-table-column prop="displayName" label="昵称" width="150" />
          <el-table-column prop="email" label="邮箱" width="200" show-overflow-tooltip />
          <el-table-column prop="studentNo" label="班级" width="120" />
          <el-table-column prop="role" label="分配角色" width="110" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.role === 'SUPER_ADMIN' ? 'danger' : scope.row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
                {{ scope.row.role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="password" label="初始登录密码" width="160" align="center">
            <template #default="scope">
              <code class="password-code">{{ scope.row.password }}</code>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="scope">
              <el-button size="small" type="primary" link :icon="DocumentCopy" @click="copyText(scope.row.password)">一键复制密码</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Download, UploadFilled, Warning, Check, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { importUsersBulk } from '../../api/http'

const loading = ref(false)
const importResult = ref<any>(null)

async function handleFileChange(uploadFile: any) {
  const rawFile = uploadFile.raw
  if (!rawFile) return

  loading.value = true
  try {
    const res = await importUsersBulk(rawFile)
    importResult.value = res
    if (res.failedCount > 0) {
      ElMessage.warning(`导入完成，成功 ${res.successCount} 个账号，有 ${res.failedCount} 行解析失败。`)
    } else {
      ElMessage.success(`导入成功，共成功创建 ${res.successCount} 个账号！`)
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '导入解析失败')
  } finally {
    loading.value = false
  }
}

function downloadCsvTemplate() {
  const content = '\uFEFF' + '用户名,邮箱,昵称,班级,专业,密码,角色\n' +
    'student_test1,test1@localoj.com,李雷,计科2201班,计算机科学与技术,Pass@123,STUDENT\n' +
    'student_test2,,韩梅梅,计科2202班,人工智能,,STUDENT\n' +
    'admin_import_test,admin_imp@localoj.com,王老师,,控制工程,AdminPass123,ADMIN\n'
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', 'users_bulk_import_template.csv')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function copyText(text: string) {
  if (!text) return
  navigator.clipboard.writeText(text)
    .then(() => {
      ElMessage.success('密码已成功复制到剪贴板！')
    })
    .catch(() => {
      ElMessage.error('复制失败，请手动选取密码文本')
    })
}
</script>

<style scoped>
.tab-content {
  padding: 1.5rem 0;
}

.action-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08) 0%, rgba(103, 194, 58, 0.08) 100%);
  border-radius: 8px;
  border: 1px dashed rgba(64, 158, 255, 0.2);
  margin-bottom: 2rem;
}
.card-info h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.card-info p {
  margin: 0;
  font-size: 0.9rem;
  color: var(--el-text-color-secondary);
}

.upload-area {
  margin-bottom: 2rem;
}
:deep(.el-upload-dragger) {
  background: var(--bg-muted);
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  transition: all 0.3s;
}
:deep(.el-upload-dragger:hover) {
  border-color: var(--primary);
  background: var(--primary-light);
}

.import-result-section {
  animation: fadeIn 0.4s ease-out;
}
.result-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1.5rem;
  margin: 1.5rem 0 2.5rem 0;
}
.stat-card {
  padding: 1.5rem;
  border-radius: 10px;
  text-align: center;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}
.stat-card.total {
  background: rgba(64, 158, 255, 0.04);
}
.stat-card.success {
  background: rgba(103, 194, 58, 0.04);
}
.stat-card.failed {
  background: rgba(245, 108, 108, 0.04);
}
.stat-card .num {
  font-size: 2.2rem;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 0.5rem;
}
.stat-card.total .num { color: var(--el-color-primary); }
.stat-card.success .num { color: var(--el-color-success); }
.stat-card.failed .num { color: var(--el-color-danger); }
.stat-card .lbl {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.result-sub-section {
  margin-bottom: 2.5rem;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.05rem;
  font-weight: 600;
  margin-bottom: 1rem;
}
.password-code {
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  background: var(--bg-muted);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  color: var(--error);
  border: 1px solid var(--border-color);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
