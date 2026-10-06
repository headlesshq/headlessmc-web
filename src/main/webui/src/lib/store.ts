// Global, reactive client state fed by the server events.
import { reactive, watch } from 'vue'
import { api } from './api'
import { EventClient, eventsUrl, type ServerEvent } from './events'
import type {
  Job,
  JobOutputEvent,
  LogEntry,
  ProcessInfo,
  ProcessLine,
  ProcessLineEvent,
  Progress,
} from './types'

const MAX_LOGS = 500
const MAX_PROCESS_LINES = 5000

export interface ConsoleEntry {
  kind: 'job' | 'log'
  key: string
  jobId?: string
  log?: LogEntry
}

export const store = reactive({
  connected: false,
  jobs: {} as Record<string, Job>,
  /** order in which jobs and log entries appeared, for the console */
  timeline: [] as ConsoleEntry[],
  logs: [] as LogEntry[],
  progress: {} as Record<string, Progress>,
  processes: {} as Record<string, ProcessInfo>,
  processLines: {} as Record<string, ProcessLine[]>,
  /** incremented whenever a job finishes, views use it to reload their data */
  revision: 0,
  /** jobs whose prompts the console answers inline instead of the global dialog */
  consoleOwnsPrompts: false,
})

export function isDone(job: Job): boolean {
  return job.status === 'SUCCEEDED' || job.status === 'FAILED' || job.status === 'CANCELLED'
}

export function upsertJob(job: Job): void {
  const existing = store.jobs[job.id]
  const wasDone = existing ? isDone(existing) : false
  // output only grows, a snapshot (e.g. the response of starting the job) can be older than streamed output
  const snapshotOutput = job.output ?? ''
  const existingOutput = existing?.output ?? ''
  const output = snapshotOutput.length >= existingOutput.length ? snapshotOutput : existingOutput
  if (existing && job.version < existing.version) {
    // an outdated snapshot, e.g. the http response of a fast job arriving after its websocket events
    existing.output = output
    return
  }
  store.jobs[job.id] = { ...job, output }
  if (!existing) {
    store.timeline.push({ kind: 'job', key: `job-${job.id}`, jobId: job.id })
  }
  if (!wasDone && isDone(job)) {
    store.revision++
  }
}

export function handleEvent(event: ServerEvent): void {
  switch (event.type) {
    case 'job':
      upsertJob(event.payload as Job)
      break
    case 'output': {
      const output = event.payload as JobOutputEvent
      const job = store.jobs[output.jobId]
      if (job) {
        job.output = (job.output ?? '') + output.text
      }
      break
    }
    case 'log': {
      const log = event.payload as LogEntry
      store.logs.push(log)
      if (store.logs.length > MAX_LOGS) store.logs.shift()
      store.timeline.push({ kind: 'log', key: `log-${log.time}-${store.timeline.length}`, log })
      break
    }
    case 'progress': {
      const progress = event.payload as Progress
      store.progress[progress.id] = progress
      if (progress.done) {
        setTimeout(() => delete store.progress[progress.id], 1500)
      }
      break
    }
    case 'process': {
      const process = event.payload as ProcessInfo
      store.processes[process.id] = process
      break
    }
    case 'process-output': {
      const { processId, line } = event.payload as ProcessLineEvent
      const lines = (store.processLines[processId] ??= [])
      if (lines.length === 0 || lines[lines.length - 1].n < line.n) {
        lines.push(line)
        if (lines.length > MAX_PROCESS_LINES) lines.splice(0, lines.length - MAX_PROCESS_LINES)
      }
      break
    }
    case 'process-removed': {
      const id = event.payload as string
      delete store.processes[id]
      delete store.processLines[id]
      break
    }
  }
}

/** Loads the current state, after (re-)connecting. */
export async function resync(): Promise<void> {
  const [jobs, processes] = await Promise.all([api.jobs(true), api.processes()])
  jobs.forEach(upsertJob)
  store.processes = Object.fromEntries(processes.map((process) => [process.id, process]))
}

export async function loadProcessOutput(id: string): Promise<void> {
  store.processLines[id] = await api.processOutput(id)
}

let client: EventClient | null = null

export function connect(): void {
  if (client) return
  client = new EventClient(eventsUrl())
  client.onEvent(handleEvent)
  client.onConnectionChange((connected) => {
    store.connected = connected
    if (connected) {
      resync().catch((error) => console.error('Failed to load state', error))
    }
  })
  client.start()
}

/** Runs a command line and returns the created job. */
export async function run(line: string, origin = 'gui'): Promise<Job> {
  const job = await api.run(line, origin)
  upsertJob(job)
  return store.jobs[job.id]
}

/** Resolves once the job is done. */
export function waitForJob(id: string): Promise<Job> {
  return new Promise((resolve) => {
    let resolved = false
    const stop = watch(
      () => store.jobs[id]?.status,
      () => {
        const job = store.jobs[id]
        if (!resolved && job && isDone(job)) {
          resolved = true
          resolve(job)
          queueMicrotask(() => stop())
        }
      },
      { immediate: true },
    )
  })
}
