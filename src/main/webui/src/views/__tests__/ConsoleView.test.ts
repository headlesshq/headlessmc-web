import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flush, mockFetch } from '../../components/__tests__/mockFetch'
import { store } from '../../lib/store'
import ConsoleView from '../ConsoleView.vue'

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: ConsoleView }] })

async function mountConsole() {
  const wrapper = mount(ConsoleView, { global: { plugins: [router] }, attachTo: document.body })
  await flush()
  return wrapper
}

function input(wrapper: Awaited<ReturnType<typeof mountConsole>>) {
  return wrapper.find('form.input-line input')
}

beforeEach(() => {
  store.jobs = {}
  store.timeline = []
  localStorage.clear()
})

afterEach(() => vi.unstubAllGlobals())

describe('ConsoleView', () => {
  it('completes a single candidate on tab', async () => {
    const calls = mockFetch({
      'POST /api/complete': () => ({ start: 0, end: 3, word: 'ver', candidates: [{ value: 'version', description: null }] }),
    })
    const wrapper = await mountConsole()
    await input(wrapper).setValue('ver')
    ;(input(wrapper).element as HTMLInputElement).setSelectionRange(3, 3)
    await input(wrapper).trigger('keydown', { key: 'Tab' })
    await flush()
    expect(calls[0].body).toEqual({ line: 'ver', cursor: 3 })
    expect((input(wrapper).element as HTMLInputElement).value).toBe('version ')
    wrapper.unmount()
  })

  it('shows multiple candidates, inserts their common prefix and cycles on tab', async () => {
    mockFetch({
      'POST /api/complete': () => ({
        start: 11,
        end: 12,
        word: 'f',
        candidates: [
          { value: 'fabric', description: null },
          { value: 'forge', description: null },
        ],
      }),
    })
    const wrapper = await mountConsole()
    await input(wrapper).setValue('server add f')
    await input(wrapper).trigger('keydown', { key: 'Tab' })
    await flush()
    expect(wrapper.findAll('.menu li').map((li) => li.text())).toEqual(['fabric', 'forge'])

    await input(wrapper).trigger('keydown', { key: 'Tab' })
    await flush()
    expect((input(wrapper).element as HTMLInputElement).value).toBe('server add fabric')
    await input(wrapper).trigger('keydown', { key: 'Tab' })
    await flush()
    expect((input(wrapper).element as HTMLInputElement).value).toBe('server add forge')
    await input(wrapper).trigger('keydown', { key: 'Enter' })
    await flush()
    expect((input(wrapper).element as HTMLInputElement).value).toBe('server add forge ')
    expect(wrapper.find('.menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('runs commands and shows their output', async () => {
    const calls = mockFetch({
      'POST /api/jobs': () => ({
        id: '5',
        line: 'help',
        origin: 'console',
        version: 0,
        status: 'SUCCEEDED',
        exitCode: 0,
        created: 0,
        started: 0,
        finished: 0,
        prompt: null,
        output: 'Usage: \u001b[1mheadlessmc\u001b[0m',
      }),
    })
    const wrapper = await mountConsole()
    await input(wrapper).setValue('help')
    await wrapper.find('form.input-line').trigger('submit')
    await flush()
    expect(calls.find((call) => call.url === '/api/jobs')?.body).toEqual({ line: 'help', origin: 'console' })
    expect(wrapper.find('.output').text()).toContain('Usage: headlessmc')
    wrapper.unmount()
  })

  it('answers prompts of console commands inline', async () => {
    const calls = mockFetch({ 'POST /api/jobs/9/input': () => ({}) })
    const wrapper = await mountConsole()
    store.jobs['9'] = {
      id: '9',
      line: 'account -p offline login',
      origin: 'console',
      version: 0,
      status: 'WAITING_FOR_INPUT',
      exitCode: null,
      created: 0,
      started: 0,
      finished: 0,
      prompt: { id: 'p1', jobId: '9', kind: 'password', message: 'Enter the token:', initial: null },
      output: '',
    }
    await flush()
    expect(wrapper.find('.prompt-message').text()).toBe('Enter the token:')
    expect(input(wrapper).attributes('type')).toBe('password')
    await input(wrapper).setValue('secret')
    await wrapper.find('form.input-line').trigger('submit')
    await flush()
    expect(calls.find((call) => call.url === '/api/jobs/9/input')?.body).toEqual({ promptId: 'p1', value: 'secret' })
    wrapper.unmount()
  })
})
