import type { CommandModel, OptionModel, PositionalModel } from '../types'

export function option(names: string[], overrides: Partial<OptionModel> = {}): OptionModel {
  return {
    names,
    description: '',
    paramLabel: '<value>',
    type: 'string',
    arityMin: 1,
    arityMax: 1,
    required: false,
    defaultValue: null,
    hidden: false,
    split: null,
    completions: false,
    ...overrides,
  }
}

export function positional(index: number, label: string, overrides: Partial<PositionalModel> = {}): PositionalModel {
  return {
    index: String(index),
    indexMin: index,
    description: '',
    paramLabel: label,
    type: 'string',
    arityMin: 1,
    arityMax: 1,
    required: true,
    hidden: false,
    completions: false,
    ...overrides,
  }
}

export function command(name: string, path: string[], overrides: Partial<CommandModel> = {}): CommandModel {
  return {
    name,
    path,
    aliases: [],
    description: `${name} command`,
    hidden: false,
    runnable: true,
    options: [],
    positionals: [],
    subcommands: [],
    ...overrides,
  }
}

/** A small excerpt of the HeadlessMc command tree. */
export function sampleTree(): CommandModel {
  const login = command('login', ['account', 'login'], {
    options: [option(['--method'], { defaultValue: 'default' })],
  })
  const account = command('account', ['account'], {
    aliases: ['auth'],
    options: [option(['--provider', '-p'], { completions: true })],
    subcommands: [login],
  })
  const launch = command('launch', ['launch'], {
    options: [
      option(['--headless', '-lwjgl'], { type: 'boolean', arityMin: 0, arityMax: 0 }),
      option(['--jvm', '-j']),
      option(['--patchers'], { type: 'list', split: ',' }),
      option(['--hidden'], { hidden: true }),
    ],
    positionals: [positional(0, 'profile', { type: 'list', arityMin: 0, arityMax: -1, required: false, completions: true })],
  })
  const edit = command('edit', ['profile', 'edit'], {
    positionals: [
      positional(0, '<name>'),
      positional(1, '<field>', { arityMin: 0, required: false }),
      positional(2, '<value>', { arityMin: 0, required: false }),
    ],
  })
  const profile = command('profile', ['profile'], { runnable: false, subcommands: [edit] })
  return command('headlessmc', [], { subcommands: [account, launch, profile] })
}
