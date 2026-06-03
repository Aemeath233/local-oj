<template>
  <section class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <img class="login-logo" src="/coderush_logo.png" alt="CodeRush Logo" />
        <h2>CodeRush OJ</h2>
      </div>

      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top" @submit.prevent="submitLogin">
            <el-form-item label="用户名 / 邮箱">
              <el-input
                v-model="loginForm.username"
                placeholder="输入用户名或邮箱地址"
                autocomplete="username"
                @input="loginError = ''"
              />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="loginForm.password"
                type="password"
                autocomplete="current-password"
                show-password
                @input="loginError = ''"
              />
            </el-form-item>
            <el-alert v-if="loginError" :title="loginError" type="error" show-icon :closable="false" />
            <el-button class="full-button" type="primary" :loading="loginLoading" @click="submitLogin">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" label-position="top" @submit.prevent="submitRegister">
            <el-form-item label="邮箱">
              <div class="inline-field">
                <el-input v-model="registerForm.email" autocomplete="email" />
                <el-button :loading="codeLoading" :disabled="codeCountdown > 0" @click="sendCode">
                  {{ codeCountdown > 0 ? `${codeCountdown}s` : '验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="验证码">
              <el-input v-model="registerForm.code" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="3-32位，仅限字母、数字、下划线" autocomplete="username" />
              <div class="form-hint">用户名注册后<strong>每自然月最多修改 3 次</strong>，是你的唯一标识，请认真填写</div>
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="registerForm.displayName" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" autocomplete="new-password" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="registerForm.confirmPassword" type="password" autocomplete="new-password" show-password />
            </el-form-item>
            <el-alert v-if="registerError" :title="registerError" type="error" show-icon :closable="false" />
            <el-button class="full-button" type="primary" :loading="registerLoading" @click="submitRegister">
              注册
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="找回密码" name="reset">
          <el-form :model="resetForm" label-position="top" @submit.prevent="submitReset">
            <el-form-item label="密保邮箱">
              <div class="inline-field">
                <el-input v-model="resetForm.email" autocomplete="email" placeholder="请输入绑定的邮箱" />
                <el-button :loading="resetCodeLoading" :disabled="resetCodeCountdown > 0" @click="sendResetCode">
                  {{ resetCodeCountdown > 0 ? `${resetCodeCountdown}s` : '验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="验证码">
              <el-input v-model="resetForm.code" placeholder="输入邮箱验证码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="resetForm.newPassword" type="password" placeholder="至少 6 位新密码" autocomplete="new-password" show-password />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="resetForm.confirmPassword" type="password" placeholder="再次输入新密码" autocomplete="new-password" show-password />
            </el-form-item>
            <el-alert v-if="resetError" :title="resetError" type="error" show-icon :closable="false" />
            <el-button class="full-button" type="primary" :loading="resetLoading" @click="submitReset">
              重置密码并登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { requestRegisterCode, requestResetPasswordCode, resetPassword } from '../api/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const mode = ref('login')
const loginLoading = ref(false)
const registerLoading = ref(false)
const codeLoading = ref(false)
const loginError = ref('')
const registerError = ref('')
const codeCountdown = ref(0)
let countdownTimer: number | null = null

const resetLoading = ref(false)
const resetCodeLoading = ref(false)
const resetError = ref('')
const resetCodeCountdown = ref(0)
let resetCountdownTimer: number | null = null

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  email: '',
  code: '',
  username: '',
  displayName: '',
  password: '',
  confirmPassword: ''
})

const resetForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

onBeforeUnmount(() => {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
  }
  if (resetCountdownTimer !== null) {
    window.clearInterval(resetCountdownTimer)
  }
})

async function submitLogin() {
  loginLoading.value = true
  loginError.value = ''
  try {
    await auth.login(loginForm.username, loginForm.password)
    router.push(redirectTarget())
  } catch (error: any) {
    loginError.value = error.response?.data?.message
      || (error.code === 'ERR_NETWORK' ? '无法连接后端服务，请检查 API 代理或 CORS 配置' : '登录失败，请稍后重试')
  } finally {
    loginLoading.value = false
  }
}

async function sendCode() {
  codeLoading.value = true
  registerError.value = ''
  try {
    await requestRegisterCode(registerForm.email)
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    registerError.value = error.response?.data?.message || '验证码发送失败'
  } finally {
    codeLoading.value = false
  }
}

async function submitRegister() {
  if (registerForm.password !== registerForm.confirmPassword) {
    registerError.value = '两次输入的密码不一致'
    return
  }
  registerLoading.value = true
  registerError.value = ''
  try {
    await auth.register({
      email: registerForm.email,
      code: registerForm.code,
      username: registerForm.username,
      displayName: registerForm.displayName,
      password: registerForm.password
    })
    router.push(redirectTarget())
  } catch (error: any) {
    registerError.value = error.response?.data?.message || '注册失败'
  } finally {
    registerLoading.value = false
  }
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
  }
  countdownTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer !== null) {
      window.clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function sendResetCode() {
  if (!resetForm.email) {
    resetError.value = '请输入密保邮箱'
    return
  }
  resetCodeLoading.value = true
  resetError.value = ''
  try {
    await requestResetPasswordCode(resetForm.email)
    ElMessage.success('重置密码验证码已发送')
    startResetCountdown()
  } catch (error: any) {
    resetError.value = error.response?.data?.message || '验证码发送失败'
  } finally {
    resetCodeLoading.value = false
  }
}

function startResetCountdown() {
  resetCodeCountdown.value = 60
  if (resetCountdownTimer !== null) {
    window.clearInterval(resetCountdownTimer)
  }
  resetCountdownTimer = window.setInterval(() => {
    resetCodeCountdown.value -= 1
    if (resetCodeCountdown.value <= 0 && resetCountdownTimer !== null) {
      window.clearInterval(resetCountdownTimer)
      resetCountdownTimer = null
    }
  }, 1000)
}

async function submitReset() {
  if (!resetForm.email) {
    resetError.value = '请输入邮箱'
    return
  }
  if (!resetForm.code) {
    resetError.value = '请输入验证码'
    return
  }
  if (!resetForm.newPassword) {
    resetError.value = '请输入新密码'
    return
  }
  if (resetForm.newPassword.length < 6) {
    resetError.value = '新密码长度至少为 6 位'
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    resetError.value = '两次输入的密码不一致'
    return
  }
  
  resetLoading.value = true
  resetError.value = ''
  try {
    await resetPassword({
      email: resetForm.email,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    })
    ElMessage.success('密码重置成功，正在自动登录...')
    
    // Log in automatically
    await auth.login(resetForm.email, resetForm.newPassword)
    router.push(redirectTarget())
  } catch (error: any) {
    resetError.value = error.response?.data?.message || '密码重置失败'
  } finally {
    resetLoading.value = false
  }
}

function redirectTarget() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/')) {
    return redirect
  }
  return '/problems'
}
</script>
