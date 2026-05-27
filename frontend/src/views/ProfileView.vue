<template>
  <section class="page-stack" v-loading="loading">
    <div class="page-heading">
      <div>
        <h1>个人主页</h1>
        <p>账号资料和安全设置</p>
      </div>
    </div>

    <section class="profile-layout">
      <div class="profile-card panel">
        <el-avatar :size="92" :src="form.avatarUrl">
          {{ avatarFallback }}
        </el-avatar>
        <div class="profile-name">
          <strong>{{ form.displayName || form.username }}</strong>
          <span>{{ form.username }}</span>
        </div>
        <input
          ref="avatarInput"
          class="visually-hidden"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif"
          @change="onAvatarSelected"
        />
        <el-button :icon="Upload" :loading="uploading" @click="openAvatarPicker">更换头像</el-button>
      </div>

      <div class="profile-main panel">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="资料" name="profile">
            <el-form class="profile-form" label-position="top" :model="form">
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input v-model="form.username" disabled />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="form.email" disabled />
                </el-form-item>
                <el-form-item label="昵称">
                  <el-input v-model="form.displayName" maxlength="128" show-word-limit />
                </el-form-item>
                <el-form-item label="学号">
                  <el-input v-model="form.studentNo" maxlength="64" show-word-limit />
                </el-form-item>
                <el-form-item label="专业">
                  <el-input v-model="form.major" maxlength="128" show-word-limit />
                </el-form-item>
              </div>
              <div class="form-actions">
                <el-button type="primary" :icon="Check" :loading="saving" @click="saveProfile">保存资料</el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="password">
            <el-form class="profile-form" label-position="top" :model="passwordForm">
              <el-alert
                v-if="!form.email"
                title="当前账号未绑定邮箱，无法通过 SMTP 修改密码"
                type="warning"
                show-icon
                :closable="false"
              />
              <div class="password-grid">
                <el-form-item label="邮箱验证码">
                  <div class="inline-field">
                    <el-input v-model="passwordForm.code" />
                    <el-button :loading="sendingCode" :disabled="codeCountdown > 0 || !form.email" @click="sendCode">
                      {{ codeCountdown > 0 ? `${codeCountdown}s` : '验证码' }}
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" show-password />
                </el-form-item>
                <el-form-item label="确认新密码">
                  <el-input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" show-password />
                </el-form-item>
              </div>
              <div class="form-actions">
                <el-button type="primary" :icon="Lock" :loading="changingPassword" :disabled="!form.email" @click="submitPassword">
                  修改密码
                </el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="偏好设置" name="preferences">
            <el-form class="profile-form" label-position="top" style="margin-top: 10px;">
              <div class="form-grid">
                <el-form-item label="默认编程语言">
                  <el-select v-model="prefLanguage" style="width: 100%;">
                    <el-option label="C++20" value="CPP" />
                    <el-option label="C++20 (O3氧气优化)" value="CPP_O3" />
                    <el-option label="C" value="C" />
                    <el-option label="Python 3.12" value="PYTHON" />
                    <el-option label="PyPy 3" value="PYPY3" />
                    <el-option label="Java 21" value="JAVA" />
                  </el-select>
                </el-form-item>

                <el-form-item label="编辑器默认字号">
                  <el-select v-model="prefFontSize" style="width: 100%;">
                    <el-option v-for="size in fontSizes" :key="size" :label="size + 'px'" :value="size" />
                  </el-select>
                </el-form-item>

                <el-form-item label="编辑器默认字体">
                  <el-select v-model="prefFontFamily" style="width: 100%;">
                    <el-option v-for="font in fontFamilies" :key="font.value" :label="font.label" :value="font.value" />
                  </el-select>
                </el-form-item>
              </div>

              <!-- Double Column for Reader Typography Settings -->
              <div class="form-grid" style="margin-top: 14px; border-top: 1px solid #f1f5f9; padding-top: 14px;">
                <el-form-item label="题面默认字号">
                  <el-select v-model="prefReaderFontSize" style="width: 100%;">
                    <el-option v-for="size in readerFontSizes" :key="size" :label="size + 'px'" :value="size" />
                  </el-select>
                </el-form-item>

                <el-form-item label="题面默认字体">
                  <el-select v-model="prefReaderFontFamily" style="width: 100%;">
                    <el-option v-for="font in readerFontFamilies" :key="font.value" :label="font.label" :value="font.value" />
                  </el-select>
                </el-form-item>
              </div>

              <!-- Real-time Previews Grid (Editor & Reader side-by-side) -->
              <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 14px;">
                <!-- Quick Font Preview Box -->
                <div style="padding: 16px; border: 1px solid #d8dee6; border-radius: 8px; background: #fafbfc; box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.025);">
                  <span style="font-size: 13px; color: #64748b; font-weight: 600; display: block; margin-bottom: 8px; font-family: sans-serif;">💻 编辑器代码实时预览：</span>
                  <pre :style="{ fontFamily: prefFontFamily, fontSize: prefFontSize + 'px', margin: 0, lineHeight: 1.5, color: '#0f766e', overflowX: 'auto', background: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid #e2e8f0' }">#include &lt;iostream&gt;

