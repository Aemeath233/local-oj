<template>
  <div class="tab-content cleanup-tab" v-loading="loading">
    <el-alert
      title="物理删除警告"
      type="warning"
      description="清理提交记录是物理删除操作，不可撤销！清理历史记录能有效压缩数据库体积并提升排行榜计算速率，建议定期删除旧的或无关测试提交。与之关联的测试点细节运行数据会自动级联清空。"
      show-icon
      :closable="false"
      class="mb-4"
    />

    <el-form :model="cleanupForm" label-position="top" class="cleanup-form-layout">
      <div class="form-grid">
        <el-form-item label="提交时间早于">
          <el-date-picker
            v-model="cleanupForm.beforeDate"
            type="datetime"
            placeholder="选择截止时间（留空则不限时间）"
            style="width: 100%"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
          <div class="form-item-tip">例如：选择删除 30 天以前的所有历史提交。</div>
        </el-form-item>

        <el-form-item label="特定题目范围">
          <el-select v-model="cleanupForm.problemId" placeholder="所有题目" clearable style="width: 100%">
            <el-option v-for="p in problems" :key="p.id" :label="`${p.id}. ${p.title} (${p.slug})`" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="特定提交用户 ID">
          <el-input-number v-model="cleanupForm.userId" placeholder="输入用户 ID" :min="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="清理范围 (Scope)">
          <el-select v-model="cleanupForm.scope" placeholder="所有提交" style="width: 100%">
            <el-option label="所有提交 (所有练习与比赛)" value="all" />
            <el-option label="仅限日常练习提交 (不含比赛)" value="practice" />
            <el-option label="所有比赛提交" value="contest" />
            <el-option label="指定某一特定比赛" value="single-contest" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="cleanupForm.scope === 'single-contest'" label="选择比赛">
          <el-select v-model="cleanupForm.contestId" placeholder="选择比赛" style="width: 100%">
            <el-option v-for="c in contests" :key="c.id" :label="`[${c.type}] ${c.title}`" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="指定评测状态 (Verdict)">
          <el-select v-model="cleanupForm.verdicts" multiple collapse-tags placeholder="所有结果类型" style="width: 100%">
            <el-option label="AC (Accepted)" value="AC" />
            <el-option label="WA (Wrong Answer)" value="WA" />
            <el-option label="TLE (Time Limit Exceeded)" value="TLE" />
            <el-option label="MLE (Memory Limit Exceeded)" value="MLE" />
            <el-option label="OLE (Output Limit Exceeded)" value="OLE" />
            <el-option label="RE (Runtime Error)" value="RE" />
            <el-option label="CE (Compilation Error)" value="CE" />
            <el-option label="IE (Internal Error)" value="IE" />
          </el-select>
          <div class="form-item-tip">例如：可以只清理 CE（编译失败）或 IE（内部错误）等对成绩没有影响的冗余数据。</div>
        </el-form-item>
      </div>

      <!-- High-Security Lock Panel -->
      <div class="security-lock-panel">
        <div class="lock-header">
          <el-icon class="lock-icon"><Lock /></el-icon>
          <h4>高安全确认保护锁</h4>
        </div>
        <p class="lock-desc">为避免意外操作，请在下方文本框输入验证短语 <strong>FORCE CLEANUP</strong> 启用安全删除按钮：</p>
        <el-input
          v-model="securityPhrase"
          placeholder="请输入 FORCE CLEANUP 进行二次安全解锁"
          class="phrase-input"
          clearable
        />
      </div>

      <div class="form-actions-clean">
        <el-button
          type="danger"
          size="large"
          :disabled="securityPhrase.trim() !== 'FORCE CLEANUP'"
          :loading="cleaningSubmissions"
          :icon="Delete"
          @click="triggerCleanup"
        >
          安全解锁并执行物理清理
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Delete, Lock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchProblems, fetchContests, cleanupSubmissions } from '../../api/http'

const loading = ref(false)
const cleaningSubmissions = ref(false)
const securityPhrase = ref('')

const problems = ref<any[]>([])
const contests = ref<any[]>([])

const cleanupForm = reactive({
  beforeDate: '',
  problemId: null as number | null,
  userId: null as number | null,
  contestId: null as number | null,
  scope: 'all' as 'all' | 'practice' | 'contest' | 'single-contest',
  verdicts: [] as string[]
})

onMounted(async () => {
  loading.value = true
  try {
    const [probList, contList] = await Promise.all([
      fetchProblems(),
      fetchContests()
    ])
    problems.value = probList || []
    contests.value = contList || []
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '初始化题目或比赛列表失败')
  } finally {
    loading.value = false
  }
})

async function triggerCleanup() {
  if (securityPhrase.value.trim() !== 'FORCE CLEANUP') {
    return
  }

  try {
    await ElMessageBox.confirm(
      '此操作为物理清空，不可恢复！您确定要清理对应的提交记录吗？',
      '极限操作风险警告',
      {
        confirmButtonText: '确定清除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  cleaningSubmissions.value = true
  try {
    const payload: any = {
      confirmationPhrase: securityPhrase.value.trim(),
      beforeDate: cleanupForm.beforeDate || undefined,
      problemId: cleanupForm.problemId || undefined,
      userId: cleanupForm.userId || undefined,
      verdicts: cleanupForm.verdicts.length > 0 ? cleanupForm.verdicts : undefined
    }

    if (cleanupForm.scope === 'practice') {
      payload.onlyPractice = true
    } else if (cleanupForm.scope === 'contest') {
      payload.onlyContest = true
    } else if (cleanupForm.scope === 'single-contest') {
      payload.contestId = cleanupForm.contestId || undefined
    }

    const res = await cleanupSubmissions(payload)
    ElMessage.success(`历史提交记录清除成功，共物理清理了 ${res.count} 条提交数据！`)
    securityPhrase.value = ''
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '清理提交数据失败')
  } finally {
    cleaningSubmissions.value = false
  }
}
</script>

<style scoped>
.tab-content {
  padding: 1.5rem 0;
}

.cleanup-form-layout {
  background: var(--bg-card, #fafafa);
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1.5rem;
}

.form-item-tip {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
  margin-top: 0.25rem;
}

.security-lock-panel {
  margin-top: 2rem;
  padding: 1.25rem;
  border-radius: 6px;
  background: rgba(245, 108, 108, 0.03);
  border: 1px solid rgba(245, 108, 108, 0.2);
}

.lock-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--el-color-danger);
  margin-bottom: 0.5rem;
}

.lock-header h4 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.lock-desc {
  margin: 0 0 1rem 0;
  font-size: 0.9rem;
  color: var(--el-text-color-regular);
}

.phrase-input {
  max-width: 360px;
}

.form-actions-clean {
  margin-top: 1.5rem;
  display: flex;
  justify-content: flex-end;
}

.mb-4 {
  margin-bottom: 1.5rem;
}
</style>
