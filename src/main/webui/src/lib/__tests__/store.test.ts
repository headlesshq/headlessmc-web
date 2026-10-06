import { beforeEach, describe, expect, it } from 'vitest'
import { handleEvent, store, upsertJob } from '../store'
import type { Job } from '../types'

function job(id: string, overrides: Partial<Job> = {}): Job {
  return {
    id,
    line: 'help',
    origin: 'gui',
    version: 1,
    status: 'RUNNING',
    exitCode: null,
    created: 0,
    started: 0,
    finished: 0,
    prompt: null,
    output: null,
    ...overrides,
  }
}

describe('store', () => {
  beforeEach(() => {
    store.jobs = {}
    store.timeline = []
    store.processes = {}
    store.processLines = {}
    store.revision = 0
  })

  it('keeps output when job updates arrive without output', () => {
    upsertJob(job('1', { output: 'a' }))
    handleEvent({ type: 'output', payload: { jobId: '1', stream: 'out', text: 'b' } })
    handleEvent({ type: 'job', payload: job('1', { status: 'SUCCEEDED' }) })
    expect(store.jobs['1'].output).toBe('ab')
    expect(store.timeline).toHaveLength(1)
  })

  it('ignores outdated snapshots of fast jobs', () => {
    // websocket events of a job that failed immediately arrive before the response of starting it
    handleEvent({ type: 'job', payload: job('1', { version: 0, status: 'QUEUED' }) })
    handleEvent({ type: 'job', payload: job('1', { version: 1, status: 'RUNNING' }) })
    handleEvent({ type: 'output', payload: { jobId: '1', stream: 'err', text: 'Unmatched argument' } })
    handleEvent({ type: 'job', payload: job('1', { version: 2, status: 'FAILED', exitCode: 2 }) })
    upsertJob(job('1', { version: 0, status: 'QUEUED', output: '' }))
    expect(store.jobs['1'].status).toBe('FAILED')
    expect(store.jobs['1'].output).toBe('Unmatched argument')
  })

  it('bumps the revision once when a job finishes', () => {
    upsertJob(job('1'))
    handleEvent({ type: 'job', payload: job('1', { status: 'FAILED' }) })
    handleEvent({ type: 'job', payload: job('1', { status: 'FAILED' }) })
    expect(store.revision).toBe(1)
  })

  it('tracks processes and their output', () => {
    const process = { id: '7', name: 'mc', directory: '/', pid: 1, status: 'RUNNING', exitCode: null, attempt: 1, started: 0, finished: 0 }
    handleEvent({ type: 'process', payload: process })
    handleEvent({ type: 'process-output', payload: { processId: '7', line: { n: 1, type: 'out', text: 'hi' } } })
    handleEvent({ type: 'process-output', payload: { processId: '7', line: { n: 1, type: 'out', text: 'hi' } } })
    expect(store.processLines['7']).toHaveLength(1)
    handleEvent({ type: 'process-removed', payload: '7' })
    expect(store.processes['7']).toBeUndefined()
  })
})
