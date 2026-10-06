import type {
  Accounts,
  CommandModel,
  CompletionResult,
  ConfigProperty,
  Info,
  InstalledVersion,
  JavaInstallation,
  Job,
  ProcessInfo,
  ProcessLine,
  Profile,
} from './types'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new ApiError(response.status, text || `${method} ${url} failed with ${response.status}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  info: () => request<Info>('GET', '/api/info'),
  commands: () => request<CommandModel>('GET', '/api/commands'),
  complete: (line: string, cursor?: number) =>
    request<CompletionResult>('POST', '/api/complete', { line, cursor: cursor ?? line.length }),

  jobs: (output = false) => request<Job[]>('GET', `/api/jobs?output=${output}`),
  job: (id: string) => request<Job>('GET', `/api/jobs/${encodeURIComponent(id)}`),
  run: (line: string, origin = 'gui') => request<Job>('POST', '/api/jobs', { line, origin }),
  answer: (jobId: string, promptId: string, value: string) =>
    request<Job>('POST', `/api/jobs/${encodeURIComponent(jobId)}/input`, { promptId, value }),
  cancelPrompt: (jobId: string, promptId: string) =>
    request<Job>('POST', `/api/jobs/${encodeURIComponent(jobId)}/input`, { promptId, cancel: true }),
  cancel: (jobId: string) => request<Job>('POST', `/api/jobs/${encodeURIComponent(jobId)}/cancel`),

  accounts: () => request<Accounts>('GET', '/api/accounts'),
  profiles: () => request<Profile[]>('GET', '/api/profiles'),
  servers: () => request<Profile[]>('GET', '/api/servers'),
  versions: () => request<InstalledVersion[]>('GET', '/api/versions'),
  java: () => request<JavaInstallation[]>('GET', '/api/java'),
  config: (all = false) => request<ConfigProperty[]>('GET', `/api/config?all=${all}`),

  processes: () => request<ProcessInfo[]>('GET', '/api/processes'),
  processOutput: (id: string) => request<ProcessLine[]>('GET', `/api/processes/${encodeURIComponent(id)}/output`),
  processInput: (id: string, line: string) =>
    request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/input`, { line }),
  stopProcess: (id: string) => request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/stop`),
  killProcess: (id: string) => request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/kill`),
  removeProcess: (id: string) => request<void>('DELETE', `/api/processes/${encodeURIComponent(id)}`),
}
