<template>
  <section class="page-stack" v-loading="loading">
    <div class="page-heading">
      <div>
        <h1>个人主页</h1>
        <p>账号资料和安全设置</p>
      </div>
    </div>

    <section class="profile-layout">
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
      </div>

      <div class="profile-main panel">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="资料" name="profile">
            <el-form class="profile-form" label-position="top" :model="form">
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input v-model="form.username" disabled />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="form.email" disabled />
                </el-form-item>
                <el-form-item label="昵称">
                  <el-input v-model="form.displayName" maxlength="128" show-word-limit />
                </el-form-item>
                <el-form-item label="学号">
                  <el-input v-model="form.studentNo" maxlength="64" show-word-limit />
                </el-form-item>
                <el-form-item label="专业">
                  <el-input v-model="form.major" maxlength="128" show-word-limit />
                </el-form-item>
              </div>
              <div class="form-actions">
                <el-button type="primary" :icon="Check" :loading="saving" @click="saveProfile">保存资料</el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="password">
            <el-form class="profile-form" label-position="top" :model="passwordForm">
              <el-alert
                v-if="!form.email"
                title="当前账号未绑定邮箱，无法通过 SMTP 修改密码"
                type="warning"
                show-icon
                :closable="false"
              />
              <div class="password-grid">
                <el-form-item label="邮箱验证码">
                  <div class="inline-field">
                    <el-input v-model="passwordForm.code" />
                    <el-button :loading="sendingCode" :disabled="codeCountdown > 0 || !form.email" @click="sendCode">
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
                <el-button type="primary" :icon="Lock" :loading="changingPassword" :disabled="!form.email" @click="submitPassword">
                  修改密码
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="偏好设置" name="preferences">
            <el-form class="profile-form" label-position="top" style="margin-top: 10px;">
              <div class="form-grid">
                <el-form-item label="默认编程语言">
                  <el-select v-model="prefLanguage" style="width: 100%;">
                    <el-option label="C++17" value="CPP" />
                    <el-option label="C" value="C" />
                    <el-option label="Python 3" value="PYTHON" />
                    <el-option label="Java 21" value="JAVA" />
                  </el-select>
                </el-form-item>

                <el-form-item label="编辑器默认字号">
                  <el-select v-model="prefFontSize" style="width: 100%;">
                    <el-option v-for="size in fontSizes" :key="size" :label="size + 'px'" :value="size" />
                  </el-select>
                </el-form-item>

                <el-form-item label="编辑器默认字体">
                  <el-select v-model="prefFontFamily" style="width: 100%;">
                    <el-option v-for="font in fontFamilies" :key="font.value" :label="font.label" :value="font.value" />
                  </el-select>
                </el-form-item>
              </div>

              <!-- Double Column for Reader Typography Settings -->
              <div class="form-grid" style="margin-top: 14px; border-top: 1px solid #f1f5f9; padding-top: 14px;">
                <el-form-item label="题面默认字号">
                  <el-select v-model="prefReaderFontSize" style="width: 100%;">
                    <el-option v-for="size in readerFontSizes" :key="size" :label="size + 'px'" :value="size" />
                  </el-select>
                </el-form-item>

                <el-form-item label="题面默认字体">
                  <el-select v-model="prefReaderFontFamily" style="width: 100%;">
                    <el-option v-for="font in readerFontFamilies" :key="font.value" :label="font.label" :value="font.value" />
                  </el-select>
                </el-form-item>
              </div>

              <!-- Real-time Previews Grid (Editor & Reader side-by-side) -->
              <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 14px;">
                <!-- Quick Font Preview Box -->
                <div style="padding: 16px; border: 1px solid #d8dee6; border-radius: 8px; background: #fafbfc; box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.025);">
                  <span style="font-size: 13px; color: #64748b; font-weight: 600; display: block; margin-bottom: 8px; font-family: sans-serif;">💻 编辑器代码实时预览：</span>
                  <pre :style="{ fontFamily: prefFontFamily, fontSize: prefFontSize + 'px', margin: 0, lineHeight: 1.5, color: '#0f766e', overflowX: 'auto', background: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid #e2e8f0' }">#include &lt;iostream&gt;

