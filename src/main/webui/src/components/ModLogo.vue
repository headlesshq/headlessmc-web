<script setup lang="ts">
import { computed, ref, watch } from 'vue'

// A mod's logo, or a colored tile with its initial if it has none (or it fails to load).
const props = defineProps<{ src: string | null; name: string; size?: number }>()
const failed = ref(false)
watch(
  () => props.src,
  () => (failed.value = false),
)

const initial = computed(() => (props.name.match(/[A-Za-z0-9]/)?.[0] ?? '?').toUpperCase())
const hue = computed(() => [...props.name].reduce((hash, c) => (hash * 31 + c.charCodeAt(0)) % 360, 7))
const px = computed(() => `${props.size ?? 48}px`)
</script>

<template>
  <img v-if="src && !failed" :src="src" :alt="name" class="logo" loading="lazy" @error="failed = true" />
  <div v-else class="logo placeholder" :style="{ background: `hsl(${hue} 35% 30%)` }" aria-hidden="true">{{ initial }}</div>
</template>

<style scoped>
.logo {
  width: v-bind(px);
  height: v-bind(px);
  flex: none;
  border-radius: 8px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-input);
}

.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 1.3rem;
  color: rgba(255, 255, 255, 0.85);
}
</style>
