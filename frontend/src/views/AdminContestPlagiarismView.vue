<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>比赛代码查重</h1>
        <p>基于 JPlag 的本地代码相似性分析检测面板</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="router.push('/admin/contests')">返回列表</el-button>
        <el-button
          type="warning"
          :icon="Cpu"
          :loading="checking"
          @click="startPlagiarismCheck"
        >
          一键运行代码查重
        </el-button>
      </div>
    </div>

    <!-- Plagiarism Result Table -->
    <section class="panel table-panel" v-loading="loading">
      <el-empty
        v-if="problems.length === 0"
        description="本场比赛尚无题目关联或查重记录"
      />
      <el-table v-else :data="problems" row-key="problemId">
        <el-table-column prop="sequence" label="#" width="76" align="center">
          <template #default="{ row }">
            <el-tag effect="dark" type="info" class="seq-tag">{{ row.sequence }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题目" min-width="200">
          <template #default="{ row }">
            <div class="problem-meta">
              <span class="problem-title">{{ row.problemTitle }}</span>
              <span class="problem-slug">({{ row.problemSlug }})</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="C/C++ (cpp)" min-width="160">
          <template #default="{ row }">
            <div class="family-status">
              <span v-if="!getCheck(row, 'cpp')" class="text-secondary">-</span>
              <div v-else>
                <div v-if="getCheck(row, 'cpp').status === 'RUNNING'" class="status-running">
                  <el-icon class="is-loading"><Loading /></el-icon> 正在运行...
                </div>
                <div v-else-if="getCheck(row, 'cpp').status === 'FAILED'" class="status-failed">
                  <el-tooltip :content="getCheck(row, 'cpp').errorMessage || '运行失败'" placement="top">
                    <el-tag type="danger" size="small">检测失败</el-tag>
                  </el-tooltip>
                </div>
                <div v-else-if="getCheck(row, 'cpp').status === 'COMPLETED'">
                  <el-tag :type="getSimilarityType(getCheck(row, 'cpp').maxSimilarity)" size="large" class="score-tag">
                    {{ formatSimilarity(getCheck(row, 'cpp').maxSimilarity) }}
                  </el-tag>
                  <el-button
                    type="primary"
                    link
                    size="small"
                    class="mt-1"
                    @click="openReport(row.problemId, 'cpp')"
                  >
                    查看报告
                  </el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Java (java)" min-width="160">
          <template #default="{ row }">
            <div class="family-status">
              <span v-if="!getCheck(row, 'java')" class="text-secondary">-</span>
              <div v-else>
                <div v-if="getCheck(row, 'java').status === 'RUNNING'" class="status-running">
                  <el-icon class="is-loading"><Loading /></el-icon> 正在运行...
                </div>
                <div v-else-if="getCheck(row, 'java').status === 'FAILED'" class="status-failed">
                  <el-tooltip :content="getCheck(row, 'java').errorMessage || '运行失败'" placement="top">
                    <el-tag type="danger" size="small">检测失败</el-tag>
                  </el-tooltip>
                </div>
                <div v-else-if="getCheck(row, 'java').status === 'COMPLETED'">
                  <el-tag :type="getSimilarityType(getCheck(row, 'java').maxSimilarity)" size="large" class="score-tag">
                    {{ formatSimilarity(getCheck(row, 'java').maxSimilarity) }}
                  </el-tag>
                  <el-button
                    type="primary"
                    link
                    size="small"
                    class="mt-1"
                    @click="openReport(row.problemId, 'java')"
                  >
                    查看报告
                  </el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Python (python3)" min-width="160">
          <template #default="{ row }">
            <div class="family-status">
              <span v-if="!getCheck(row, 'python3')" class="text-secondary">-</span>
              <div v-else>
                <div v-if="getCheck(row, 'python3').status === 'RUNNING'" class="status-running">
                  <el-icon class="is-loading"><Loading /></el-icon> 正在运行...
                </div>
                <div v-else-if="getCheck(row, 'python3').status === 'FAILED'" class="status-failed">
                  <el-tooltip :content="getCheck(row, 'python3').errorMessage || '运行失败'" placement="top">
                    <el-tag type="danger" size="small">检测失败</el-tag>
                  </el-tooltip>
                </div>
                <div v-else-if="getCheck(row, 'python3').status === 'COMPLETED'">
                  <el-tag :type="getSimilarityType(getCheck(row, 'python3').maxSimilarity)" size="large" class="score-tag">
                    {{ formatSimilarity(getCheck(row, 'python3').maxSimilarity) }}
                  </el-tag>
                  <el-button
                    type="primary"
                    link
                    size="small"
                    class="mt-1"
                    @click="openReport(row.problemId, 'python3')"
                  >
                    查看报告
                  </el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- Tips card -->
    <div class="maintenance-box mt-4">
      <div class="section-title">
        <el-icon><InfoFilled /></el-icon> 代码相似性检测说明
      </div>
      <ul class="guideline-list">
        <li>
          <span class="bullet">1</span>
          <span class="text">本查重功能直接基于本地 JPlag 分析引擎离线运行，数据不上传第三方服务器。</span>
        </li>
        <li>
          <span class="bullet">2</span>
          <span class="text">系统采用语义分析，更改变量名、增删注释、修改缩进均无法逃避重复度计算。</span>
        </li>
        <li>
          <span class="bullet">3</span>
          <span class="text">分析时会自动挑选出每位选手在该题目下的最新一次有效提交（过滤掉未完结的判题状态）。</span>
        </li>
        <li>
          <span class="bullet">4</span>
          <span class="text">对于某题的某一编程语言家族，如果在此场比赛中只有不到2名选手的有效提交，则该语言会自动跳过分析。</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled, Loading } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import { http } from '../api/base'

