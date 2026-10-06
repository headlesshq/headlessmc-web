// Mirrors the json produced by the Quarkus backend (io.github.headlesshq.web).

export type JobStatus = 'QUEUED' | 'RUNNING' | 'WAITING_FOR_INPUT' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface Prompt {
  id: string
  jobId: string
  kind: 'line' | 'password' | 'edit'
  message: string
  initial: string | null
}

export interface Job {
  id: string
  line: string
  origin: string
  /** increases with every change of status/prompt, older snapshots must not overwrite newer ones */
  version: number
  status: JobStatus
  exitCode: number | null
  created: number
  started: number
  finished: number
  prompt: Prompt | null
  output: string | null
}

export interface JobOutputEvent {
  jobId: string
  stream: 'out' | 'err' | 'log'
  text: string
}

export interface LogEntry {
  level: string
  logger: string
  message: string
  time: number
}

export interface Progress {
  id: string
  task: string
  current: number
  max: number
  unit: string | null
  unitSize: number
  done: boolean
}

export type ProcessStatus = 'RUNNING' | 'EXITED' | 'STOPPED'

export interface ProcessInfo {
  id: string
  name: string
  directory: string
  pid: number
  status: ProcessStatus
  exitCode: number | null
  attempt: number
  started: number
  finished: number
}

export interface ProcessLine {
  n: number
  type: 'out' | 'err' | 'in' | 'hmc'
  text: string
}

export interface ProcessLineEvent {
  processId: string
  line: ProcessLine
}

export type ArgType = 'boolean' | 'integer' | 'number' | 'string' | 'list'

export interface OptionModel {
  names: string[]
  description: string
  paramLabel: string
  type: ArgType
  arityMin: number
  arityMax: number
  required: boolean
  defaultValue: string | null
  hidden: boolean
  split: string | null
  completions: boolean
}

export interface PositionalModel {
  index: string
  indexMin: number
  description: string
  paramLabel: string
  type: ArgType
  arityMin: number
  arityMax: number
  required: boolean
  hidden: boolean
  completions: boolean
}

export interface CommandModel {
  name: string
  path: string[]
  aliases: string[]
  description: string
  hidden: boolean
  runnable: boolean
  options: OptionModel[]
  positionals: PositionalModel[]
  subcommands: CommandModel[]
}

export interface CompletionCandidate {
  value: string
  description: string | null
}

export interface CompletionResult {
  start: number
  end: number
  word: string
  candidates: CompletionCandidate[]
}

export interface Info {
  name: string
  version: string
  directories: Record<string, string>
  maxMemory: number
  usedMemory: number
  javaVersion: string
}

export interface Account {
  name: string
  uuid: string
  type: string
  provider: string
}

export interface AuthProviderInfo {
  name: string
  methods: string[]
  accounts: Account[]
}

export interface Accounts {
  providers: AuthProviderInfo[]
  selected: Account | null
}

export interface Profile {
  name: string
  side: 'client' | 'server'
  version: string
  currentVersion: string
  path: string
  javaVersion: number | null
  patchers: string[]
  vmArgs: string[]
  gameArgs: string[] | null
  systemProperties: Record<string, string | null>
  eulaStatus: string
}

export interface InstalledVersion {
  id: string
  inheritsFrom: string | null
  type: string | null
}

export interface JavaInstallation {
  name: string
  version: number
  home: string
  current: boolean
}

export interface ConfigProperty {
  name: string
  value: string | null
  description: string | null
}

export interface ContainedMod {
  id: string
  name: string
}

export interface ModFile {
  /** relative to the game directory, identifies the file */
  path: string
  fileName: string
  type: string
  world: string | null
  directory: boolean
  size: number
  modified: number
  enabled: boolean
  displayName: string
  version: string | null
  description: string | null
  authors: string[]
  mods: ContainedMod[]
  hasLogo: boolean
}

export interface ModsListing {
  profile: string
  side: 'client' | 'server'
  platform: string
  type: string
  types: string[]
  worlds: string[]
  files: ModFile[]
}

export interface RemoteMod {
  id: string
  name: string
  description: string
}
