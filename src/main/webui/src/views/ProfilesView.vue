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

const { data, error } = useData(() => api.profiles(), [])
const lastJob = ref<string | null>(null)
const dialog = ref<null | { kind: 'add' } | { kind: 'launch' | 'edit'; name: string }>(null)
</script>

<template>
  <h1>Profiles</h1>
  <p class="muted">Profiles are client installations with their own game directory, arguments and mods.</p>
  <div class="row toolbar">
    <button class="primary" @click="dialog = { kind: 'add' }">Add profile</button>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <RecentJob :job-id="lastJob" @dismiss="lastJob = null" />
  <div class="card">
    <ProfileTable :profiles="data.filter((p) => p.side === 'client')">
      <template #empty>No profiles yet. Add one, or launch a version directly from the dashboard.</template>
      <template #actions="{ profile }">
        <button class="small primary" @click="dialog = { kind: 'launch', name: profile.name }">Launch</button>
        <button class="small" @click="dialog = { kind: 'edit', name: profile.name }">Edit</button>
        <RouterLink :to="{ path: '/mods', query: { profile: profile.name } }" class="small-link">Mods</RouterLink>
        <ActionButton
          :line="`profile remove ${quoteArg(profile.name)}`"
          :confirm="`Remove profile ${profile.name}?`"
          danger
          @started="lastJob = $event"
        >
          Remove
        </ActionButton>
      </template>
    </ProfileTable>
  </div>

  <ModalDialog v-if="dialog?.kind === 'add'" title="Add profile" @close="dialog = null">
    <CommandForm :path="['profile', 'add']" submit-label="Add" />
  </ModalDialog>
  <ModalDialog v-else-if="dialog?.kind === 'launch'" :title="`Launch ${dialog.name}`" @close="dialog = null">
    <CommandForm :path="['profile', 'launch']" :initial="{ '#0': dialog.name }" submit-label="Launch" />
  </ModalDialog>
  <ModalDialog v-else-if="dialog?.kind === 'edit'" :title="`Edit ${dialog.name}`" @close="dialog = null">
    <p class="muted">Leave the value empty to list the fields, or to be asked for the new value (prefilled with the current one).</p>
    <CommandForm :path="['profile', 'edit']" :initial="{ '#0': dialog.name }" submit-label="Edit" :show-description="false" />
  </ModalDialog>
</template>

<style scoped>
.small-link {
  font-size: 0.85rem;
  padding: 0.15rem 0.5rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--fg);
  text-decoration: none;
}

.toolbar {
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}
</style>
