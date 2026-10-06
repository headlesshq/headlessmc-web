// Helpers to build HeadlessMc command lines the way HeadlessMc's (jline) argument splitter reads them.

const NEEDS_QUOTES = /[\s"'\\]/

/** Quotes an argument if necessary, so that it is read as a single argument. */
export function quoteArg(arg: string): string {
  if (arg === '') {
    return '""'
  }
  if (!NEEDS_QUOTES.test(arg)) {
    return arg
  }
  return '"' + arg.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

/** Splits a line into arguments, honouring quotes and backslash escapes. */
export function splitArgs(line: string): string[] {
  const args: string[] = []
  let current = ''
  let quote: string | null = null
  let inArg = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '\\' && i + 1 < line.length) {
      current += line[++i]
      inArg = true
    } else if (quote) {
      if (c === quote) {
        quote = null
      } else {
        current += c
      }
    } else if (c === '"' || c === "'") {
      quote = c
      inArg = true
    } else if (/\s/.test(c)) {
      if (inArg) {
        args.push(current)
        current = ''
        inArg = false
      }
    } else {
      current += c
      inArg = true
    }
  }
  if (inArg) {
    args.push(current)
  }
  return args
}

/** Joins already quoted parts and arguments to a line, skipping empty parts. */
export function joinLine(parts: string[]): string {
  return parts.filter((part) => part.length > 0).join(' ')
}

export interface CompletionApplication {
  line: string
  cursor: number
}

/**
 * Replaces the word that has been completed (start..end) with the given candidate.
 * Adds a trailing space if the candidate completes the word, e.g. not for "--option=".
 */
export function applyCompletion(
  line: string,
  start: number,
  end: number,
  candidate: string,
  complete = true,
): CompletionApplication {
  let actualStart = start
  // the backend reports the start of the unquoted word, include an opening quote
  if (actualStart > 0 && (line[actualStart - 1] === '"' || line[actualStart - 1] === "'")) {
    actualStart--
  }
  let replacement = quoteArg(candidate)
  const addSpace = complete && !candidate.endsWith('=') && !candidate.endsWith('/')
  const rest = line.slice(end)
  if (addSpace && !rest.startsWith(' ')) {
    replacement += ' '
  }
  const newLine = line.slice(0, actualStart) + replacement + rest
  return { line: newLine, cursor: actualStart + replacement.length }
}

/** The longest common prefix of the given strings. */
export function commonPrefix(values: string[]): string {
  if (values.length === 0) {
    return ''
  }
  let prefix = values[0]
  for (const value of values) {
    let i = 0
    while (i < prefix.length && i < value.length && prefix[i] === value[i]) {
      i++
    }
    prefix = prefix.slice(0, i)
  }
  return prefix
}
