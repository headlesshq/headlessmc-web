import { describe, expect, it } from 'vitest'
import { linkify, parseAnsi, stripAnsi, styleToCss } from '../ansi'

describe('parseAnsi', () => {
  it('returns plain text as a single segment', () => {
    expect(parseAnsi('hello')).toEqual([{ text: 'hello', style: {} }])
  })

  it('applies and resets colors', () => {
    const segments = parseAnsi('\u001b[31mred\u001b[0m plain \u001b[1;92mbold green\u001b[22m')
    expect(segments.map((s) => s.text)).toEqual(['red', ' plain ', 'bold green'])
    expect(segments[0].style.fg).toBeDefined()
    expect(segments[1].style).toEqual({})
    expect(segments[2].style.bold).toBe(true)
  })

  it('supports 256 and true colors', () => {
    expect(parseAnsi('\u001b[38;5;196mx')[0].style.fg).toMatch(/^rgb/)
    expect(parseAnsi('\u001b[48;2;1;2;3mx')[0].style.bg).toBe('rgb(1, 2, 3)')
  })

  it('drops other escape sequences', () => {
    expect(parseAnsi('a\u001b[2Kb\u001b]0;title\u0007c').map((s) => s.text).join('')).toBe('abc')
  })

  it('treats an empty SGR as reset', () => {
    expect(parseAnsi('\u001b[31ma\u001b[mb')[1].style).toEqual({})
  })
})

describe('stripAnsi', () => {
  it('removes escapes', () => {
    expect(stripAnsi('\u001b[1m\u001b[33mwarn\u001b[0m')).toBe('warn')
  })
})

describe('styleToCss', () => {
  it('maps styles to css', () => {
    expect(styleToCss({ fg: 'red', bold: true, underline: true })).toEqual({
      color: 'red',
      fontWeight: 'bold',
      textDecoration: 'underline',
    })
  })
})

describe('linkify', () => {
  it('finds urls, without trailing punctuation', () => {
    expect(linkify('To login visit https://www.microsoft.com/link?otc=ABC. Then wait')).toEqual([
      { text: 'To login visit ' },
      { text: 'https://www.microsoft.com/link?otc=ABC', url: 'https://www.microsoft.com/link?otc=ABC' },
      { text: '. Then wait' },
    ])
  })
})
