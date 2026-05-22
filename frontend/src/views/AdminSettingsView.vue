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
      </el-tabs>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import {
  fetchSandboxSettings,
  fetchSmtpSettings,
  updateSandboxSettings,
  updateSmtpSettings
} from '../api/http'

const loading = ref(false)
const savingSmtp = ref(false)
const savingSandbox = ref(false)
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



const smtpPasswordPlaceholder = computed(() => (smtpPasswordSet.value ? '已保存，留空不修改' : ''))


onMounted(load)

async function load() {
  loading.value = true
  try {
    const [smtp, sandbox] = await Promise.all([
      fetchSmtpSettings(),
      fetchSandboxSettings()
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

  } finally {
    loading.value = false
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


</script>