int main() {
    std::cout &lt;&lt; "Hello Local OJ!" &lt;&lt; std::endl;
    return 0;
}</pre>
                </div>

                <!-- Quick Reader Preview Box -->
                <div style="padding: 16px; border: 1px solid #d8dee6; border-radius: 8px; background: #fafbfc; box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.025);">
                  <span style="font-size: 13px; color: #64748b; font-weight: 600; display: block; margin-bottom: 8px; font-family: sans-serif;">📖 题面排版实时预览：</span>
                  <div :style="{ fontFamily: prefReaderFontFamily, fontSize: prefReaderFontSize + 'px', margin: 0, lineHeight: 1.6, color: '#334155', overflowX: 'auto', background: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid #e2e8f0', minHeight: '120px' }">
                    <h3 style="margin: 0 0 6px; font-size: 1.2em;">A + B 问题</h3>
                    <p style="margin: 0 0 6px;">输入两个整数 $A$ 和 $B$，计算它们的和并输出。</p>
                    <strong style="color: #0f766e;">[输入格式]</strong> 一行输入两个整数，以空格分隔。
                  </div>
                </div>
              </div>

              <!-- Custom Code Templates Section -->
              <div style="margin-top: 20px; border-top: 1px solid #f1f5f9; padding-top: 20px;">
                <h3 style="margin: 0 0 12px 0; font-size: 1rem; font-weight: 600; color: var(--el-text-color-primary);">🛠️ 自定义各语言默认代码模板</h3>
                <p style="margin: 0 0 14px 0; font-size: 0.85rem; color: var(--el-text-color-secondary);">
                  您可以为不同语言编写个性化的默认初始化代码，当您打开新题目时系统将自动为您填充这些代码，省去手动编写初始结构的繁琐步骤。
                </p>

                <div style="display: flex; gap: 16px; align-items: flex-start; flex-direction: column;">
                  <div style="display: flex; gap: 12px; align-items: center; width: 100%; flex-wrap: wrap;">
                    <span style="font-size: 0.9rem; font-weight: 500;">选择编辑语言：</span>
                    <el-select v-model="templateLang" style="width: 180px;" @change="loadTemplateForLang">
                      <el-option label="C++20" value="CPP" />
                      <el-option label="C++20 (O3氧气优化)" value="CPP_O3" />
                      <el-option label="C" value="C" />
                      <el-option label="Python 3.12" value="PYTHON" />
                      <el-option label="PyPy 3" value="PYPY3" />
                      <el-option label="Java 21" value="JAVA" />
                    </el-select>
                    <el-button type="info" plain size="small" style="margin-left: auto;" @click="resetTemplateToDefault">恢复当前语言默认</el-button>
                  </div>

                  <div style="width: 100%; border: 1px solid #dcdfe6; border-radius: 8px; overflow: hidden; background: #fff;">
                    <CodeEditor v-model="currentTemplateText" :language="templateLang" style="height: 240px; min-height: 240px;" />
                  </div>
                </div>
              </div>

              <div class="form-actions" style="margin-top: 18px;">
                <el-button type="primary" :icon="Check" @click="savePreferences">保存偏好设置</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <!-- 学习激励大盘：难度统计与提交冷热图 -->
    <section class="stats-dashboard-grid" v-if="!loading">
      <!-- 做题难度环形图 -->
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
                  <strong>{{ stats.difficultyDistribution.easySolved }}</strong> / {{ stats.difficultyDistribution.easyTotal }}
                </span>
                <span class="difficulty-pct">{{ getPercentage(stats.difficultyDistribution.easySolved, stats.difficultyDistribution.easyTotal) }}%</span>
              </div>
              <div class="progress-bar-track">
                <div
                  class="progress-bar-fill easy"
                  :style="{ width: getPercentage(stats.difficultyDistribution.easySolved, stats.difficultyDistribution.easyTotal) + '%' }"
                ></div>
              </div>
            </div>

            <!-- Medium -->
            <div class="difficulty-row">
              <div class="difficulty-header">
                <span class="difficulty-badge medium">中等</span>
                <span class="difficulty-count">
                  <strong>{{ stats.difficultyDistribution.mediumSolved }}</strong> / {{ stats.difficultyDistribution.mediumTotal }}
                </span>
                <span class="difficulty-pct">{{ getPercentage(stats.difficultyDistribution.mediumSolved, stats.difficultyDistribution.mediumTotal) }}%</span>
              </div>
              <div class="progress-bar-track">
                <div
                  class="progress-bar-fill medium"
                  :style="{ width: getPercentage(stats.difficultyDistribution.mediumSolved, stats.difficultyDistribution.mediumTotal) + '%' }"
                ></div>
              </div>
            </div>

            <!-- Hard -->
            <div class="difficulty-row">
              <div class="difficulty-header">
                <span class="difficulty-badge hard">困难</span>
                <span class="difficulty-count">
                  <strong>{{ stats.difficultyDistribution.hardSolved }}</strong> / {{ stats.difficultyDistribution.hardTotal }}
                </span>
                <span class="difficulty-pct">{{ getPercentage(stats.difficultyDistribution.hardSolved, stats.difficultyDistribution.hardTotal) }}%</span>
              </div>
              <div class="progress-bar-track">
                <div
                  class="progress-bar-fill hard"
                  :style="{ width: getPercentage(stats.difficultyDistribution.hardSolved, stats.difficultyDistribution.hardTotal) + '%' }"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 提交冷热图 -->
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
    </section>

    <!-- Interactive Avatar Cropping Dialog -->
    <AvatarCropperDialog
      v-model="cropperVisible"
      :image-file="selectedFile"
      @crop="onAvatarCropped"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Check, Lock, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  changePassword,
  fetchProfile,
  requestPasswordChangeCode,
  updateProfile,
  uploadAvatar,
  fetchUserStats
} from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { User, Language, UserStats } from '../types'
import CodeEditor from '../components/CodeEditor.vue'
import AvatarCropperDialog from '../components/AvatarCropperDialog.vue'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const sendingCode = ref(false)
const changingPassword = ref(false)

