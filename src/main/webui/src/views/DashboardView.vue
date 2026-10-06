<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import CommandForm from '../components/CommandForm.vue'
import JobStatus from '../components/JobStatus.vue'
import { api } from '../lib/api'
import { store } from '../lib/store'
import { useData } from '../lib/useData'

const { data: info } = useData(() => api.info(), null)
const { data: accounts } = useData(() => api.accounts(), null)
const { data: counts } = useData(async () => {
  const [profiles, servers, versions, java] = await Promise.all([api.profiles(), api.servers(), api.versions(), api.java()])
  return { profiles: profiles.length, servers: servers.length, versions: versions.length, java: java.length }
}, null)

const processes = computed(() => Object.values(store.processes).filter((process) => process.status === 'RUNNING'))
const recentJobs = computed(() => Object.values(store.jobs).slice(-6).reverse())
const mb = (bytes: number) => `${Math.round(bytes / 1024 / 1024)} MB`
</script>

<template>
  <h1>Dashboard</h1>
  <div class="grid">
    <div class="card">
      <h3>{{ info?.name ?? 'HeadlessMc' }} <span class="muted">{{ info?.version }}</span></h3>
      <table v-if="info" class="compact">
        <tbody>
        <tr v-for="(dir, name) in info.directories" :key="name">
          <th>{{ name }}</th>
          <td class="mono">{{ dir }}</td>
        </tr>
        <tr>
          <th>memory</th>
          <td>{{ mb(info.usedMemory) }} / {{ mb(info.maxMemory) }} · Java {{ info.javaVersion }}</td>
        </tr>
        </tbody>
      </table>
    </div>
    <div class="card">
      <h3>Account</h3>
      <p v-if="accounts?.selected">
        <strong>{{ accounts.selected.name }}</strong> <span class="muted">({{ accounts.selected.provider }})</span>
      </p>
      <p v-else class="muted">No account selected. Offline launches are forced headless.</p>
      <RouterLink to="/accounts">Manage accounts →</RouterLink>
      <h3 class="spaced">Installed</h3>
      <div v-if="counts" class="stats">
        <RouterLink to="/profiles"><strong>{{ counts.profiles }}</strong> profiles</RouterLink>
        <RouterLink to="/servers"><strong>{{ counts.servers }}</strong> servers</RouterLink>
        <RouterLink to="/versions"><strong>{{ counts.versions }}</strong> versions</RouterLink>
        <RouterLink to="/java"><strong>{{ counts.java }}</strong> java</RouterLink>
      </div>
    </div>
  </div>

  <h2>Launch</h2>
  <div class="card">
    <CommandForm :path="['launch']" submit-label="Launch" />
  </div>

  <div class="grid">
    <div>
      <h2>Running processes</h2>
      <div class="card">
        <div v-if="!processes.length" class="empty">Nothing is running.</div>
        <div v-for="process in processes" :key="process.id" class="row">
          <RouterLink :to="`/processes/${process.id}`">{{ process.name }}</RouterLink>
          <span class="muted">pid {{ process.pid }}</span>
        </div>
      </div>
    </div>
    <div>
      <h2>Recent commands</h2>
      <div class="card">
        <div v-if="!recentJobs.length" class="empty">No commands have been run yet.</div>
        <div v-for="job in recentJobs" :key="job.id" class="row job">
          <JobStatus :status="job.status" />
          <code>{{ job.line }}</code>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr));
  gap: 1rem;
}

h3 {
  margin: 0 0 0.75rem;
}

.spaced {
  margin-top: 1.25rem;
}

.compact th,
.compact td {
  padding: 0.2rem 0.5rem 0.2rem 0;
  border: none;
}

.stats {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.job {
  padding: 0.2rem 0;
}
</style>
