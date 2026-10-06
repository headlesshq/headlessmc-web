import type {
  Accounts,
  CommandModel,
  CompletionResult,
  ConfigProperty,
  Info,
  InstalledVersion,
  JavaInstallation,
  Job,
  ModFile,
  ModsListing,
  ProcessInfo,
  ProcessLine,
  Profile,
  RemoteMod,
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
  const form = body instanceof FormData
  const response = await fetch(url, {
    method,
    headers: body === undefined || form ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
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

  mods: (profile: string, type?: string) =>
    request<ModsListing>('GET', `/api/mods/${encodeURIComponent(profile)}${type ? `?type=${encodeURIComponent(type)}` : ''}`),
  modLogoUrl: (profile: string, file: ModFile) =>
    `/api/mods/${encodeURIComponent(profile)}/logo?path=${encodeURIComponent(file.path)}&v=${file.modified}`,
  addMods: (profile: string, type: string, files: File[], world?: string | null, overwrite = false) => {
    const form = new FormData()
    files.forEach((file) => form.append('files', file, file.name))
    const query = new URLSearchParams({ type, overwrite: String(overwrite) })
    if (world) query.set('world', world)
    return request<ModFile[]>('POST', `/api/mods/${encodeURIComponent(profile)}/files?${query}`, form)
  },
  removeMod: (profile: string, path: string) =>
    request<void>('DELETE', `/api/mods/${encodeURIComponent(profile)}/files?path=${encodeURIComponent(path)}`),
  setModEnabled: (profile: string, path: string, enabled: boolean) =>
    request<ModFile>(
      'POST',
      `/api/mods/${encodeURIComponent(profile)}/files/enabled?path=${encodeURIComponent(path)}&enabled=${enabled}`,
    ),
  searchMods: (profile: string, type: string, query: string) =>
    request<RemoteMod[]>(
      'GET',
      `/api/mods/${encodeURIComponent(profile)}/search?${new URLSearchParams({ type, query })}`,
    ),

  processes: () => request<ProcessInfo[]>('GET', '/api/processes'),
  processOutput: (id: string) => request<ProcessLine[]>('GET', `/api/processes/${encodeURIComponent(id)}/output`),
  processInput: (id: string, line: string) =>
    request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/input`, { line }),
  stopProcess: (id: string) => request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/stop`),
  killProcess: (id: string) => request<ProcessInfo>('POST', `/api/processes/${encodeURIComponent(id)}/kill`),
  removeProcess: (id: string) => request<void>('DELETE', `/api/processes/${encodeURIComponent(id)}`),
}
