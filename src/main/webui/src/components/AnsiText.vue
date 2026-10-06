<script setup lang="ts">
import { computed } from 'vue'
import { linkify, parseAnsi, styleToCss } from '../lib/ansi'

const props = defineProps<{ text: string }>()

const segments = computed(() =>
  parseAnsi(props.text.replace(/\r(?!\n)/g, '')).map((segment) => ({
    css: styleToCss(segment.style),
    parts: linkify(segment.text),
  })),
)
</script>

<template>
  <span class="ansi"
    ><span v-for="(segment, i) in segments" :key="i" :style="segment.css"
      ><template v-for="(part, j) in segment.parts" :key="j"
        ><a v-if="part.url" :href="part.url" target="_blank" rel="noopener noreferrer">{{ part.text }}</a
        ><template v-else>{{ part.text }}</template></template
      ></span
    ></span
  >
</template>
