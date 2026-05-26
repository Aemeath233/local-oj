<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>专项练习</h1>
        <p>按专项分类自主学习，掌握核心算法知识</p>
      </div>
      <RouterLink v-if="auth.isAdmin" to="/admin/training">
        <el-button type="primary">管理题单</el-button>
      </RouterLink>
    </div>

    <!-- Search filter bar -->
    <div class="filter-card-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索题单..."
        clearable
        class="filter-search-box"
        :prefix-icon="Search"
      />
    </div>

    <div v-loading="loading" class="training-container">
      <el-empty v-if="filteredSets.length === 0 && !loading" description="暂无专项练习题单" />

      <div v-else class="training-grid">
        <div
          v-for="set in filteredSets"
          :key="set.id"
          class="training-card"
          @click="enterTraining(set.id)"
        >
          <div class="card-header">
            <h3 class="card-title">{{ set.title }}</h3>
            <span class="problem-count">{{ set.totalProblems }} 道题</span>
          </div>

          <p class="card-description">{{ set.description || '暂无详细描述。' }}</p>

          <div class="card-progress">
            <div class="progress-labels">
              <span>完成度</span>
              <span class="progress-ratio">{{ set.solvedProblems }} / {{ set.totalProblems }} AC</span>
            </div>
            <el-progress
              :percentage="getProgressPercentage(set)"
              :status="getProgressPercentage(set) === 100 ? 'success' : undefined"
              :stroke-width="8"
            />
          </div>

          <div class="card-footer">
            <span class="card-time">创建于 {{ formatDate(set.createdAt) }}</span>
            <el-button link type="primary" class="enter-btn">
              开始练习
              <el-icon style="margin-left: 4px;"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import { http } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface TrainingSetDto {
  id: number
  title: string
  description: string
  visible: boolean
  totalProblems: number
  solvedProblems: number
  createdAt: string
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const searchQuery = ref('')
const sets = ref<TrainingSetDto[]>([])

const filteredSets = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return sets.value
  return sets.value.filter(
    s => s.title.toLowerCase().includes(query) || s.description.toLowerCase().includes(query)
  )
})

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    const res = await http.get('/training')
    if (res.data && res.data.code === 200) {
      sets.value = res.data.data
    }
  } catch (err) {
    console.error('Failed to load training sets', err)
  } finally {
    loading.value = false
  }
}

function getProgressPercentage(set: TrainingSetDto): number {
  if (set.totalProblems === 0) return 0
  return Math.round((set.solvedProblems / set.totalProblems) * 100)
}

function enterTraining(id: number) {
  router.push(`/training/${id}`)
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.training-filter-bar {
  margin-bottom: 24px;
}
.search-input {
  max-width: 320px;
}
.training-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}
.training-card {
  background: #ffffff;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}
.training-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
  transform: translateY(-2px);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}
.card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #1e293b;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.problem-count {
  font-size: 12px;
  color: #475569;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
.card-description {
  margin: 0 0 20px 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-progress {
  margin-bottom: 20px;
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  border: 1px dashed #e2e8f0;
}
.progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
  font-weight: 500;
}
.progress-ratio {
  color: #1e293b;
  font-weight: 600;
}
.card-footer {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
}
.card-time {
  font-size: 11px;
  color: #94a3b8;
}
.enter-btn {
  font-size: 13px;
  font-weight: 600;
  padding: 0;
}
</style>