int main() {
    std::cout &lt;&lt; "Hello Local OJ!" &lt;&lt; std::endl;
    return 0;
}</pre>
                </div>

                <!-- Quick Reader Preview Box -->
                <div style="padding: 16px; border: 1px solid #d8dee6; border-radius: 8px; background: #fafbfc; box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.025);">
                  <span style="font-size: 13px; color: #64748b; font-weight: 600; display: block; margin-bottom: 8px; font-family: sans-serif;">📖 题面排版实时预览：</span>
                  <div :style="{ fontFamily: prefReaderFontFamily, fontSize: prefReaderFontSize + 'px', margin: 0, lineHeight: 1.6, color: '#334155', overflowX: 'auto', background: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid #e2e8f0', minHeight: '120px' }">
                    <h3 style="margin: 0 0 6px; font-size: 1.2em;">A + B 问题</h3>
                    <p style="margin: 0 0 6px;">输入两个整数 $A$ 和 $B$，计算它们的和并输出。</p>
                    <strong style="color: #0f766e;">[输入格式]</strong> 一行输入两个整数，以空格分隔。
                  </div>
                </div>
              </div>

              <!-- Custom Code Templates Section -->
              <div style="margin-top: 20px; border-top: 1px solid #f1f5f9; padding-top: 20px;">
                <h3 style="margin: 0 0 12px 0; font-size: 1rem; font-weight: 600; color: var(--el-text-color-primary);">🛠️ 自定义各语言默认代码模板</h3>
                <p style="margin: 0 0 14px 0; font-size: 0.85rem; color: var(--el-text-color-secondary);">
                  您可以为不同语言编写个性化的默认初始化代码，当您打开新题目时系统将自动为您填充这些代码，省去手动编写初始结构的繁琐步骤。
                </p>

                <div style="display: flex; gap: 16px; align-items: flex-start; flex-direction: column;">
                  <div style="display: flex; gap: 12px; align-items: center; width: 100%; flex-wrap: wrap;">
                    <span style="font-size: 0.9rem; font-weight: 500;">选择编辑语言：</span>
                    <el-select v-model="templateLang" style="width: 140px;" @change="loadTemplateForLang">
                      <el-option label="C++17" value="CPP" />
                      <el-option label="C" value="C" />
                      <el-option label="Python 3" value="PYTHON" />
                      <el-option label="Java 21" value="JAVA" />
                    </el-select>
                    <el-button type="info" plain size="small" style="margin-left: auto;" @click="resetTemplateToDefault">恢复当前语言默认</el-button>
                  </div>

                  <div style="width: 100%; border: 1px solid #dcdfe6; border-radius: 8px; overflow: hidden; background: #fff;">
                    <CodeEditor v-model="currentTemplateText" :language="templateLang" style="height: 240px; min-height: 240px;" />
                  </div>
                </div>
              </div>

              <div class="form-actions" style="margin-top: 18px;">
                <el-button type="primary" :icon="Check" @click="savePreferences">保存偏好设置</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Check, Lock, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  changePassword,
  fetchProfile,
  requestPasswordChangeCode,
  updateProfile,
  uploadAvatar
} from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { User, Language } from '../types'
import CodeEditor from '../components/CodeEditor.vue'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const sendingCode = ref(false)
const changingPassword = ref(false)
const activeTab = ref('profile')
const codeCountdown = ref(0)
const avatarInput = ref<HTMLInputElement | null>(null)
let countdownTimer: number | undefined
const MAX_AVATAR_BYTES = 2 * 1024 * 1024
const AVATAR_MAX_SIDE = 512

// Editor Preferences Configurations
const fontSizes = [14, 15, 16, 18, 20]
const fontFamilies = [
  { label: 'JetBrains Mono', value: "'JetBrains Mono', 'Cascadia Code', Consolas, monospace" },
  { label: 'Fira Code', value: "'Fira Code', 'JetBrains Mono', Consolas, monospace" },
  { label: '系统等宽', value: "ui-monospace, 'Cascadia Code', Consolas, 'Courier New', monospace" }
]
const readerFontSizes = [15, 16, 18]
const readerFontFamilies = [
  { label: '系统无衬线', value: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif" },
  { label: '系统宋体', value: "'Songti SC', 'SimSun', Georgia, serif" },
  { label: '系统黑体', value: "'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif" }
]

const prefLanguage = ref(localStorage.getItem('localoj.editor.defaultLanguage') || 'CPP')
const prefFontSize = ref(Number(localStorage.getItem('localoj.editor.fontSize')) || 14)
const prefFontFamily = ref(localStorage.getItem('localoj.editor.fontFamily') || "'JetBrains Mono', 'Cascadia Code', Consolas, monospace")

const prefReaderFontSize = ref(Number(localStorage.getItem('localoj.reader.fontSize')) || 15)
const prefReaderFontFamily = ref(localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

const templateLang = ref<Language>('CPP')
const currentTemplateText = ref('')

function loadTemplateForLang() {
  const custom = localStorage.getItem(`localoj.template.${templateLang.value}`)
  if (custom !== null) {
    currentTemplateText.value = custom
  } else {
    currentTemplateText.value = defaultTemplateFor(templateLang.value)
  }
}

function defaultTemplateFor(value: Language) {
  if (value === 'PYTHON') {
    return 'a, b = map(int, input().split())\nprint(a + b)\n'
  }
  if (value === 'JAVA') {
    return 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        int a = scanner.nextInt();\n        int b = scanner.nextInt();\n        System.out.println(a + b);\n    }\n}\n'
  }
  if (value === 'C') {
    return '#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf("%d %d", &a, &b);\n    printf("%d\\n", a + b);\n    return 0;\n}\n'
  }
  return '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n\n    int a, b;\n    cin >> a >> b;\n    cout << a + b << "\\n";\n    return 0;\n}\n'
}

