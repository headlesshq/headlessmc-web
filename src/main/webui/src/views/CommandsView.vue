<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommandForm from '../components/CommandForm.vue'
import CommandTree from '../components/CommandTree.vue'
import { findChain, loadCommands } from '../lib/commands'
import { commandLabel } from '../lib/labels'
import type { CommandModel } from '../lib/types'

// Every HeadlessMc command, with a form generated from its picocli model.
const route = useRoute()
const router = useRouter()
const root = ref<CommandModel | null>(null)
const showHidden = ref(false)
const error = ref('')

const path = computed(() => {
  const param = route.params.path
  return (Array.isArray(param) ? param : param ? [param] : []).filter((part) => part)
})

const chain = computed(() => (root.value ? findChain(root.value, path.value) : null))
const selected = computed(() => (chain.value ? chain.value[chain.value.length - 1] : null))
const title = computed(() => (chain.value ?? []).slice(1).map(commandLabel).join(' › '))

onMounted(async () => {
  try {
    root.value = await loadCommands()
  } catch (e) {
    error.value = String(e)
  }
})

function select(command: CommandModel) {
  router.push(`/commands/${command.path.join('/')}`)
}
</script>

<template>
  <h1>All commands</h1>
  <div v-if="error" class="error">{{ error }}</div>
  <div v-if="root" class="layout">
    <div class="tree card">
      <label class="row muted toggle"><input v-model="showHidden" type="checkbox" /> show hidden</label>
      <CommandTree :command="root" :selected="path" :show-hidden="showHidden" @select="select" />
    </div>
    <div class="card form">
      <template v-if="selected && path.length">
        <h2>{{ title }}</h2>
        <CommandForm :key="path.join(' ')" :path="path" />
        <template v-if="selected.subcommands.length">
          <h3>Sub commands</h3>
          <ul>
            <li v-for="sub in selected.subcommands.filter((s) => showHidden || !s.hidden)" :key="sub.name">
              <a href="#" @click.prevent="select(sub)">{{ commandLabel(sub) }}</a> <span class="muted">{{ sub.description }}</span>
            </li>
          </ul>
        </template>
      </template>
      <div v-else class="muted">Select a command on the left.</div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 15rem 1fr;
  gap: 1rem;
  align-items: start;
}

.tree {
  position: sticky;
  top: 1rem;
  max-height: calc(100vh - 6rem);
  overflow: auto;
}

.toggle {
  font-size: 0.85rem;
  margin-bottom: 0.5rem;
}

h2 {
  margin-top: 0;
}

.error {
  color: var(--danger);
}
</style>
