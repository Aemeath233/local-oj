<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>数据管理</h1>
        <p>用户批量导入、提交记录清理、测试数据存储空间分析以及数据库备份运维中心。</p>
      </div>
    </div>

    <section class="panel data-panel" v-loading="loading">
      <el-tabs v-model="activeTab">
        <!-- TAB 1: USER BULK IMPORT -->
        <el-tab-pane label="用户批量导入" name="users">
          <div class="tab-content users-tab">
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
                    只能上传 .csv 格式的文件。请保证用户名和学号/邮箱的唯一性。
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
                  <el-table-column prop="studentNo" label="学号" width="120" />
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
        </el-tab-pane>

        <!-- TAB 2: SUBMISSION CLEANUP -->
        <el-tab-pane label="提交记录清理" name="cleanup">
          <div class="tab-content cleanup-tab">
            <el-alert
              title="物理删除警告"
              type="warning"
              description="清理提交记录是物理删除操作，不可撤销！清理历史记录能有效压缩数据库体积并提升排行榜计算速率，建议定期删除旧的或无关测试提交。与之关联的测试点细节运行数据会自动级联清空。"
              show-icon
              :closable="false"
              class="mb-4"
            />

            <el-form :model="cleanupForm" label-position="top" class="cleanup-form-layout">
              <div class="form-grid">
                <el-form-item label="提交时间早于">
                  <el-date-picker
                    v-model="cleanupForm.beforeDate"
                    type="datetime"
                    placeholder="选择截止时间（留空则不限时间）"
                    style="width: 100%"
                    value-format="YYYY-MM-DDTHH:mm:ss"
                  />
                  <div class="form-item-tip">例如：选择删除 30 天以前的所有历史提交。</div>
                </el-form-item>

                <el-form-item label="特定题目范围">
                  <el-select v-model="cleanupForm.problemId" placeholder="所有题目" clearable style="width: 100%">
                    <el-option v-for="p in problems" :key="p.id" :label="`${p.id}. ${p.title} (${p.slug})`" :value="p.id" />
                  </el-select>
                </el-form-item>

                <el-form-item label="特定提交用户 ID">
                  <el-input-number v-model="cleanupForm.userId" placeholder="输入用户 ID" :min="1" style="width: 100%" />
                </el-form-item>

                <el-form-item label="清理范围 (Scope)">
                  <el-select v-model="cleanupForm.scope" placeholder="所有提交" style="width: 100%">
                    <el-option label="所有提交 (所有练习与比赛)" value="all" />
                    <el-option label="仅限日常练习提交 (不含比赛)" value="practice" />
                    <el-option label="所有比赛提交" value="contest" />
                    <el-option label="指定某一特定比赛" value="single-contest" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="cleanupForm.scope === 'single-contest'" label="选择比赛">
                  <el-select v-model="cleanupForm.contestId" placeholder="选择比赛" style="width: 100%">
                    <el-option v-for="c in contests" :key="c.id" :label="`[${c.type}] ${c.title}`" :value="c.id" />
                  </el-select>
                </el-form-item>

                <el-form-item label="指定评测状态 (Verdict)">
                  <el-select v-model="cleanupForm.verdicts" multiple collapse-tags placeholder="所有结果类型" style="width: 100%">
                    <el-option label="AC (Accepted)" value="AC" />
                    <el-option label="WA (Wrong Answer)" value="WA" />
                    <el-option label="TLE (Time Limit Exceeded)" value="TLE" />
                    <el-option label="MLE (Memory Limit Exceeded)" value="MLE" />
                    <el-option label="OLE (Output Limit Exceeded)" value="OLE" />
                    <el-option label="RE (Runtime Error)" value="RE" />
                    <el-option label="CE (Compilation Error)" value="CE" />
                    <el-option label="IE (Internal Error)" value="IE" />
                  </el-select>
                  <div class="form-item-tip">例如：可以只清理 CE（编译失败）或 IE（内部错误）等对成绩没有影响的冗余数据。</div>
                </el-form-item>
              </div>

              <!-- High-Security Lock Panel -->
              <div class="security-lock-panel">
                <div class="lock-header">
                  <el-icon class="lock-icon"><Lock /></el-icon>
                  <h4>高安全确认保护锁</h4>
                </div>
                <p class="lock-desc">为避免意外操作，请在下方文本框输入验证短语 <strong>FORCE CLEANUP</strong> 启用安全删除按钮：</p>
                <el-input
                  v-model="securityPhrase"
                  placeholder="请输入 FORCE CLEANUP 进行二次安全解锁"
                  class="phrase-input"
                  clearable
                />
              </div>

              <div class="form-actions-clean">
                <el-button
                  type="danger"
                  size="large"
                  :disabled="securityPhrase.trim() !== 'FORCE CLEANUP'"
                  :loading="cleaningSubmissions"
                  :icon="Delete"
                  @click="triggerCleanup"
                >
                  安全解锁并执行物理清理
                </el-button>
              </div>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- TAB 3: TEST DATA STORAGE STATS -->
        <el-tab-pane label="测试数据空间统计" name="storage">
          <div class="tab-content storage-tab" v-if="storageData">
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
        </el-tab-pane>

        <!-- TAB 4: DATABASE BACKUP INFO -->
        <el-tab-pane label="数据库备份提示" name="backup">
          <div class="tab-content backup-tab" v-if="backupData">
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
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Download,
  UploadFilled,
  Warning,
  Check,
  DocumentCopy,
  Delete,
  Lock,
  Refresh,
  Files,
  InfoFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import {
  importUsersBulk,
  cleanupSubmissions,
  fetchStorageStats,
  cleanupOrphanedCases,
  fetchBackupInfo,
  fetchProblems,
  fetchContests
} from '../api/http'

