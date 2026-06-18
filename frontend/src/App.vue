<template>
  <el-config-provider :locale="zhCn">
    <!-- If logged in and on a mobile viewport (width <= 768px), intercept and show the premium PC Guidance Page -->
    <MobileGuide v-if="isMobile && auth.isLoggedIn && router.currentRoute.value.path.startsWith('/admin')" />

    <!-- Normal App Shell for PC or non-logged-in mobile guest users -->
    <el-container v-else class="app-shell" direction="vertical">
      <AppHeader />
  
      <el-main class="main">
        <RouterView v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive :include="['HomeView', 'ProblemListView', 'TrainingListView', 'LeaderboardView', 'ContestListView', 'SubmissionListView']">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </RouterView>
      </el-main>
    </el-container>
  </el-config-provider>
</template>
  
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { ElConfigProvider, ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { SESSION_EXPIRED_EVENT } from './api/base'
import { useAuthStore } from './stores/auth'

import MobileGuide from './components/layout/MobileGuide.vue'
import AppHeader from './components/layout/AppHeader.vue'

const auth = useAuthStore()
const router = useRouter()

const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth <= 768
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

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
  window.removeEventListener('resize', checkMobile)
})
</script>


