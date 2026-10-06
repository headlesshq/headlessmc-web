<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AnsiText from '../components/AnsiText.vue'
import { api } from '../lib/api'
import { loadProcessOutput, store } from '../lib/store'
import type { ProcessInfo } from '../lib/types'

const MAX_RENDERED = 2000

const route = useRoute()
const router = useRouter()
const selectedId = computed(() => (route.params.id as string | undefined) || undefined)
const processes = computed(() => Object.values(store.processes).sort((a, b) => b.started - a.started))
const selected = computed(() => (selectedId.value ? store.processes[selectedId.value] : undefined))
const lines = computed(() => {
  const all = selectedId.value ? (store.processLines[selectedId.value] ?? []) : []
  return all.slice(-MAX_RENDERED)
})

const input = ref('')
const history = ref<string[]>([])
const historyIndex = ref(-1)
const error = ref('')
const output = ref<HTMLElement | null>(null)
const follow = ref(true)

watch(
  selectedId,
  async (id) => {
    error.value = ''
    if (id) {
      await loadProcessOutput(id).catch((e) => (error.value = String(e)))
      scrollToBottom()
    } else if (processes.value.length) {
      router.replace(`/processes/${processes.value[0].id}`)
    }
  },
  { immediate: true },
)

watch(
  () => processes.value.length,
  () => {
    if (!selectedId.value && processes.value.length) router.replace(`/processes/${processes.value[0].id}`)
  },
)

watch(() => lines.value.length, scrollToBottom)

function scrollToBottom() {
  if (!follow.value) return
  nextTick(() => {
    if (output.value) output.value.scrollTop = output.value.scrollHeight
  })
}

function onScroll() {
  const element = output.value
  if (element) follow.value = element.scrollHeight - element.scrollTop - element.clientHeight < 40
}

async function send() {
  if (!selectedId.value || !input.value) return
  error.value = ''
  try {
    await api.processInput(selectedId.value, input.value)
    history.value.push(input.value)
    historyIndex.value = -1
    input.value = ''
    follow.value = true
  } catch (e) {
    error.value = String(e)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowUp' && history.value.length) {
    historyIndex.value = historyIndex.value < 0 ? history.value.length - 1 : Math.max(0, historyIndex.value - 1)
    input.value = history.value[historyIndex.value]
    event.preventDefault()
  } else if (event.key === 'ArrowDown' && historyIndex.value >= 0) {
    historyIndex.value++
    input.value = historyIndex.value < history.value.length ? history.value[historyIndex.value] : ''
    if (historyIndex.value >= history.value.length) historyIndex.value = -1
    event.preventDefault()
  }
}

async function action(kind: 'stop' | 'kill' | 'remove', process: ProcessInfo) {
  error.value = ''
  try {
    if (kind === 'stop') await api.stopProcess(process.id)
    else if (kind === 'kill') await api.killProcess(process.id)
    else {
      await api.removeProcess(process.id)
      router.replace('/processes')
    }
  } catch (e) {
    error.value = String(e)
  }
}

const statusClass = (process: ProcessInfo) =>
  process.status === 'RUNNING' ? 'ok' : process.exitCode === 0 || process.status === 'STOPPED' ? '' : 'bad'
</script>

<template>
  <h1>Processes</h1>
  <div v-if="!processes.length" class="card empty">
    No processes. Launch a client from the dashboard or profiles, or launch a server.
  </div>
  <div v-else class="layout">
    <div class="list">
      <RouterLink
        v-for="process in processes"
        :key="process.id"
        :to="`/processes/${process.id}`"
        class="item"
        :class="{ active: process.id === selectedId }"
      >
        <div class="name">{{ process.name }}</div>
        <div class="row">
          <span class="badge" :class="statusClass(process)">
            {{ process.status.toLowerCase() }}<template v-if="process.exitCode !== null"> ({{ process.exitCode }})</template>
          </span>
          <span class="muted">pid {{ process.pid }}</span>
        </div>
      </RouterLink>
    </div>
    <div v-if="selected" class="detail">
      <div class="row head">
        <strong>{{ selected.name }}</strong>
        <span class="muted mono">{{ selected.directory }}</span>
        <span v-if="selected.attempt > 1" class="muted">attempt {{ selected.attempt }}</span>
        <div class="actions">
          <button v-if="selected.status === 'RUNNING'" class="small" @click="action('stop', selected)">Stop</button>
          <button v-if="selected.status === 'RUNNING'" class="small danger" @click="action('kill', selected)">Kill</button>
          <button v-else class="small" @click="action('remove', selected)">Remove</button>
        </div>
      </div>
      <div ref="output" class="terminal output" @scroll="onScroll">
        <div v-for="line in lines" :key="line.n" :class="`line ${line.type}`"><AnsiText :text="line.text" /></div>
      </div>
      <form class="row" @submit.prevent="send">
        <input
          v-model="input"
          class="mono command"
          :disabled="selected.status !== 'RUNNING'"
          placeholder="Send a line to the process (e.g. a server command, or an hmc-specifics command)"
          @keydown="onKeydown"
        />
        <button class="primary" type="submit" :disabled="selected.status !== 'RUNNING' || !input">Send</button>
      </form>
      <div v-if="error" class="error">{{ error }}</div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 16rem 1fr;
  gap: 1rem;
  height: calc(100vh - 6rem);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  overflow: auto;
}

.item {
  display: block;
  color: var(--fg);
  text-decoration: none;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0.5rem 0.6rem;
  background: var(--bg-elevated);
}

.item.active {
  border-color: var(--accent);
}

.name {
  font-family: var(--mono);
  font-size: 0.85rem;
  margin-bottom: 0.3rem;
  overflow-wrap: anywhere;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
  min-height: 0;
}

.head .actions {
  margin-left: auto;
}

.output {
  flex: 1;
  min-height: 10rem;
}

.line.err {
  color: #f0a0a6;
}

.line.in {
  color: var(--info);
}

.line.in::before {
  content: '> ';
}

.line.hmc {
  color: var(--warning);
}

.command {
  flex: 1;
}

.error {
  color: var(--danger);
}
</style>
