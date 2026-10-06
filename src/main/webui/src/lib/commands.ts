// Turns the picocli command model into command lines, for the generated command forms.
import { api } from './api'
import { joinLine, quoteArg, splitArgs } from './shell'
import type { CommandModel, OptionModel, PositionalModel } from './types'

let commandsPromise: Promise<CommandModel> | null = null

/** The HeadlessMc command tree, loaded once. */
export function loadCommands(): Promise<CommandModel> {
  commandsPromise ??= api.commands().catch((error) => {
    commandsPromise = null
    throw error
  })
  return commandsPromise
}

/** Finds a command by its path of names or aliases. Returns the chain from the root to the command. */
export function findChain(root: CommandModel, path: string[]): CommandModel[] | null {
  const chain = [root]
  let current = root
  for (const name of path) {
    const next = current.subcommands.find((sub) => sub.name === name || sub.aliases.includes(name))
    if (!next) return null
    chain.push(next)
    current = next
  }
  return chain
}

export type FieldValue = string | boolean

/** Values of a command form, keyed by {@link optionKey} and {@link positionalKey}. */
export type FormValues = Record<string, FieldValue>

export function optionKey(depth: number, option: OptionModel): string {
  return `${depth}:${option.names[0]}`
}

export function positionalKey(positional: PositionalModel): string {
  return `pos:${positional.index}`
}

export function visibleOptions(command: CommandModel): OptionModel[] {
  return command.options.filter((option) => !option.hidden)
}

export function visiblePositionals(command: CommandModel): PositionalModel[] {
  return command.positionals.filter((positional) => !positional.hidden)
}

export type FieldRef = { kind: 'option'; depth: number; option: OptionModel } | { kind: 'positional'; positional: PositionalModel }

function isEmpty(value: FieldValue | undefined): boolean {
  return value === undefined || value === false || (typeof value === 'string' && value.trim() === '')
}

function optionParts(option: OptionModel, value: FieldValue | undefined): string[] {
  if (isEmpty(value)) return []
  const name = option.names[0]
  if (option.type === 'boolean' || option.arityMax === 0) {
    return value === true || value === 'true' ? [name] : []
  }
  const text = String(value).trim()
  if (option.type === 'list' && !option.split) {
    // repeated option, one value per (quoted) argument
    return splitArgs(text).flatMap((arg) => [name, quoteArg(arg)])
  }
  return [name, quoteArg(text)]
}

function positionalPart(positional: PositionalModel, value: FieldValue | undefined): string {
  if (isEmpty(value)) return ''
  const text = String(value).trim()
  // multi value positionals are entered as (shell) words, e.g. "fabric 1.21.1"
  return positional.type === 'list' ? text : quoteArg(text)
}

/**
 * Builds the command line for the given chain of commands (root first, the root itself is not part of the line).
 * If {@code until} is given, the line stops before that field (for completions of the field).
 */
export function buildLine(chain: CommandModel[], values: FormValues, until?: FieldRef): string {
  const parts: string[] = []
  const last = chain.length - 1
  for (let depth = 1; depth <= last; depth++) {
    const command = chain[depth]
    parts.push(command.name)
    for (const option of visibleOptions(command)) {
      if (until?.kind === 'option' && until.depth === depth && until.option === option) continue
      parts.push(...optionParts(option, values[optionKey(depth, option)]))
    }
    if (until?.kind === 'option' && until.depth === depth) {
      // the value of the option is completed right after it
      parts.push(until.option.names[0])
      return joinLine(parts)
    }
  }
  for (const positional of visiblePositionals(chain[last])) {
    if (until?.kind === 'positional' && until.positional.indexMin <= positional.indexMin) break
    parts.push(positionalPart(positional, values[positionalKey(positional)]))
  }
  return joinLine(parts)
}

/** Required fields that have no value yet. */
export function missingRequired(chain: CommandModel[], values: FormValues): string[] {
  const missing: string[] = []
  chain.forEach((command, depth) => {
    if (depth === 0) return
    for (const option of visibleOptions(command)) {
      if (option.required && isEmpty(values[optionKey(depth, option)])) missing.push(option.names[0])
    }
  })
  for (const positional of visiblePositionals(chain[chain.length - 1])) {
    if (positional.required && positional.arityMin > 0 && isEmpty(values[positionalKey(positional)])) {
      missing.push(positional.paramLabel)
    }
  }
  return missing
}

/**
 * Converts friendly initial values into form values. Keys can be option names (e.g. "--provider"),
 * positional labels (e.g. "profile") or positional indices (e.g. "#0").
 * Option names are matched on the deepest command that has them.
 */
export function initialValues(chain: CommandModel[], initial: Record<string, FieldValue>): FormValues {
  const values: FormValues = {}
  for (const [key, value] of Object.entries(initial)) {
    let matched = false
    for (let depth = chain.length - 1; depth >= 1 && !matched; depth--) {
      const option = chain[depth].options.find((candidate) => candidate.names.includes(key))
      if (option) {
        values[optionKey(depth, option)] = value
        matched = true
      }
    }
    if (!matched) {
      const positional = chain[chain.length - 1].positionals.find(
        (candidate) => candidate.paramLabel === key || `#${candidate.indexMin}` === key,
      )
      if (positional) values[positionalKey(positional)] = value
    }
  }
  return values
}

export function fieldLabel(field: OptionModel | PositionalModel): string {
  return 'names' in field ? field.names[0] : field.paramLabel.replace(/^<|>$/g, '')
}
