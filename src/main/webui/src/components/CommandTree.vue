<script setup lang="ts">
import type { CommandModel } from '../lib/types'

const props = defineProps<{ command: CommandModel; selected: string[]; showHidden: boolean; depth?: number }>()
const emit = defineEmits<{ select: [command: CommandModel] }>()

const isSelected = (command: CommandModel) => command.path.join(' ') === props.selected.join(' ')
const visible = (command: CommandModel) => props.showHidden || !command.hidden
</script>

<template>
  <ul class="tree">
    <li v-for="sub in command.subcommands.filter(visible)" :key="sub.name">
      <a href="#" :class="{ active: isSelected(sub), hidden: sub.hidden }" :title="sub.description" @click.prevent="emit('select', sub)">
        {{ sub.name }}
      </a>
      <CommandTree
        v-if="sub.subcommands.length"
        :command="sub"
        :selected="selected"
        :show-hidden="showHidden"
        :depth="(depth ?? 0) + 1"
        @select="emit('select', $event)"
      />
    </li>
  </ul>
</template>

<style scoped>
.tree {
  list-style: none;
  margin: 0;
  padding: 0 0 0 0.9rem;
}

:deep(.tree) {
  padding-left: 0.9rem;
}

a {
  display: block;
  color: var(--fg);
  text-decoration: none;
  font-family: var(--mono);
  font-size: 0.9rem;
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
}

a:hover {
  background: var(--bg-input);
}

a.active {
  color: var(--accent);
  background: var(--bg-input);
}

a.hidden {
  opacity: 0.6;
}
</style>
