<script setup lang="ts">
import { computed, ref } from 'vue'
import CommandForm from '../components/CommandForm.vue'
import { api } from '../lib/api'
import { useData } from '../lib/useData'

const { data: profiles } = useData(async () => [...(await api.profiles()), ...(await api.servers())], [])
const target = ref('')
const tab = ref<'list' | 'search' | 'add' | 'remove' | 'worlds'>('search')
const tabs = [
  { id: 'search', label: 'Search' },
  { id: 'add', label: 'Add' },
  { id: 'list', label: 'Installed' },
  { id: 'remove', label: 'Remove' },
  { id: 'worlds', label: 'Worlds' },
] as const

// the profile/version positional of the mod commands
const initial = computed((): Record<string, string> => {
  if (!target.value) return {}
  switch (tab.value) {
    case 'add':
      return { '#2': target.value }
    case 'search':
    case 'remove':
      return { '#1': target.value }
    default:
      return { '#0': target.value }
  }
})
</script>

<template>
  <h1>Mods</h1>
  <p class="muted">
    Mods, resource packs, shaders, data packs and mod packs from mod distribution platforms like Modrinth.
    Choose a profile or server, or type a version like <code>fabric 1.21.1</code> into the forms.
  </p>
  <div class="row toolbar">
    <label>Profile</label>
    <select v-model="target">
      <option value="">(enter in form)</option>
      <option v-for="profile in profiles" :key="profile.name" :value="profile.name">{{ profile.name }} ({{ profile.side }})</option>
    </select>
  </div>
  <div class="tabs">
    <button v-for="t in tabs" :key="t.id" :class="{ active: tab === t.id }" @click="tab = t.id">{{ t.label }}</button>
  </div>
  <div class="card">
    <CommandForm :key="tab + target" :path="['mod', tab]" :initial="initial" />
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 1rem;
}

.tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: -1px;
}

.tabs button {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.tabs button.active {
  background: var(--bg-elevated);
  color: var(--accent);
  border-bottom-color: var(--bg-elevated);
}

.card {
  border-top-left-radius: 0;
}
</style>
