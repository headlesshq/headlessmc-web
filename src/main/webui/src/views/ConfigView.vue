<script setup lang="ts">
import { computed, ref } from 'vue'
import RecentJob from '../components/RecentJob.vue'
import { api } from '../lib/api'
import { quoteArg } from '../lib/shell'
import { run } from '../lib/store'
import { useData } from '../lib/useData'

const showAll = ref(false)
const filter = ref('')
const { data, error, reload } = useData(() => api.config(showAll.value), [])
const edits = ref<Record<string, string>>({})
const temporary = ref(false)
const lastJob = ref<string | null>(null)

const filtered = computed(() => {
  const query = filter.value.toLowerCase()
  return data.value.filter(
    (property) =>
      !query || property.name.toLowerCase().includes(query) || (property.description ?? '').toLowerCase().includes(query),
  )
})

async function save(name: string) {
  const value = edits.value[name]
  const job = await run(`config set ${temporary.value ? '--temp ' : ''}${quoteArg(name)} ${quoteArg(value)}`)
  lastJob.value = job.id
  delete edits.value[name]
}

function toggleAll() {
  showAll.value = !showAll.value
  reload()
}
</script>

<template>
  <h1>Config</h1>
  <p class="muted">HeadlessMc's configuration. Changes are written to the HeadlessMc config file, unless set temporarily.</p>
  <div class="row toolbar">
    <input v-model="filter" placeholder="Filter…" />
    <label class="row"><input type="checkbox" :checked="showAll" @change="toggleAll" /> all properties</label>
    <label class="row"><input v-model="temporary" type="checkbox" /> only until restart (--temp)</label>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <RecentJob :job-id="lastJob" @dismiss="lastJob = null" />
  <div class="card">
    <table>
      <thead>
        <tr>
          <th>Property</th>
          <th>Value</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="property in filtered" :key="property.name">
          <td class="mono name">{{ property.name }}</td>
          <td class="value">
            <form class="row" @submit.prevent="save(property.name)">
              <input
                :value="edits[property.name] ?? property.value ?? ''"
                class="mono"
                @input="edits[property.name] = ($event.target as HTMLInputElement).value"
              />
              <button v-if="edits[property.name] !== undefined" class="small primary" type="submit">Save</button>
            </form>
          </td>
          <td class="muted">{{ property.description ?? '' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 1rem;
}

.name {
  white-space: nowrap;
}

.value input {
  width: 16rem;
}

.error {
  color: var(--danger);
}
</style>
