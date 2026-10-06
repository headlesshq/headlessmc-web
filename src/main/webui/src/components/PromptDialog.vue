<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { store } from '../lib/store'
import AnsiText from './AnsiText.vue'
import PromptInput from './PromptInput.vue'

// Shows prompts of commands that wait for input, wherever the user currently is.
const route = useRoute()

const pending = computed(() => {
  // the console answers prompts of its own commands inline
  const consoleOwns = store.consoleOwnsPrompts && route.name === 'console'
  return Object.values(store.jobs).find((job) => job.prompt && !(consoleOwns && job.origin === 'console'))
})

// show the last lines of output, e.g. the Microsoft device code login link
const recentOutput = computed(() => {
  const output = pending.value?.output ?? ''
  return output.split('\n').slice(-8).join('\n')
})
</script>

<template>
  <div v-if="pending && pending.prompt" class="overlay">
    <div class="dialog card">
      <div class="muted mono">&gt; {{ pending.line }}</div>
      <div v-if="recentOutput.trim()" class="terminal"><AnsiText :text="recentOutput" /></div>
      <PromptInput :prompt="pending.prompt" />
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.dialog {
  width: min(40rem, 92vw);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.terminal {
  max-height: 12rem;
}
</style>
