<template>
  <div>
    <el-form class="profile-form" label-position="top" :model="localForm">
      <div class="form-grid">
        <el-form-item label="用户名">
          <el-input v-model="localForm.username" :disabled="usernameChangeCount >= 3" maxlength="32" />
          <div class="username-tip" v-if="usernameChangeCount !== undefined">
            本月已修改 {{ usernameChangeCount }} 次 (剩余 {{ Math.max(0, 3 - usernameChangeCount) }} 次)
          </div>
        </el-form-item>
        <el-form-item label="邮箱">
          <div class="email-input-row">
            <el-input v-model="localForm.email" disabled />
            <el-button type="primary" plain @click="openEmailDialog">换绑邮箱</el-button>
          </div>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="localForm.displayName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="班级">
          <el-input v-model="localForm.studentNo" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="localForm.major" maxlength="128" show-word-limit />
        </el-form-item>
      </div>
      <div class="form-actions">
        <el-button type="primary" :icon="Check" :loading="saving" @click="saveProfile">保存资料</el-button>
      </div>
    </el-form>

    <!-- Email Change Dialog -->
    <el-dialog
      v-model="emailDialogVisible"
      title="换绑邮箱"
      width="440px"
      destroy-on-close
      class="premium-dialog"
    >
      <el-form :model="emailForm" :rules="emailRules" ref="emailFormRef" label-position="top">
        <el-form-item label="原邮箱">
          <el-input :value="props.form.email" disabled />
        </el-form-item>
        
        <el-form-item label="原邮箱验证码" prop="code">
          <div class="code-input-row">
            <el-input v-model="emailForm.code" placeholder="输入 6 位验证码" maxlength="6" />
            <el-button type="primary" :disabled="countdown > 0" @click="sendEmailCode" :loading="sendingCode">
              {{ countdown > 0 ? `${countdown} 秒后重试` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        
        <el-form-item label="新邮箱" prop="newEmail">
          <el-input v-model="emailForm.newEmail" placeholder="请输入新邮箱" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <div class="dialog-actions">
          <el-button @click="emailDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submittingEmail" @click="submitEmailChange">确认换绑</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, reactive, computed, onUnmounted } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { updateProfile, requestEmailChangeCode, changeEmail } from '../../api/http'
import type { User } from '../../types'

const props = defineProps<{
  form: User
}>()

const emit = defineEmits<{
  (e: 'profile-updated', profile: User): void
}>()

const saving = ref(false)

// Username changes this calendar month
const usernameChangeCount = computed(() => {
  return props.form.usernameChangeCountCurrentMonth !== undefined ? props.form.usernameChangeCountCurrentMonth : 0
})

// Create a local reactive copy to edit, sync on changes
const localForm = reactive({
  username: props.form.username || '',
  email: props.form.email || '',
  displayName: props.form.displayName || '',
  studentNo: props.form.studentNo || '',
  major: props.form.major || ''
})

watch(() => props.form, (newVal) => {
  localForm.username = newVal.username || ''
  localForm.email = newVal.email || ''
  localForm.displayName = newVal.displayName || ''
  localForm.studentNo = newVal.studentNo || ''
  localForm.major = newVal.major || ''
}, { deep: true })

async function saveProfile() {
  saving.value = true
  try {
    const payload: any = {
      displayName: localForm.displayName,
      studentNo: localForm.studentNo || undefined,
      major: localForm.major || undefined
    }
    // Only send username if changed
    if (localForm.username !== props.form.username) {
      payload.username = localForm.username
    }
    const profile = await updateProfile(payload)
    emit('profile-updated', profile)
    ElMessage.success('资料已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// --- Email Change Dialog State & Logic ---
const emailDialogVisible = ref(false)
const emailFormRef = ref<FormInstance | null>(null)
const sendingCode = ref(false)
const submittingEmail = ref(false)
const countdown = ref(0)
let timer: number | null = null

const emailForm = reactive({
  code: '',
  newEmail: ''
})

const emailRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newEmail: [
    { required: true, message: '请输入新邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

function openEmailDialog() {
  emailForm.code = ''
  emailForm.newEmail = ''
  emailDialogVisible.value = true
}

async function sendEmailCode() {
  sendingCode.value = true
  try {
    await requestEmailChangeCode()
    ElMessage.success('验证码已发送至原邮箱，请注意查收')
    countdown.value = 60
    timer = window.setInterval(() => {
      if (countdown.value > 0) {
        countdown.value--
      } else {
        if (timer) {
          clearInterval(timer)
          timer = null
        }
      }
    }, 1000)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function submitEmailChange() {
  if (!emailFormRef.value) return
  await emailFormRef.value.validate(async (valid) => {
    if (!valid) return
    submittingEmail.value = true
    try {
      const updatedUser = await changeEmail({
        newEmail: emailForm.newEmail.trim(),
        code: emailForm.code.trim()
      })
      emit('profile-updated', updatedUser)
      ElMessage.success('邮箱换绑成功！')
      emailDialogVisible.value = false
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '换绑失败')
    } finally {
      submittingEmail.value = false
    }
  })
}

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
.profile-form {
  padding-top: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.email-input-row {
  display: flex;
  width: 100%;
  gap: 8px;
}

.code-input-row {
  display: flex;
  width: 100%;
  gap: 8px;
}

.username-tip {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.2;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 16px;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
