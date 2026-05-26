<template>
  <el-container class="app-shell">
    <el-header class="topbar">
      <div class="topbar-content">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">OJ</span>
          <span>Local Judge</span>
        </RouterLink>

        <nav class="nav">
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/problems">题库</RouterLink>
          <RouterLink to="/training">专项练习</RouterLink>
          <RouterLink to="/leaderboard">排行榜</RouterLink>
          <RouterLink to="/contests">比赛</RouterLink>
          <RouterLink v-if="auth.isLoggedIn" to="/submissions">提交</RouterLink>
          <RouterLink v-if="auth.isAdmin" to="/admin">管理</RouterLink>
        </nav>

        <div class="account">
          <template v-if="auth.isLoggedIn">
            <RouterLink class="profile-link" to="/profile">
              <el-avatar :size="30" :src="auth.user?.avatarUrl">
                {{ avatarFallback }}
              </el-avatar>
              <span class="username">{{ auth.user?.displayName || auth.user?.username }}</span>
            </RouterLink>
            <el-button :icon="SwitchButton" circle @click="logout" />
          </template>
          <RouterLink v-else to="/login">
            <el-button type="primary">登录</el-button>
          </RouterLink>
        </div>
      </div>
    </el-header>

    <el-main class="main">
      <RouterView />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { SESSION_EXPIRED_EVENT } from './api/http'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const avatarFallback = computed(() => (auth.user?.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())

function logout() {
  auth.logout()
  router.push('/login')
}

function handleSessionExpired(event: Event) {
  auth.logout()
  const message = event instanceof CustomEvent && typeof event.detail === 'string'
    ? event.detail
    : '登录已过期，请重新登录'
  ElMessage.warning(message)
  router.push({
    path: '/login',
    query: { redirect: router.currentRoute.value.fullPath }
  })
}

window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)

onBeforeUnmount(() => {
  window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
})
</script>
