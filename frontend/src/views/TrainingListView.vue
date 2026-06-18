<template>
  <section class="page-stack">
    <div class="page-heading">
      <div>
        <h1>专项练习</h1>
        <p>按专项分类自主学习，掌握核心算法知识</p>
      </div>
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
            <div class="card-meta">
              <span class="card-author">
                <el-icon class="meta-icon"><User /></el-icon>
                {{ set.creatorNickname || '管理员' }}
              </span>
              <span class="card-time">创建于 {{ formatDate(set.createdAt) }}</span>
            </div>
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
import { computed, onMounted, onActivated, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight, User } from '@element-plus/icons-vue'
import { http } from '../api/base'
import { useAuthStore } from '../stores/auth'
import { shouldRefreshSection, forceUpdateSectionVersion } from '../utils/versionCheck'

interface TrainingSetDto {
  id: number
  title: string
  description?: string | null
  visible: boolean
  totalProblems: number
  solvedProblems: number
  createdAt: string
  creatorNickname?: string
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
    s => s.title.toLowerCase().includes(query) || (s.description || '').toLowerCase().includes(query)
  )
})

onMounted(() => {
  load()
  forceUpdateSectionVersion('trainings')
})

onActivated(async () => {
  if (await shouldRefreshSection('trainings')) {
    await load(true)
  }
})

async function load(isSilent: boolean = false) {
  if (!isSilent) {
    loading.value = true
  }
  try {
    if (!isSilent) {
      forceUpdateSectionVersion('trainings')
    }
    const res = await http.get('/training')
    if (res.data && res.data.data) {
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
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}
.training-card:hover {
  border-color: var(--el-color-primary-light-7);
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
  color: var(--text-primary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.problem-count {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 600;
  flex-shrink: 0;
}
.card-description {
  margin: 0 0 20px 0;
  font-size: 13px;
  color: var(--text-muted);
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
  background: var(--bg-app);
  border-radius: 8px;
  padding: 12px;
  border: 1px dashed var(--border-color);
}
.progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
  font-weight: 500;
}
.progress-ratio {
  color: var(--text-primary);
  font-weight: 600;
}
.card-footer {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border-light);
  padding-top: 12px;
}
.card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-author {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.card-author .el-icon {
  font-size: 13px;
}
.card-time {
  font-size: 11px;
  color: var(--text-muted);
}
.enter-btn {
  font-size: 13px;
  font-weight: 600;
  padding: 0;
}
</style>
