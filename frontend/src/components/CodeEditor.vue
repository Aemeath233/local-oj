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
    theme: 'vs',
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

function toMonacoLanguage(language: Language) {
  return {
    C: 'c',
    CPP: 'cpp',
    PYTHON: 'python',
    JAVA: 'java'
  }[language]
}
</script>