// Heatmap Virtual Tooltip handlers to prevent SVG hover flickering
const tooltipVisible = ref(false)
const tooltipContent = ref('')
const tooltipStyle = ref({
  left: '0px',
  top: '0px'
})

let activeCell: any = null
let hoverTimeout: number | undefined
let hideTimeout: number | undefined

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
const activeTab = ref('profile')
const codeCountdown = ref(0)
const avatarInput = ref<HTMLInputElement | null>(null)
const cropperVisible = ref(false)
const selectedFile = ref<File | null>(null)
let countdownTimer: number | undefined
const MAX_AVATAR_BYTES = 2 * 1024 * 1024

// Editor Preferences Configurations
const fontSizes = [14, 15, 16, 18, 20]
const fontFamilies = [
  { label: 'JetBrains Mono', value: "'JetBrains Mono', 'Cascadia Code', Consolas, monospace" },
  { label: 'Fira Code', value: "'Fira Code', 'JetBrains Mono', Consolas, monospace" },
  { label: 'Source Code Pro', value: "'Source Code Pro', 'JetBrains Mono', Consolas, monospace" },
  { label: 'IBM Plex Mono', value: "'IBM Plex Mono', Consolas, monospace" },
  { label: 'Ubuntu Mono', value: "'Ubuntu Mono', Consolas, monospace" },
  { label: '系统等宽', value: "ui-monospace, 'Cascadia Code', Consolas, 'Courier New', monospace" }
]
const readerFontSizes = [15, 16, 18]
const readerFontFamilies = [
  { label: '系统默认无衬线', value: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif" },
  { label: '苹方 / 微软雅黑 (现代黑体)', value: "'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif" },
  { label: '思源黑体 / 华文细黑', value: "'Source Han Sans SC', 'STHeiti', sans-serif" },
  { label: '官方宋体 / 中易宋体', value: "'Songti SC', 'SimSun', 'STSong', Georgia, serif" },
  { label: '官方楷体 / 华文楷体', value: "'STKaiti', 'KaiTi', 'Kaiti SC', serif" },
  { label: '仿宋 / 华文仿宋', value: "'FangSong', 'STFangsong', serif" }
]

const prefLanguage = ref(localStorage.getItem('localoj.editor.defaultLanguage') || 'CPP')
const prefFontSize = ref(Number(localStorage.getItem('localoj.editor.fontSize')) || 14)
const prefFontFamily = ref(localStorage.getItem('localoj.editor.fontFamily') || "'JetBrains Mono', 'Cascadia Code', Consolas, monospace")

const prefReaderFontSize = ref(Number(localStorage.getItem('localoj.reader.fontSize')) || 15)
const prefReaderFontFamily = ref(localStorage.getItem('localoj.reader.fontFamily') || "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif")

const templateLang = ref<Language>('CPP')
const currentTemplateText = ref('')

function loadTemplateForLang() {
  const custom = localStorage.getItem(`localoj.template.${templateLang.value}`)
  if (custom !== null) {
    currentTemplateText.value = custom
  } else {
    currentTemplateText.value = defaultTemplateFor(templateLang.value)
  }
}

function defaultTemplateFor(value: Language) {
  if (value === 'PYTHON') {
    return 'a, b = map(int, input().split())\nprint(a + b)\n'
  }
  if (value === 'JAVA') {
    return 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        int a = scanner.nextInt();\n        int b = scanner.nextInt();\n        System.out.println(a + b);\n    }\n}\n'
  }
  if (value === 'C') {
    return '#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf("%d %d", &a, &b);\n    printf("%d\\n", a + b);\n    return 0;\n}\n'
  }
  return '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n\n    int a, b;\n    cin >> a >> b;\n    cout << a + b << "\\n";\n    return 0;\n}\n'
}

