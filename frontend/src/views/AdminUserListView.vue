<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>用户管理</h1>
        <p>管理系统中的所有用户，支持信息修改、密码重置、账号启禁与角色权限提升。</p>
      </div>
      <el-button :icon="Refresh" circle :loading="loading" @click="load" />
    </div>

    <!-- Search / Filter Toolbar -->
    <div class="panel toolbar-panel">
      <div class="search-box">
        <el-input
          v-model="search"
          placeholder="搜索用户名、邮箱、昵称、学号..."
          clearable
          :prefix-icon="Search"
          @input="handleSearchInput"
          @clear="load"
        />
      </div>
    </div>

    <!-- User Table -->
    <div class="panel table-panel" v-loading="loading">
      <el-table :data="users" row-key="id" empty-text="未找到匹配的用户">
        <!-- User Identity Info -->
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-profile-cell">
              <el-avatar :size="36" :src="row.avatarUrl">
                {{ userFallback(row) }}
              </el-avatar>
              <div class="user-identity-text">
                <span class="username-title">{{ row.username }}</span>
                <span class="email-subtitle">{{ row.email || '未绑定邮箱' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- Display Name -->
        <el-table-column prop="displayName" label="昵称" min-width="150" />

        <!-- Academic Details -->
        <el-table-column label="学籍信息" min-width="180">
          <template #default="{ row }">
            <div v-if="row.studentNo || row.major">
              <div class="student-no-text">{{ row.studentNo || '-' }}</div>
              <div class="major-text muted">{{ row.major || '未填专业' }}</div>
            </div>
            <div v-else class="muted">-</div>
          </template>
        </el-table-column>

        <!-- Role Badge -->
        <el-table-column prop="role" label="角色" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'SUPER_ADMIN'" class="role-tag super-admin" effect="dark">
              超级管理员
            </el-tag>
            <el-tag v-else-if="row.role === 'ADMIN'" class="role-tag admin" type="warning" effect="dark">
              管理员
            </el-tag>
            <el-tag v-else class="role-tag student" type="success" effect="dark">
              学生
            </el-tag>
          </template>
        </el-table-column>

        <!-- Account Status -->
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small" effect="plain">正常</el-tag>
            <el-tag v-else type="danger" size="small" effect="plain">已禁用</el-tag>
          </template>
        </el-table-column>

        <!-- Date Created -->
        <el-table-column label="注册时间" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <!-- Actions -->
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button :icon="Edit" type="primary" size="small" circle @click="openEditDialog(row)" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Edit User Dialog -->
    <el-dialog
      v-model="editDialogVisible"
      title="修改用户信息"
      width="540px"
      destroy-on-close
      class="admin-user-dialog"
    >
      <el-form
        ref="formRef"
        :model="editForm"
        :rules="formRules"
        label-position="top"
        class="admin-form"
      >
        <div class="form-row-2">
          <el-form-item label="用户名" class="disabled-item">
            <el-input v-model="editForm.username" disabled />
          </el-form-item>
          <el-form-item label="昵称" prop="displayName">
            <el-input v-model="editForm.displayName" placeholder="昵称" />
          </el-form-item>
        </div>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="邮箱" />
        </el-form-item>

        <div class="form-row-2">
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="editForm.studentNo" placeholder="学号 (选填)" />
          </el-form-item>
          <el-form-item label="专业" prop="major">
            <el-input v-model="editForm.major" placeholder="专业 (选填)" />
          </el-form-item>
        </div>

        <div class="form-row-2">
          <el-form-item label="账号角色" prop="role">
            <el-select v-model="editForm.role" placeholder="选择角色" :disabled="isSelfEdit">
              <el-option label="学生" value="STUDENT" />
              <el-option label="管理员" value="ADMIN" />
              <el-option label="超级管理员" value="SUPER_ADMIN" />
            </el-select>
            <div v-if="isSelfEdit" class="self-edit-tip">不可修改自己的角色</div>
          </el-form-item>
          <el-form-item label="账号状态" prop="enabled">
            <div class="status-switch-container">
              <el-switch
                v-model="editForm.enabled"
                active-text="启用"
                inactive-text="禁用"
                :disabled="isSelfEdit"
              />
              <div v-if="isSelfEdit" class="self-edit-tip">不可禁用当前超级管理员</div>
            </div>
          </el-form-item>
        </div>

        <el-form-item label="重置密码" prop="password">
          <el-input
            v-model="editForm.password"
            type="password"
            show-password
            placeholder="留空表示不修改密码"
          />
          <div class="password-tip">如果需要重置该用户的登录密码，请输入新密码（至少 6 位），否则留空。</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-actions">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveUser">保存修改</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Edit, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { fetchAdminUsers, updateAdminUser } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/time'
import type { User } from '../types'

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const users = ref<User[]>([])
const search = ref('')

let debounceTimer: number | null = null

// Dialog state
const editDialogVisible = ref(false)
const formRef = ref<FormInstance | null>(null)

const editForm = reactive({
  id: 0,
  username: '',
  displayName: '',
  email: '',
  studentNo: '',
  major: '',
  role: 'STUDENT' as any,
  enabled: true,
  password: ''
})

const isSelfEdit = computed(() => {
  return editForm.id === authStore.user?.id
})

// Validation rules
const formRules = {
  displayName: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && value.length < 6) {
          callback(new Error('密码长度至少为 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

onMounted(() => {
  load()
})

onUnmounted(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
})

async function load() {
  loading.value = true
  try {
    users.value = await fetchAdminUsers(search.value)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearchInput() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = window.setTimeout(() => {
    load()
  }, 300)
}

function userFallback(user: User) {
  return (user.displayName || user.username || '?').substring(0, 1).toUpperCase()
}

function openEditDialog(user: User) {
  editForm.id = user.id
  editForm.username = user.username
  editForm.displayName = user.displayName || ''
  editForm.email = user.email || ''
  editForm.studentNo = user.studentNo || ''
  editForm.major = user.major || ''
  editForm.role = user.role
  editForm.enabled = user.enabled !== false
  editForm.password = ''

  editDialogVisible.value = true
}

async function saveUser() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      const payload: any = {
        displayName: editForm.displayName,
        email: editForm.email,
        studentNo: editForm.studentNo || null,
        major: editForm.major || null,
        role: editForm.role,
        enabled: editForm.enabled
      }
      if (editForm.password) {
        payload.password = editForm.password
      }

      await updateAdminUser(editForm.id, payload)
      ElMessage.success('保存成功')
      editDialogVisible.value = false
      load()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '修改失败')
    } finally {
      saving.value = false
    }
  })
}
</script>

<style scoped>
.toolbar-panel {
  padding: 14px;
  border: 1px solid #d8dee6;
  margin-bottom: 2px;
}
.search-box {
  max-width: 320px;
}
.user-profile-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-identity-text {
  display: flex;
  flex-direction: column;
}
.username-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.email-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.student-no-text {
  font-weight: 500;
}
.major-text {
  font-size: 12px;
}
.time-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.disabled-item :deep(.el-input__inner) {
  cursor: not-allowed;
}
.status-switch-container {
  display: flex;
  flex-direction: column;
  height: 40px;
  justify-content: center;
}
.self-edit-tip {
  font-size: 11px;
  color: var(--el-color-warning);
  margin-top: 4px;
  line-height: 1.2;
}
.password-tip {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.3;
  margin-top: 4px;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* Custom premium styles for roles */
.role-tag.super-admin {
  background: linear-gradient(135deg, #ff4e50 0%, #f9d423 100%);
  border: none;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(249, 212, 35, 0.2);
}
.role-tag.admin {
  background: linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%);
  border: none;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(109, 213, 237, 0.2);
}
.role-tag.student {
  font-weight: 500;
}
</style>
