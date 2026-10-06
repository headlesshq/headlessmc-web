<script setup lang="ts">
import { computed } from 'vue'
import { store } from '../lib/store'
import JobOutput from './JobOutput.vue'

// Shows the output of the last job started from a page (e.g. by an ActionButton).
const props = defineProps<{ jobId: string | null }>()
const emit = defineEmits<{ dismiss: [] }>()
const job = computed(() => (props.jobId ? store.jobs[props.jobId] : undefined))
</script>

<template>
  <div v-if="job" class="card recent">
    <div class="row head">
      <strong>Last command</strong>
      <button class="small" @click="emit('dismiss')">Dismiss</button>
    </div>
    <JobOutput :job-id="job.id" max-height="14rem" />
  </div>
</template>

<style scoped>
.recent {
  margin-bottom: 1rem;
}

.head {
  justify-content: space-between;
  margin-bottom: 0.5rem;
}
</style>
