<template>
  <div class="heatmap-card panel">
    <div class="heatmap-header">
      <h3 class="panel-title">提交冷热图</h3>
      <div class="heatmap-stats">
        <div class="heatmap-stat-item">
          <span class="heatmap-stat-label">过去一年提交</span>
          <strong class="heatmap-stat-value">{{ heatmapTotalSubmissions }}</strong>
        </div>
        <div class="heatmap-stat-item">
          <span class="heatmap-stat-label">AC 通过数</span>
          <strong class="heatmap-stat-value ac">{{ heatmapTotalAc }}</strong>
        </div>
        <div class="heatmap-stat-item">
          <span class="heatmap-stat-label">通过率</span>
          <strong class="heatmap-stat-value">{{ heatmapAcRate }}</strong>
        </div>
        <div class="heatmap-stat-item">
          <span class="heatmap-stat-label">活跃天数</span>
          <strong class="heatmap-stat-value">{{ heatmapActiveDays }} 天</strong>
        </div>
      </div>
    </div>

    <div class="heatmap-container">
      <svg viewBox="0 0 740 135" width="100%" class="heatmap-svg">
        <!-- Month Labels -->
        <text
          v-for="(label, lIndex) in monthLabels"
          :key="'m-' + lIndex"
          :x="label.x"
          y="12"
          class="heatmap-label"
          font-size="9"
          fill="#64748b"
        >
          {{ label.text }}
        </text>

        <!-- Weekday Labels -->
        <text x="0" y="38" class="heatmap-label" font-size="9" fill="#64748b">周一</text>
        <text x="0" y="64" class="heatmap-label" font-size="9" fill="#64748b">周三</text>
        <text x="0" y="90" class="heatmap-label" font-size="9" fill="#64748b">周五</text>

        <!-- Grid Cells -->
        <g v-for="(week, wIndex) in weeks" :key="'w-' + wIndex">
          <template v-for="(day, dIndex) in week" :key="'d-' + dIndex">
            <rect
              v-if="day.level >= 0"
              :x="30 + wIndex * 13"
              :y="20 + dIndex * 13"
              width="10"
              height="10"
              rx="2"
              ry="2"
              :class="['heatmap-cell', `level-${day.level}`]"
              @mouseenter="showTooltip($event, day)"
              @mouseleave="hideTooltip"
            />
          </template>
        </g>

        <!-- Legend -->
        <g transform="translate(580, 118)">
          <text x="0" y="9" class="heatmap-label" font-size="9" fill="#64748b">少</text>
          <rect x="18" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-0" />
          <rect x="31" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-1" />
          <rect x="44" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-2" />
          <rect x="57" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-3" />
          <rect x="70" y="0" width="10" height="10" rx="2" ry="2" class="heatmap-cell level-4" />
          <text x="86" y="9" class="heatmap-label" font-size="9" fill="#64748b">多</text>
        </g>
      </svg>
      <Teleport to="body">
        <Transition name="fade-fast">
          <div
            v-show="tooltipVisible"
            class="custom-heatmap-tooltip"
            :style="tooltipStyle"
          >
            {{ tooltipContent }}
            <div class="tooltip-arrow"></div>
          </div>
        </Transition>
      </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onUnmounted, watch } from 'vue'

const props = defineProps<{
  heatmapData: Record<string, { totalCount: number; acCount: number }>
}>()

const heatmapTotalSubmissions = computed(() => {
  let sum = 0
  for (const date in props.heatmapData) {
    sum += props.heatmapData[date].totalCount
  }
  return sum
})

const heatmapTotalAc = computed(() => {
  let sum = 0
  for (const date in props.heatmapData) {
    sum += props.heatmapData[date].acCount
  }
  return sum
})

const heatmapAcRate = computed(() => {
  if (heatmapTotalSubmissions.value === 0) return '0.0%'
  return ((heatmapTotalAc.value / heatmapTotalSubmissions.value) * 100).toFixed(1) + '%'
})

const heatmapActiveDays = computed(() => {
  let count = 0
  for (const date in props.heatmapData) {
    if (props.heatmapData[date].totalCount > 0) {
      count++
    }
  }
  return count
})

// Virtual Tooltip handlers to prevent SVG hover flickering
const tooltipVisible = ref(false)
const tooltipContent = ref('')
const tooltipStyle = ref({
  left: '0px',
  top: '0px'
})

let activeCell: any = null
let hoverTimeout: number | undefined
let hideTimeout: number | undefined

interface HeatmapDay {
  date: Date
  dateString: string
  isFuture: boolean
  totalCount: number
  acCount: number
  level: number
}

const weeks = ref<HeatmapDay[][]>([])
const monthLabels = ref<Array<{ text: string; x: number }>>([])

function showTooltip(event: MouseEvent, day: any) {
  const rectEl = event.currentTarget as SVGRectElement
  if (!rectEl) return

  if (hideTimeout) {
    clearTimeout(hideTimeout)
    hideTimeout = undefined
  }
  if (activeCell === day) return
  activeCell = day
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
  }
  hoverTimeout = window.setTimeout(() => {
    const rectBounds = rectEl.getBoundingClientRect()
    const left = rectBounds.left + window.scrollX + rectBounds.width / 2
    const top = rectBounds.top + window.scrollY - 8
    
    tooltipStyle.value = {
      left: `${left}px`,
      top: `${top}px`
    }

    tooltipContent.value = getTooltipContent(day)
    tooltipVisible.value = true
  }, 20)
}

function hideTooltip() {
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
    hoverTimeout = undefined
  }
  activeCell = null
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
  hideTimeout = window.setTimeout(() => {
    tooltipVisible.value = false
  }, 40)
}

