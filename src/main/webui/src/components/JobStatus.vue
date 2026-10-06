<script setup lang="ts">
import { computed } from 'vue'
import type { JobStatus } from '../lib/types'

const props = defineProps<{ status: JobStatus }>()

const css = computed(() => {
  switch (props.status) {
    case 'SUCCEEDED':
      return 'ok'
    case 'FAILED':
      return 'bad'
    case 'CANCELLED':
      return ''
    default:
      return 'busy'
  }
})

const label = computed(() => props.status.toLowerCase().replace(/_/g, ' '))
</script>

<template>
  <span class="badge" :class="css">{{ label }}</span>
</template>
