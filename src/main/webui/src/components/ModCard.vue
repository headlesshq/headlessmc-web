<script setup lang="ts">
import { computed } from 'vue'
import type { ModFile } from '../lib/types'
import ModLogo from './ModLogo.vue'

const props = defineProps<{ mod: ModFile; logo: string | null; busy?: boolean }>()
const emit = defineEmits<{ toggle: [enabled: boolean]; remove: [] }>()

const size = computed(() => {
  const bytes = props.mod.size
  if (props.mod.directory) return 'folder'
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
})
const others = computed(() => props.mod.mods.slice(1).map((mod) => mod.name))
</script>

<template>
  <div class="mod" :class="{ disabled: !mod.enabled }">
    <ModLogo :src="logo" :name="mod.displayName" />
    <div class="info">
      <div class="title">
        <strong>{{ mod.displayName }}</strong>
        <span v-if="mod.version" class="version">{{ mod.version }}</span>
        <span v-if="!mod.enabled" class="badge">disabled</span>
      </div>
      <div v-if="mod.description" class="description">{{ mod.description }}</div>
      <div class="meta muted">
        <span class="mono">{{ mod.fileName }}</span>
        <span>{{ size }}</span>
        <span v-if="mod.world">world: {{ mod.world }}</span>
        <span v-if="mod.authors.length">by {{ mod.authors.join(', ') }}</span>
        <span v-if="others.length">also contains {{ others.join(', ') }}</span>
      </div>
    </div>
    <div class="controls">
      <label class="switch" :title="mod.enabled ? 'Disable' : 'Enable'">
        <input type="checkbox" :checked="mod.enabled" :disabled="busy" @change="emit('toggle', ($event.target as HTMLInputElement).checked)" />
        <span class="slider" />
      </label>
      <button class="small danger" :disabled="busy" title="Remove" @click="emit('remove')">Remove</button>
    </div>
  </div>
</template>

<style scoped>
.mod {
  display: flex;
  gap: 0.9rem;
  align-items: center;
  padding: 0.7rem 0.4rem;
  border-bottom: 1px solid var(--border);
}

.mod:last-child {
  border-bottom: none;
}

.mod.disabled .info,
.mod.disabled :deep(.logo) {
  opacity: 0.5;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.title {
  display: flex;
  gap: 0.5rem;
  align-items: baseline;
  flex-wrap: wrap;
}

.version {
  color: var(--fg-muted);
  font-size: 0.85rem;
}

.description {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: 0.8rem;
}

.meta .mono {
  overflow-wrap: anywhere;
}

.controls {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.switch {
  position: relative;
  width: 2.2rem;
  height: 1.2rem;
  flex: none;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  inset: 0;
  cursor: pointer;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 999px;
  transition: background 0.15s;
}

.slider::before {
  content: '';
  position: absolute;
  width: 0.8rem;
  height: 0.8rem;
  left: 0.15rem;
  top: 0.13rem;
  border-radius: 50%;
  background: var(--fg-muted);
  transition: transform 0.15s;
}

.switch input:checked + .slider {
  background: var(--accent-strong);
  border-color: var(--accent-strong);
}

.switch input:checked + .slider::before {
  transform: translateX(1rem);
  background: #fff;
}
</style>
