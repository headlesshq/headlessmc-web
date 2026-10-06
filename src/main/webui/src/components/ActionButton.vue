<script setup lang="ts">
import { ref } from 'vue'
import { run, waitForJob } from '../lib/store'

// Runs a HeadlessMc command line when clicked, optionally after a confirmation.
const props = defineProps<{ line: string; confirm?: string; danger?: boolean }>()
const emit = defineEmits<{ started: [jobId: string]; finished: [jobId: string] }>()
const busy = ref(false)

async function click() {
  if (props.confirm && !window.confirm(props.confirm)) return
  busy.value = true
  try {
    const job = await run(props.line)
    emit('started', job.id)
    await waitForJob(job.id)
    emit('finished', job.id)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button class="small" :class="{ danger }" :disabled="busy" :title="line" @click="click"><slot /></button>
</template>
