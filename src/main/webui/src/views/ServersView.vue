<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import ActionButton from '../components/ActionButton.vue'
import CommandForm from '../components/CommandForm.vue'
import ModalDialog from '../components/ModalDialog.vue'
import RecentJob from '../components/RecentJob.vue'
import { api } from '../lib/api'
import { quoteArg } from '../lib/shell'
import { useData } from '../lib/useData'
import ProfileTable from './ProfileTable.vue'

const { data, error } = useData(() => api.servers(), [])
const EULA_CONFIRM = 'Do you accept the Minecraft EULA (https://aka.ms/MinecraftEULA)?'
const lastJob = ref<string | null>(null)
const dialog = ref<null | { kind: 'add' } | { kind: 'launch'; name: string }>(null)
</script>

<template>
  <h1>Servers</h1>
  <p class="muted">
    Launched servers run in the background, use the <RouterLink to="/processes">Processes</RouterLink> page to see their
    console and to enter server commands.
  </p>
  <div class="row toolbar">
    <button class="primary" @click="dialog = { kind: 'add' }">Add server</button>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <RecentJob :job-id="lastJob" @dismiss="lastJob = null" />
  <div class="card">
    <ProfileTable :profiles="data">
      <template #empty>No servers yet.</template>
      <template #actions="{ profile }">
        <button class="small primary" @click="dialog = { kind: 'launch', name: profile.name }">Launch</button>
        <ActionButton :line="`server eula read ${quoteArg(profile.name)}`" @started="lastJob = $event">Read EULA</ActionButton>
        <ActionButton
          :line="`server eula accept ${quoteArg(profile.name)}`"
          :confirm="EULA_CONFIRM"
          @started="lastJob = $event"
        >
          Accept EULA
        </ActionButton>
        <ActionButton
          :line="`server remove ${quoteArg(profile.name)}`"
          :confirm="`Remove server ${profile.name}? This deletes its directory.`"
          danger
          @started="lastJob = $event"
        >
          Remove
        </ActionButton>
      </template>
    </ProfileTable>
  </div>

  <ModalDialog v-if="dialog?.kind === 'add'" title="Add server" @close="dialog = null">
    <p class="muted">Platform and version, e.g. <code>paper 1.21.4</code>, <code>fabric 1.21.1</code> or <code>vanilla 26.2</code>.</p>
    <CommandForm :path="['server', 'add']" submit-label="Install server" :show-description="false" />
  </ModalDialog>
  <ModalDialog v-else-if="dialog?.kind === 'launch'" :title="`Launch ${dialog.name}`" @close="dialog = null">
    <CommandForm :path="['server', 'launch']" :initial="{ '#0': dialog.name }" submit-label="Launch" />
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
