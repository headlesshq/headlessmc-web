import { describe, expect, it } from 'vitest'
import { humanize, optionLabel, placeholder, positionalLabel } from '../labels'
import { option, positional } from './fixtures'

describe('humanize', () => {
  it('turns command line names into labels', () => {
    expect(humanize('<versionArg>')).toBe('Version')
    expect(humanize('versionArg')).toBe('Version')
    expect(humanize('profile')).toBe('Profile')
    expect(humanize('--eula-accept')).toBe('EULA accept')
    expect(humanize('--game-dir')).toBe('Game dir')
    expect(humanize('customURL')).toBe('Custom URL')
    expect(humanize('args')).toBe('Args')
  })
})

describe('labels', () => {
  it('uses nicer labels for well known options', () => {
    expect(optionLabel(option(['--eula-accept', '-eula']))).toBe('Accept EULA')
    expect(optionLabel(option(['--jvm', '-j']))).toBe('JVM arguments')
    expect(optionLabel(option(['--provider', '-p']))).toBe('Provider')
  })

  it('never shows dashes or angle brackets', () => {
    expect(positionalLabel(positional(0, '<versionArg>'))).toBe('Version')
    expect(optionLabel(option(['--retries', '-ret']))).toBe('Retries')
  })

  it('uses defaults as placeholders', () => {
    expect(placeholder(option(['--method'], { defaultValue: 'default' }))).toBe('Default: default')
    expect(placeholder(option(['--force'], { defaultValue: 'false' }))).toBe('')
  })
})
