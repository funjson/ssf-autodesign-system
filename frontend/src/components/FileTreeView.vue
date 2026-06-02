<script setup lang="ts">
import { FileText, FolderClosed } from '@lucide/vue';
import type { FileTreeNodeDto } from '../types/models';

defineOptions({ name: 'FileTreeView' });

defineProps<{
  node: FileTreeNodeDto;
  selectedPath: string;
}>();

const emit = defineEmits<{
  (event: 'select-file', path: string): void;
}>();
</script>

<template>
  <details v-if="node.directory" class="file-tree-dir" open>
    <summary>
      <FolderClosed :size="13" />
      <span>{{ node.name }}</span>
    </summary>
    <div class="file-tree-children">
      <FileTreeView
        v-for="child in node.children"
        :key="child.relativePath || child.name"
        :node="child"
        :selected-path="selectedPath"
        @select-file="(path) => emit('select-file', path)"
      />
    </div>
  </details>
  <button
    v-else
    class="file-tree-file"
    :class="{ selected: node.relativePath === selectedPath }"
    :title="node.relativePath"
    @click="emit('select-file', node.relativePath)"
  >
    <FileText :size="13" />
    <span>{{ node.name }}</span>
  </button>
</template>
