import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { sampleTree } from '../../lib/__tests__/fixtures'
import CommandForm from '../CommandForm.vue'
import { flush, mockFetch } from './mockFetch'

function job(line: string) {
  return { id: '1', line, origin: 'gui', version: 0, status: 'QUEUED', exitCode: null, created: 0, started: 0, finished: 0, prompt: null, output: '' }
}

afterEach(() => vi.unstubAllGlobals())

describe('CommandForm', () => {
  it('renders the fields of a command and its parents and runs the built line', async () => {
    const calls = mockFetch({
      'GET /api/commands': () => sampleTree(),
      'POST /api/jobs': (call) => job((call.body as { line: string }).line),
    })
    const wrapper = mount(CommandForm, { props: { path: ['account', 'login'], initial: { '--provider': 'offline' } } })
    await flush()

    const text = wrapper.text()
    expect(text).toContain('--provider')
    expect(text).toContain('--method')
    expect(text).toContain('options of account')
    expect(wrapper.find('.preview').text()).toBe('> account --provider offline login')

    await wrapper.find('form').trigger('submit')
    await flush()
    const run = calls.find((call) => call.method === 'POST' && call.url === '/api/jobs')
    expect(run?.body).toEqual({ line: 'account --provider offline login', origin: 'gui' })
  })

  it('requires required positionals', async () => {
    mockFetch({ 'GET /api/commands': () => sampleTree() })
    const wrapper = mount(CommandForm, { props: { path: ['profile', 'edit'] } })
    await flush()
    expect(wrapper.find('button[type=submit]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Required: <name>')

    await wrapper.find('input').setValue('my profile')
    expect(wrapper.find('button[type=submit]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.preview').text()).toBe('> profile edit "my profile"')
  })

  it('reports unknown commands', async () => {
    mockFetch({ 'GET /api/commands': () => sampleTree() })
    const wrapper = mount(CommandForm, { props: { path: ['nope'] } })
    await flush()
    expect(wrapper.text()).toContain('Unknown command: nope')
  })
})
