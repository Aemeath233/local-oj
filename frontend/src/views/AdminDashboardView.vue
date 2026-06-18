<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>管理总览</h1>
        <p>题目、提交和判题队列状态。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid" v-loading="loading">
      <div class="stat-tile">
        <span>题目</span>
        <strong>{{ dashboard?.problemCount ?? 0 }}</strong>
        <small>{{ dashboard?.visibleProblemCount ?? 0 }} 可见</small>
      </div>
      <div class="stat-tile">
        <span>用户</span>
        <strong>{{ dashboard?.userCount ?? 0 }}</strong>
        <small>已启用账户</small>
      </div>
      <div class="stat-tile">
        <span>提交</span>
        <strong>{{ dashboard?.submissionCount ?? 0 }}</strong>
        <small>{{ acRate }} AC</small>
      </div>
      <div class="stat-tile" :class="{ attention: unfinishedCount > 0 }">
        <span>队列</span>
        <strong>{{ unfinishedCount }}</strong>
        <small>Pending / Running</small>
      </div>
    </div>

    <div class="admin-grid">
      <section class="panel table-panel">
        <div class="panel-toolbar">
          <div>
            <h2>最近提交</h2>
            <p class="muted">最近 10 条判题记录</p>
          </div>
          <RouterLink to="/admin/submissions">
            <el-button :icon="ArrowRight" circle />
          </RouterLink>
        </div>
        <el-table :data="dashboard?.recentSubmissions ?? []" row-key="id">
          <el-table-column prop="id" label="#" width="76" />
          <el-table-column label="题目" min-width="180">
            <template #default="{ row }">
              <span>{{ row.problemTitle || `#${row.problemId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用户" min-width="130">
            <template #default="{ row }">
              <span>{{ row.displayName || row.username || `#${row.userId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="118">
            <template #default="{ row }">
              <VerdictTag :status="row.status" :verdict="row.verdict" />
            </template>
          </el-table-column>
          <el-table-column prop="score" label="分数" width="88" />
        </el-table>
      </section>

      <section class="panel verdict-panel">
        <div class="panel-toolbar">
          <div>
            <h2>Verdict</h2>
            <p class="muted">结果分布</p>
          </div>
        </div>
        <div class="verdict-list">
          <div v-for="item in verdictItems" :key="item.label" class="verdict-row">
            <VerdictTag status="FINISHED" :verdict="item.label" />
            <span>{{ item.count }}</span>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import AdminNav from '../components/AdminNav.vue'
import VerdictTag from '../components/VerdictTag.vue'
import { fetchAdminDashboard } from '../api/admin'
import type { AdminDashboard, Verdict } from '../types'

const loading = ref(false)
const dashboard = ref<AdminDashboard | null>(null)
const verdictOrder: Verdict[] = ['AC', 'WA', 'TLE', 'MLE', 'OLE', 'RE', 'CE', 'IE']

const unfinishedCount = computed(() => {
  return (dashboard.value?.pendingSubmissionCount ?? 0) + (dashboard.value?.runningSubmissionCount ?? 0)
})

const acRate = computed(() => {
  const total = dashboard.value?.submissionCount ?? 0
  if (total === 0) {
    return '0%'
  }
  return `${Math.round(((dashboard.value?.acceptedSubmissionCount ?? 0) / total) * 100)}%`
})

const verdictItems = computed(() => {
  const counts = dashboard.value?.verdictCounts
  return verdictOrder.map((label) => ({ label, count: counts?.[label] ?? 0 }))
})

async function load() {
  loading.value = true
  try {
    dashboard.value = await fetchAdminDashboard()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
