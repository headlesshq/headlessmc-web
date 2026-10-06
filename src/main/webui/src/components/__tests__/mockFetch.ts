import { vi } from 'vitest'

export interface Call {
  method: string
  url: string
  body: unknown
}

type Handler = (call: Call) => unknown

/** Replaces fetch with handlers keyed by "METHOD /path" (without query). Records all calls. */
export function mockFetch(handlers: Record<string, Handler>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const path = url.split('?')[0]
      const call = { method, url, body: init?.body ? JSON.parse(String(init.body)) : undefined }
      calls.push(call)
      const handler = handlers[`${method} ${path}`]
      if (!handler) {
        return new Response('not found', { status: 404 })
      }
      return new Response(JSON.stringify(handler(call)), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }),
  )
  return calls
}

export async function flush(times = 5): Promise<void> {
  for (let i = 0; i < times; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}
