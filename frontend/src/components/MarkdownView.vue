<template>
  <div class="markdown-body" v-html="html" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import katex from 'katex'
import 'katex/dist/katex.min.css'

const props = defineProps<{
  source?: string | null
}>()

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
    try {
      return katex.renderToString(tokens[idx].content, {
        displayMode: false,
        throwOnError: false
      })
    } catch (err: any) {
      return `<span class="katex-error">${tokens[idx].content}</span>`
    }
  }

  md.renderer.rules.math_inline_block = (tokens: any[], idx: number) => {
    try {
      return katex.renderToString(tokens[idx].content, {
        displayMode: true,
        throwOnError: false
      })
    } catch (err: any) {
      return `<span class="katex-error">${tokens[idx].content}</span>`
    }
  }

  md.renderer.rules.math_block = (tokens: any[], idx: number) => {
    try {
      return `<div class="math-block">${katex.renderToString(tokens[idx].content, {
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
