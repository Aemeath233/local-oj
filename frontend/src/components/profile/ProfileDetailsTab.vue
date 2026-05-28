<template>
  <el-form class="profile-form" label-position="top" :model="localForm">
    <div class="form-grid">
      <el-form-item label="用户名">
        <el-input v-model="localForm.username" disabled />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="localForm.email" disabled />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="localForm.displayName" maxlength="128" show-word-limit />
      </el-form-item>
      <el-form-item label="学号">
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
</template>

<script setup lang="ts">
import { ref, watch, reactive } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { updateProfile } from '../../api/http'
import type { User } from '../../types'

const props = defineProps<{
  form: User
}>()

const emit = defineEmits<{
  (e: 'profile-updated', profile: User): void
}>()

const saving = ref(false)

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
    const profile = await updateProfile({
      displayName: localForm.displayName,
      studentNo: localForm.studentNo || undefined,
      major: localForm.major || undefined
    })
    emit('profile-updated', profile)
    ElMessage.success('资料已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
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

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 16px;
}
</style>
