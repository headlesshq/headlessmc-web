<script setup lang="ts">
import { computed } from 'vue'
import { store } from '../lib/store'
import type { Progress } from '../lib/types'

const bars = computed(() => Object.values(store.progress))

function percent(progress: Progress): number | null {
  if (progress.max <= 0) return null
  return Math.min(100, Math.round((progress.current / progress.max) * 100))
}

function amount(progress: Progress): string {
  const scale = (n: number) => (progress.unit ? (n / progress.unitSize).toFixed(1) : String(n))
  const unit = progress.unit ? ` ${progress.unit}` : ''
  return progress.max > 0 ? `${scale(progress.current)}/${scale(progress.max)}${unit}` : `${scale(progress.current)}${unit}`
}
</script>

<template>
  <div v-if="bars.length" class="progress-panel">
    <div v-for="bar in bars" :key="bar.id" class="bar">
      <div class="row label">
        <span>{{ bar.task }}</span>
        <span class="muted">{{ amount(bar) }}</span>
      </div>
      <div class="track">
        <div
          class="fill"
          :class="{ indeterminate: percent(bar) === null }"
          :style="{ width: percent(bar) === null ? '30%' : percent(bar) + '%' }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.progress-panel {
  position: fixed;
  right: 1rem;
  bottom: 1rem;
  width: 22rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 0.75rem;
  z-index: 50;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

.label {
  justify-content: space-between;
  font-size: 0.85rem;
}

.track {
  height: 6px;
  background: var(--bg-input);
  border-radius: 3px;
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.2s;
}

.fill.indeterminate {
  animation: slide 1.2s linear infinite;
}

@keyframes slide {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(350%);
  }
}
</style>
