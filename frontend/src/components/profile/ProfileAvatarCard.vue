<template>
  <div class="profile-card panel">
    <el-avatar :size="92" :src="form.avatarUrl">
      {{ avatarFallback }}
    </el-avatar>
    <div class="profile-name">
      <strong>{{ form.displayName || form.username }}</strong>
      <span>{{ form.username }}</span>
    </div>
    <input
      ref="avatarInput"
      class="visually-hidden"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      @change="onAvatarSelected"
    />
    <el-button :icon="Upload" :loading="uploading" @click="openAvatarPicker">更换头像</el-button>

    <!-- Interactive Avatar Cropping Dialog -->
    <AvatarCropperDialog
      v-model="cropperVisible"
      :image-file="selectedFile"
      @crop="onAvatarCropped"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AvatarCropperDialog from '../AvatarCropperDialog.vue'
import { uploadAvatar } from '../../api/http'
import type { User } from '../../types'

const props = defineProps<{
  form: User
}>()

const emit = defineEmits<{
  (e: 'profile-updated', profile: User): void
}>()

const uploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const cropperVisible = ref(false)
const selectedFile = ref<File | null>(null)

const avatarFallback = computed(() => {
  return (props.form.displayName || props.form.username || 'U').slice(0, 1).toUpperCase()
})

function openAvatarPicker() {
  avatarInput.value?.click()
}

function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    input.value = ''
    return
  }
  
  selectedFile.value = file
  cropperVisible.value = true
  input.value = ''
}

async function onAvatarCropped(croppedFile: File) {
  cropperVisible.value = false
  uploading.value = true
  try {
    const profile = await uploadAvatar(croppedFile)
    emit('profile-updated', profile)
    ElMessage.success('头像已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '头像上传失败')
  } finally {
    uploading.value = false
    selectedFile.value = null
  }
}
</script>

<style scoped>
.profile-card {
  width: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  box-sizing: border-box;
}

.profile-name {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.profile-name strong {
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.profile-name span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}
</style>
