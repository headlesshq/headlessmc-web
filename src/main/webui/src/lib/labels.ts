// Human readable labels for the picocli model, so the GUI does not show command line artifacts
// like "--eula-accept" or "<versionArg>".
import type { CommandModel, OptionModel, PositionalModel } from './types'

/** Labels that a generic conversion would get wrong, keyed by the (longest) option name. */
const OPTION_LABELS: Record<string, string> = {
  '--eula-accept': 'Accept EULA',
  '--jvm': 'JVM arguments',
  '--game': 'Game arguments',
  '--server': 'Join server',
  '--url': 'Installer URL',
  '--dir': 'Directory',
  '--temp': 'Only until restart',
  '--remote': 'Show remote',
  '--providers': 'Show providers',
  '--methods': 'Show login methods',
  '--method': 'Login method',
  '--packwiz': 'Packwiz pack',
  '--all': 'All profiles',
  '--force': 'Force',
  '--headless': 'Headless',
}

const ACRONYMS: Record<string, string> = {
  eula: 'EULA',
  jvm: 'JVM',
  url: 'URL',
  id: 'ID',
  uuid: 'UUID',
  lwjgl: 'LWJGL',
  hmc: 'HeadlessMc',
}

/** "versionArg" -> "Version", "--eula-accept" -> "Eula accept", "game-dir" -> "Game dir". */
export function humanize(name: string): string {
  const words = name
    .replace(/^-+/, '')
    .replace(/^<|>$/g, '')
    .replace(/(?:Args?|Arguments?)$/u, (match: string, offset: number) => (offset > 0 ? '' : match))
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .split(/[\s_\-.]+/)
    .filter((word) => word.length > 0)
    .map((word) => ACRONYMS[word.toLowerCase()] ?? word.toLowerCase())
  if (words.length === 0) return name
  const first = words[0]
  words[0] = first === first.toUpperCase() ? first : first.charAt(0).toUpperCase() + first.slice(1)
  return words.join(' ')
}

export function optionLabel(option: OptionModel): string {
  return OPTION_LABELS[option.names[0]] ?? humanize(option.names[0])
}

export function positionalLabel(positional: PositionalModel): string {
  return humanize(positional.paramLabel)
}

export function commandLabel(command: CommandModel): string {
  return humanize(command.name)
}

/** "Accept the EULA" style placeholder for a field: its default value, if any. */
export function placeholder(field: OptionModel | PositionalModel): string {
  if ('defaultValue' in field && field.defaultValue && field.defaultValue !== 'false') {
    return `Default: ${field.defaultValue}`
  }
  if (field.type === 'list' && !('names' in field)) {
    return 'Separate multiple values with spaces'
  }
  return ''
}
