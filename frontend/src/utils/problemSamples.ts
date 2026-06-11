export interface ProblemSample {
  inputText: string
  expectedOutput: string
}

export type ProblemStatementSegment =
  | { type: 'markdown'; content: string }
  | { type: 'sample'; sample: ProblemSample }

const SAMPLE_CONTAINER_OPEN = /^\s*:::\s*sample(?:\s+.*)?\s*$/i
const SAMPLE_CONTAINER_CLOSE = /^\s*:::\s*$/
const SAMPLE_TAG_OPEN = /^\s*<sample(?:\s[^>]*)?>\s*$/i
const SAMPLE_TAG_CLOSE = /^\s*<\/sample>\s*$/i

export function parseProblemStatement(source?: string | null): ProblemStatementSegment[] {
  const text = normalizeNewlines(source || '')
  if (!text) {
    return []
  }

  const lines = text.split('\n')
  const segments: ProblemStatementSegment[] = []
  const markdownBuffer: string[] = []

  const flushMarkdown = () => {
    const content = markdownBuffer.join('\n')
    markdownBuffer.length = 0
    if (content.trim()) {
      segments.push({ type: 'markdown', content })
    }
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const isColonContainer = SAMPLE_CONTAINER_OPEN.test(line)
    const isTagContainer = SAMPLE_TAG_OPEN.test(line)
    if (!isColonContainer && !isTagContainer) {
      markdownBuffer.push(line)
      continue
    }

    const closePattern = isColonContainer ? SAMPLE_CONTAINER_CLOSE : SAMPLE_TAG_CLOSE
    const { endIndex, blockLines } = findContainerEnd(lines, i, closePattern)

    if (endIndex === -1) {
      markdownBuffer.push(line)
      continue
    }

    const sample = parseSampleBlock(blockLines.join('\n'))
    if (!sample) {
      markdownBuffer.push(line, ...blockLines, lines[endIndex])
      i = endIndex
      continue
    }

    flushMarkdown()
    segments.push({ type: 'sample', sample })
    i = endIndex
  }

  flushMarkdown()
  return segments
}

export function extractProblemSamples(source?: string | null): ProblemSample[] {
  return parseProblemStatement(source)
    .filter((segment): segment is { type: 'sample'; sample: ProblemSample } => segment.type === 'sample')
    .map(segment => segment.sample)
}

export function firstProblemSampleInput(source?: string | null): string {
  return extractProblemSamples(source).find(sample => sample.inputText.trim().length > 0)?.inputText || ''
}

export function countProblemSamples(source?: string | null): number {
  return extractProblemSamples(source).length
}

function parseSampleBlock(block: string): ProblemSample | null {
  const tagged = parseTaggedSample(block)
  if (tagged) {
    return tagged
  }

  const fenced = parseFencedSample(block)
  if (fenced) {
    return fenced
  }

  return null
}

function findContainerEnd(
  lines: string[],
  openIndex: number,
  closePattern: RegExp
): { endIndex: number; blockLines: string[] } {
  const blockLines: string[] = []
  let activeFence = ''

  for (let j = openIndex + 1; j < lines.length; j++) {
    const line = lines[j]

    if (activeFence) {
      blockLines.push(line)
      if (isClosingFence(line, activeFence)) {
        activeFence = ''
      }
      continue
    }

    if (closePattern.test(line)) {
      return { endIndex: j, blockLines }
    }

    blockLines.push(line)
    const openingFence = line.match(/^\s*(`{3,}|~{3,})/)
    if (openingFence) {
      activeFence = openingFence[1]
    }
  }

  return { endIndex: -1, blockLines }
}

function parseTaggedSample(block: string): ProblemSample | null {
  const inputText = extractTagContent(block, ['sample-input', 'input', 'stdin'])
  const expectedOutput = extractTagContent(block, ['sample-output', 'output', 'stdout', 'answer', 'expected-output'])
  if (inputText === null && expectedOutput === null) {
    return null
  }
  return {
    inputText: inputText || '',
    expectedOutput: expectedOutput || ''
  }
}

function parseFencedSample(block: string): ProblemSample | null {
  const lines = normalizeNewlines(block).split('\n')
  const unlabeledBlocks: string[] = []
  let inputText = ''
  let expectedOutput = ''

  for (let i = 0; i < lines.length; i++) {
    const open = lines[i].match(/^\s*(`{3,}|~{3,})\s*([^\r\n]*)$/)
    if (!open) {
      continue
    }

    const fence = open[1]
    const info = open[2] || ''
    const contentLines: string[] = []
    let endIndex = -1
    for (let j = i + 1; j < lines.length; j++) {
      if (isClosingFence(lines[j], fence)) {
        endIndex = j
        break
      }
      contentLines.push(lines[j])
    }

    if (endIndex === -1) {
      continue
    }

    const content = stripOuterBlankLines(contentLines.join('\n'))
    const kind = sampleFenceKind(info)
    if (kind === 'input') {
      inputText = content
    } else if (kind === 'output') {
      expectedOutput = content
    } else {
      unlabeledBlocks.push(content)
    }
    i = endIndex
  }

  if (!inputText && unlabeledBlocks.length > 0) {
    inputText = unlabeledBlocks[0]
  }
  if (!expectedOutput && unlabeledBlocks.length > 1) {
    expectedOutput = unlabeledBlocks[1]
  }

  if (!inputText && !expectedOutput) {
    return null
  }

  return { inputText, expectedOutput }
}

function extractTagContent(block: string, tagNames: string[]): string | null {
  for (const tagName of tagNames) {
    const escaped = tagName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const pattern = new RegExp(`<${escaped}\\b[^>]*>([\\s\\S]*?)<\\/${escaped}>`, 'i')
    const match = block.match(pattern)
    if (match) {
      return stripOuterBlankLines(match[1])
    }
  }
  return null
}

function sampleFenceKind(info: string): 'input' | 'output' | null {
  const normalized = info.toLowerCase().replace(/[\s_-]/g, '')
  if (normalized.includes('input') || normalized.includes('stdin') || normalized.includes('输入')) {
    return 'input'
  }
  if (
    normalized.includes('output') ||
    normalized.includes('stdout') ||
    normalized.includes('answer') ||
    normalized.includes('expected') ||
    normalized.includes('输出') ||
    normalized.includes('答案')
  ) {
    return 'output'
  }
  return null
}

function isClosingFence(line: string, openingFence: string): boolean {
  const trimmed = line.trim()
  if (!trimmed) {
    return false
  }
  const marker = openingFence[0]
  const pattern = marker === '`' ? /^`+\s*$/ : /^~+\s*$/
  const closingFence = trimmed.match(marker === '`' ? /^`+/ : /^~+/)?.[0] || ''
  return pattern.test(trimmed) && closingFence.length >= openingFence.length
}

function normalizeNewlines(value: string): string {
  return value.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

function stripOuterBlankLines(value: string): string {
  return normalizeNewlines(value).replace(/^\n+/, '').replace(/\n+$/, '')
}
