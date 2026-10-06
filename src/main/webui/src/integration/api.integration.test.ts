// Runs the real api client against a running backend (e.g. ./gradlew quarkusDev or the built jar):
//   HMC_BACKEND=http://localhost:8080 npm run test:integration
// Guards against regressions the mocked unit tests cannot catch, e.g. the dev mode proxy loop
// between Quinoa and Vite that makes every /api request hang.
import { beforeAll, describe, expect, it } from 'vitest'
import { api } from '../lib/api'

const backend = process.env.HMC_BACKEND ?? 'http://localhost:8080'

beforeAll(() => {
  const realFetch = globalThis.fetch
  globalThis.fetch = ((input: string, init?: RequestInit) =>
    realFetch(input.startsWith('/') ? backend + input : input, { ...init, signal: AbortSignal.timeout(10_000) })) as typeof fetch
})

async function awaitJob(id: string) {
  for (let i = 0; i < 200; i++) {
    const job = await api.job(id)
    if (job.status === 'SUCCEEDED' || job.status === 'FAILED' || job.status === 'CANCELLED') return job
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
  throw new Error(`Job ${id} did not finish`)
}

describe('backend', () => {
  it('serves info', async () => {
    const info = await api.info()
    expect(info.name).toBe('HeadlessMc')
  })

  it('describes the commands', async () => {
    const root = await api.commands()
    expect(root.subcommands.map((command) => command.name)).toContain('launch')
  })

  it('completes', async () => {
    const result = await api.complete('ver')
    expect(result.candidates.map((candidate) => candidate.value)).toContain('version')
  })

  it('runs commands', async () => {
    const job = await awaitJob((await api.run('config get hmc.java.download', 'test')).id)
    expect(job.status).toBe('SUCCEEDED')
    expect(job.output).toContain('hmc.java.download')
  })

  it('serves the frontend', async () => {
    const response = await fetch('/')
    expect(response.status).toBe(200)
    expect(await response.text()).toContain('<div id="app">')
  })
})
