<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  buildLine,
  fieldLabel,
  findChain,
  initialValues,
  loadCommands,
  missingRequired,
  optionKey,
  positionalKey,
  visibleOptions,
  visiblePositionals,
  type FieldValue,
  type FormValues,
} from '../lib/commands'
import { run } from '../lib/store'
import type { CommandModel } from '../lib/types'
import CompletionInput from './CompletionInput.vue'
import JobOutput from './JobOutput.vue'

// A form for any HeadlessMc command, generated from its picocli model.
const props = withDefaults(
  defineProps<{
    path: string[]
    initial?: Record<string, FieldValue>
    title?: string
    submitLabel?: string
    showDescription?: boolean
  }>(),
  { initial: () => ({}), title: undefined, submitLabel: 'Run', showDescription: true },
)

const emit = defineEmits<{ submitted: [jobId: string] }>()

const chain = ref<CommandModel[] | null>(null)
const values = ref<FormValues>({})
const error = ref('')
const jobId = ref<string | null>(null)
const busy = ref(false)

const command = computed(() => chain.value?.[chain.value.length - 1] ?? null)
const line = computed(() => (chain.value ? buildLine(chain.value, values.value) : ''))
const missing = computed(() => (chain.value ? missingRequired(chain.value, values.value) : []))

const levels = computed(() =>
  (chain.value ?? [])
    .map((cmd, depth) => ({ depth, command: cmd, options: visibleOptions(cmd) }))
    .filter((level) => level.depth > 0 && level.options.length > 0),
)

async function load() {
  error.value = ''
  try {
    const root = await loadCommands()
    const found = findChain(root, props.path)
    if (!found) {
      error.value = `Unknown command: ${props.path.join(' ')}`
      chain.value = null
      return
    }
    chain.value = found
    values.value = initialValues(found, props.initial)
  } catch (e) {
    error.value = String(e)
  }
}

onMounted(load)
watch(() => [props.path.join(' '), JSON.stringify(props.initial)], load)

function contextFor(depth: number, option: CommandModel['options'][number]): string {
  return chain.value ? buildLine(chain.value, values.value, { kind: 'option', depth, option }) : ''
}

function contextForPositional(positional: CommandModel['positionals'][number]): string {
  return chain.value ? buildLine(chain.value, values.value, { kind: 'positional', positional }) : ''
}

function stringValue(key: string): string {
  const value = values.value[key]
  return typeof value === 'string' ? value : ''
}

function setValue(key: string, value: FieldValue) {
  values.value = { ...values.value, [key]: value }
}

async function submit(extra = '') {
  busy.value = true
  error.value = ''
  try {
    const job = await run(extra ? `${line.value} ${extra}` : line.value)
    jobId.value = job.id
    emit('submitted', job.id)
  } catch (e) {
    error.value = String(e)
  } finally {
    busy.value = false
  }
}

function help() {
  run(`${props.path.join(' ')} --help`).then((job) => (jobId.value = job.id))
}
</script>

<template>
  <div class="command-form">
    <div v-if="error" class="error">{{ error }}</div>
    <form v-if="command && chain" @submit.prevent="submit()">
      <div v-if="title || showDescription" class="head">
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="showDescription && command.description" class="muted">{{ command.description }}</p>
      </div>

      <div v-for="positional in visiblePositionals(command)" :key="positionalKey(positional)" class="field">
        <label>
          {{ fieldLabel(positional) }}<span v-if="positional.required && positional.arityMin > 0" class="required">*</span>
        </label>
        <div class="input">
          <CompletionInput
            :model-value="stringValue(positionalKey(positional))"
            :context="contextForPositional(positional)"
            :multi-word="positional.type === 'list'"
            :completions="positional.completions"
            :input-type="positional.type === 'integer' || positional.type === 'number' ? 'number' : 'text'"
            :placeholder="positional.type === 'list' ? 'values separated by spaces' : ''"
            @update:model-value="setValue(positionalKey(positional), $event)"
          />
          <small v-if="positional.description" class="muted">{{ positional.description }}</small>
        </div>
      </div>

      <template v-for="level in levels" :key="level.depth">
        <div v-if="level.depth < chain.length - 1" class="level muted">options of <code>{{ level.command.path.join(' ') }}</code></div>
        <div v-for="option in level.options" :key="optionKey(level.depth, option)" class="field">
          <label :title="option.names.join(', ')">
            {{ option.names[0] }}<span v-if="option.required" class="required">*</span>
          </label>
          <div class="input">
            <label v-if="option.type === 'boolean' || option.arityMax === 0" class="checkbox">
              <input
                type="checkbox"
                :checked="values[optionKey(level.depth, option)] === true"
                @change="setValue(optionKey(level.depth, option), ($event.target as HTMLInputElement).checked)"
              />
              <span class="muted">{{ option.description }}</span>
            </label>
            <template v-else>
              <CompletionInput
                :model-value="stringValue(optionKey(level.depth, option))"
                :context="contextFor(level.depth, option)"
                :completions="option.completions"
                :input-type="option.type === 'integer' || option.type === 'number' ? 'number' : 'text'"
                :placeholder="option.defaultValue ? `default: ${option.defaultValue}` : option.paramLabel"
                @update:model-value="setValue(optionKey(level.depth, option), $event)"
              />
              <small v-if="option.description" class="muted">{{ option.description }}</small>
            </template>
          </div>
        </div>
      </template>

      <div class="row submit">
        <button type="submit" class="primary" :disabled="busy || missing.length > 0 || !command.runnable">
          {{ submitLabel }}
        </button>
        <button type="button" @click="help">Help</button>
        <code class="preview" title="The command that will be executed">&gt; {{ line }}</code>
      </div>
      <div v-if="!command.runnable" class="muted">This command needs a sub command.</div>
      <div v-else-if="missing.length" class="muted">Required: {{ missing.join(', ') }}</div>
    </form>

    <JobOutput v-if="jobId" :job-id="jobId" class="result" />
  </div>
</template>

<style scoped>
.command-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

h3 {
  margin: 0 0 0.25rem;
  font-size: 1rem;
}

.head p {
  margin: 0;
}

.field {
  display: grid;
  grid-template-columns: 11rem 1fr;
  gap: 0.75rem;
  align-items: start;
}

.field > label {
  padding-top: 0.4rem;
  font-family: var(--mono);
  font-size: 0.9em;
  overflow: hidden;
  text-overflow: ellipsis;
}

.input {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.checkbox {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  padding-top: 0.35rem;
}

.required {
  color: var(--danger);
  margin-left: 0.15rem;
}

.level {
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.submit {
  margin-top: 0.25rem;
}

.preview {
  color: var(--fg-muted);
  overflow-wrap: anywhere;
}

.error {
  color: var(--danger);
}
</style>