function resetTemplateToDefault() {
  currentTemplateText.value = defaultTemplateFor(templateLang.value)
  localStorage.removeItem(`localoj.template.${templateLang.value}`)
  ElMessage.success('已恢复为默认 A+B 模板')
}

function savePreferences() {
  localStorage.setItem('localoj.editor.defaultLanguage', prefLanguage.value)
  localStorage.setItem('localoj.editor.fontSize', String(prefFontSize.value))
  localStorage.setItem('localoj.editor.fontFamily', prefFontFamily.value)
  localStorage.setItem('localoj.reader.fontSize', String(prefReaderFontSize.value))
  localStorage.setItem('localoj.reader.fontFamily', prefReaderFontFamily.value)

  // Save current active language's template
  localStorage.setItem(`localoj.template.${templateLang.value}`, currentTemplateText.value)

  ElMessage.success('编辑器与偏好设置已成功保存！')
}

const form = reactive({
  id: 0,
  username: '',
  email: '',
  displayName: '',
  avatarUrl: '',
  studentNo: '',
  major: '',
  role: 'STUDENT'
})

const passwordForm = reactive({
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const avatarFallback = computed(() => (form.displayName || form.username || 'U').slice(0, 1).toUpperCase())

// --- Learning Stats State & Logic ---
const stats = ref<UserStats>({
  heatmap: {},
  difficultyDistribution: {
    easySolved: 0,
    easyTotal: 0,
    mediumSolved: 0,
    mediumTotal: 0,
    hardSolved: 0,
    hardTotal: 0
  }
})

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

const solvedTotal = computed(() => stats.value.difficultyDistribution.easySolved + stats.value.difficultyDistribution.mediumSolved + stats.value.difficultyDistribution.hardSolved)
const problemsTotal = computed(() => stats.value.difficultyDistribution.easyTotal + stats.value.difficultyDistribution.mediumTotal + stats.value.difficultyDistribution.hardTotal)

const donutStrokeOffset = computed(() => {
  const total = problemsTotal.value
  if (total === 0) return 263.89
  const solved = solvedTotal.value
  const pct = Math.min(1, solved / total)
  return 263.89 * (1 - pct)
})

const heatmapTotalSubmissions = computed(() => {
  let sum = 0
  for (const date in stats.value.heatmap) {
    sum += stats.value.heatmap[date].totalCount
  }
  return sum
})

const heatmapTotalAc = computed(() => {
  let sum = 0
  for (const date in stats.value.heatmap) {
    sum += stats.value.heatmap[date].acCount
  }
  return sum
})

const heatmapAcRate = computed(() => {
  if (heatmapTotalSubmissions.value === 0) return '0.0%'
  return ((heatmapTotalAc.value / heatmapTotalSubmissions.value) * 100).toFixed(1) + '%'
})

const heatmapActiveDays = computed(() => {
  let count = 0
  for (const date in stats.value.heatmap) {
    if (stats.value.heatmap[date].totalCount > 0) {
      count++
    }
  }
  return count
})

function getPercentage(solved: number, total: number) {
  if (total === 0) return 0
  return Math.round((solved / total) * 100)
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
  if (ac === 0) return 1 // attempted but no AC
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

onMounted(() => {
  loadProfileAndStats()
  loadTemplateForLang()
})

async function loadProfileAndStats() {
  loading.value = true
  try {
    const profilePromise = fetchProfile()
    const statsPromise = fetchUserStats()
    const [profile, userStats] = await Promise.all([profilePromise, statsPromise])
    applyProfile(profile)
    if (userStats) {
      stats.value = userStats
      generateHeatmapData(userStats.heatmap)
    }
  } catch (error: any) {
    console.error('Failed to load profile or stats', error)
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const profile = await updateProfile({
      displayName: form.displayName,
      studentNo: form.studentNo || undefined,
      major: form.major || undefined
    })
    applyProfile(profile)
    ElMessage.success('资料已保存')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openAvatarPicker() {
  avatarInput.value?.click()
}

function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    input.value = ''
    return
  }
  
  selectedFile.value = file
  cropperVisible.value = true
  input.value = '' // Clear input so the same file can be selected again
}

async function onAvatarCropped(croppedFile: File) {
  cropperVisible.value = false
  uploading.value = true
  try {
    const profile = await uploadAvatar(croppedFile)
    applyProfile(profile)
    ElMessage.success('头像已更新')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '头像上传失败')
  } finally {
    uploading.value = false
    selectedFile.value = null
  }
}

async function sendCode() {
  sendingCode.value = true
  try {
    await requestPasswordChangeCode()
    ElMessage.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function submitPassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await changePassword({
      code: passwordForm.code,
      newPassword: passwordForm.newPassword
    })
    passwordForm.code = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('密码已修改')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '修改失败')
  } finally {
    changingPassword.value = false
  }
}

function applyProfile(profile: User) {
  form.id = profile.id
  form.username = profile.username
  form.email = profile.email || ''
  form.displayName = profile.displayName || profile.username
  form.avatarUrl = profile.avatarUrl || ''
  form.studentNo = profile.studentNo || ''
  form.major = profile.major || ''
  form.role = profile.role
  auth.setUser(profile)
}

function startCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
  countdownTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer)
      countdownTimer = undefined
    }
  }, 1000)
}