const loading = ref(false)
const activeTab = ref('users')

// TAB 1: Bulk User Import
const importResult = ref<any>(null)

// TAB 2: Submissions Cleanup
const cleaningSubmissions = ref(false)
const securityPhrase = ref('')
const problems = ref<any[]>([])
const contests = ref<any[]>([])
const cleanupForm = reactive({
  beforeDate: '',
  problemId: null as number | null,
  userId: null as number | null,
  contestId: null as number | null,
  scope: 'all' as 'all' | 'practice' | 'contest' | 'single-contest',
  verdicts: [] as string[]
})

// TAB 3: Storage Stats
const storageData = ref<any>(null)
const cleaningOrphans = ref(false)

// TAB 4: Backup info
const backupData = ref<any>(null)

onMounted(initialize)

async function initialize() {
  loading.value = true
  try {
    const [probList, contList, storage, backup] = await Promise.all([
      fetchProblems(),
      fetchContests(),
      fetchStorageStats(),
      fetchBackupInfo()
    ])
    problems.value = probList || []
    contests.value = contList || []
    storageData.value = storage
    backupData.value = backup
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '初始化数据加载失败')
  } finally {
    loading.value = false
  }
}

// User bulk import handler
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

// Dynamic CSV Template generation for UTF-8 with BOM
function downloadCsvTemplate() {
  const content = '\uFEFF' + '用户名,邮箱,昵称,学号,专业,密码,角色\n' +
    'student_test1,test1@localoj.com,李雷,202601001,计算机科学与技术,Pass@123,STUDENT\n' +
    'student_test2,,韩梅梅,202601002,人工智能,,STUDENT\n' +
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

// Submissions record cleanup
async function triggerCleanup() {
  if (securityPhrase.value.trim() !== 'FORCE CLEANUP') {
    return
  }

  try {
    await ElMessageBox.confirm(
      '此操作为物理清空，不可恢复！您确定要清理对应的提交记录吗？',
      '极限操作风险警告',
      {
        confirmButtonText: '确定清除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  cleaningSubmissions.value = true
  try {
    const payload: any = {
      confirmationPhrase: securityPhrase.value.trim(),
      beforeDate: cleanupForm.beforeDate || undefined,
      problemId: cleanupForm.problemId || undefined,
      userId: cleanupForm.userId || undefined,
      verdicts: cleanupForm.verdicts.length > 0 ? cleanupForm.verdicts : undefined
    }

    if (cleanupForm.scope === 'practice') {
      payload.onlyPractice = true
    } else if (cleanupForm.scope === 'contest') {
      payload.onlyContest = true
    } else if (cleanupForm.scope === 'single-contest') {
      payload.contestId = cleanupForm.contestId || undefined
    }

    const res = await cleanupSubmissions(payload)
    ElMessage.success(`历史提交记录清除成功，共物理清理了 ${res.count} 条提交数据！`)
    securityPhrase.value = ''
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '清理提交数据失败')
  } finally {
    cleaningSubmissions.value = false
  }
}

// Load storage stats
async function loadStorageStats() {
  loading.value = true
  try {
    storageData.value = await fetchStorageStats()
    ElMessage.success('存储空间分析完成')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '更新空间统计失败')
  } finally {
    loading.value = false
  }
}

// Clean orphaned directories
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

// Clipboard copying
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

// Format utilities
function formatBytes(bytes: number) {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const storageUsedPercentage = computed(() => {
  if (!storageData.value || storageData.value.totalSpaceBytes === 0) return 0
  const total = storageData.value.totalSpaceBytes + storageData.value.freeSpaceBytes
  return Math.min(100, Math.round((storageData.value.totalSpaceBytes / total) * 100))
})

const orphanedDirectoriesCount = computed(() => {
  if (!storageData.value || !storageData.value.problemStats) return 0
  return storageData.value.problemStats.filter((p: any) => p.orphaned).length
})
</script>

<style scoped>
.data-panel {
  margin-top: 1rem;
  padding: 1.5rem;
  background: var(--bg-panel, #ffffff);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.tab-content {
  padding: 1.5rem 0;
}

/* Tab 1: Users Bulk Import */
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
  background: var(--bg-card, #fafafa);
  border: 2px dashed var(--el-border-color-light);
  border-radius: 8px;
  transition: all 0.3s;
}
:deep(.el-upload-dragger:hover) {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.02);
}

/* Result report styling */
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
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.05);
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
  background: var(--bg-card, #f4f4f5);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  color: #d03050;
  border: 1px solid var(--el-border-color-light);
}

/* Tab 2: Cleanup */
.cleanup-form-layout {
  background: var(--bg-card, #fafafa);
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1.5rem;
}
.form-item-tip {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
  margin-top: 0.25rem;
}
.security-lock-panel {
  margin-top: 2rem;
  padding: 1.25rem;
  border-radius: 6px;
  background: rgba(245, 108, 108, 0.03);
  border: 1px solid rgba(245, 108, 108, 0.2);
}
.lock-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--el-color-danger);
  margin-bottom: 0.5rem;
}
.lock-header h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}
.lock-desc {
  margin: 0 0 1rem 0;
  font-size: 0.9rem;
  color: var(--el-text-color-regular);
}
.phrase-input {
  max-width: 360px;
}
.form-actions-clean {
  margin-top: 1.5rem;
  display: flex;
  justify-content: flex-end;
}
.mb-4 {
  margin-bottom: 1.5rem;
}

