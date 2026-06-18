<template>
  <section class="panel submissions-panel" v-loading="loading">
    <div class="panel-header">
      <div class="header-icon-box sub-icon">
        <el-icon><Message /></el-icon>
      </div>
      <div>
        <h2>全站提交动态</h2>
        <p class="muted">最近 5 次提交结果实时展示</p>
      </div>
      <RouterLink to="/submissions" class="more-link">
        <el-button size="small" circle :icon="ArrowRight" />
      </RouterLink>
    </div>

    <div class="home-sub-list">
      <div v-if="recentSubmissions.length === 0" class="empty-state">
        <el-empty description="暂无公共提交记录" :image-size="50" />
      </div>
      <div
        v-for="sub in recentSubmissions"
        :key="sub.id"
        class="home-sub-row"
      >
        <div class="sub-row-user">
          <el-avatar :size="22" :src="sub.avatarUrl">
            {{ (sub.displayName || sub.username || 'U').slice(0, 1).toUpperCase() }}
          </el-avatar>
          <span class="sub-user-nick">{{ sub.displayName || sub.username }}</span>
        </div>
        <div class="sub-row-problem">
          <span class="sub-prob-text">
            {{ sub.problemTitle || `题目 #${sub.problemId}` }}
          </span>
        </div>
        <div class="sub-row-lang">
          <span class="sub-lang-text">{{ sub.language }}</span>
        </div>
        <div class="sub-row-verdict">
          <VerdictTag :status="sub.status" :verdict="sub.verdict" />
        </div>
        <div class="sub-row-time">
          <span class="sub-time-text">{{ formatRelativeTime(sub.createdAt) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Message, ArrowRight } from '@element-plus/icons-vue'
import { fetchSubmissions } from '../../api/submission'
import { formatRelativeTime } from '../../utils/time'
import type { SubmissionSummary } from '../../types'
import VerdictTag from '../VerdictTag.vue'

const loading = ref(false)
const recentSubmissions = ref<SubmissionSummary[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const submissionsData = await fetchSubmissions()
    recentSubmissions.value = (submissionsData || []).slice(0, 5)
  } catch (error: any) {
    console.error('Failed to load recent submissions', error)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.panel {
  border-radius: 16px !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: var(--shadow-sm) !important;
  background: var(--bg-surface) !important;
  padding: 24px !important;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.panel:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md) !important;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  position: relative;
}

.header-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.sub-icon {
  background: #f0fdfa;
  color: #0d9488;
  border: 1px solid #ccfbf1;
}

.panel-header h2 {
  font-size: 1.15rem;
  font-weight: 750;
  color: var(--text-primary);
  margin: 0;
  text-align: left;
}

.panel-header .muted {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 3px 0 0 0;
  font-weight: 500;
  text-align: left;
}

.more-link {
  margin-left: auto;
}

.home-sub-list {
  display: flex;
  flex-direction: column;
}

.home-sub-row {
  display: grid;
  grid-template-columns: 140px minmax(120px, 1fr) 80px 100px 90px;
  align-items: center;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--border-light);
  gap: 8px;
}

.home-sub-row:hover {
  background: var(--bg-app);
}

.home-sub-row:last-child {
  border-bottom: none;
}

.sub-row-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sub-user-nick {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sub-row-problem {
  min-width: 0;
  text-align: left;
}

.sub-prob-text {
  font-size: 0.88rem;
  font-weight: 650;
  color: var(--text-primary);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.sub-row-lang {
  font-family: var(--font-mono), monospace;
  font-size: 0.75rem;
  color: var(--text-muted);
  font-weight: 600;
  text-align: center;
}

.sub-lang-text {
  background: var(--bg-app);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
}

.sub-row-verdict {
  display: flex;
  justify-content: flex-start;
}

.sub-row-time {
  text-align: right;
}

.sub-time-text {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-weight: 500;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
}
html.dark .sub-icon {
  background: rgba(13, 148, 136, 0.1) !important;
  color: #2dd4bf !important;
  border-color: rgba(13, 148, 136, 0.2) !important;
}
</style>
