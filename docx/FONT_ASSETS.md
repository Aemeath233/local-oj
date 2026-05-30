# CodeRush OJ - Local Offline Font Asset Configuration

This document specifies the design, configuration, and maintenance details of the system's local offline font loading system.

---

## 🎨 Design Goal

CodeRush OJ is deployed inside isolated, pure-offline enterprise networks where external Internet access is restricted. Standard browser links targeting font CDNs (like `fonts.googleapis.com` or `fonts.gstatic.com`) fail to load, resulting in slow page loading times and generic fallback typography.

To ensure visual excellence, premium appearance, and complete offline capability, all fonts are bundled directly in the frontend build artifact and served locally.

---

## 📦 Installed Font Packages

All fonts are managed as standard NPM dependencies and compiled locally by Vite into standard `.woff2` files:

1. **`@fontsource/plus-jakarta-sans`**: Main application sans-serif typography.
2. **`@fontsource/jetbrains-mono`**: Primary code workspace font.
3. **`@fontsource/fira-code`**: Second code workspace font.
4. **`@fontsource/source-code-pro`**: Third code workspace font.
5. **`@fontsource/ubuntu-mono`**: Fourth code workspace font.
6. **`@fontsource/ibm-plex-mono`**: Fifth code workspace font.

---

## 🔌 Stylesheet Imports (`styles.css`)

Fonts are imported at the very top of `frontend/src/styles.css` using localized stylesheets:

```css
/* Local Plus Jakarta Sans Weights */
@import '@fontsource/plus-jakarta-sans/300.css';
@import '@fontsource/plus-jakarta-sans/400.css';
@import '@fontsource/plus-jakarta-sans/500.css';
@import '@fontsource/plus-jakarta-sans/600.css';
@import '@fontsource/plus-jakarta-sans/700.css';
@import '@fontsource/plus-jakarta-sans/800.css';

/* Local Editor Monospace Fonts */
@import '@fontsource/jetbrains-mono/400.css';
@import '@fontsource/jetbrains-mono/500.css';
@import '@fontsource/jetbrains-mono/700.css';
@import '@fontsource/fira-code/400.css';
@import '@fontsource/fira-code/500.css';
@import '@fontsource/fira-code/700.css';
@import '@fontsource/source-code-pro/400.css';
@import '@fontsource/source-code-pro/500.css';
@import '@fontsource/source-code-pro/700.css';
@import '@fontsource/ubuntu-mono/400.css';
@import '@fontsource/ubuntu-mono/700.css';
@import '@fontsource/ibm-plex-mono/400.css';
@import '@fontsource/ibm-plex-mono/500.css';
@import '@fontsource/ibm-plex-mono/700.css';
```

---

## 🛠️ User Interface Preferences Configuration

In `frontend/src/views/ProfileView.vue`, standard and calligraphic fonts are mapped into selection arrays.

### 1. Editor Coding Fonts (Monospace)
Users can choose their preferred local coding font:
- **JetBrains Mono**: `'JetBrains Mono', 'Cascadia Code', Consolas, monospace`
- **Fira Code**: `'Fira Code', 'JetBrains Mono', Consolas, monospace`
- **Source Code Pro**: `'Source Code Pro', 'JetBrains Mono', Consolas, monospace`
- **IBM Plex Mono**: `'IBM Plex Mono', Consolas, monospace`
- **Ubuntu Mono**: `'Ubuntu Mono', Consolas, monospace`
- **System Monospace**: `ui-monospace, 'Cascadia Code', Consolas, 'Courier New', monospace`

### 2. Chinese-Friendly Reading Fonts (Sans/Serif/Calligraphy)
The problem statement reader offers tailored typography for high readability:
- **System Default**: `system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif`
- **Modern Clean Sans (苹方 / 微软雅黑)**: `'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif`
- **Elegant Heiti (思源黑体 / 华文细黑)**: `'Source Han Sans SC', 'STHeiti', sans-serif`
- **Traditional Serif (官方宋体 / 中易宋体)**: `'Songti SC', 'SimSun', 'STSong', Georgia, serif`
- **Calligraphic Kaiti (官方楷体 / 华文楷体)**: `'STKaiti', 'KaiTi', 'Kaiti SC', serif`
- **Classic Fangsong (仿宋 / 华文仿宋)**: `'FangSong', 'STFangsong', serif`