const route = useRoute()
const router = useRouter()
const contestId = Number(route.params.id)

const loading = ref(false)
const checking = ref(false)
const problems = ref<any[]>([])
let pollTimer: any = null

onMounted(() => {
  loadStatus()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

async function loadStatus() {
  if (checking.value) return
  loading.value = true
  try {
    const res = await http.get(`/admin/plagiarism/status/${contestId}`)
    problems.value = res.data.data || []
    
    // Check if any check is currently RUNNING
    const hasRunning = problems.value.some(p => 
      p.checks && p.checks.some((c: any) => c.status === 'RUNNING')
    )
    checking.value = hasRunning
  } catch (error) {
    ElMessage.error('加载查重状态失败')
  } finally {
    loading.value = false
  }
}

async function pollStatus() {
  try {
    const res = await http.get(`/admin/plagiarism/status/${contestId}`)
    problems.value = res.data.data || []
    
    const hasRunning = problems.value.some(p => 
      p.checks && p.checks.some((c: any) => c.status === 'RUNNING')
    )
    checking.value = hasRunning
    if (!hasRunning) {
      stopPolling()
    }
  } catch (error) {
    // Ignore polling errors
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(pollStatus, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function startPlagiarismCheck() {
  checking.value = true
  try {
    const res = await http.post(`/admin/plagiarism/check/${contestId}`)
    ElMessage.success(res.data.data || '查重任务已成功拉起，请稍后刷新状态')
    startPolling()
  } catch (error: any) {
    checking.value = false
    ElMessage.error(error.response?.data?.message || '发起查重任务失败')
  }
}

function getCheck(row: any, family: string) {
  if (!row.checks) return null
  return row.checks.find((c: any) => c.family === family) || null
}

function formatSimilarity(sim: number | null) {
  if (sim === null || sim === undefined) return '-'
  return sim.toFixed(1) + '%'
}

function getSimilarityType(sim: number | null) {
  if (sim === null || sim === undefined) return 'info'
  if (sim >= 70.0) return 'danger'
  if (sim >= 50.0) return 'warning'
  return 'success'
}

function openReport(problemId: number, family: string) {
  const token = localStorage.getItem('coderush.token') || localStorage.getItem('coderushoj.token')
  if (token) {
    // Set the cookie under the report path
    document.cookie = `coderushoj.token=${token}; path=/api/admin/plagiarism/report/; SameSite=Strict; Secure`;
  }
  
  const reportUrl = `/api/admin/plagiarism/report/${contestId}/${problemId}/${family}/index.html`
  window.open(reportUrl, '_blank')
}
</script>

<style scoped>
.seq-tag {
  font-weight: bold;
  font-family: monospace;
}

.problem-meta {
  display: flex;
  flex-direction: column;
}

.problem-title {
  font-weight: bold;
  font-size: 0.9rem;
  color: var(--el-text-color-primary);
}

.problem-slug {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}

.family-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.status-running {
  color: var(--el-color-warning);
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-failed {
  cursor: pointer;
}

.score-tag {
  font-family: monospace;
  font-weight: bold;
}

.maintenance-box {
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
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
  color: var(--el-color-primary);
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
  background: var(--el-color-primary);
  color: #fff;
  font-size: 0.75rem;
  font-weight: bold;
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
  line-height: 1.5;
}

.mt-4 {
  margin-top: 1.5rem;
}

.mt-1 {
  margin-top: 4px;
}
</style>
