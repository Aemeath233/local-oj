import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// Automatically copy Monaco Editor static files to public directory for local fallback
function copyMonaco() {
  const srcDir = path.resolve(__dirname, 'node_modules/monaco-editor/min/vs')
  const destDir = path.resolve(__dirname, 'public/libs/monaco-editor/vs')
  
  if (fs.existsSync(srcDir) && !fs.existsSync(destDir)) {
    const copyDir = (src: string, dest: string) => {
      fs.mkdirSync(dest, { recursive: true })
      const entries = fs.readdirSync(src, { withFileTypes: true })
      for (const entry of entries) {
        const srcPath = path.join(src, entry.name)
        const destPath = path.join(dest, entry.name)
        if (entry.isDirectory()) {
          copyDir(srcPath, destPath)
        } else {
          fs.copyFileSync(srcPath, destPath)
        }
      }
    }
    console.log('Copying Monaco Editor static files for local fallback...')
    copyDir(srcDir, destDir)
    console.log('Monaco Editor static files copied successfully!')
  }
}

// Automatically copy KaTeX static files to public directory for local fallback
function copyKaTeX() {
  const srcDir = path.resolve(__dirname, 'node_modules/katex/dist')
  const destDir = path.resolve(__dirname, 'public/libs/katex')
  
  if (fs.existsSync(srcDir) && !fs.existsSync(destDir)) {
    const copyDir = (src: string, dest: string) => {
      fs.mkdirSync(dest, { recursive: true })
      const entries = fs.readdirSync(src, { withFileTypes: true })
      for (const entry of entries) {
        const srcPath = path.join(src, entry.name)
        const destPath = path.join(dest, entry.name)
        if (entry.isDirectory()) {
          copyDir(srcPath, destPath)
        } else {
          fs.copyFileSync(srcPath, destPath)
        }
      }
    }
    console.log('Copying KaTeX static files for local fallback...')
    copyDir(srcDir, destDir)
    console.log('KaTeX static files copied successfully!')
  }
}

copyMonaco()
copyKaTeX()

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('monaco-editor')) {
              return 'vendor-monaco'
            }
            if (id.includes('katex')) {
              return 'vendor-katex'
            }
            return 'vendor-core'
          }
        }
      }
    }
  }
})