/* Tab 3: Storage Stats */
.storage-dashboard-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 1.5rem;
}
.storage-card {
  background: var(--bg-card, #fafafa);
  padding: 1.5rem;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
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
  font-size: 3.5rem;
  font-weight: 700;
  color: var(--el-color-success);
}
.size-display .unit {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-left: 0.25rem;
}
.space-progress {
  margin-bottom: 1rem;
}
.space-details {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}

.storage-card.info h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.05rem;
  font-weight: 600;
}
.storage-card.info p {
  margin: 0 0 1.5rem 0;
  font-size: 0.88rem;
  color: var(--el-text-color-secondary);
}
.orphaned-warning-box {
  padding: 1rem;
  border-radius: 6px;
  background: var(--bg-panel, #ffffff);
  border: 1px solid var(--el-border-color-light);
  font-size: 0.85rem;
}
.orphaned-warning-box.has-orphans {
  background: rgba(245, 108, 108, 0.03);
  border-color: rgba(245, 108, 108, 0.15);
}
.orphaned-warning-box .warning-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 0.5rem;
}
.orphaned-warning-box.has-orphans .warning-title {
  color: var(--el-color-danger);
}
.orphaned-warning-box p {
  margin: 0 0 1rem 0;
  font-size: 0.8rem;
}

/* Tab 4: Backup Panel */
.backup-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}
.meta-card {
  padding: 1.25rem;
  border-radius: 8px;
  background: var(--bg-card, #fafafa);
  border: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.meta-item {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
}
.meta-item .lbl {
  color: var(--el-text-color-secondary);
}
.meta-item .val code {
  background: var(--bg-panel, #fff);
  padding: 0.15rem 0.35rem;
  border-radius: 3px;
  border: 1px solid var(--el-border-color-light);
}

.commands-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.command-box {
  padding: 1.25rem;
  border-radius: 8px;
  background: var(--bg-card, #f4f4f5);
  border: 1px solid var(--el-border-color-light);
}
.box-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.box-header h4 {
  margin: 0;
  font-size: 0.98rem;
  font-weight: 600;
  flex: 1;
}
.box-header .badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
}
.box-header .badge.docker {
  background: rgba(64, 158, 255, 0.12);
  color: var(--el-color-primary);
}
.box-header .badge.host {
  background: rgba(103, 194, 58, 0.12);
  color: var(--el-color-success);
}
.box-header .badge.danger {
  background: rgba(245, 108, 108, 0.12);
  color: var(--el-color-danger);
}
.command-box .desc {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
}
.code-container {
  background: #1e1e1e;
  padding: 0.75rem 1rem;
  border-radius: 5px;
  overflow-x: auto;
}
.code-container code {
  color: #dcdcdc;
  font-family: var(--font-mono, monospace);
  font-size: 0.85rem;
  white-space: pre-wrap;
  word-break: break-all;
}

.maintenance-box {
  background: var(--bg-card, #fafafa);
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}
.guideline-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.guideline-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}
.guideline-list .bullet {
  background: var(--el-color-primary);
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
  color: var(--el-text-color-regular);
  line-height: 1.4;
}

/* Animations */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.text-danger { color: var(--el-color-danger); }
.text-success { color: var(--el-color-success); }
.font-bold { font-weight: 600; }
.font-mono { font-family: var(--font-mono, monospace); }
.ml-2 { margin-left: 0.5rem; }
.mt-4 { margin-top: 1.5rem; }
</style>
