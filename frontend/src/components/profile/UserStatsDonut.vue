<template>
  <div class="donut-chart-card panel">
    <h3 class="panel-title">做题统计</h3>
    <div class="donut-content">
      <div class="donut-chart-wrapper">
        <svg width="120" height="120" viewBox="0 0 100 100" class="donut-svg">
          <!-- Background Track -->
          <circle cx="50" cy="50" r="42" fill="transparent" stroke="#f1f5f9" stroke-width="8" />
          <!-- Active Progress Ring -->
          <circle
            cx="50"
            cy="50"
            r="42"
            fill="transparent"
            stroke="#0f766e"
            stroke-width="8"
            :stroke-dasharray="263.89"
            :stroke-dashoffset="donutStrokeOffset"
            transform="rotate(-90 50 50)"
            stroke-linecap="round"
            class="donut-ring-active"
          />
          <!-- Central Text -->
          <text x="50" y="48" text-anchor="middle" class="donut-total" font-size="16" font-weight="bold" fill="#0f766e">
            {{ solvedTotal }}
          </text>
          <text x="50" y="66" text-anchor="middle" class="donut-label" font-size="8" fill="#64748b" font-weight="600">
            已解决 / {{ problemsTotal }}
          </text>
        </svg>
      </div>

      <div class="difficulty-list">
        <!-- Easy -->
        <div class="difficulty-row">
          <div class="difficulty-header">
            <span class="difficulty-badge easy">简单</span>
            <span class="difficulty-count">
              <strong>{{ difficulty.easySolved }}</strong> / {{ difficulty.easyTotal }}
            </span>
            <span class="difficulty-pct">{{ getPercentage(difficulty.easySolved, difficulty.easyTotal) }}%</span>
          </div>
          <div class="progress-bar-track">
            <div
              class="progress-bar-fill easy"
              :style="{ width: getPercentage(difficulty.easySolved, difficulty.easyTotal) + '%' }"
            ></div>
          </div>
        </div>

        <!-- Medium -->
        <div class="difficulty-row">
          <div class="difficulty-header">
            <span class="difficulty-badge medium">中等</span>
            <span class="difficulty-count">
              <strong>{{ difficulty.mediumSolved }}</strong> / {{ difficulty.mediumTotal }}
            </span>
            <span class="difficulty-pct">{{ getPercentage(difficulty.mediumSolved, difficulty.mediumTotal) }}%</span>
          </div>
          <div class="progress-bar-track">
            <div
              class="progress-bar-fill medium"
              :style="{ width: getPercentage(difficulty.mediumSolved, difficulty.mediumTotal) + '%' }"
            ></div>
          </div>
        </div>

        <!-- Hard -->
        <div class="difficulty-row">
          <div class="difficulty-header">
            <span class="difficulty-badge hard">困难</span>
            <span class="difficulty-count">
              <strong>{{ difficulty.hardSolved }}</strong> / {{ difficulty.hardTotal }}
            </span>
            <span class="difficulty-pct">{{ getPercentage(difficulty.hardSolved, difficulty.hardTotal) }}%</span>
          </div>
          <div class="progress-bar-track">
            <div
              class="progress-bar-fill hard"
              :style="{ width: getPercentage(difficulty.hardSolved, difficulty.hardTotal) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  difficulty: {
    easySolved: number
    easyTotal: number
    mediumSolved: number
    mediumTotal: number
    hardSolved: number
    hardTotal: number
  }
}>()

const solvedTotal = computed(() => {
  const d = props.difficulty
  return d.easySolved + d.mediumSolved + d.hardSolved
})

const problemsTotal = computed(() => {
  const d = props.difficulty
  return d.easyTotal + d.mediumTotal + d.hardTotal
})

const donutStrokeOffset = computed(() => {
  const total = problemsTotal.value
  if (total === 0) return 263.89
  const solved = solvedTotal.value
  const pct = Math.min(1, solved / total)
  return 263.89 * (1 - pct)
})

function getPercentage(solved: number, total: number) {
  if (total === 0) return 0
  return Math.round((solved / total) * 100)
}
</script>

<style scoped>
/* Donut chart styles */
.donut-chart-card {
  padding: 24px;
}

.donut-content {
  display: flex;
  gap: 30px;
  align-items: center;
}

.donut-chart-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
}

.donut-ring-active {
  transition: stroke-dashoffset 1s ease-out;
}

.donut-total {
  font-family: var(--font-sans), sans-serif;
}

.donut-label {
  font-family: var(--font-sans), sans-serif;
}

.difficulty-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.difficulty-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}

.difficulty-badge {
  font-weight: 600;
  margin-right: auto;
}

.difficulty-badge.easy { color: #10b981; }
.difficulty-badge.medium { color: #f59e0b; }
.difficulty-badge.hard { color: #ef4444; }

.difficulty-count {
  color: var(--el-text-color-secondary);
  font-family: var(--font-mono), monospace;
  margin-right: 12px;
}
.difficulty-count strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.difficulty-pct {
  width: 32px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-family: var(--font-mono), monospace;
  font-weight: 500;
}

.progress-bar-track {
  height: 6px;
  background: var(--bg-muted);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 1s ease-out;
}

.progress-bar-fill.easy { background: #10b981; }
.progress-bar-fill.medium { background: #f59e0b; }
.progress-bar-fill.hard { background: #ef4444; }

@media (max-width: 600px) {
  .donut-content {
    flex-direction: column;
    align-items: center;
    gap: 24px;
  }
  .difficulty-list {
    width: 100%;
  }
}
</style>
