<template>
  <div class="code-diff-editor">
    <div ref="container" class="code-diff-editor-canvas" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import type { Language } from '../types'

const props = defineProps<{
  original: string
  modified: string
  language: Language
}>()

const container = ref<HTMLElement | null>(null)
let diffEditor: monaco.editor.IStandaloneDiffEditor | null = null

const fontSize = ref(Number(localStorage.getItem('localoj.editor.fontSize')) || 14)
const fontFamily = ref(localStorage.getItem('localoj.editor.fontFamily') || "'JetBrains Mono', 'Cascadia Code', Consolas, monospace")

window.MonacoEnvironment = {
  getWorker() {
    return new EditorWorker()
  }
}

onMounted(() => {
  if (!container.value) return

  diffEditor = monaco.editor.createDiffEditor(container.value, {
    theme: 'vs',
    automaticLayout: true,
    fontSize: fontSize.value,
    fontFamily: fontFamily.value,
    lineHeight: Math.round(fontSize.value * 1.5),
    scrollBeyondLastLine: false,
    readOnly: true,
    renderSideBySide: true,
    minimap: { enabled: false }
  })

  updateModels()
})

function toMonacoLanguage(language: Language) {
  return {
    C: 'c',
    CPP: 'cpp',
    PYTHON: 'python',
    JAVA: 'java'
  }[language] || 'cpp'
}

function updateModels() {
  if (!diffEditor) return

  const originalModel = monaco.editor.createModel(
    props.original,
    toMonacoLanguage(props.language)
  )
  const modifiedModel = monaco.editor.createModel(
    props.modified,
    toMonacoLanguage(props.language)
  )

  diffEditor.setModel({
    original: originalModel,
    modified: modifiedModel
  })
}

watch(
  [() => props.original, () => props.modified, () => props.language],
  () => {
    const oldModels = diffEditor?.getModel()
    if (oldModels) {
      oldModels.original?.dispose()
      oldModels.modified?.dispose()
    }
    updateModels()
  }
)

onBeforeUnmount(() => {
  const oldModels = diffEditor?.getModel()
  if (oldModels) {
    oldModels.original?.dispose()
    oldModels.modified?.dispose()
  }
  diffEditor?.dispose()
})
</script>

<style scoped>
.code-diff-editor {
  width: 100%;
  height: 100%;
}
.code-diff-editor-canvas {
  width: 100%;
  height: 100%;
}
</style>
