import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flush, mockFetch } from '../../components/__tests__/mockFetch'
import ModsView from '../ModsView.vue'

const profile = {
  name: 'neoforge-26.3',
  side: 'client',
  version: 'neoforge 26.3',
  currentVersion: 'neoforge 26.3',
  path: '/mc',
  javaVersion: null,
  patchers: [],
  vmArgs: [],
  gameArgs: [],
  systemProperties: {},
  eulaStatus: 'UNKNOWN',
}

const mod = {
  path: 'mods/sodium.jar',
  fileName: 'sodium.jar',
  type: 'mod',
  world: null,
  directory: false,
  size: 2_000_000,
  modified: 1,
  enabled: true,
  displayName: 'Sodium',
  version: '0.9.2',
  description: 'Fast rendering',
  authors: ['jellysquid3'],
  mods: [{ id: 'sodium', name: 'Sodium' }],
  hasLogo: true,
}

function listing(files: unknown[]) {
  return { profile: profile.name, side: 'client', platform: 'neoforge', type: 'mod', types: ['mod', 'resourcepack'], worlds: [], files }
}

async function mountMods() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/mods', component: ModsView }] })
  await router.push('/mods')
  const wrapper = mount(ModsView, { global: { plugins: [router] } })
  await flush(10)
  return { wrapper, router }
}

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('ModsView', () => {
  it('selects a profile and shows its mods with logos', async () => {
    mockFetch({
      'GET /api/profiles': () => [profile],
      'GET /api/servers': () => [],
      'GET /api/mods/neoforge-26.3': () => listing([mod]),
    })
    const { wrapper, router } = await mountMods()
    expect(router.currentRoute.value.query.profile).toBe('neoforge-26.3')
    expect(wrapper.text()).toContain('Sodium')
    expect(wrapper.text()).toContain('0.9.2')
    expect(wrapper.text()).toContain('Resource packs')
    expect(wrapper.find('img.logo').attributes('src')).toBe('/api/mods/neoforge-26.3/logo?path=mods%2Fsodium.jar&v=1')
  })

  it('uploads dropped jar files', async () => {
    let uploaded = false
    const calls = mockFetch({
      'GET /api/profiles': () => [profile],
      'GET /api/servers': () => [],
      'GET /api/mods/neoforge-26.3': () => listing(uploaded ? [mod] : []),
      'POST /api/mods/neoforge-26.3/files': () => {
        uploaded = true
        return [mod]
      },
    })
    const { wrapper } = await mountMods()
    expect(wrapper.text()).toContain('Drag .jar or .zip files here')

    const jar = new File(['jar'], 'sodium.jar')
    const text = new File(['txt'], 'notes.txt')
    await wrapper.find('section.installed').trigger('drop', { dataTransfer: { files: [jar, text], types: ['Files'] } })
    await flush(10)

    const upload = calls.find((call) => call.method === 'POST')
    expect(upload?.url).toBe('/api/mods/neoforge-26.3/files?type=mod&overwrite=false')
    expect(wrapper.text()).toContain('Only .jar and .zip files can be added: notes.txt')
    expect(wrapper.text()).toContain('Sodium')
  })
})
