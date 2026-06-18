<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>系统运维监控中心</h1>
        <p>实时监控底层容器、JVM 虚拟机、数据库连接池及分布式判题机的运行状态</p>
      </div>
      <div class="toolbar-actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新 (5s)"
          @change="toggleAutoRefresh"
          class="mr-3"
        />
        <el-button
          :icon="Refresh"
          type="primary"
          :loading="loading"
          @click="fetchStats"
        >
          手动刷新
        </el-button>
      </div>
    </div>

    <!-- Service Health Row -->
    <div class="metrics-grid">
      <!-- Backend Card -->
      <div class="health-card" :class="getHealthClass(stats?.redis?.status)">
        <div class="health-header">
          <span class="service-name">后端 API 服务</span>
          <span class="status-indicator"></span>
        </div>
        <div class="health-body">
          <div class="metric-value">在线</div>
          <div class="metric-desc">运行时间: {{ formatUptime(stats?.jvm?.uptime) }}</div>
        </div>
      </div>

      <!-- MySQL Pool Card -->
      <div class="health-card" :class="stats?.dbPool ? 'health-ok' : 'health-err'">
        <div class="health-header">
          <span class="service-name">数据库连接池 (Hikari)</span>
          <span class="status-indicator"></span>
        </div>
        <div class="health-body">
          <div class="metric-value">
            {{ stats?.dbPool?.activeConnections ?? 0 }} / {{ stats?.dbPool?.maxPoolSize ?? 0 }}
          </div>
          <div class="metric-desc">活动/最大连接 | 线程等待: {{ stats?.dbPool?.threadsAwaitingConnection ?? 0 }}</div>
        </div>
      </div>

      <!-- Redis Card -->
      <div class="health-card" :class="getHealthClass(stats?.redis?.status)">
        <div class="health-header">
          <span class="service-name">缓存服务 (Redis)</span>
          <span class="status-indicator"></span>
        </div>
        <div class="health-body">
          <div class="metric-value">
            <span v-if="stats?.redis?.status === 'UP'">{{ stats.redis.version }}</span>
            <span v-else>离线</span>
          </div>
          <div class="metric-desc">内存: {{ formatBytes(stats?.redis?.usedMemory) }} | 延迟: {{ stats?.redis?.pingMs ?? 0 }}ms</div>
        </div>
      </div>

      <!-- Sandbox Card -->
      <div class="health-card" :class="getHealthClass(stats?.goJudge?.status)">
        <div class="health-header">
          <span class="service-name">沙箱引擎 (go-judge)</span>
          <span class="status-indicator"></span>
        </div>
        <div class="health-body">
          <div class="metric-value">
            <span v-if="stats?.goJudge?.status === 'UP'">{{ stats.goJudge.version || '正常' }}</span>
            <span v-else>异常</span>
          </div>
          <div class="metric-desc">连接协议: HTTP REST | API 版本: v1</div>
        </div>
      </div>

      <!-- Queue Card -->
      <div class="health-card" :class="getHealthClass(stats?.queue?.status)">
        <div class="health-header">
          <span class="service-name">判题队列</span>
          <span class="status-indicator"></span>
        </div>
        <div class="health-body">
          <div class="metric-value">
            {{ stats?.queue?.size ?? 0 }}
            <span class="dlq-badge" v-if="(stats?.queue?.dlqSize ?? 0) > 0">
              死信: {{ stats.queue.dlqSize }}
            </span>
          </div>
          <div class="metric-desc">缓冲区积压 | DLQ 积压</div>
        </div>
      </div>
    </div>

    <!-- Main Load & Resource Section -->
    <div class="main-sections mt-4">
      <!-- Left Resource Gauges -->
      <div class="left-section">
        <div class="panel">
          <div class="panel-header">
            <h2>主机资源负载 (System)</h2>
          </div>
          
          <div class="gauges-wrapper">
            <!-- CPU Circular Gauge -->
            <div class="gauge-box">
              <svg class="progress-ring" width="120" height="120">
                <circle class="progress-ring__background" cx="60" cy="60" r="50" />
                <circle
                  class="progress-ring__circle progress-ring__cpu"
                  :stroke-dasharray="strokeDasharray"
                  :stroke-dashoffset="getCpuOffset"
                  cx="60" cy="60" r="50"
                />
              </svg>
              <div class="gauge-label">
                <span class="gauge-num">{{ formatPercentage(stats?.system?.systemCpuLoad) }}</span>
                <span class="gauge-name">系统 CPU</span>
              </div>
            </div>

            <!-- Memory Circular Gauge -->
            <div class="gauge-box">
              <svg class="progress-ring" width="120" height="120">
                <circle class="progress-ring__background" cx="60" cy="60" r="50" />
                <circle
                  class="progress-ring__circle progress-ring__mem"
                  :stroke-dasharray="strokeDasharray"
                  :stroke-dashoffset="getMemoryOffset"
                  cx="60" cy="60" r="50"
                />
              </svg>
              <div class="gauge-label">
                <span class="gauge-num">{{ formatPercentage(getMemoryUsageRatio) }}</span>
                <span class="gauge-name">系统内存</span>
              </div>
            </div>
          </div>

          <div class="system-details mt-3">
            <div class="detail-item">
              <span class="lbl">操作系统:</span>
              <span class="val">{{ stats?.system?.osName }} ({{ stats?.system?.osArch }})</span>
            </div>
            <div class="detail-item">
              <span class="lbl">内核版本:</span>
              <span class="val">{{ stats?.system?.osVersion }}</span>
            </div>
            <div class="detail-item">
              <span class="lbl">逻辑核心数:</span>
              <span class="val">{{ stats?.system?.availableProcessors }} Cores</span>
            </div>
            <div class="detail-item">
              <span class="lbl">物理内存:</span>
              <span class="val">
                已用 {{ formatBytes((stats?.system?.totalPhysicalMemory ?? 0) - (stats?.system?.freePhysicalMemory ?? 0)) }} 
                / 共 {{ formatBytes(stats?.system?.totalPhysicalMemory) }}
              </span>
            </div>
          </div>
        </div>

        <div class="panel mt-4">
          <div class="panel-header">
            <h2>磁盘容量状态 (Disk)</h2>
          </div>
          <div class="disk-usage-container">
            <div class="progress-bar-wrapper">
              <div class="progress-header">
                <span>{{ appDataRoot }} 数据分区</span>
                <span>{{ formatPercentage(getDiskUsageRatio) }}</span>
              </div>
              <div class="custom-progress">
                <div class="progress-fill" :style="{ width: formatPercentage(getDiskUsageRatio) }"></div>
              </div>
              <div class="progress-footer mt-1">
                <span>剩余: {{ formatBytes(stats?.system?.diskFree) }}</span>
                <span>总量: {{ formatBytes(stats?.system?.diskTotal) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right JVM details -->
      <div class="right-section">
        <div class="panel">
          <div class="panel-header">
            <h2>Java 虚拟机运行指标 (Backend JVM)</h2>
          </div>
          
          <div class="jvm-indicators mt-2">
            <!-- JVM memory bar -->
            <div class="indicator-group">
              <div class="progress-header">
                <span>堆内存使用 (Heap Memory)</span>
                <span>{{ formatBytes(stats?.jvm?.heapMemoryUsed) }} / {{ formatBytes(stats?.jvm?.heapMemoryMax) }}</span>
              </div>
              <div class="custom-progress progress-jvm">
                <div class="progress-fill" :style="{ width: formatPercentage(getJvmHeapRatio) }"></div>
              </div>
            </div>

            <div class="indicator-group mt-3">
              <div class="progress-header">
                <span>非堆内存使用 (Non-Heap Memory)</span>
                <span>{{ formatBytes(stats?.jvm?.nonHeapMemoryUsed) }}</span>
              </div>
            </div>

            <div class="grid-2-cols mt-4">
              <div class="stat-card">
                <div class="stat-title">虚拟机 CPU 负载</div>
                <div class="stat-value">{{ formatPercentage(stats?.jvm?.processCpuLoad) }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-title">活跃线程 / 线程峰值</div>
                <div class="stat-value">{{ stats?.jvm?.threadCount ?? 0 }} / {{ stats?.jvm?.peakThreadCount ?? 0 }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-title">GC 收集次数</div>
                <div class="stat-value">{{ stats?.jvm?.gcCollectionCount ?? 0 }} 次</div>
              </div>
              <div class="stat-card">
                <div class="stat-title">GC 收集总耗时</div>
                <div class="stat-value">{{ stats?.jvm?.gcCollectionTime ?? 0 }} ms</div>
              </div>
            </div>

            <div class="detail-item mt-4">
              <span class="lbl">守护线程数:</span>
              <span class="val">{{ stats?.jvm?.daemonThreadCount }} Threads</span>
            </div>
            <div class="detail-item">
              <span class="lbl">启动时间:</span>
              <span class="val">{{ formatTimestamp(stats?.jvm?.startTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>



  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { http } from '../api/base'

const loading = ref(false)
const autoRefresh = ref(true)
const stats = ref<any>(null)
let refreshTimer: any = null

const appDataRoot = './data/oj'

// SVG Circular Gauge configurations
const strokeDasharray = 2 * Math.PI * 50 // 314.16

onMounted(() => {
  fetchStats()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})

async function fetchStats() {
  loading.value = true
  try {
    const res = await http.get('/admin/monitoring/stats')
    stats.value = res.data.data
  } catch (error) {
    ElMessage.error('获取监控数据失败')
  } finally {
    loading.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(fetchStats, 5000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

function toggleAutoRefresh(val: boolean) {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

// Compute Dash offsets for gauges
const getCpuOffset = computed(() => {
  const load = stats.value?.system?.systemCpuLoad ?? 0
  return strokeDasharray * (1 - load)
})

const getMemoryUsageRatio = computed(() => {
  const total = stats.value?.system?.totalPhysicalMemory ?? 0
  const free = stats.value?.system?.freePhysicalMemory ?? 0
  if (total === 0) return 0
  return (total - free) / total
})

const getMemoryOffset = computed(() => {
  const ratio = getMemoryUsageRatio.value
  return strokeDasharray * (1 - ratio)
})

const getDiskUsageRatio = computed(() => {
  const total = stats.value?.system?.diskTotal ?? 0
  const free = stats.value?.system?.diskFree ?? 0
  if (total === 0) return 0
  return (total - free) / total
})

const getJvmHeapRatio = computed(() => {
  const used = stats.value?.jvm?.heapMemoryUsed ?? 0
  const max = stats.value?.jvm?.heapMemoryMax ?? 1
  return used / max
})

// Formatting Helpers
function formatPercentage(val: number | null | undefined) {
  if (val === null || val === undefined) return '0.0%'
  return (val * 100).toFixed(1) + '%'
}

function formatBytes(bytes: number | null | undefined) {
  if (bytes === null || bytes === undefined || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatUptime(ms: number | null | undefined) {
  if (!ms) return '0s'
  const seconds = Math.floor(ms / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60

  const parts = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (minutes > 0) parts.push(`${minutes}m`)
  if (remainingSeconds > 0 || parts.length === 0) parts.push(`${remainingSeconds}s`)
  
  return parts.join(' ')
}

function formatTimestamp(ts: number | null | undefined) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

function getHealthClass(status: string | null | undefined) {
  if (status === 'UP') return 'health-ok'
  if (status === 'DOWN') return 'health-err'
  return 'health-warn'
}
</script>

<style scoped>
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.mr-3 {
  margin-right: 1rem;
}

/* Service Health Grid */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1.25rem;
  margin-top: 1.5rem;
}

.health-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transition: transform 0.2s, box-shadow 0.2s;
}

.health-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -2px rgba(0, 0, 0, 0.04);
}

.health-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.service-name {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.health-ok .status-indicator {
  background-color: var(--el-color-success);
  box-shadow: 0 0 8px var(--el-color-success);
}

.health-warn .status-indicator {
  background-color: var(--el-color-warning);
  box-shadow: 0 0 8px var(--el-color-warning);
}

.health-err .status-indicator {
  background-color: var(--el-color-danger);
  box-shadow: 0 0 8px var(--el-color-danger);
}

.health-body .metric-value {
  font-size: 1.6rem;
  font-weight: 750;
  color: var(--el-text-color-primary);
  font-family: 'Outfit', 'Inter', sans-serif;
  line-height: 1.2;
  display: flex;
  align-items: center;
  gap: 8px;
}

.health-body .metric-desc {
  font-size: 0.78rem;
  color: var(--el-text-color-secondary);
  margin-top: 0.35rem;
}

.dlq-badge {
  font-size: 0.7rem;
  background-color: rgba(245, 108, 108, 0.15);
  color: var(--el-color-danger);
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: bold;
}

/* Main Resource Sections Layout */
.main-sections {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 992px) {
  .main-sections {
    grid-template-columns: 1fr;
  }
}

/* Circular Gauges */
.gauges-wrapper {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 1.5rem 0;
}

.gauge-box {
  position: relative;
  width: 120px;
  height: 120px;
}

.progress-ring {
  transform: rotate(-90deg);
}

.progress-ring__background {
  fill: none;
  stroke: var(--el-fill-color-darker);
  stroke-width: 10;
}

.progress-ring__circle {
  fill: none;
  stroke-width: 10;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.35s;
}

.progress-ring__cpu {
  stroke: url(#cpu-grad);
  /* Fallback color */
  stroke: #6366f1; 
}

.progress-ring__mem {
  stroke: #a855f7;
}

.gauge-label {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.gauge-num {
  font-size: 1.25rem;
  font-weight: 750;
  color: var(--el-text-color-primary);
  font-family: 'Outfit', sans-serif;
}

.gauge-name {
  font-size: 0.7rem;
  color: var(--el-text-color-secondary);
}

/* Custom Progress Bars */
.progress-bar-wrapper {
  padding: 0.5rem 0;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: var(--el-text-color-regular);
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.custom-progress {
  height: 10px;
  background: var(--el-fill-color-darker);
  border-radius: 5px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #3b82f6);
  border-radius: 5px;
  transition: width 0.4s ease-out;
}

.progress-jvm .progress-fill {
  background: linear-gradient(90deg, #a855f7, #6366f1);
}

.progress-footer {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: var(--el-text-color-secondary);
}

/* Details and Info Items */
.system-details {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 1rem;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  padding: 0.4rem 0;
  border-bottom: 1px dashed var(--el-border-color-extra-light);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-item .lbl {
  color: var(--el-text-color-secondary);
}

.detail-item .val {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

/* JVM Details columns */
.grid-2-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.stat-card {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  padding: 0.9rem;
  border-radius: 8px;
  text-align: center;
}

.stat-title {
  font-size: 0.75rem;
  color: var(--el-text-color-secondary);
  margin-bottom: 0.4rem;
}

.stat-value {
  font-size: 1.2rem;
  font-weight: bold;
  color: var(--el-text-color-primary);
  font-family: 'Outfit', sans-serif;
}


</style>
