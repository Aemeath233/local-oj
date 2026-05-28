<template>
  <div class="code-editor">
    <div ref="container" class="code-editor-canvas" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import type { Language } from '../types'
import { useThemeStore } from '../stores/theme'

const props = defineProps<{
  modelValue: string
  language: Language
  readOnly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const container = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

const themeStore = useThemeStore()

const fontSize = ref(Number(localStorage.getItem('localoj.editor.fontSize')) || 14)
const fontFamily = ref(localStorage.getItem('localoj.editor.fontFamily') || "'JetBrains Mono', 'Cascadia Code', Consolas, monospace")

window.MonacoEnvironment = {
  getWorker() {
    return new EditorWorker()
  }
}

onMounted(() => {
  if (!container.value) {
    return
  }
  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: toMonacoLanguage(props.language),
    theme: themeStore.isDark ? 'vs-dark' : 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: fontSize.value,
    fontFamily: fontFamily.value,
    lineHeight: Math.round(fontSize.value * 1.5),
    scrollBeyondLastLine: false,
    tabSize: 4,
    readOnly: props.readOnly ?? false,
    domReadOnly: props.readOnly ?? false
  })
  if (!props.readOnly) {
    editor.onDidChangeModelContent(() => {
      emit('update:modelValue', editor?.getValue() ?? '')
    })
  }
})

watch(
  () => themeStore.isDark,
  (isDark) => {
    monaco.editor.setTheme(isDark ? 'vs-dark' : 'vs')
  }
)

watch(
  () => props.modelValue,
  (value) => {
    if (editor && editor.getValue() !== value) {
      editor.setValue(value)
    }
  }
)

watch(
  () => props.language,
  (language) => {
    const model = editor?.getModel()
    if (model) {
      monaco.editor.setModelLanguage(model, toMonacoLanguage(language))
    }
  }
)

onBeforeUnmount(() => {
  editor?.dispose()
})

function setValuePreservingHistory(value: string) {
  if (!editor) return
  const model = editor.getModel()
  if (model) {
    editor.executeEdits('format', [{
      range: model.getFullModelRange(),
      text: value,
      forceMoveMarkers: true
    }])
  }
}

defineExpose({
  setValuePreservingHistory
})

function toMonacoLanguage(language: Language) {
  return {
    C: 'c',
    CPP: 'cpp',
    PYTHON: 'python',
    JAVA: 'java',
    PYPY3: 'python',
    CPP_O3: 'cpp'
  }[language]
}
</script>
