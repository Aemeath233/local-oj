<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import type * as KaTeXType from 'katex'
import { loadKaTeX } from '../utils/katexLoader'

const props = defineProps<{
  source?: string | null
}>()

const katexInstance = ref<typeof KaTeXType | null>((window as any).katex || null)

onMounted(() => {
  if (!katexInstance.value) {
    loadKaTeX()
      .then((katex) => {
        katexInstance.value = katex
      })
      .catch((err) => {
        console.error('Failed to load KaTeX asynchronously', err)
      })
  }
})

function mathPlugin(md: any) {
  // Inline math: $...$
  md.inline.ruler.after('escape', 'math_inline', (state: any, silent: boolean) => {
    let start = state.pos
    if (state.src.charCodeAt(start) !== 0x24 /* $ */) {
      return false
    }

    // Check if it's block math written inline like $$...$$
    let isBlock = false
    if (state.src.charCodeAt(start + 1) === 0x24) {
      isBlock = true
    }

    let marker = isBlock ? '$$' : '$'
    let markerLen = marker.length

    // Check spaces for inline single $
    if (!isBlock) {
      let nextChar = state.src.charAt(start + 1)
      if (nextChar === ' ' || nextChar === '\t' || nextChar === '\n' || nextChar === '\r') {
        return false
      }
    }

    // Find the closing marker
    let pos = start + markerLen
    let found = false
    while (pos < state.src.length) {
      if (state.src.charCodeAt(pos) === 0x5C /* \ */) {
        pos += 2
        continue
      }
      if (state.src.startsWith(marker, pos)) {
        if (!isBlock) {
          let prevChar = state.src.charAt(pos - 1)
          if (prevChar === ' ' || prevChar === '\t' || prevChar === '\n' || prevChar === '\r') {
            pos += markerLen
            continue
          }
        }
        found = true
        break
      }
      pos++
    }

    if (!found) {
      return false
    }

    if (!silent) {
      let content = state.src.slice(start + markerLen, pos)
      let token = state.push(isBlock ? 'math_inline_block' : 'math_inline', 'math', 0)
      token.markup = marker
      token.content = content.trim()
    }

    state.pos = pos + markerLen
    return true
  })

  // Block math: $$ ... $$
  md.block.ruler.after('blockquote', 'math_block', (state: any, startLine: number, endLine: number, silent: boolean) => {
    let pos = state.bMarks[startLine] + state.tShift[startLine]
    let max = state.eMarks[startLine]

    if (pos + 2 > max || state.src.slice(pos, pos + 2) !== '$$') {
      return false
    }

    let firstLine = state.src.slice(pos + 2, max)

    if (silent) {
      return true
    }

    if (firstLine.trim().endsWith('$$') && firstLine.trim().length >= 2) {
      let content = firstLine.trim().slice(0, -2)
      let token = state.push('math_block', 'math', 0)
      token.block = true
      token.markup = '$$'
      token.content = content.trim()
      token.map = [startLine, startLine + 1]
      state.line = startLine + 1
      return true
    }

    let nextLine = startLine
    let contentLines = [firstLine]
    let found = false

    while (true) {
      nextLine++
      if (nextLine >= endLine) {
        break
      }

      pos = state.bMarks[nextLine] + state.tShift[nextLine]
      max = state.eMarks[nextLine]

      let lineText = state.src.slice(pos, max)
      if (lineText.trim() === '$$') {
        found = true
        break
      }
      contentLines.push(lineText)
    }

    let token = state.push('math_block', 'math', 0)
    token.block = true
    token.markup = '$$'
    token.content = contentLines.join('\n').trim()
    token.map = [startLine, nextLine + (found ? 1 : 0)]

    state.line = nextLine + (found ? 1 : 0)
    return true
  })

  // Renderers
  md.renderer.rules.math_inline = (tokens: any[], idx: number) => {
    const k = katexInstance.value
    if (!k) {
      return `<code class="math-loading">${tokens[idx].content}</code>`
    }
    try {
      return k.renderToString(tokens[idx].content, {
        displayMode: false,
        throwOnError: false
      })
    } catch (err: any) {
      return `<span class="katex-error">${tokens[idx].content}</span>`
    }
  }

  md.renderer.rules.math_inline_block = (tokens: any[], idx: number) => {
    const k = katexInstance.value
    if (!k) {
      return `<code class="math-loading">${tokens[idx].content}</code>`
    }
    try {
      return k.renderToString(tokens[idx].content, {
        displayMode: true,
        throwOnError: false
      })
    } catch (err: any) {
      return `<span class="katex-error">${tokens[idx].content}</span>`
    }
  }

  md.renderer.rules.math_block = (tokens: any[], idx: number) => {
    const k = katexInstance.value
    if (!k) {
      return `<div class="math-block"><pre class="math-loading">${tokens[idx].content}</pre></div>`
    }
    try {
      return `<div class="math-block">${k.renderToString(tokens[idx].content, {
        displayMode: true,
        throwOnError: false
      })}</div>`
    } catch (err: any) {
      return `<div class="math-block katex-error">${tokens[idx].content}</div>`
    }
  }
}

const markdown = new MarkdownIt({
  breaks: false,
  html: false,
  linkify: true,
  typographer: false
}).use(mathPlugin)

const defaultLinkOpen = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  const token = tokens[index]
  const hrefIndex = token.attrIndex('href')
  if (hrefIndex >= 0) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noreferrer noopener')
  }
  return defaultLinkOpen ? defaultLinkOpen(tokens, index, options, env, self) : self.renderToken(tokens, index, options)
}

const html = computed(() => DOMPurify.sanitize(markdown.render(props.source ?? ''), {
  USE_PROFILES: { html: true, mathMl: true }
}))
</script>

<style scoped>
.math-loading {
  font-family: inherit;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 0.9em;
  border: 1px dashed var(--el-border-color-light);
  display: inline-block;
  animation: pulse 1.5s infinite ease-in-out;
}

div.math-block .math-loading {
  display: block;
  padding: 12px;
  text-align: center;
  margin: 8px 0;
  overflow-x: auto;
}

@keyframes pulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}
</style>
