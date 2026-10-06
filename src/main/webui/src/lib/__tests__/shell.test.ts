import { describe, expect, it } from 'vitest'
import { applyCompletion, commonPrefix, joinLine, quoteArg, splitArgs } from '../shell'

describe('quoteArg', () => {
  it('leaves simple arguments alone', () => {
    expect(quoteArg('fabric')).toBe('fabric')
    expect(quoteArg('--jvm=-Xmx2G')).toBe('--jvm=-Xmx2G')
  })

  it('quotes whitespace, quotes and backslashes', () => {
    expect(quoteArg('my profile')).toBe('"my profile"')
    expect(quoteArg('say "hi"')).toBe('"say \\"hi\\""')
    expect(quoteArg('C:\\games')).toBe('"C:\\\\games"')
    expect(quoteArg('')).toBe('""')
  })

  it('round trips through splitArgs', () => {
    const args = ['launch', 'my profile', '--jvm', '-Xmx2G -Dkey="value"', '', 'C:\\x']
    expect(splitArgs(args.map(quoteArg).join(' '))).toEqual(args)
  })
})

describe('splitArgs', () => {
  it('splits on whitespace and honours quotes', () => {
    expect(splitArgs('  account  login --method "default" ')).toEqual(['account', 'login', '--method', 'default'])
    expect(splitArgs("a 'b c' d\\ e")).toEqual(['a', 'b c', 'd e'])
  })
})

describe('joinLine', () => {
  it('skips empty parts', () => {
    expect(joinLine(['launch', '', 'fabric'])).toBe('launch fabric')
  })
})

describe('applyCompletion', () => {
  it('replaces the word and adds a space', () => {
    expect(applyCompletion('server add fa', 11, 13, 'fabric')).toEqual({ line: 'server add fabric ', cursor: 18 })
  })

  it('completes in the middle of a line', () => {
    expect(applyCompletion('acc list', 0, 3, 'account')).toEqual({ line: 'account list', cursor: 7 })
  })

  it('does not add a space for partial completions', () => {
    expect(applyCompletion('ver', 0, 3, 'version', false)).toEqual({ line: 'version', cursor: 7 })
    expect(applyCompletion('launch --jv', 7, 11, '--jvm=')).toEqual({ line: 'launch --jvm=', cursor: 13 })
  })

  it('quotes candidates with spaces and replaces an opening quote', () => {
    expect(applyCompletion('profile launch "my', 16, 18, 'my profile')).toEqual({
      line: 'profile launch "my profile" ',
      cursor: 28,
    })
  })
})

describe('commonPrefix', () => {
  it('finds the common prefix', () => {
    expect(commonPrefix(['fabric', 'fabric-server', 'fab'])).toBe('fab')
    expect(commonPrefix(['a', 'b'])).toBe('')
    expect(commonPrefix([])).toBe('')
  })
})
