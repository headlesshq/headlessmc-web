import { describe, expect, it } from 'vitest'
import { buildLine, findChain, initialValues, missingRequired, optionKey, positionalKey } from '../commands'
import { sampleTree } from './fixtures'

describe('findChain', () => {
  it('finds commands by name and alias', () => {
    const root = sampleTree()
    expect(findChain(root, ['account', 'login'])?.map((c) => c.name)).toEqual(['headlessmc', 'account', 'login'])
    expect(findChain(root, ['auth'])?.map((c) => c.name)).toEqual(['headlessmc', 'account'])
    expect(findChain(root, ['nope'])).toBeNull()
  })
})

describe('buildLine', () => {
  const root = sampleTree()

  it('places parent options after the parent command', () => {
    const chain = findChain(root, ['account', 'login'])!
    const values = {
      [optionKey(1, chain[1].options[0])]: 'offline',
      [optionKey(2, chain[2].options[0])]: 'default',
    }
    expect(buildLine(chain, values)).toBe('account --provider offline login --method default')
  })

  it('handles flags, quoting, lists and multi word positionals', () => {
    const chain = findChain(root, ['launch'])!
    const [headless, jvm, patchers] = chain[1].options
    const values = {
      [optionKey(1, headless)]: true,
      [optionKey(1, jvm)]: '-Xmx2G -Dx=y',
      [optionKey(1, patchers)]: 'lwjgl,log4j',
      [positionalKey(chain[1].positionals[0])]: 'fabric 1.21.1',
    }
    expect(buildLine(chain, values)).toBe('launch --headless --jvm "-Xmx2G -Dx=y" --patchers lwjgl,log4j fabric 1.21.1')
  })

  it('skips unchecked flags, empty values and hidden options', () => {
    const chain = findChain(root, ['launch'])!
    const values = { [optionKey(1, chain[1].options[0])]: false, [optionKey(1, chain[1].options[3])]: 'x' }
    expect(buildLine(chain, values)).toBe('launch')
  })

  it('builds the context line for completing a field', () => {
    const chain = findChain(root, ['profile', 'edit'])!
    const [name, field, value] = chain[2].positionals
    const values = { [positionalKey(name)]: 'my profile', [positionalKey(field)]: 'name', [positionalKey(value)]: 'x' }
    expect(buildLine(chain, values, { kind: 'positional', positional: field })).toBe('profile edit "my profile"')
    const account = findChain(root, ['account', 'login'])!
    expect(buildLine(account, {}, { kind: 'option', depth: 1, option: account[1].options[0] })).toBe('account --provider')
    expect(buildLine(account, {}, { kind: 'option', depth: 2, option: account[2].options[0] })).toBe(
      'account login --method',
    )
  })
})

describe('initialValues', () => {
  it('maps option names, labels and indices', () => {
    const root = sampleTree()
    const chain = findChain(root, ['profile', 'edit'])!
    expect(initialValues(chain, { '#0': 'a', '<field>': 'name' })).toEqual({
      [positionalKey(chain[2].positionals[0])]: 'a',
      [positionalKey(chain[2].positionals[1])]: 'name',
    })
    const login = findChain(root, ['account', 'login'])!
    expect(initialValues(login, { '-p': 'offline' })).toEqual({ [optionKey(1, login[1].options[0])]: 'offline' })
  })
})

describe('missingRequired', () => {
  it('reports missing required positionals', () => {
    const chain = findChain(sampleTree(), ['profile', 'edit'])!
    expect(missingRequired(chain, {})).toEqual(['<name>'])
    expect(missingRequired(chain, { [positionalKey(chain[2].positionals[0])]: 'x' })).toEqual([])
  })
})
