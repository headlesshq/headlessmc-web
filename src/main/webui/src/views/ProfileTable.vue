<script setup lang="ts">
import { ref } from 'vue'
import type { Profile } from '../lib/types'

defineProps<{ profiles: Profile[] }>()
const expanded = ref<string | null>(null)
</script>

<template>
  <div v-if="!profiles.length" class="empty"><slot name="empty">Nothing here yet.</slot></div>
  <table v-else>
    <thead>
      <tr>
        <th>Name</th>
        <th>Version</th>
        <th>Java</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <template v-for="profile in profiles" :key="profile.name">
        <tr>
          <td>
            <a href="#" @click.prevent="expanded = expanded === profile.name ? null : profile.name">{{ profile.name }}</a>
          </td>
          <td class="mono">{{ profile.currentVersion }}</td>
          <td>{{ profile.javaVersion ?? 'auto' }}</td>
          <td class="actions"><slot name="actions" :profile="profile" /></td>
        </tr>
        <tr v-if="expanded === profile.name">
          <td colspan="4">
            <table class="details">
              <tbody>
              <tr><th>path</th><td class="mono">{{ profile.path }}</td></tr>
              <tr><th>side</th><td>{{ profile.side }}</td></tr>
              <tr><th>specified version</th><td class="mono">{{ profile.version }}</td></tr>
              <tr><th>patchers</th><td class="mono">{{ profile.patchers.join(', ') || '-' }}</td></tr>
              <tr><th>vm args</th><td class="mono">{{ profile.vmArgs.join(' ') || '-' }}</td></tr>
              <tr><th>game args</th><td class="mono">{{ profile.gameArgs?.join(' ') || '-' }}</td></tr>
              <tr>
                <th>system properties</th>
                <td class="mono">
                  <div v-for="(value, key) in profile.systemProperties" :key="key">{{ key }}={{ value ?? '' }}</div>
                  <span v-if="!Object.keys(profile.systemProperties).length">-</span>
                </td>
              </tr>
              <tr v-if="profile.side === 'server'"><th>eula</th><td>{{ profile.eulaStatus }}</td></tr>
              </tbody>
            </table>
          </td>
        </tr>
      </template>
    </tbody>
  </table>
</template>

<style scoped>
.details th,
.details td {
  border: none;
  padding: 0.15rem 0.75rem 0.15rem 0;
}

.details th {
  width: 10rem;
}
</style>
