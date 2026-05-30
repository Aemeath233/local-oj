<template>
  <el-config-provider :locale="zhCn">
    <!-- If logged in and on a mobile viewport (width <= 768px), intercept and show the premium PC Guidance Page -->
    <div v-if="isMobile && auth.isLoggedIn && router.currentRoute.value.path !== '/login'" class="mobile-guide-container">
      <div class="mobile-guide-card">
        <div class="logo-glow-wrapper">
          <img class="mobile-logo" src="/coderush_logo.png" alt="CodeRush Logo" />
        </div>
        <h2 class="mobile-title">CodeRush Online Judge</h2>
        <span class="mobile-badge">移动端工作台提示</span>
        
        <div class="mobile-content-box">
          <p class="mobile-text-p">
            👋 您好，<strong>{{ auth.user?.displayName || auth.user?.username }}</strong>！
          </p>
          <p class="mobile-text-main">
            为了确保极致的**代码编辑调试、实时编译沙箱运行**以及**多维度 ICPC/OI 排行榜单**体验，本系统专为**宽屏桌面端浏览器**设计与优化。
          </p>
          <div class="mobile-tip-bullet">
            <span>💻 <strong>Monaco Editor</strong>：全功能桌面级代码编程器</span>
            <span>⚡ <strong>多窗口调试</strong>：支持代码与测试样例并排评测</span>
            <span>📊 <strong>榜单追踪</strong>：大屏幕提供最完整的排名信息</span>
          </div>
          <p class="mobile-text-footer">
            请复制下方链接，并在您的电脑浏览器中打开，立即进入全功能算法评测空间！
          </p>
        </div>

        <div class="mobile-actions">
          <el-button type="primary" size="large" class="copy-url-btn" @click="copyPCUrl">
            复制电脑端访问链接
          </el-button>
          <el-button size="large" class="logout-btn" @click="logout">
            退出登录
          </el-button>
        </div>
      </div>
    </div>

    <!-- Normal App Shell for PC or non-logged-in mobile guest users (for login, register, reset-password) -->
    <el-container v-else class="app-shell">
      <el-header class="topbar">
        <div class="topbar-content">
          <RouterLink class="brand" to="/">
            <img class="brand-logo" src="/coderush_logo.png" alt="CodeRush Logo" />
            <span class="brand-name">CodeRush</span>
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
            <el-button :icon="themeStore.isDark ? Sunny : Moon" circle @click="themeStore.toggle" />
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
        <RouterView v-slot="{ Component }">
          <keep-alive :include="['HomeView', 'ProblemListView', 'TrainingListView', 'LeaderboardView', 'ContestListView', 'SubmissionListView']">
            <component :is="Component" />
          </keep-alive>
        </RouterView>
      </el-main>
    </el-container>
  </el-config-provider>
</template>
  
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElConfigProvider, ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { SwitchButton, Sunny, Moon } from '@element-plus/icons-vue'
import { SESSION_EXPIRED_EVENT } from './api/http'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'

const auth = useAuthStore()
const themeStore = useThemeStore()
const router = useRouter()

const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth <= 768
}

function copyPCUrl() {
  try {
    navigator.clipboard.writeText(window.location.origin)
    ElMessage.success("平台链接复制成功，快去电脑浏览器打开吧！")
  } catch (err) {
    // Fallback copy method
    const el = document.createElement('textarea')
    el.value = window.location.origin
    document.body.appendChild(el)
    el.select()
    document.execCommand('copy')
    document.body.removeChild(el)
    ElMessage.success("平台链接复制成功，快去电脑浏览器打开吧！")
  }
}

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

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onBeforeUnmount(() => {
  window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
/* Mobile Guide styling (Beautiful glassmorphism with radial glowing backdrop) */
.mobile-guide-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b0f19 0%, #111827 50%, #1e1b4b 100%);
  padding: 24px;
  box-sizing: border-box;
  font-family: system-ui, -apple-system, sans-serif;
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 99999;
  overflow-y: auto;
}

.mobile-guide-card {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 32px 24px;
  text-align: center;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4);
  color: #f3f4f6;
  box-sizing: border-box;
}

.logo-glow-wrapper {
  position: relative;
  width: 72px;
  height: 72px;
  margin: 0 auto 16px auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-glow-wrapper::before {
  content: '';
  position: absolute;
  width: 80px;
  height: 80px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.3) 0%, transparent 70%);
  z-index: 1;
}

.mobile-logo {
  width: 64px;
  height: 64px;
  object-fit: contain;
  position: relative;
  z-index: 2;
  filter: drop-shadow(0 4px 12px rgba(99, 102, 241, 0.2));
}

.mobile-title {
  font-size: 20px;
  font-weight: 850;
  margin: 0 0 6px 0;
  color: #fff;
  letter-spacing: -0.01em;
}

.mobile-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: #818cf8;
  background: rgba(129, 140, 248, 0.12);
  border: 1px solid rgba(129, 140, 248, 0.2);
  padding: 4px 12px;
  border-radius: 99px;
  margin-bottom: 24px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.mobile-content-box {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.04);
  border-radius: 12px;
  padding: 20px;
  text-align: left;
  margin-bottom: 28px;
}

.mobile-text-p {
  margin: 0 0 10px 0;
  font-size: 14.5px;
  color: #fff;
}

.mobile-text-main {
  margin: 0 0 16px 0;
  font-size: 13px;
  line-height: 1.6;
  color: #9ca3af;
}

.mobile-tip-bullet {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0 0 18px 0;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
  padding: 14px 0;
}

.mobile-tip-bullet span {
  font-size: 12px;
  color: #d1d5db;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mobile-text-footer {
  margin: 0;
  font-size: 12px;
  color: #818cf8;
  line-height: 1.5;
  font-weight: 500;
}

.mobile-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.copy-url-btn {
  width: 100%;
  font-weight: 600;
  background: #6366f1 !important;
  border-color: #6366f1 !important;
  color: #fff !important;
  height: 46px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.copy-url-btn:hover {
  background: #4f46e5 !important;
  border-color: #4f46e5 !important;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.3);
}

.logout-btn {
  width: 100%;
  font-weight: 600;
  background: transparent !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  color: #d1d5db !important;
  height: 46px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.05) !important;
  color: #fff !important;
}
</style>

