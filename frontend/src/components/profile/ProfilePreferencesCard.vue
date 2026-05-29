<template>
  <el-form class="profile-form" label-position="top">
    <!-- Editor Preferences Section -->
    <div class="pref-section-title">
      <svg class="pref-title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 15px; height: 15px; color: var(--primary);">
        <polyline points="16 18 22 12 16 6" />
        <polyline points="8 6 2 12 8 18" />
      </svg>
      <span>编辑器与代码首选项</span>
    </div>

    <div class="form-grid-3">
      <el-form-item label="默认编程语言">
        <el-select v-model="prefLanguage" style="width: 100%;">
          <el-option label="C++20 (O2)" value="CPP" />
          <el-option label="C++20 (O3)" value="CPP_O3" />
          <el-option label="C17 (O2)" value="C" />
          <el-option label="Python 3.12" value="PYTHON" />
          <el-option label="PyPy 3" value="PYPY3" />
          <el-option label="Java 21" value="JAVA" />
        </el-select>
      </el-form-item>

      <el-form-item label="编辑器默认字体">
        <el-select v-model="prefFontFamily" style="width: 100%;">
          <el-option v-for="font in fontFamilies" :key="font.value" :label="font.label" :value="font.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="编辑器默认字号">
        <el-select v-model="prefFontSize" style="width: 100%;">
          <el-option v-for="size in fontSizes" :key="size" :label="size + 'px'" :value="size" />
        </el-select>
      </el-form-item>
    </div>

    <!-- Reader Typography Section -->
    <div class="pref-section-title" style="margin-top: 24px;">
      <svg class="pref-title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 15px; height: 15px; color: var(--primary);">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
      </svg>
      <span>题面排版与阅读首选项</span>
    </div>

    <div class="form-grid-2">
      <el-form-item label="题面默认字体">
        <el-select v-model="prefReaderFontFamily" style="width: 100%;">
          <el-option v-for="font in readerFontFamilies" :key="font.value" :label="font.label" :value="font.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="题面默认字号">
        <el-select v-model="prefReaderFontSize" style="width: 100%;">
          <el-option v-for="size in readerFontSizes" :key="size" :label="size + 'px'" :value="size" />
        </el-select>
      </el-form-item>
    </div>

    <!-- Real-time Previews Grid (Editor & Reader side-by-side) -->
    <div class="preview-section-title">
      <svg class="pref-title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 15px; height: 15px; color: var(--primary);">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
        <circle cx="12" cy="12" r="3" />
      </svg>
      <span>排版实时预览</span>
    </div>

    <div class="previews-grid">
      <!-- Quick Font Preview Box -->
      <div class="preview-box">
        <div class="preview-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 13px; height: 13px; margin-right: 4px; vertical-align: -2.5px;">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
          </svg>
          <span>编辑器代码预览</span>
        </div>
        <pre :style="{ fontFamily: prefFontFamily, fontSize: prefFontSize + 'px', margin: 0, lineHeight: 1.5, color: 'var(--primary)', overflowX: 'auto', background: 'var(--bg-muted)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', minHeight: '120px' }">#include &lt;iostream&gt;

int main() {
    std::cout &lt;&lt; "Hello CodeRush!" &lt;&lt; std::endl;
    return 0;
}</pre>
      </div>

      <!-- Quick Reader Preview Box -->
      <div class="preview-box">
        <div class="preview-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width: 13px; height: 13px; margin-right: 4px; vertical-align: -2.5px;">
            <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
            <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
          </svg>
          <span>题面排版预览</span>
        </div>
        <div :style="{ fontFamily: prefReaderFontFamily, fontSize: prefReaderFontSize + 'px', margin: 0, lineHeight: 1.6, color: 'var(--text-primary)', overflowX: 'auto', background: 'var(--bg-muted)', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-color)', minHeight: '120px', boxSizing: 'border-box' }">
          <h3 style="margin: 0 0 6px; font-size: 1.15em; font-weight: 700;">A + B 问题</h3>
          <p style="margin: 0 0 6px;">输入两个整数 $A$ 和 $B$，计算它们的和并输出。</p>
          <strong style="color: var(--primary); font-weight: 600;">[输入格式]</strong> 一行输入两个整数，以空格分隔。
        </div>
      </div>
    </div>

    <div class="form-actions">
      <el-button type="primary" :icon="Check" @click="savePreferences">保存偏好设置</el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

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

function savePreferences() {
  localStorage.setItem('localoj.editor.defaultLanguage', prefLanguage.value)
  localStorage.setItem('localoj.editor.fontSize', String(prefFontSize.value))
  localStorage.setItem('localoj.editor.fontFamily', prefFontFamily.value)
  localStorage.setItem('localoj.reader.fontSize', String(prefReaderFontSize.value))
  localStorage.setItem('localoj.reader.fontFamily', prefReaderFontFamily.value)

  ElMessage.success('偏好设置已成功保存！')

  // Dispatch active event to notify reader fonts updates locally in current app views
  window.dispatchEvent(new Event('localoj-preferences-saved'))
}
</script>

<style scoped>
.profile-form {
  padding-top: 10px;
}

.pref-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 8px;
}

.pref-section-title .icon {
  font-size: 16px;
}

.preview-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 24px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 8px;
}

.preview-section-title .icon {
  font-size: 16px;
}

.form-grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.previews-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.preview-box {
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-header {
  font-size: 12.5px;
  color: var(--text-secondary);
  font-weight: 600;
  font-family: sans-serif;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

@media (max-width: 768px) {
  .form-grid-3,
  .form-grid-2,
  .previews-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