function getTooltipContent(day: any) {
  if (day.totalCount === 0) {
    return `${day.dateString} : 无提交记录`
  }
  return `${day.dateString} : 提交 ${day.totalCount} 次，通过 AC ${day.acCount} 次`
}

function formatDate(d: Date) {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getContributionLevel(total: number, ac: number, isFuture: boolean) {
  if (isFuture) return -1
  if (total === 0) return 0
  if (ac === 0) return 1
  if (ac <= 2) return 2
  if (ac <= 5) return 3
  return 4
}

function generateHeatmapData(heatmapData: Record<string, { totalCount: number; acCount: number }>) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  const lastSaturday = new Date(today)
  lastSaturday.setDate(today.getDate() + (6 - today.getDay()))

  const gridStartDate = new Date(lastSaturday)
  gridStartDate.setDate(lastSaturday.getDate() - 370)

  const tempWeeks: any[] = []
  for (let w = 0; w < 53; w++) {
    const weekDays: any[] = []
    for (let d = 0; d < 7; d++) {
      const date = new Date(gridStartDate)
      date.setDate(gridStartDate.getDate() + (w * 7 + d))

      const dateString = formatDate(date)
      const isFuture = date > today
      const statsObj = heatmapData[dateString] || { totalCount: 0, acCount: 0 }

      weekDays.push({
        date,
        dateString,
        isFuture,
        totalCount: statsObj.totalCount,
        acCount: statsObj.acCount,
        level: getContributionLevel(statsObj.totalCount, statsObj.acCount, isFuture)
      })
    }
    tempWeeks.push(weekDays)
  }
  weeks.value = tempWeeks
  generateMonthLabels()
}

function generateMonthLabels() {
  const labels: Array<{ text: string; x: number }> = []
  let lastMonth = -1

  for (let w = 0; w < 53; w++) {
    const week = weeks.value[w]
    if (!week || week.length === 0) continue
    const sundayDate = week[0].date
    const m = sundayDate.getMonth()

    if (w === 0 || m !== lastMonth) {
      const monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
      labels.push({
        text: monthNames[m],
        x: 30 + w * 13
      })
      lastMonth = m
    }
  }

  if (labels.length >= 2 && labels[1].x - labels[0].x < 26) {
    labels.shift()
  }

  monthLabels.value = labels
}

watch(() => props.heatmapData, (newData) => {
  if (newData) generateHeatmapData(newData)
}, { immediate: true })

onUnmounted(() => {
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
  }
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
})
</script>

<style scoped>
/* Heatmap Chart Styles */
.heatmap-card {
  padding: 24px;
}

.heatmap-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.heatmap-stats {
  display: flex;
  gap: 32px;
}

.heatmap-stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.heatmap-stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.heatmap-stat-value {
  font-size: 18px;
  font-family: var(--font-mono), monospace;
  color: var(--el-text-color-primary);
}

.heatmap-stat-value.ac {
  color: #10b981;
}

.heatmap-container {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 8px;
}

.heatmap-svg {
  min-width: 700px;
}

.heatmap-label {
  font-family: var(--font-sans), sans-serif;
  user-select: none;
}

.heatmap-cell {
  stroke: rgba(27, 31, 35, 0.06);
  stroke-width: 1px;
  cursor: pointer;
}

/* Light mode colors */
.heatmap-cell.level-0 { fill: #ebedf0; }
.heatmap-cell.level-1 { fill: #9be9a8; }
.heatmap-cell.level-2 { fill: #40c463; }
.heatmap-cell.level-3 { fill: #30a14e; }
.heatmap-cell.level-4 { fill: #216e39; }

/* Dark mode colors */
html.dark .heatmap-cell.level-0 { fill: #161b22; stroke: rgba(255,255,255,0.05); }
html.dark .heatmap-cell.level-1 { fill: #0e4429; stroke: rgba(255,255,255,0.05); }
html.dark .heatmap-cell.level-2 { fill: #006d32; stroke: rgba(255,255,255,0.05); }
html.dark .heatmap-cell.level-3 { fill: #26a641; stroke: rgba(255,255,255,0.05); }
html.dark .heatmap-cell.level-4 { fill: #39d353; stroke: rgba(255,255,255,0.05); }

/* Tooltip styles */
.custom-heatmap-tooltip {
  position: absolute;
  background: var(--bg-surface);
  color: var(--el-text-color-primary);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-family: var(--font-sans), sans-serif;
  pointer-events: none;
  z-index: 10000;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  border: 1px solid var(--border-color);
  transform: translate(-50%, -100%);
  white-space: nowrap;
}

html.dark .custom-heatmap-tooltip {
  box-shadow: 0 4px 12px rgba(0,0,0,0.5);
  background: #1e293b;
}

.tooltip-arrow {
  position: absolute;
  bottom: -5px;
  left: 50%;
  transform: translateX(-50%);
  border-width: 5px 5px 0;
  border-style: solid;
  border-color: var(--border-color) transparent transparent transparent;
}
.tooltip-arrow::after {
  content: "";
  position: absolute;
  bottom: 1px;
  left: -5px;
  border-width: 5px 5px 0;
  border-style: solid;
  border-color: var(--bg-surface) transparent transparent transparent;
}
html.dark .tooltip-arrow::after {
  border-color: #1e293b transparent transparent transparent;
}

.fade-fast-enter-active,
.fade-fast-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.fade-fast-enter-from,
.fade-fast-leave-to {
  opacity: 0;
  transform: translate(-50%, -90%);
}

@media (max-width: 768px) {
  .heatmap-header {
    flex-direction: column;
    gap: 16px;
  }
  .heatmap-stats {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }
}
</style>
