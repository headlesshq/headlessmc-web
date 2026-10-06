<script setup lang="ts">
import { ref } from 'vue'
import CommandForm from '../components/CommandForm.vue'
import ModalDialog from '../components/ModalDialog.vue'
import { api } from '../lib/api'
import { useData } from '../lib/useData'

const { data, error } = useData(() => api.versions(), [])
const dialog = ref<null | 'install' | 'remote' | 'profile'>(null)
const profileFor = ref('')

function addProfile(id: string) {
  profileFor.value = id
  dialog.value = 'profile'
}
</script>

<template>
  <h1>Versions</h1>
  <div class="row toolbar">
    <button class="primary" @click="dialog = 'install'">Install version</button>
    <button @click="dialog = 'remote'">Browse remote versions</button>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <div class="card">
    <div v-if="!data.length" class="empty">No versions installed.</div>
    <table v-else>
      <thead>
        <tr>
          <th>Name</th>
          <th>Parent</th>
          <th>Type</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="version in data" :key="version.id">
          <td class="mono">{{ version.id }}</td>
          <td class="mono">{{ version.inheritsFrom ?? '' }}</td>
          <td>{{ version.type ?? '' }}</td>
          <td class="actions"><button class="small" @click="addProfile(version.id)">Add profile</button></td>
        </tr>
      </tbody>
    </table>
  </div>

  <ModalDialog v-if="dialog === 'install'" title="Install version" @close="dialog = null">
    <p class="muted">
      Version, e.g. <code>fabric 1.21.1</code>, <code>vanilla 26.2</code> or <code>server paper 1.21.4</code>.
      Servers are added to the Servers page.
    </p>
    <CommandForm :path="['version', 'install']" submit-label="Install" :show-description="false" />
  </ModalDialog>
  <ModalDialog v-if="dialog === 'remote'" title="Remote versions" @close="dialog = null">
    <p class="muted">Search parameters filter by platform, side, version or build, e.g. <code>fabric 1.21.1</code>.</p>
    <CommandForm :path="['version', 'list']" :initial="{ '--remote': true }" submit-label="List" :show-description="false" />
  </ModalDialog>
  <ModalDialog v-if="dialog === 'profile'" title="Add profile" @close="dialog = null">
    <CommandForm :path="['profile', 'add']" submit-label="Add profile" :initial="{ '--name': profileFor }" />
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
