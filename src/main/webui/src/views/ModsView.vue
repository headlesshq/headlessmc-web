<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import JobOutput from '../components/JobOutput.vue'
import ModCard from '../components/ModCard.vue'
import ModLogo from '../components/ModLogo.vue'
import { api, ApiError } from '../lib/api'
import { acceptsFile, typeName } from '../lib/modTypes'
import { quoteArg } from '../lib/shell'
import { run, store, waitForJob } from '../lib/store'
import type { ModFile, ModsListing, Profile, RemoteMod } from '../lib/types'
import { useData } from '../lib/useData'

const LAST_PROFILE = 'hmc-mods-profile'
const route = useRoute()
const router = useRouter()

const { data: profiles } = useData<Profile[]>(async () => [...(await api.profiles()), ...(await api.servers())], [])
const profile = computed(() => (route.query.profile as string | undefined) ?? '')
const type = computed(() => (route.query.type as string | undefined) ?? '')

const listing = ref<ModsListing | null>(null)
const error = ref('')
const loadError = ref('')
const notice = ref('')
const loading = ref(false)
const busy = ref<Record<string, boolean>>({})
const filter = ref('')
const world = ref('')

// search state, declared before the watchers below that reset it
const query = ref('')
const results = ref<RemoteMod[]>([])
const searching = ref(false)
const searchError = ref('')
const installJobs = ref<Record<string, string>>({})

// select the last used (or first) profile
watch(
  profiles,
  (available) => {
    if (profile.value || !available.length) return
    let last: string | null = null
    try {
      last = localStorage.getItem(LAST_PROFILE)
    } catch {
      // storage unavailable
    }
    const name = available.find((p) => p.name === last)?.name ?? available[0].name
    router.replace({ query: { profile: name } })
  },
  { immediate: true },
)

async function load() {
  if (!profile.value) return
  loading.value = true
  try {
    listing.value = await api.mods(profile.value, type.value || undefined)
    loadError.value = ''
    if (!listing.value.worlds.includes(world.value)) world.value = listing.value.worlds[0] ?? ''
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
    listing.value = null
  } finally {
    loading.value = false
  }
}

watch([profile, type], () => {
  try {
    if (profile.value) localStorage.setItem(LAST_PROFILE, profile.value)
  } catch {
    // storage unavailable
  }
  results.value = []
  error.value = ''
  notice.value = ''
  load()
}, { immediate: true })
watch(() => store.revision, load)

function select(query: Record<string, string>) {
  router.push({ query: { ...route.query, ...query } })
}

const currentType = computed(() => listing.value?.type ?? 'mod')
const files = computed(() => {
  const query = filter.value.toLowerCase()
  return (listing.value?.files ?? []).filter(
    (file) =>
      !query ||
      [file.displayName, file.fileName, file.description ?? '', ...file.authors].some((text) => text.toLowerCase().includes(query)),
  )
})
const logoUrl = (file: ModFile) => (file.hasLogo ? api.modLogoUrl(profile.value, file) : null)
const needsWorld = computed(() => currentType.value === 'datapack')

// ---- adding files (drag and drop / file picker) ----
const dragDepth = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)

function onDragEnter(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragDepth.value++
}

function onDragLeave() {
  dragDepth.value = Math.max(0, dragDepth.value - 1)
}

function onDrop(event: DragEvent) {
  dragDepth.value = 0
  upload([...(event.dataTransfer?.files ?? [])])
}

