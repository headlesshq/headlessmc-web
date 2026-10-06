<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import ProgressPanel from './components/ProgressPanel.vue'
import PromptDialog from './components/PromptDialog.vue'
import { isDone, store } from './lib/store'

const nav = [
  { to: '/', label: 'Dashboard' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/versions', label: 'Versions' },
  { to: '/profiles', label: 'Profiles' },
  { to: '/servers', label: 'Servers' },
  { to: '/mods', label: 'Mods' },
  { to: '/java', label: 'Java' },
  { to: '/config', label: 'Config' },
  { to: '/processes', label: 'Processes' },
  { to: '/commands', label: 'All commands' },
  { to: '/console', label: 'Console' },
]

const runningProcesses = computed(() => Object.values(store.processes).filter((p) => p.status === 'RUNNING').length)
const busyJob = computed(() => Object.values(store.jobs).find((job) => !isDone(job)))
</script>

<template>
  <div class="layout">
    <nav class="sidebar">
      <div class="brand">HeadlessMc</div>
      <RouterLink v-for="item in nav" :key="item.to" :to="item.to" class="nav-link">
        {{ item.label }}
        <span v-if="item.to === '/processes' && runningProcesses" class="badge ok">{{ runningProcesses }}</span>
      </RouterLink>
      <div class="status">
        <div v-if="busyJob" class="busy mono" :title="busyJob.line">▶ {{ busyJob.line }}</div>
        <div :class="store.connected ? 'online' : 'offline'">{{ store.connected ? '● connected' : '○ disconnected' }}</div>
      </div>
    </nav>
    <main class="content">
      <RouterView />
    </main>
    <PromptDialog />
    <ProgressPanel />
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 12rem 1fr;
  min-height: 100vh;
}

.sidebar {
  background: var(--bg-elevated);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 1rem 0.5rem;
  gap: 0.1rem;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  font-weight: 700;
  font-size: 1.1rem;
  padding: 0 0.6rem 1rem;
  color: var(--accent);
}

.nav-link {
  color: var(--fg);
  text-decoration: none;
  padding: 0.4rem 0.6rem;
  border-radius: var(--radius);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-link:hover {
  background: var(--bg-input);
}

.nav-link.router-link-exact-active,
.nav-link.router-link-active:not([href='/']) {
  background: var(--bg-input);
  color: var(--accent);
}

.status {
  margin-top: auto;
  font-size: 0.8rem;
  padding: 0 0.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.busy {
  color: var(--info);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.online {
  color: var(--accent);
}

.offline {
  color: var(--danger);
}

.content {
  padding: 1.5rem 2rem;
  min-width: 0;
}

@media (max-width: 720px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    height: auto;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .status {
    margin-top: 0;
  }

  .content {
    padding: 1rem;
  }
}
</style>
