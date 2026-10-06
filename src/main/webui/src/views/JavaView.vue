<script setup lang="ts">
import { ref } from 'vue'
import ActionButton from '../components/ActionButton.vue'
import CommandForm from '../components/CommandForm.vue'
import ModalDialog from '../components/ModalDialog.vue'
import RecentJob from '../components/RecentJob.vue'
import { api } from '../lib/api'
import { quoteArg } from '../lib/shell'
import { useData } from '../lib/useData'

const { data, error } = useData(() => api.java(), [])
const lastJob = ref<string | null>(null)
const dialog = ref<null | 'install' | 'remote'>(null)
</script>

<template>
  <h1>Java</h1>
  <p class="muted">Java installations HeadlessMc found or installed. Missing versions are downloaded automatically if enabled.</p>
  <div class="row toolbar">
    <button class="primary" @click="dialog = 'install'">Install java</button>
    <button @click="dialog = 'remote'">Browse distributions</button>
    <ActionButton line="java list" @started="lastJob = $event">Rescan</ActionButton>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <RecentJob :job-id="lastJob" @dismiss="lastJob = null" />
  <div class="card">
    <div v-if="!data.length" class="empty">No java installations found.</div>
    <table v-else>
      <thead>
        <tr>
          <th>Name</th>
          <th>Version</th>
          <th>Home</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="java in data" :key="java.home">
          <td>{{ java.name }} <span v-if="java.current" class="badge ok">current</span></td>
          <td>{{ java.version }}</td>
          <td class="mono">{{ java.home }}</td>
          <td class="actions">
            <ActionButton
              :line="`java remove ${quoteArg(java.name)}`"
              :confirm="`Delete java installation ${java.name} at ${java.home}?`"
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

  <ModalDialog v-if="dialog === 'install'" title="Install java" @close="dialog = null">
    <CommandForm :path="['java', 'install']" submit-label="Install" />
  </ModalDialog>
  <ModalDialog v-if="dialog === 'remote'" title="Java distributions" @close="dialog = null">
    <p class="muted">Lists distributions, or with a version the installable runtimes. Use --providers to list providers.</p>
    <CommandForm :path="['java', 'list']" :initial="{ '--remote': true }" submit-label="List" :show-description="false" />
  </ModalDialog>
</template>

<style scoped>
.toolbar {
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}
</style>
