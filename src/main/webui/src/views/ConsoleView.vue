<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import AnsiText from '../components/AnsiText.vue'
import JobStatus from '../components/JobStatus.vue'
import { api } from '../lib/api'
import { History } from '../lib/history'
import { applyCompletion, commonPrefix } from '../lib/shell'
import { isDone, run, store, type ConsoleEntry } from '../lib/store'
import type { CompletionCandidate, Prompt } from '../lib/types'

// A simple HeadlessMc shell: commands, their output, HeadlessMc logs, prompts and tab completion.
const line = ref('')
const input = ref<HTMLInputElement | null>(null)
const output = ref<HTMLElement | null>(null)
const onlyConsole = ref(false)
const clearedAt = ref(0)
const follow = ref(true)
const history = new History()

interface Menu {
  start: number
  end: number
  candidates: CompletionCandidate[]
  index: number
}

const menu = ref<Menu | null>(null)
let completionRequest = 0

const entries = computed(() =>
  store.timeline.slice(clearedAt.value).filter((entry: ConsoleEntry) => {
    if (entry.kind === 'log') return !onlyConsole.value
    const job = store.jobs[entry.jobId!]
    return job && (!onlyConsole.value || job.origin === 'console')
  }),
)

/** The prompt of a running command started from the console, answered in the input line. */
const prompt = computed<Prompt | null>(() => {
  const job = Object.values(store.jobs).find((j) => j.prompt && j.origin === 'console')
  return job?.prompt ?? null
})

const runningConsoleJob = computed(() =>
  Object.values(store.jobs)
    .reverse()
    .find((job) => job.origin === 'console' && !isDone(job)),
)

onMounted(() => {
  store.consoleOwnsPrompts = true
  focus()
  scrollToBottom()
})

onUnmounted(() => {
  store.consoleOwnsPrompts = false
})

watch(prompt, (current) => {
  line.value = current?.kind === 'edit' ? (current.initial ?? '') : ''
  menu.value = null
  focus()
})

watch(
  () => [entries.value.length, entries.value.map((e) => (e.jobId ? store.jobs[e.jobId]?.output?.length : 0)).join()],
  scrollToBottom,
)

function focus() {
  nextTick(() => input.value?.focus())
}

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

async function submit() {
  menu.value = null
  const current = prompt.value
  if (current) {
    const value = line.value
    line.value = ''
    await api.answer(current.jobId, current.id, value).catch(() => undefined)
    return
  }
  const text = line.value.trim()
  line.value = ''
  history.add(text)
  follow.value = true
  if (!text) return
  if (text === 'clear' || text === 'cls') {
    clear()
    return
  }
  await run(text, 'console')
}

function clear() {
  clearedAt.value = store.timeline.length
}

function setLine(value: string, cursor: number) {
  line.value = value
  nextTick(() => input.value?.setSelectionRange(cursor, cursor))
}

async function complete() {
  const el = input.value
  if (!el) return
  const cursor = el.selectionStart ?? line.value.length

  if (menu.value) {
    // cycle through the candidates of the open menu
    const m = menu.value
    m.index = (m.index + 1) % m.candidates.length
    const applied = applyCompletion(line.value, m.start, m.end, m.candidates[m.index].value, false)
    m.end = applied.cursor
    setLine(applied.line, applied.cursor)
    return
  }

  const id = ++completionRequest
  const result = await api.complete(line.value, cursor).catch(() => null)
  if (!result || id !== completionRequest || result.candidates.length === 0) return
  if (result.candidates.length === 1) {
    const applied = applyCompletion(line.value, result.start, result.end, result.candidates[0].value)
    setLine(applied.line, applied.cursor)
    return
  }

  const prefix = commonPrefix(result.candidates.map((candidate) => candidate.value))
  let end = result.end
  if (prefix.length > result.word.length) {
    const applied = applyCompletion(line.value, result.start, result.end, prefix, false)
    end = applied.cursor
    setLine(applied.line, applied.cursor)
  }
  menu.value = { start: result.start, end, candidates: result.candidates, index: -1 }
}

