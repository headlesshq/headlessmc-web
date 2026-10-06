// Minimal ANSI escape sequence renderer for HeadlessMc's (picocli/jline) colored output.

export interface AnsiStyle {
  fg?: string
  bg?: string
  bold?: boolean
  dim?: boolean
  italic?: boolean
  underline?: boolean
  inverse?: boolean
}

export interface AnsiSegment {
  text: string
  style: AnsiStyle
}

const BASIC = ['#3b3b3b', '#e05561', '#8cc265', '#d18f52', '#4aa5f0', '#c162de', '#42b3c2', '#d7dae0']
const BRIGHT = ['#7f848e', '#ff616e', '#a5e075', '#f0a45d', '#4dc4ff', '#de73ff', '#4cd1e0', '#e6e6e6']

// CSI (ESC [ ... final byte), OSC (ESC ] ... BEL/ST) and other two character escapes
const ESCAPE = /\u001b(?:\[([0-9;?]*)([@-~])|\][^\u0007\u001b]*(?:\u0007|\u001b\\)|[@-Z\\-_])/g

function color256(n: number): string | undefined {
  if (n < 0 || n > 255 || Number.isNaN(n)) {
    return undefined
  }
  if (n < 8) {
    return BASIC[n]
  }
  if (n < 16) {
    return BRIGHT[n - 8]
  }
  if (n < 232) {
    const i = n - 16
    const level = (v: number) => (v === 0 ? 0 : 55 + v * 40)
    return `rgb(${level(Math.floor(i / 36))}, ${level(Math.floor(i / 6) % 6)}, ${level(i % 6)})`
  }
  const gray = 8 + (n - 232) * 10
  return `rgb(${gray}, ${gray}, ${gray})`
}

function applySgr(style: AnsiStyle, params: number[]): AnsiStyle {
  const next: AnsiStyle = { ...style }
  for (let i = 0; i < params.length; i++) {
    const p = params[i]
    if (p === 0) {
      for (const key of Object.keys(next) as (keyof AnsiStyle)[]) {
        delete next[key]
      }
    } else if (p === 1) next.bold = true
    else if (p === 2) next.dim = true
    else if (p === 3) next.italic = true
    else if (p === 4) next.underline = true
    else if (p === 7) next.inverse = true
    else if (p === 22) {
      delete next.bold
      delete next.dim
    } else if (p === 23) delete next.italic
    else if (p === 24) delete next.underline
    else if (p === 27) delete next.inverse
    else if (p >= 30 && p <= 37) next.fg = BASIC[p - 30]
    else if (p === 39) delete next.fg
    else if (p >= 40 && p <= 47) next.bg = BASIC[p - 40]
    else if (p === 49) delete next.bg
    else if (p >= 90 && p <= 97) next.fg = BRIGHT[p - 90]
    else if (p >= 100 && p <= 107) next.bg = BRIGHT[p - 100]
    else if (p === 38 || p === 48) {
      let color: string | undefined
      if (params[i + 1] === 5) {
        color = color256(params[i + 2])
        i += 2
      } else if (params[i + 1] === 2) {
        color = `rgb(${params[i + 2] ?? 0}, ${params[i + 3] ?? 0}, ${params[i + 4] ?? 0})`
        i += 4
      }
      if (color) {
        if (p === 38) next.fg = color
        else next.bg = color
      }
    }
  }
  return next
}

/** Parses text containing ANSI escape sequences into styled segments, dropping unsupported sequences. */
export function parseAnsi(input: string): AnsiSegment[] {
  const segments: AnsiSegment[] = []
  let style: AnsiStyle = {}
  let last = 0
  const push = (text: string) => {
    if (!text) return
    const previous = segments[segments.length - 1]
    if (previous && previous.style === style) {
      previous.text += text
    } else {
      segments.push({ text, style })
    }
  }
  for (const match of input.matchAll(ESCAPE)) {
    push(input.slice(last, match.index))
    last = match.index + match[0].length
    if (match[2] === 'm') {
      const params = (match[1] ?? '')
        .split(';')
        .filter((s) => s !== '' && !s.startsWith('?'))
        .map(Number)
      style = applySgr(style, params.length === 0 ? [0] : params)
    }
  }
  push(input.slice(last))
  return segments
}

/** Removes all ANSI escape sequences. */
export function stripAnsi(input: string): string {
  return input.replace(ESCAPE, '')
}

/** CSS for a segment style. */
export function styleToCss(style: AnsiStyle): Record<string, string> {
  const css: Record<string, string> = {}
  let fg = style.fg
  let bg = style.bg
  if (style.inverse) {
    ;[fg, bg] = [bg ?? 'var(--bg)', fg ?? 'var(--fg)']
  }
  if (fg) css.color = fg
  if (bg) css.backgroundColor = bg
  if (style.bold) css.fontWeight = 'bold'
  if (style.dim) css.opacity = '0.7'
  if (style.italic) css.fontStyle = 'italic'
  if (style.underline) css.textDecoration = 'underline'
  return css
}

const URL_PATTERN = /https?:\/\/[^\s"'<>]+[^\s"'<>.,;:!?)\]]/g

export interface TextPart {
  text: string
  url?: string
}

/** Splits text into plain parts and urls, so urls (e.g. the Microsoft login link) can be rendered as links. */
export function linkify(text: string): TextPart[] {
  const parts: TextPart[] = []
  let last = 0
  for (const match of text.matchAll(URL_PATTERN)) {
    if (match.index > last) {
      parts.push({ text: text.slice(last, match.index) })
    }
    parts.push({ text: match[0], url: match[0] })
    last = match.index + match[0].length
  }
  if (last < text.length) {
    parts.push({ text: text.slice(last) })
  }
  return parts
}
