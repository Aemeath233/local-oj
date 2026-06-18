<template>
  <div class="panel statement-card">
    <div class="statement-body">
      <MarkdownView :source="contest.description || '*出题人太懒了，没有填写任何比赛说明。*'" />
    </div>
  </div>

  <!-- Registration Prompt Banner -->
  <div v-if="!isRegisteredOrAdmin" class="registration-prompt-card panel" style="margin-top: 20px; padding: 24px; background: var(--bg-muted); border: 1px solid var(--border-color); border-radius: var(--radius-lg); box-shadow: var(--shadow-md);">
    <h3 style="margin: 0 0 8px; font-size: 16px; color: var(--text-primary); font-weight: 700;">您尚未报名此场比赛</h3>
    <p style="margin: 0 0 16px; font-size: 13.5px; color: var(--text-muted); line-height: 1.6;">
      本场评测比赛包含特定隐藏评测题目，只有<b>报名参赛</b>的用户才能查看题目列表、在线提交评测代码，并实时刷新 ICPC/OI 赛制排行榜单。
    </p>
    <div style="display: flex; align-items: center; gap: 20px; flex-wrap: wrap;">
      <template v-if="!auth.isLoggedIn">
        <el-button
          type="primary"
          style="font-weight: 600; padding: 12px 24px; border-radius: var(--radius-md); font-size: 14px;"
          @click="router.push('/login')"
        >
          请先登录以报名参赛
        </el-button>
      </template>
      <template v-else>
        <el-button
          v-if="registrationStatus?.canRegister"
          type="primary"
          style="font-weight: 600; padding: 12px 24px; border-radius: var(--radius-md); font-size: 14px;"
          :loading="registering"
          @click="$emit('register')"
        >
          立即报名参赛
        </el-button>
        <el-tag v-else type="info" size="large" style="font-weight: 600; padding: 6px 14px; border-radius: var(--radius-md);">
          报名通道已关闭 (比赛已结束)
        </el-tag>
      </template>
      <span v-if="registrationStatus" style="font-size: 13.5px; color: var(--text-secondary); font-weight: 550; display: flex; align-items: center; gap: 6px;">
        <span style="display: inline-flex; align-items: center; color: var(--text-muted);">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 16px; height: 16px; margin-right: 4px;">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
          目前已有
        </span>
        <span style="font-size: 16px; font-weight: 700; color: var(--primary);">{{ registrationStatus.registrationCount }}</span>
        <span style="color: var(--text-muted);">人报名参赛</span>
      </span>
    </div>
  </div>

  <!-- Registered Info Banner -->
  <div v-else-if="!auth.isAdmin && registrationStatus?.registered" class="registration-success-card panel" style="margin-top: 20px; padding: 16px 20px; background: var(--bg-muted); border: 1px solid var(--border-color); border-radius: var(--radius-md); display: flex; align-items: center; justify-content: space-between;">
    <div style="display: flex; align-items: center; gap: 12px; color: var(--text-secondary); font-size: 13.5px;">
      <span style="font-weight: 600; color: var(--text-primary);">您已成功报名此场比赛</span>
      <span style="color: var(--border-color);">|</span>
      <span style="color: var(--text-muted); font-size: 12.5px;">报名时间: {{ formatFullTime(registrationStatus.registeredAt || '') }}</span>
    </div>
    <el-tag type="success" effect="light" style="font-weight: 600; border-radius: var(--radius-sm);">已参赛</el-tag>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import MarkdownView from '../MarkdownView.vue'
import { useAuthStore } from '../../stores/auth'
import type { Contest, ContestRegistrationStatus } from '../../types'

const props = defineProps<{
  contest: Contest
  registrationStatus: ContestRegistrationStatus | null
  isRegisteredOrAdmin: boolean
  registering: boolean
}>()

defineEmits<{
  (e: 'register'): void
}>()

const router = useRouter()
const auth = useAuthStore()

function formatFullTime(timeStr: string) {
  return new Date(timeStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.statement-card {
  padding: 32px 40px;
}
.statement-body {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-primary);
}
</style>
