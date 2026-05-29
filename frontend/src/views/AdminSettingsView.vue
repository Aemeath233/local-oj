<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>系统设置</h1>
        <p>邮件和沙箱配置。</p>
      </div>
    </div>

    <section class="panel settings-panel" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="SMTP" name="smtp">
          <el-form class="admin-form settings-form" :model="smtpForm" label-position="top">
            <div class="form-grid">
              <el-form-item label="启用 SMTP">
                <el-switch v-model="smtpForm.enabled" />
              </el-form-item>
              <el-form-item label="SMTP 主机">
                <el-input v-model="smtpForm.host" />
              </el-form-item>
              <el-form-item label="端口">
                <el-input-number v-model="smtpForm.port" :min="1" :max="65535" />
              </el-form-item>
              <el-form-item label="发件邮箱">
                <el-input v-model="smtpForm.fromAddress" />
              </el-form-item>
              <el-form-item label="发件名称">
                <el-input v-model="smtpForm.fromName" />
              </el-form-item>
              <el-form-item label="SMTP 认证">
                <el-switch v-model="smtpForm.authEnabled" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input v-model="smtpForm.username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="smtpForm.password" type="password" show-password :placeholder="smtpPasswordPlaceholder" />
              </el-form-item>
              <el-form-item label="SSL">
                <el-switch v-model="smtpForm.useSsl" />
              </el-form-item>
              <el-form-item label="STARTTLS">
                <el-switch v-model="smtpForm.useStarttls" />
              </el-form-item>
            </div>
            <div class="form-actions">
              <el-button type="primary" :icon="Check" :loading="savingSmtp" @click="saveSmtp">保存 SMTP</el-button>
            </div>
          </el-form>
        </el-tab-pane>



        <el-tab-pane label="沙箱设置" name="sandbox">
          <el-form class="admin-form settings-form" :model="sandboxForm" label-position="top">
            <div class="form-grid">
              <el-form-item label="运行线程数量 (workerThreads)">
                <el-input-number v-model="sandboxForm.workerThreads" :min="1" />
              </el-form-item>
              <el-form-item label="最大并发运行数 (maxConcurrentRuns)">
                <el-input-number v-model="sandboxForm.maxConcurrentRuns" :min="1" />
              </el-form-item>
              <el-form-item label="编译超时限制 ms (compileTimeoutMs)">
                <el-input-number v-model="sandboxForm.compileTimeoutMs" :min="1000" :step="500" />
              </el-form-item>
              <el-form-item label="默认输出大小限制 KB (defaultOutputLimitKb)">
                <el-input-number v-model="sandboxForm.defaultOutputLimitKb" :min="1024" :step="1024" />
              </el-form-item>
              <el-form-item label="单任务进程数限制 (maxProcessCount)">
                <el-input-number v-model="sandboxForm.maxProcessCount" :min="1" />
              </el-form-item>
            </div>
            <div class="form-actions">
              <el-button type="primary" :icon="Check" :loading="savingSandbox" @click="saveSandbox">保存沙箱设置</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="网站设置 (CORS)" name="system">
          <el-form class="admin-form settings-form" label-position="top">
            <div style="margin-bottom: 16px;">
              <el-alert
                title="CORS 跨域安全配置说明"
                type="info"
                description="配置允许访问本 OJ 系统的域名或 IP。如果配置了独立域名或使用局域网 IP 访问时登录提示 CORS 错误，请在此处添加对应的域名或 IP。多个地址请用英文逗号分隔。支持通配符模式（例：http://*.yourdomain.com）。"
                show-icon
                :closable="false"
              />
            </div>
            <el-form-item label="允许访问的主机来源 (CORS Allowed Origins)" required>
              <div class="origins-list" style="display: flex; flex-direction: column; gap: 10px; max-width: 650px;">
                <div v-for="(origin, index) in originsList" :key="index" style="display: flex; gap: 10px; align-items: center; width: 100%;">
                  <el-input
                    v-model="originsList[index]"
                    placeholder="例如：http://your-domain.com 或 http://192.168.1.100:5173"
                    style="flex: 1;"
                  />
                  <el-button
                    type="danger"
                    plain
                    :icon="Delete"
                    circle
                    @click="removeOriginRow(index)"
                    :disabled="originsList.length <= 1 && originsList[0] === ''"
                  />
                </div>
                <div style="margin-top: 4px;">
                  <el-button type="success" plain size="small" :icon="Plus" @click="addOriginRow">
                    添加允许来源
                  </el-button>
                </div>
              </div>
            </el-form-item>
            <div class="form-actions">
              <el-button type="primary" :icon="Check" :loading="savingSystem" @click="saveSystem">保存网站设置</el-button>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import {
  fetchSandboxSettings,
  fetchSmtpSettings,
  fetchSystemSettings,
  updateSandboxSettings,
  updateSmtpSettings,
  updateSystemSettings
} from '../api/http'

