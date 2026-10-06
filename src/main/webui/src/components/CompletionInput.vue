<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../lib/api'
import type { CompletionCandidate } from '../lib/types'

// A text input that offers HeadlessMc's tab completions for its value.
const props = withDefaults(
  defineProps<{
    modelValue: string
    /** the command line in front of this field, e.g. "account --provider" */
    context: string
    /** whether the value consists of multiple words (multi value positional) */
    multiWord?: boolean
    completions?: boolean
    placeholder?: string
    inputType?: string
  }>(),
  { multiWord: false, completions: true, placeholder: '', inputType: 'text' },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const candidates = ref<CompletionCandidate[]>([])
const open = ref(false)
const highlighted = ref(0)
const replaceFrom = ref(0)
let timer: ReturnType<typeof setTimeout> | undefined
let requestId = 0

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})

function canComplete(text: string): boolean {
  return props.completions && (props.multiWord || !/[\s"'\\]/.test(text))
}

async function fetchCompletions() {
  const text = value.value
  if (!canComplete(text)) {
    candidates.value = []
    return
  }
  const prefix = props.context ? props.context + ' ' : ''
  const id = ++requestId
  try {
    const result = await api.complete(prefix + text)
    if (id !== requestId) return
    replaceFrom.value = Math.max(0, result.start - prefix.length)
    candidates.value = result.candidates.filter((candidate) => !candidate.value.startsWith('-')).slice(0, 200)
    highlighted.value = 0
  } catch {
    candidates.value = []
  }
}

function schedule() {
  clearTimeout(timer)
  timer = setTimeout(fetchCompletions, 150)
}

watch(() => [props.context, value.value], () => open.value && schedule())

function onFocus() {
  open.value = true
  schedule()
}

function onBlur() {
  // delay, so a click on a candidate is registered
  setTimeout(() => (open.value = false), 150)
}

function choose(candidate: CompletionCandidate) {
  const text = value.value
  value.value = text.slice(0, replaceFrom.value) + candidate.value + (props.multiWord ? ' ' : '')
  candidates.value = []
  if (props.multiWord) schedule()
}

function onKeydown(event: KeyboardEvent) {
  if (!open.value || candidates.value.length === 0) return
  if (event.key === 'ArrowDown') {
    highlighted.value = (highlighted.value + 1) % candidates.value.length
    event.preventDefault()
  } else if (event.key === 'ArrowUp') {
    highlighted.value = (highlighted.value - 1 + candidates.value.length) % candidates.value.length
    event.preventDefault()
  } else if (event.key === 'Tab' || (event.key === 'Enter' && candidates.value.length > 0 && open.value)) {
    const candidate = candidates.value[highlighted.value]
    if (candidate && candidate.value !== value.value.slice(replaceFrom.value)) {
      choose(candidate)
      event.preventDefault()
    }
  } else if (event.key === 'Escape') {
    open.value = false
  }
}
</script>

<template>
  <div class="completion-input">
    <input
      v-model="value"
      :type="inputType"
      :placeholder="placeholder"
      autocomplete="off"
      spellcheck="false"
      @focus="onFocus"
      @blur="onBlur"
      @input="open = true"
      @keydown="onKeydown"
    />
    <ul v-if="open && candidates.length" class="candidates">
      <li
        v-for="(candidate, i) in candidates"
        :key="candidate.value"
        :class="{ highlighted: i === highlighted }"
        @mousedown.prevent="choose(candidate)"
      >
        <span class="mono">{{ candidate.value }}</span>
        <span v-if="candidate.description" class="muted description">{{ candidate.description }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.completion-input {
  position: relative;
  flex: 1;
}

input {
  width: 100%;
}

.candidates {
  position: absolute;
  z-index: 20;
  left: 0;
  right: 0;
  top: 100%;
  margin: 2px 0 0;
  padding: 0.25rem 0;
  list-style: none;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  max-height: 16rem;
  overflow: auto;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

li {
  padding: 0.25rem 0.6rem;
  cursor: pointer;
  display: flex;
  gap: 0.75rem;
  justify-content: space-between;
}

li.highlighted,
li:hover {
  background: var(--bg-input);
}

.description {
  font-size: 0.85em;
  text-align: right;
}
</style>
