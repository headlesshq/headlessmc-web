<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ActionButton from '../components/ActionButton.vue'
import JobOutput from '../components/JobOutput.vue'
import RecentJob from '../components/RecentJob.vue'
import { api } from '../lib/api'
import { quoteArg } from '../lib/shell'
import { run } from '../lib/store'
import { useData } from '../lib/useData'

const { data, error } = useData(() => api.accounts(), null)
const lastJob = ref<string | null>(null)

const provider = ref('default')
const method = ref('default')
const loginJob = ref<string | null>(null)
const methods = computed(() => data.value?.providers.find((p) => p.name === provider.value)?.methods ?? [])
watch(methods, (available) => {
  if (!available.includes(method.value)) method.value = available[0] ?? 'default'
})

const isSelected = (name: string, providerName: string) =>
  data.value?.selected?.name === name && data.value?.selected?.provider === providerName

async function login() {
  const job = await run(`account --provider ${quoteArg(provider.value)} login --method ${quoteArg(method.value)}`)
  loginJob.value = job.id
}

const accountLine = (action: string, providerName: string, name: string) =>
  `account --provider ${quoteArg(providerName)} ${action} ${quoteArg(name)}`
</script>

<template>
  <h1>Accounts</h1>
  <div v-if="error" class="error">{{ error }}</div>
  <RecentJob :job-id="lastJob" @dismiss="lastJob = null" />

  <div class="card">
    <h3>Log in</h3>
    <p class="muted">
      The <code>default</code> provider logs into a Microsoft account with a device code: open the link that is shown
      and confirm the login. <code>offline</code> accounts can only be used to run the game headless.
    </p>
    <form class="row" @submit.prevent="login">
      <label>Provider</label>
      <select v-model="provider">
        <option v-for="p in data?.providers ?? []" :key="p.name" :value="p.name">{{ p.name }}</option>
      </select>
      <label>Method</label>
      <select v-model="method">
        <option v-for="m in methods" :key="m" :value="m">{{ m }}</option>
      </select>
      <button class="primary" type="submit">Log in</button>
    </form>
    <JobOutput v-if="loginJob" :job-id="loginJob" class="spaced" />
  </div>

  <template v-for="p in data?.providers ?? []" :key="p.name">
    <h2>{{ p.name }}</h2>
    <div class="card">
      <div v-if="!p.accounts.length" class="empty">No accounts.</div>
      <table v-else>
        <thead>
          <tr>
            <th>Name</th>
            <th>UUID</th>
            <th>Type</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="account in p.accounts" :key="account.uuid + account.name">
            <td>
              {{ account.name }}
              <span v-if="isSelected(account.name, p.name)" class="badge ok">selected</span>
            </td>
            <td class="mono">{{ account.uuid }}</td>
            <td>{{ account.type }}</td>
            <td class="actions">
              <ActionButton :line="accountLine('select', p.name, account.name)" @started="lastJob = $event">Select</ActionButton>
              <ActionButton :line="accountLine('refresh', p.name, account.name)" @started="lastJob = $event">Refresh</ActionButton>
              <ActionButton
                :line="accountLine('remove', p.name, account.name)"
                :confirm="`Remove account ${account.name}?`"
                danger
                @started="lastJob = $event"
              >
                Remove
              </ActionButton>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<style scoped>
h3 {
  margin-top: 0;
}

.spaced {
  margin-top: 1rem;
}

.error {
  color: var(--danger);
}
</style>
