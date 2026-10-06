<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { api } from '../lib/api'
import type { Prompt } from '../lib/types'

const props = defineProps<{ prompt: Prompt }>()
const value = ref(props.prompt.initial ?? '')
const input = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const busy = ref(false)
const error = ref('')

watch(
  () => props.prompt.id,
  () => {
    value.value = props.prompt.initial ?? ''
    error.value = ''
    focus()
  },
)

function focus() {
  nextTick(() => input.value?.focus())
}

onMounted(focus)

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await api.answer(props.prompt.jobId, props.prompt.id, value.value)
  } catch (e) {
    error.value = String(e)
  } finally {
    busy.value = false
  }
}

async function cancel() {
  await api.cancelPrompt(props.prompt.jobId, props.prompt.id).catch(() => undefined)
}
</script>

<template>
  <form class="prompt" @submit.prevent="submit">
    <label class="message">{{ prompt.message || 'Input required' }}</label>
    <div class="row">
      <input
        v-if="prompt.kind !== 'edit'"
        ref="input"
        v-model="value"
        :type="prompt.kind === 'password' ? 'password' : 'text'"
        autocomplete="off"
        class="value"
        @keydown.esc.prevent="cancel"
      />
      <textarea v-else ref="input" v-model="value" rows="3" class="value" @keydown.esc.prevent="cancel" />
      <button type="submit" class="primary" :disabled="busy">Send</button>
      <button type="button" :disabled="busy" @click="cancel">Cancel</button>
    </div>
    <div v-if="error" class="error">{{ error }}</div>
  </form>
</template>

<style scoped>
.prompt {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.message {
  font-weight: 500;
}

.value {
  flex: 1;
  min-width: 12rem;
  font-family: var(--mono);
}

.error {
  color: var(--danger);
}
</style>