async function upload(selected: File[]) {
  error.value = ''
  notice.value = ''
  const accepted = selected.filter((file) => acceptsFile(file.name))
  const rejected = selected.filter((file) => !acceptsFile(file.name))
  if (rejected.length) {
    error.value = `Only .jar and .zip files can be added: ${rejected.map((f) => f.name).join(', ')}`
  }
  if (!accepted.length || !listing.value) return
  if (needsWorld.value && !world.value) {
    error.value = 'Choose the world to add data packs to.'
    return
  }

  const added: string[] = []
  for (const file of accepted) {
    try {
      await api.addMods(profile.value, currentType.value, [file], world.value || null)
      added.push(file.name)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409 && window.confirm(`${file.name} already exists. Replace it?`)) {
        await api.addMods(profile.value, currentType.value, [file], world.value || null, true)
        added.push(file.name)
      } else if (!(e instanceof ApiError && e.status === 409)) {
        error.value = e instanceof Error ? e.message : String(e)
      }
    }
  }
  if (added.length) notice.value = `Added ${added.join(', ')}`
  await load()
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  upload([...(input.files ?? [])])
  input.value = ''
}

// ---- toggling / removing ----
async function toggle(file: ModFile, enabled: boolean) {
  busy.value[file.path] = true
  try {
    await api.setModEnabled(profile.value, file.path, enabled)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    delete busy.value[file.path]
  }
}

async function remove(file: ModFile) {
  if (!window.confirm(`Remove ${file.displayName} (${file.fileName})?`)) return
  busy.value[file.path] = true
  try {
    await api.removeMod(profile.value, file.path)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    delete busy.value[file.path]
  }
}

// ---- downloading from Modrinth (HeadlessMc's mod add command) ----

async function search() {
  if (!query.value.trim()) return
  searching.value = true
  searchError.value = ''
  try {
    results.value = await api.searchMods(profile.value, currentType.value, query.value.trim())
  } catch (e) {
    searchError.value = e instanceof Error ? e.message : String(e)
  } finally {
    searching.value = false
  }
}

async function install(mod: RemoteMod) {
  const worldOption = needsWorld.value && world.value ? ` --world ${quoteArg(world.value)}` : ''
  const job = await run(`mod add ${quoteArg(currentType.value)} ${quoteArg(mod.id)} ${quoteArg(profile.value)}${worldOption}`)
  installJobs.value[mod.id] = job.id
  await waitForJob(job.id)
}

const installStatus = (id: string) => {
  const jobId = installJobs.value[id]
  return jobId ? store.jobs[jobId]?.status : undefined
}
</script>

