<template>
  <div class="admin-nav-wrapper">
    <div class="admin-nav">
      <RouterLink to="/admin">总览</RouterLink>
      <RouterLink to="/admin/problems">题目</RouterLink>
      <RouterLink to="/admin/training">题单</RouterLink>
      <RouterLink to="/admin/contests">比赛</RouterLink>
      <RouterLink to="/admin/submissions">提交</RouterLink>
    </div>
    <template v-if="auth.isSuperAdmin">
      <div class="admin-nav-divider">
        <span class="divider-label">超级管理</span>
      </div>
      <div class="admin-nav admin-nav-super">
        <RouterLink to="/admin/logs">日志</RouterLink>
        <RouterLink to="/admin/users">用户管理</RouterLink>
        <RouterLink to="/admin/data">数据管理</RouterLink>
        <RouterLink to="/admin/monitoring">系统监控</RouterLink>
        <RouterLink to="/admin/settings">设置</RouterLink>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.admin-nav-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-nav-divider {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
}

.admin-nav-divider::before {
  content: '';
  width: 1px;
  height: 24px;
  background: linear-gradient(180deg, transparent, var(--border-color), transparent);
}

.divider-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.admin-nav-super {
  border-color: #fef3c7 !important;
  background: #fffdf5 !important;
}

.admin-nav-super .router-link-active {
  background: #fef3c7 !important;
  color: #b45309 !important;
}

.admin-nav-super a:hover {
  background: #fefce8 !important;
  color: #b45309 !important;
}

/* Dark mode overrides */
html.dark .admin-nav-super {
  border-color: rgba(217, 119, 6, 0.25) !important;
  background: rgba(217, 119, 6, 0.06) !important;
}

html.dark .admin-nav-super a {
  color: #fbbf24 !important;
}

html.dark .admin-nav-super .router-link-active {
  background: rgba(217, 119, 6, 0.25) !important;
  color: #fef08a !important;
}

html.dark .admin-nav-super a:hover {
  background: rgba(217, 119, 6, 0.15) !important;
  color: #fef08a !important;
}
</style>