const loading = ref(false)
const savingSmtp = ref(false)
const savingSandbox = ref(false)
const savingSystem = ref(false)
const smtpPasswordSet = ref(false)
const activeTab = ref('smtp')

const smtpForm = reactive({
  enabled: false,
  host: '',
  port: 587,
  username: '',
  password: '',
  fromAddress: '',
  fromName: 'Local Judge',
  authEnabled: true,
  useSsl: false,
  useStarttls: true
})

const sandboxForm = reactive({
  workerThreads: 4,
  maxConcurrentRuns: 4,
  compileTimeoutMs: 5000,
  defaultOutputLimitKb: 65536,
  maxProcessCount: 128
})

const originsList = ref<string[]>([''])

const smtpPasswordPlaceholder = computed(() => (smtpPasswordSet.value ? '已保存，留空不修改' : ''))

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [smtp, sandbox, system] = await Promise.all([
      fetchSmtpSettings(),
      fetchSandboxSettings(),
      fetchSystemSettings()
    ])
    smtpForm.enabled = smtp.enabled
    smtpForm.host = smtp.host || ''
    smtpForm.port = smtp.port || 587
    smtpForm.username = smtp.username || ''
    smtpForm.fromAddress = smtp.fromAddress || ''
    smtpForm.fromName = smtp.fromName || 'Local Judge'
    smtpForm.authEnabled = smtp.authEnabled
    smtpForm.useSsl = smtp.useSsl
    smtpForm.useStarttls = smtp.useStarttls
    smtpPasswordSet.value = smtp.passwordSet

    sandboxForm.workerThreads = sandbox.workerThreads
    sandboxForm.maxConcurrentRuns = sandbox.maxConcurrentRuns
    sandboxForm.compileTimeoutMs = sandbox.compileTimeoutMs
    sandboxForm.defaultOutputLimitKb = sandbox.defaultOutputLimitKb
    sandboxForm.maxProcessCount = sandbox.maxProcessCount

    const originsStr = system.allowedOrigins || ''
    if (originsStr.trim() === '') {
      originsList.value = ['']
    } else {
      originsList.value = originsStr.split(',').map(s => s.trim()).filter(s => s !== '')
      if (originsList.value.length === 0) {
        originsList.value = ['']
      }
    }
  } finally {
    loading.value = false
  }
}

function addOriginRow() {
  originsList.value.push('')
}

function removeOriginRow(index: number) {
  originsList.value.splice(index, 1)
  if (originsList.value.length === 0) {
    originsList.value.push('')
  }
}

async function saveSmtp() {
  savingSmtp.value = true
  try {
    const settings = await updateSmtpSettings({
      enabled: smtpForm.enabled,
      host: smtpForm.host,
      port: smtpForm.port,
      username: smtpForm.username,
      password: smtpForm.password || undefined,
      fromAddress: smtpForm.fromAddress,
      fromName: smtpForm.fromName,
      authEnabled: smtpForm.authEnabled,
      useSsl: smtpForm.useSsl,
      useStarttls: smtpForm.useStarttls
    })
    smtpForm.password = ''
    smtpPasswordSet.value = settings.passwordSet
    ElMessage.success('SMTP 已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingSmtp.value = false
  }
}

async function saveSandbox() {
  savingSandbox.value = true
  try {
    await updateSandboxSettings({
      workerThreads: sandboxForm.workerThreads,
      maxConcurrentRuns: sandboxForm.maxConcurrentRuns,
      compileTimeoutMs: sandboxForm.compileTimeoutMs,
      defaultOutputLimitKb: sandboxForm.defaultOutputLimitKb,
      maxProcessCount: sandboxForm.maxProcessCount
    })
    ElMessage.success('沙箱设置已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingSandbox.value = false
  }
}

async function saveSystem() {
  const cleanedOrigins = originsList.value
    .map(s => s.trim())
    .filter(s => s !== '')
    .join(',')

  savingSystem.value = true
  try {
    await updateSystemSettings({
      allowedOrigins: cleanedOrigins
    })
    ElMessage.success('网站设置已成功保存，跨域访问规则已即时生效！')
    if (cleanedOrigins === '') {
      originsList.value = ['']
    } else {
      originsList.value = cleanedOrigins.split(',')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingSystem.value = false
  }
}
</script>
