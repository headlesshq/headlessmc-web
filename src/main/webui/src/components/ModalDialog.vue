<script setup lang="ts">
defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div class="overlay" @mousedown.self="emit('close')" @keydown.esc="emit('close')">
    <div class="dialog card">
      <div class="row head">
        <h2>{{ title }}</h2>
        <button class="small" @click="emit('close')">Close</button>
      </div>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 5vh 1rem;
  z-index: 80;
  overflow: auto;
}

.dialog {
  width: min(52rem, 100%);
}

.head {
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

h2 {
  margin: 0;
}
</style>