<template>
  <h1>Mods</h1>
  <div class="row toolbar">
    <label>Profile</label>
    <select :value="profile" @change="select({ profile: ($event.target as HTMLSelectElement).value, type: '' })">
      <option v-for="p in profiles" :key="p.name" :value="p.name">{{ p.name }}{{ p.side === 'server' ? ' (server)' : '' }}</option>
    </select>
    <span v-if="listing" class="muted">{{ listing.platform }}</span>
  </div>

  <div v-if="!profiles.length" class="card empty">Create a profile or server first.</div>

  <template v-else-if="listing">
    <div class="tabs">
      <button v-for="t in listing.types" :key="t" :class="{ active: t === currentType }" @click="select({ type: t })">
        {{ typeName(t) }}
      </button>
    </div>

    <div class="layout">
      <section
        class="card installed"
        :class="{ dragging: dragDepth > 0 }"
        @dragenter.prevent="onDragEnter"
        @dragover.prevent
        @dragleave="onDragLeave"
        @drop.prevent="onDrop"
      >
        <div class="row head">
          <input v-model="filter" class="filter" :placeholder="`Filter ${typeName(currentType).toLowerCase()}…`" />
          <label v-if="needsWorld" class="row">
            World
            <select v-model="world">
              <option v-if="!listing.worlds.length" value="">no worlds yet</option>
              <option v-for="w in listing.worlds" :key="w" :value="w">{{ w }}</option>
            </select>
          </label>
          <button class="primary" :disabled="needsWorld && !world" @click="fileInput?.click()">Add files…</button>
          <input ref="fileInput" type="file" accept=".jar,.zip" multiple hidden @change="onPick" />
        </div>
        <div v-if="error" class="error">{{ error }}</div>
        <div v-if="notice" class="notice">{{ notice }}</div>

        <div v-if="!files.length" class="dropzone">
          <template v-if="filter">Nothing matches “{{ filter }}”.</template>
          <template v-else>
            No {{ typeName(currentType).toLowerCase() }} installed.<br />
            Drag .jar or .zip files here, or download them from Modrinth.
          </template>
        </div>
        <div v-else class="list">
          <ModCard
            v-for="file in files"
            :key="file.path"
            :mod="file"
            :logo="logoUrl(file)"
            :busy="busy[file.path]"
            @toggle="toggle(file, $event)"
            @remove="remove(file)"
          />
        </div>
        <div class="muted hint">{{ listing.files.length }} files · drop .jar or .zip files anywhere on this panel to add them</div>
        <div v-if="dragDepth > 0" class="overlay">Drop to add to {{ profile }}</div>
      </section>

      <aside class="card search">
        <h3>Download from Modrinth</h3>
        <form class="row" @submit.prevent="search">
          <input v-model="query" class="filter" :placeholder="`Search ${typeName(currentType).toLowerCase()}…`" />
          <button type="submit" :disabled="searching || !query.trim()">Search</button>
        </form>
        <p class="muted small">Only shows {{ typeName(currentType).toLowerCase() }} compatible with {{ profile }}.</p>
        <div v-if="searchError" class="error">{{ searchError }}</div>
        <div v-if="searching" class="muted">Searching…</div>
        <div v-for="mod in results" :key="mod.id" class="result">
          <ModLogo :src="mod.iconUrl" :name="mod.name" :size="36" />
          <div class="info">
            <strong>{{ mod.name }}</strong>
            <div class="muted small description">{{ mod.description }}</div>
          </div>
          <button
            class="small"
            :class="{ primary: !installStatus(mod.id) }"
            :disabled="installStatus(mod.id) === 'RUNNING' || installStatus(mod.id) === 'QUEUED' || installStatus(mod.id) === 'SUCCEEDED'"
            @click="install(mod)"
          >
            {{
              installStatus(mod.id) === 'SUCCEEDED'
                ? 'Installed'
                : installStatus(mod.id) === 'FAILED'
                  ? 'Retry'
                  : installStatus(mod.id)
                    ? 'Installing…'
                    : 'Install'
            }}
          </button>
        </div>
        <template v-for="(jobId, id) in installJobs" :key="id">
          <JobOutput v-if="store.jobs[jobId]?.status === 'FAILED'" :job-id="jobId" max-height="8rem" />
        </template>
      </aside>
    </div>
  </template>
  <div v-else-if="loadError" class="error">{{ loadError }}</div>
  <div v-else-if="loading" class="muted">Loading…</div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 1rem;
}

.tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: -1px;
}

.tabs button {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.tabs button.active {
  background: var(--bg-elevated);
  color: var(--accent);
  border-bottom-color: var(--bg-elevated);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 22rem;
  gap: 1rem;
  align-items: start;
}

.installed {
  position: relative;
  border-top-left-radius: 0;
  min-height: 20rem;
}

.installed.dragging {
  border-color: var(--accent);
}

.head {
  margin-bottom: 0.5rem;
}

.filter {
  flex: 1;
  min-width: 10rem;
}

.dropzone {
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  padding: 3rem 1rem;
  text-align: center;
  color: var(--fg-muted);
  line-height: 1.6;
}

.hint {
  margin-top: 0.75rem;
  font-size: 0.8rem;
}

.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(22, 24, 29, 0.85);
  border: 2px dashed var(--accent);
  border-radius: var(--radius);
  color: var(--accent);
  font-size: 1.2rem;
  pointer-events: none;
}

.search h3 {
  margin: 0 0 0.75rem;
}

.small {
  font-size: 0.8rem;
}

.result {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--border);
}

.result .info {
  flex: 1;
  min-width: 0;
}

.result .description {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.error {
  color: var(--danger);
  margin: 0.5rem 0;
}

.notice {
  color: var(--accent);
  margin: 0.5rem 0;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