function choose(candidate: CompletionCandidate) {
  const m = menu.value
  if (!m) return
  const applied = applyCompletion(line.value, m.start, m.end, candidate.value)
  menu.value = null
  setLine(applied.line, applied.cursor)
  focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Tab') {
    event.preventDefault()
    if (!prompt.value) complete()
    return
  }
  if (event.key === 'Enter' && menu.value && menu.value.index >= 0) {
    // accept the highlighted candidate
    event.preventDefault()
    choose(menu.value.candidates[menu.value.index])
    return
  }
  if (event.key !== 'Shift') menu.value = null
  if (event.key === 'Escape') return
  if (event.key === 'ArrowUp' && !prompt.value) {
    const previous = history.previous(line.value)
    if (previous !== undefined) setLine(previous, previous.length)
    event.preventDefault()
  } else if (event.key === 'ArrowDown' && !prompt.value) {
    const next = history.next()
    if (next !== undefined) setLine(next, next.length)
    event.preventDefault()
  } else if (event.key === 'c' && event.ctrlKey && !window.getSelection()?.toString()) {
    event.preventDefault()
    if (runningConsoleJob.value) {
      api.cancel(runningConsoleJob.value.id).catch(() => undefined)
    } else {
      line.value = ''
    }
  } else if (event.key === 'l' && event.ctrlKey) {
    event.preventDefault()
    clear()
  }
}

const levelClass = (level: string) =>
  level === 'SEVERE' || level === 'ERROR' ? 'log-error' : level === 'WARNING' || level === 'WARN' ? 'log-warn' : 'log-info'
</script>

<template>
  <div class="console-view">
    <div class="row toolbar">
      <h1>Console</h1>
      <label class="row muted"><input v-model="onlyConsole" type="checkbox" /> only console commands</label>
      <button class="small" @click="clear">Clear</button>
      <span class="muted hint">Tab completes · ↑/↓ history · Ctrl+C cancels · <code>help</code> lists commands</span>
    </div>
    <div ref="output" class="terminal output" @scroll="onScroll" @click="focus">
      <template v-for="entry in entries" :key="entry.key">
        <div v-if="entry.kind === 'job' && store.jobs[entry.jobId!]" class="job">
          <div class="command">
            <span class="prompt-char">&gt;</span> {{ store.jobs[entry.jobId!].line }}
            <span v-if="store.jobs[entry.jobId!].origin !== 'console'" class="origin">({{ store.jobs[entry.jobId!].origin }})</span>
            <JobStatus v-if="store.jobs[entry.jobId!].status !== 'SUCCEEDED'" :status="store.jobs[entry.jobId!].status" />
          </div>
          <AnsiText :text="store.jobs[entry.jobId!].output ?? ''" />
        </div>
        <div v-else-if="entry.log" :class="levelClass(entry.log.level)">[{{ entry.log.level }}] {{ entry.log.message }}</div>
      </template>
    </div>
    <div class="input-area">
      <ul v-if="menu" class="menu">
        <li
          v-for="(candidate, i) in menu.candidates"
          :key="candidate.value"
          :class="{ highlighted: i === menu.index }"
          :title="candidate.description ?? ''"
          @mousedown.prevent="choose(candidate)"
        >
          {{ candidate.value }}
        </li>
      </ul>
      <form class="input-line" @submit.prevent="submit">
        <label v-if="prompt" class="prompt-message">{{ prompt.message || 'Input' }}</label>
        <span v-else class="prompt-char">&gt;</span>
        <input
          ref="input"
          v-model="line"
          :type="prompt?.kind === 'password' ? 'password' : 'text'"
          autocomplete="off"
          spellcheck="false"
          :placeholder="prompt ? '' : 'Enter a HeadlessMc command, e.g. launch fabric 1.21.1'"
          @keydown="onKeydown"
        />
      </form>
    </div>
  </div>
</template>

<style scoped>
.console-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 3rem);
  gap: 0.5rem;
}

.toolbar h1 {
  margin: 0 1rem 0 0;
}

.hint {
  margin-left: auto;
  font-size: 0.85rem;
}

.output {
  flex: 1;
  min-height: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.job {
  margin-bottom: 0.35rem;
}

.command {
  color: #fff;
  font-weight: 600;
}

.origin {
  color: var(--fg-muted);
  font-weight: normal;
}

.prompt-char {
  color: var(--accent);
}

.log-info {
  color: var(--fg-muted);
}

.log-warn {
  color: var(--warning);
}

.log-error {
  color: var(--danger);
}

.input-area {
  position: relative;
  margin-top: -0.5rem;
}

.input-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #0f1014;
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 var(--radius) var(--radius);
  padding: 0.4rem 0.8rem;
  font-family: var(--mono);
}

.input-line input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: var(--mono);
  font-size: 13px;
  padding: 0.2rem 0;
}

.input-line input:focus {
  outline: none;
}

.prompt-message {
  color: var(--info);
  white-space: nowrap;
}

.menu {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  margin: 0;
  padding: 0.4rem 0.8rem;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 1.25rem;
  max-height: 12rem;
  overflow: auto;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  font-family: var(--mono);
  font-size: 13px;
}

.menu li {
  cursor: pointer;
}

.menu li.highlighted,
.menu li:hover {
  color: var(--accent);
}
</style>
