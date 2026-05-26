<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>标签字典管理</h1>
        <p>维护系统题目的规范标签库。普通管理员可查看标签，超级管理员可增删改。</p>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="isSuperAdmin" type="primary" :icon="Plus" @click="openCreateDialog">添加标签</el-button>
        <el-button :icon="Refresh" circle :loading="loading" @click="load" />
      </div>
    </div>

    <!-- Tags Table -->
    <div class="panel table-panel" v-loading="loading">
      <el-table :data="tags" row-key="id" empty-text="字典表中暂无标签">
        <el-table-column prop="id" label="#" width="100" />
        <el-table-column label="标签名称" min-width="200">
          <template #default="{ row }">
            <el-tag :color="resolveTagColor(row.color, row.name) + '20'" :style="{ borderColor: resolveTagColor(row.color, row.name), color: resolveTagColor(row.color, row.name) }" class="premium-tag" effect="plain">
              {{ row.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="color" label="颜色代码" width="160">
          <template #default="{ row }">
            <div class="color-cell">
              <span class="color-dot" :style="{ backgroundColor: resolveTagColor(row.color, row.name) }"></span>
              <span class="color-hex">{{ resolveTagColor(row.color, row.name) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="200">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isSuperAdmin" label="操作" width="160" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button :icon="Edit" type="primary" size="small" circle @click="openEditDialog(row)" />
              <el-button :icon="Delete" type="danger" size="small" circle @click="handleDelete(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑标签' : '添加标签'"
      width="420px"
      destroy-on-close
      class="glass-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        class="admin-form"
      >
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入标签名称" />
        </el-form-item>

        <el-form-item label="标签预览与配色">
          <div class="preview-row" style="display: flex; align-items: center; gap: 12px;">
            <el-tag
              :color="form.color + '20'"
              :style="{ borderColor: form.color, color: form.color }"
              class="premium-tag"
              effect="plain"
            >
              {{ form.name || '标签预览' }}
            </el-tag>
            <el-button type="success" size="small" :icon="Refresh" @click="randomizeColor">
              随机换色
            </el-button>
          </div>
        </el-form-item>

        <!-- Advanced custom hex field -->
        <el-collapse class="advanced-collapse" style="border: none;">
          <el-collapse-item title="高级设置 (自定义颜色代码)" name="color" style="border: none;">
            <el-form-item prop="color">
              <el-input v-model="form.color" placeholder="#RRGGBB 格式颜色" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <div class="dialog-actions">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveTag">确定保存</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { fetchProblemTags, createProblemTag, updateProblemTag, deleteProblemTag } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/time'
import type { ProblemTag } from '../types'

import { watch } from 'vue'
import { getTagColor } from '../utils/tag'

const beautifulColors = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C', 
  '#8E44AD', '#1ABC9C', '#2ECC71', '#3498DB', 
  '#E67E22', '#E74C3C', '#E056FD', '#686DE0', 
  '#FC5C65', '#FD9644', '#2BCBBA', '#26DE81'
]

const authStore = useAuthStore()
const isSuperAdmin = computed(() => authStore.user?.role === 'SUPER_ADMIN')

const loading = ref(false)
const saving = ref(false)
const tags = ref<ProblemTag[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const currentTagId = ref<number | null>(null)
const formRef = ref<FormInstance | null>(null)

const form = reactive({
  name: '',
  color: '#409EFF'
})

function randomizeColor() {
  const current = form.color
  let next = current
  while (next === current) {
    next = beautifulColors[Math.floor(Math.random() * beautifulColors.length)]
  }
  form.color = next
}

// Automatically assign beautiful deterministic hash color in real-time as user types in create mode
watch(() => form.name, (newName) => {
  if (!isEdit.value && newName.trim()) {
    form.color = getTagColor(newName.trim())
  }
})

function resolveTagColor(color?: string, name?: string): string {
  if (!color || color === '#909399') {
    return getTagColor(name || '')
  }
  return color
}

const formRules = {
  name: [
    { required: true, message: '标签名称不能为空', trigger: 'blur' },
    { max: 64, message: '标签名称过长', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value.includes(',') || value.includes('，')) {
          callback(new Error('标签名不能包含逗号'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  color: [
    { required: true, message: '颜色不能为空', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (!value.match(/^#[0-9A-Fa-f]{6}$/)) {
          callback(new Error('颜色格式必须为 #RRGGBB'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function load() {
  loading.value = true
  try {
    tags.value = await fetchProblemTags()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '获取标签字典失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  currentTagId.value = null
  form.name = ''
  form.color = beautifulColors[Math.floor(Math.random() * beautifulColors.length)]
  dialogVisible.value = true
}

function openEditDialog(row: ProblemTag) {
  isEdit.value = true
  currentTagId.value = row.id
  form.name = row.name
  form.color = row.color || getTagColor(row.name)
  dialogVisible.value = true
}

async function saveTag() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && currentTagId.value !== null) {
        await updateProblemTag(currentTagId.value, { name: form.name, color: form.color })
        ElMessage.success('保存成功')
      } else {
        await createProblemTag({ name: form.name, color: form.color })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      load()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '操作失败')
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: ProblemTag) {
  try {
    await ElMessageBox.confirm(`确定要删除标签 "${row.name}" 吗？这会移除该标签与所有题目的关联关系。`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    await deleteProblemTag(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.premium-tag {
  font-weight: 500;
  border-radius: 6px;
  font-size: 0.85rem;
  padding: 0.25rem 0.6rem;
  background-color: transparent !important;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: 0 0 6px rgba(0,0,0,0.15);
}

.color-hex {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  color: var(--text-regular);
}

.time-text {
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.table-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.color-picker-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-input {
  flex-grow: 1;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