onUnmounted(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
  if (hoverTimeout) {
    clearTimeout(hoverTimeout)
  }
  if (hideTimeout) {
    clearTimeout(hideTimeout)
  }
})
</script>

<style scoped>
.stats-dashboard-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
  align-items: stretch;
}

.panel-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 12px;
}

.donut-chart-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
}

.donut-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  flex: 1;
  justify-content: center;
}

.donut-chart-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
}

.donut-svg {
  transform: rotate(0deg);
}

.donut-ring-active {
  transition: stroke-dashoffset 0.6s ease-out;
}

.donut-total {
  font-family: var(--el-font-family);
}

.donut-label {
  font-family: var(--el-font-family);
  letter-spacing: 0.5px;
}

.difficulty-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.difficulty-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.difficulty-header {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.difficulty-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  margin-right: 8px;
  line-height: 1;
}

.difficulty-badge.easy {
  background-color: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.difficulty-badge.medium {
  background-color: #fffbeb;
  color: #d97706;
  border: 1px solid #fde68a;
}

.difficulty-badge.hard {
  background-color: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.difficulty-count {
  color: var(--el-text-color-secondary);
}

.difficulty-count strong {
  color: var(--el-text-color-primary);
}

.difficulty-pct {
  margin-left: auto;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.progress-bar-track {
  height: 6px;
  background-color: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
  width: 100%;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-bar-fill.easy {
  background-color: #22c55e;
}

.progress-bar-fill.medium {
  background-color: #f59e0b;
}

.progress-bar-fill.hard {
  background-color: #ef4444;
}

/* Heatmap Card styling */
.heatmap-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
}

.heatmap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 16px;
  padding-bottom: 12px;
  flex-wrap: wrap;
  gap: 12px;
}

.heatmap-header .panel-title {
  margin: 0;
  border-bottom: none;
  padding-bottom: 0;
}

.heatmap-stats {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}

.heatmap-stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.heatmap-stat-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.heatmap-stat-value {
  font-size: 15px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.heatmap-stat-value.ac {
  color: #16a34a;
}

.heatmap-container {
  position: relative;
  overflow-x: auto;
  padding: 4px 0;
  flex: 1;
  display: flex;
  align-items: center;
}

.heatmap-svg {
  min-width: 700px;
}

.heatmap-cell {
  fill: #f1f5f9;
  transition: fill 0.15s ease;
  cursor: pointer;
}

.heatmap-cell:hover {
  stroke: #0f172a;
  stroke-width: 1.5;
}

.heatmap-cell.level-0 {
  fill: #f1f5f9;
}
 
.heatmap-cell.level-1 {
  fill: #ccfbf1; /* attempts but 0 AC - soft teal */
}
 
.heatmap-cell.level-2 {
  fill: #5eead4; /* low AC - light teal */
}
 
.heatmap-cell.level-3 {
  fill: #0d9488; /* medium AC - solid teal */
}
 
.heatmap-cell.level-4 {
  fill: #115e59; /* high AC - deep forest teal */
}

.heatmap-label {
  user-select: none;
  font-family: var(--el-font-family);
}

@media (max-width: 992px) {
  .stats-dashboard-grid {
    grid-template-columns: 1fr;
  }
}

.custom-heatmap-tooltip {
  position: absolute;
  transform: translate(-50%, -100%);
  pointer-events: none;
  z-index: 100;
  background: #0f172a;
  color: #ffffff;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.tooltip-arrow {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%) rotate(45deg);
  width: 8px;
  height: 8px;
  background: #0f172a;
  border-right: 1px solid rgba(255, 255, 255, 0.15);
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}

.fade-fast-enter-active,
.fade-fast-leave-active {
  transition: opacity 0.12s ease;
}
.fade-fast-enter-from,
.fade-fast-leave-to {
  opacity: 0;
}
</style>
