<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { api } from '../lib/api'
import { isDone, store } from '../lib/store'
import AnsiText from './AnsiText.vue'
import JobStatus from './JobStatus.vue'

const props = withDefaults(defineProps<{ jobId: string; showLine?: boolean; maxHeight?: string }>(), {
  showLine: true,
  maxHeight: '24rem',
})

const job = computed(() => store.jobs[props.jobId])
const output = ref<HTMLElement | null>(null)

watch(
  () => job.value?.output,
  async () => {
    const element = output.value
    if (!element) return
    const atBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 40
    await nextTick()
    if (atBottom) element.scrollTop = element.scrollHeight
  },
)

function cancel() {
  api.cancel(props.jobId).catch(() => undefined)
}
</script>

<template>
  <div v-if="job" class="job">
    <div class="row header">
      <code v-if="showLine" class="line">&gt; {{ job.line }}</code>
      <JobStatus :status="job.status" />
      <span v-if="job.exitCode !== null && job.exitCode !== 0" class="muted">exit code {{ job.exitCode }}</span>
      <button v-if="!isDone(job)" class="small danger" @click="cancel">Cancel</button>
    </div>
    <div v-if="job.output" ref="output" class="terminal" :style="{ maxHeight }"><AnsiText :text="job.output" /></div>
    <div v-if="job.prompt" class="muted">Waiting for input…</div>
  </div>
</template>

<style scoped>
.job {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.line {
  color: var(--fg-muted);
}
</style>
