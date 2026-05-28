<template>
  <el-form class="profile-form" label-position="top" style="margin-top: 10px;">
    <div class="form-grid">
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
            <el-option label="C++20 (O2)" value="CPP" />
            <el-option label="C++20 (O3)" value="CPP_O3" />
            <el-option label="C17 (O2)" value="C" />
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
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CodeEditor from '../CodeEditor.vue'
import type { Language } from '../../types'

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

onMounted(() => {
  loadTemplateForLang()
})

function loadTemplateForLang() {
  const custom = localStorage.getItem(`localoj.template.${templateLang.value}`)
  if (custom !== null) {
    currentTemplateText.value = custom
  } else {
    currentTemplateText.value = defaultTemplateFor(templateLang.value)
  }
}

function defaultTemplateFor(value: Language) {
  if (value === 'PYTHON' || value === 'PYPY3') {
    return `import sys

# Fast I/O
input = lambda: sys.stdin.readline().rstrip("\\r\\n")

def solve():
    # Write your code here
    pass

def main():
    solve()
    
    # Multi test cases
    # t = int(input())
    # for _ in range(t):
    #     solve()

if __name__ == "__main__":
    main()
`
  }
  if (value === 'JAVA') {
    return `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Fast I/O
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter out = new PrintWriter(new BufferedOutputStream(System.out));
        
        solve(br, out);
        
        out.flush();
    }
    
    private static void solve(BufferedReader br, PrintWriter out) throws IOException {
        // Write your code here
    }
}
`
  }
  if (value === 'C') {
    return `#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

void solve(void) {
    // Write your code here
}

int main(void) {
    int t = 1;
    // if (scanf("%d", &t) != EOF)
    while (t--) {
        solve();
    }
    return 0;
}
`
  }
  return `#include <bits/stdc++.h>
using namespace std;

void solve() {
    // Write your code here
}

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    int t = 1;
    // cin >> t; // Uncomment if there are multiple test cases
    while (t--) {
        solve();
    }

    return 0;
}
`
}

function resetTemplateToDefault() {
  currentTemplateText.value = defaultTemplateFor(templateLang.value)
  localStorage.removeItem(`localoj.template.${templateLang.value}`)
  ElMessage.success('已恢复为当前语言默认算法模板')
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
  
  // Dispatch active event to notify reader fonts updates locally in current app views
  window.dispatchEvent(new Event('localoj-preferences-saved'))
}
</script>

<style scoped>
.profile-form {
  padding-top: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-light);
  padding-top: 16px;
}
</style>
