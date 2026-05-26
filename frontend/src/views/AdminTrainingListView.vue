<template>
  <section class="page-stack">
    <AdminNav />

    <div class="page-heading">
      <div>
        <h1>题单管理</h1>
        <p>创建及管理用于专项练习的知识点题单</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">创建新题单</el-button>
    </div>

    <div class="panel">
      <el-table v-loading="loading" :data="sets">
        <el-table-column prop="id" label="#" width="80" />
        
        <el-table-column prop="title" label="题单标题" min-width="220">
          <template #default="{ row }">
            <span class="set-title-link" @click="editSetProblems(row.id)">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="totalProblems" label="包含题目" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.totalProblems }} 道题</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="可见性" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.visible"
              @change="toggleVisibility(row)"
              active-text="公开"
              inactive-text="隐藏"
              inline-prompt
            />
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editSetMetadata(row)">编辑属性</el-button>
            <el-button link type="success" @click="editSetProblems(row.id)">管理题目</el-button>
            <el-button link type="danger" @click="deleteSetPrompt(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑题单属性' : '创建新题单'"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="题单标题" required>
          <el-input v-model="form.title" placeholder="例如：动态规划基础专项练习" />
        </el-form-item>
        
        <el-form-item label="描述 (支持 Markdown)">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="简要介绍本题单的范围、学习目标和前置知识要求..."
          />
        </el-form-item>

        <el-form-item label="公开状态">
          <el-radio-group v-model="form.visible">
            <el-radio :value="true">公开 (所有用户可见并可作答)</el-radio>
            <el-radio :value="false">隐藏 (仅管理员可见进行编辑)</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSet">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'
import AdminNav from '../components/AdminNav.vue'

interface TrainingSetDto {
  id: number
  title: string
  description: string
  visible: boolean
  totalProblems: number
  createdAt: string
}

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const sets = ref<TrainingSetDto[]>([])

// Form variables
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  title: '',
  description: '',
  visible: true
})

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    const res = await http.get('/admin/training')
    if (res.data && res.data.code === 200) {
      sets.value = res.data.data
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载题单失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  form.value = {
    title: '',
    description: '',
    visible: true
  }
  dialogVisible.value = true
}

function editSetMetadata(row: TrainingSetDto) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    title: row.title,
    description: row.description || '',
    visible: row.visible
  }
  dialogVisible.value = true
}

async function saveSet() {
  if (!form.value.title.trim()) {
    ElMessage.warning('题单标题不能为空')
    return
  }
  saving.value = true
  try {
    if (isEditing.value && editingId.value) {
      await http.put(`/admin/training/${editingId.value}`, form.value)
      ElMessage.success('题单更新成功')
    } else {
      await http.post('/admin/training', form.value)
      ElMessage.success('题单创建成功')
    }
    dialogVisible.value = false
    await load()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleVisibility(row: TrainingSetDto) {
  try {
    await http.put(`/admin/training/${row.id}`, {
      title: row.title,
      description: row.description,
      visible: row.visible
    })
    ElMessage.success(`题单已设置为${row.visible ? '公开' : '隐藏'}`)
  } catch (err: any) {
    row.visible = !row.visible // rollback
    ElMessage.error(err.response?.data?.message || '更新可见性失败')
  }
}

function editSetProblems(id: number) {
  router.push(`/admin/training/${id}`)
}

function deleteSetPrompt(row: TrainingSetDto) {
  ElMessageBox.confirm(
    `确定要删除题单《${row.title}》吗？这只会解除题单内的题目关联，不会删除题目本身。`,
    '警告',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await http.delete(`/admin/training/${row.id}`)
      ElMessage.success('题单已成功删除')
      await load()
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '删除失败')
    }
  }).catch(() => {})
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.set-title-link {
  font-weight: 600;
  color: #4f46e5;
  cursor: pointer;
  transition: color 0.15s ease;
}
.set-title-link:hover {
  color: #6366f1;
  text-decoration: underline;
}
</style>
