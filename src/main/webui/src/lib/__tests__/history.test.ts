import { describe, expect, it } from 'vitest'
import { History } from '../history'

function memoryStorage(): Storage {
  const map = new Map<string, string>()
  return {
    get length() {
      return map.size
    },
    clear: () => map.clear(),
    getItem: (key) => map.get(key) ?? null,
    key: (index) => [...map.keys()][index] ?? null,
    removeItem: (key) => void map.delete(key),
    setItem: (key, value) => void map.set(key, value),
  }
}

describe('History', () => {
  it('navigates entries and restores the draft', () => {
    const history = new History(memoryStorage())
    history.add('one')
    history.add('two')
    history.add('two') // no duplicates
    expect(history.previous('draft')).toBe('two')
    expect(history.previous('two')).toBe('one')
    expect(history.previous('one')).toBeUndefined()
    expect(history.next()).toBe('two')
    expect(history.next()).toBe('draft')
    expect(history.next()).toBeUndefined()
  })

  it('persists entries', () => {
    const storage = memoryStorage()
    new History(storage).add('launch fabric 1.21.1')
    expect(new History(storage).previous('')).toBe('launch fabric 1.21.1')
  })
})
