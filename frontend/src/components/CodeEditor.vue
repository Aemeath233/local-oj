<template>
  <div class="code-editor">
    <div ref="container" class="code-editor-canvas" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type * as MonacoType from 'monaco-editor'
import { loadMonaco } from '../utils/monacoLoader'
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
let editor: MonacoType.editor.IStandaloneCodeEditor | null = null
let monaco: typeof MonacoType | null = null

const themeStore = useThemeStore()

const fontSize = ref(Number(localStorage.getItem('coderushoj.editor.fontSize')) || 14)
const fontFamily = ref(localStorage.getItem('coderushoj.editor.fontFamily') || "'JetBrains Mono', 'Cascadia Code', Consolas, monospace")

onMounted(() => {
  if (!container.value) {
    return
  }

  loadMonaco().then((monacoInstance) => {
    monaco = monacoInstance
    if (!container.value) return

    const createdEditor = monacoInstance.editor.create(container.value, {
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
      domReadOnly: props.readOnly ?? false,
      // Completely disable autocomplete/code suggestions as requested by user
      quickSuggestions: false,
      suggestOnTriggerCharacters: false,
      acceptSuggestionOnEnter: 'off',
      tabCompletion: 'off',
      wordBasedSuggestions: 'off',
      parameterHints: { enabled: false }
    })
    editor = createdEditor

    if (!props.readOnly) {
      createdEditor.onDidChangeModelContent(() => {
        emit('update:modelValue', createdEditor.getValue())
      })
    }
    
    if (document.fonts) {
      document.fonts.ready.then(() => {
        if (monaco && typeof (monaco.editor as any).remeasureTemplates === 'function') {
          (monaco.editor as any).remeasureTemplates()
        }
        editor?.layout()
      })
    }
  }).catch((err) => {
    console.error('Failed to initialize Monaco Editor', err)
  })
})

watch(
  () => themeStore.isDark,
  (isDark) => {
    if (monaco) {
      monaco.editor.setTheme(isDark ? 'vs-dark' : 'vs')
    }
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
    if (model && monaco) {
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
