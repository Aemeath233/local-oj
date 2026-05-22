<template>
  <div class="admin-nav">
    <RouterLink to="/admin">总览</RouterLink>
    <RouterLink to="/admin/problems">题目</RouterLink>
    <RouterLink to="/admin/contests">比赛</RouterLink>
    <RouterLink to="/admin/submissions">提交</RouterLink>
    <RouterLink v-if="auth.isSuperAdmin" to="/admin/users">用户管理</RouterLink>
    <RouterLink v-if="auth.isSuperAdmin" to="/admin/settings">设置</RouterLink>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>
