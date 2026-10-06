// Command history of the console, persisted in the browser.
const KEY = 'hmc-console-history'
const MAX = 200

export class History {
  private entries: string[]
  private index = -1
  private draft = ''

  constructor(private readonly storage: Storage | null = typeof localStorage === 'undefined' ? null : localStorage) {
    try {
      this.entries = JSON.parse(this.storage?.getItem(KEY) ?? '[]') as string[]
    } catch {
      this.entries = []
    }
  }

  add(line: string): void {
    this.index = -1
    if (!line.trim() || this.entries[this.entries.length - 1] === line) return
    this.entries.push(line)
    if (this.entries.length > MAX) this.entries.splice(0, this.entries.length - MAX)
    try {
      this.storage?.setItem(KEY, JSON.stringify(this.entries))
    } catch {
      // storage might be full or unavailable
    }
  }

  /** The previous entry, or undefined if there is none. */
  previous(current: string): string | undefined {
    if (this.entries.length === 0) return undefined
    if (this.index < 0) {
      this.draft = current
      this.index = this.entries.length
    }
    if (this.index === 0) return undefined
    this.index--
    return this.entries[this.index]
  }

  /** The next entry, or the draft when leaving the history. */
  next(): string | undefined {
    if (this.index < 0) return undefined
    this.index++
    if (this.index >= this.entries.length) {
      this.index = -1
      return this.draft
    }
    return this.entries[this.index]
  }
}
