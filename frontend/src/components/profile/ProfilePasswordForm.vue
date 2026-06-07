<template>
  <el-form class="profile-form" label-position="top" :model="passwordForm">
    <el-alert
      v-if="!email"
      title="当前账号未绑定邮箱，无法通过 SMTP 修改密码"
      type="warning"
      show-icon
      :closable="false"
    />
    <div class="password-grid">
      <el-form-item label="邮箱验证码">
        <div class="inline-field">
          <el-input v-model="passwordForm.code" />
          <el-button :loading="sendingCode" :disabled="codeCountdown > 0 || !email" @click="sendCode">
            {{ codeCountdown > 0 ? `${codeCountdown}s` : '验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" show-password />
      </el-form-item>
    </div>
    <div class="form-actions">
      <el-button type="primary" :icon="Lock" :loading="changingPassword" :disabled="!email" @click="submitPassword">
        修改密码
      </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { requestPasswordChangeCode, changePassword } from '../../api/http'

defineProps<{
  email?: string
}>()

const sendingCode = ref(false)
const changingPassword = ref(false)
const codeCountdown = ref(0)
let countdownTimer: number | undefined

const passwordForm = reactive({
  code: '',
  newPassword: '',
  confirmPassword: ''
})

async function sendCode() {
  sendingCode.value = true
  try {
    await requestPasswordChangeCode()
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function submitPassword() {
  if (!passwordForm.code || passwordForm.code.trim() === '') {
    ElMessage.warning('验证码不能为空')
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.trim() === '') {
    ElMessage.warning('新密码不能为空')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await changePassword({
      code: passwordForm.code,
      newPassword: passwordForm.newPassword
    })
    passwordForm.code = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('密码已修改')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '修改失败')
  } finally {
    changingPassword.value = false
  }
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
  countdownTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer)
      countdownTimer = undefined
    }
  }, 1000)
}

onUnmounted(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.profile-form {
  padding-top: 10px;
}

.password-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.inline-field {
  display: flex;
  gap: 10px;
  width: 100%;
}

.inline-field .el-input {
  flex: 1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 16px;
}
</style>