function resetTemplateToDefault() {
  currentTemplateText.value = defaultTemplateFor(templateLang.value)
  localStorage.removeItem(`localoj.template.${templateLang.value}`)
  ElMessage.success('已恢复为默认 A+B 模板')
}

function savePreferences() {
  localStorage.setItem('localoj.editor.defaultLanguage', prefLanguage.value)
  localStorage.setItem('localoj.editor.fontSize', String(prefFontSize.value))
  localStorage.setItem('localoj.editor.fontFamily', prefFontFamily.value)
  localStorage.setItem('localoj.reader.fontSize', String(prefReaderFontSize.value))
  localStorage.setItem('localoj.reader.fontFamily', prefReaderFontFamily.value)

  // Save current active language's template
  localStorage.setItem(`localoj.template.${templateLang.value}`, currentTemplateText.value)

  ElMessage.success('编辑器与偏好设置已成功保存！')
}

const form = reactive({
  id: 0,
  username: '',
  email: '',
  displayName: '',
  avatarUrl: '',
  studentNo: '',
  major: '',
  role: 'STUDENT'
})

const passwordForm = reactive({
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarFallback = computed(() => (form.displayName || form.username || 'U').slice(0, 1).toUpperCase())

onMounted(() => {
  loadProfile()
  loadTemplateForLang()
})
onUnmounted(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
})

async function loadProfile() {
  loading.value = true
  try {
    applyProfile(await fetchProfile())
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const profile = await updateProfile({
      displayName: form.displayName,
      studentNo: form.studentNo || undefined,
      major: form.major || undefined
    })
    applyProfile(profile)
    ElMessage.success('资料已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openAvatarPicker() {
  avatarInput.value?.click()
}

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const optimizedFile = await optimizeAvatar(file)
    const profile = await uploadAvatar(optimizedFile)
    applyProfile(profile)
    ElMessage.success('头像已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '头像上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function optimizeAvatar(file: File) {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  const image = await loadImage(file)
  const scale = Math.min(1, AVATAR_MAX_SIDE / Math.max(image.width, image.height))
  const width = Math.max(1, Math.round(image.width * scale))
  const height = Math.max(1, Math.round(image.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('浏览器无法处理头像')
  }
  context.drawImage(image, 0, 0, width, height)

  let lastBlob: Blob | null = null
  for (let quality = 0.86; quality >= 0.42; quality -= 0.08) {
    const blob = await canvasToBlob(canvas, 'image/webp', quality)
    lastBlob = blob
    if (blob.size <= MAX_AVATAR_BYTES) {
      return new File([blob], webpName(file.name), { type: 'image/webp' })
    }
  }
  if (lastBlob && lastBlob.size <= MAX_AVATAR_BYTES) {
    return new File([lastBlob], webpName(file.name), { type: 'image/webp' })
  }
  throw new Error('头像压缩后仍超过 2MB，请换一张图片')
}

function loadImage(file: File) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读取失败'))
    }
    image.src = url
  })
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('头像转换失败'))
        return
      }
      resolve(blob)
    }, type, quality)
  })
}

function webpName(filename: string) {
  const base = filename.replace(/\.[^.]+$/, '') || 'avatar'
  return `${base}.webp`
}

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

function applyProfile(profile: User) {
  form.id = profile.id
  form.username = profile.username
  form.email = profile.email || ''
  form.displayName = profile.displayName || profile.username
  form.avatarUrl = profile.avatarUrl || ''
  form.studentNo = profile.studentNo || ''
  form.major = profile.major || ''
  form.role = profile.role
  auth.setUser(profile)
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
</script>
