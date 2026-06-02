<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  Eye,
  FileSearch,
  FolderOpen,
  FolderPlus,
  Pencil,
  RefreshCw,
  Server,
  Trash2,
  Upload,
  Workflow
} from '@lucide/vue';
import FileTreeView from './FileTreeView.vue';
import type { FileTreeNodeDto, ProjectDto, ProjectTreeDto, SpiInstanceDto, WorkspaceDto } from '../types/models';

const props = defineProps<{
  projects: ProjectDto[];
  tree: ProjectTreeDto | null;
  activeProjectId: number | null;
  selectedInstanceId: number | null;
  previewMode: 'current' | 'architecture' | 'generated' | 'annotation' | 'resources' | 'prototype' | 'html-prototype' | 'prototype-upload';
  fileTree: FileTreeNodeDto | null;
  selectedFilePath: string;
}>();

const emit = defineEmits<{
  (event: 'create-project', name: string): void;
  (event: 'delete-project', project: ProjectDto): void;
  (event: 'select-project', id: number): void;
  (event: 'add-workspace', path: string): void;
  (event: 'import-workspace', files: File[], relativePaths: string[], displayName: string): void;
  (event: 'remove-workspace', workspace: WorkspaceDto): void;
  (event: 'rename-workspace', workspace: WorkspaceDto, displayName: string): void;
  (event: 'refresh-tree'): void;
  (event: 'select-instance', instance: SpiInstanceDto): void;
  (event: 'select-architecture-manager', instance: SpiInstanceDto): void;
  (event: 'select-generated-preview', instance: SpiInstanceDto): void;
  (event: 'select-annotation-workbench', instance: SpiInstanceDto): void;
  (event: 'select-resource-manager', instance: SpiInstanceDto): void;
  (event: 'select-prototype-upload', instance: SpiInstanceDto): void;
  (event: 'select-file', path: string): void;
  (event: 'upload-prototype', instance: SpiInstanceDto): void;
  (event: 'upload-html-folder', instance: SpiInstanceDto): void;
  (event: 'upload-html-file', instance: SpiInstanceDto): void;
}>();

const projectName = ref('');
const workspacePath = ref('');
const projectNameError = ref('');
const workspacePathError = ref('');
const projectShake = ref(false);
const workspaceShake = ref(false);
const folderInput = ref<HTMLInputElement | null>(null);
const showAdvancedPath = ref(false);
const editingWorkspaceId = ref<number | null>(null);
const editingWorkspaceName = ref('');

const allProjects = computed(() => props.projects);
const activeProject = computed(() => props.projects.find((project) => project.id === props.activeProjectId) ?? null);

watch(() => props.activeProjectId, () => {
  projectNameError.value = '';
  workspacePathError.value = '';
  editingWorkspaceId.value = null;
});

function createProject() {
  if (!projectName.value.trim()) {
    showValidation('project', '请输入项目名称');
    return;
  }
  projectNameError.value = '';
  emit('create-project', projectName.value.trim());
  projectName.value = '';
}

function addWorkspace() {
  if (!workspacePath.value.trim()) {
    showValidation('workspace', '请选择文件夹，或在高级模式中填写 workspace 路径');
    return;
  }
  workspacePathError.value = '';
  emit('add-workspace', workspacePath.value.trim());
  workspacePath.value = '';
}

function openFolderPicker() {
  if (!props.activeProjectId) {
    showValidation('workspace', '请先创建或选择一个项目');
    return;
  }
  folderInput.value?.click();
}

function handleFolderSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  if (!files.length) {
    showValidation('workspace', '请选择 Skill 生成的 ssf-workspace 文件夹');
    input.value = '';
    return;
  }
  const relativePaths = files.map((file) => {
    const webkitFile = file as File & { webkitRelativePath?: string };
    return webkitFile.webkitRelativePath || file.name;
  });
  workspacePathError.value = '';
  emit('import-workspace', files, relativePaths, '');
  input.value = '';
}

function showValidation(target: 'project' | 'workspace', message: string) {
  if (target === 'project') {
    projectNameError.value = message;
    projectShake.value = false;
    window.setTimeout(() => (projectShake.value = true));
    window.setTimeout(() => (projectShake.value = false), 420);
    return;
  }
  workspacePathError.value = message;
  workspaceShake.value = false;
  window.setTimeout(() => (workspaceShake.value = true));
  window.setTimeout(() => (workspaceShake.value = false), 420);
}

function sourceLabel(sourceType: string) {
  if (sourceType === 'demo') return 'DEMO';
  if (sourceType === 'imported') return '导入副本';
  return '本地路径';
}

function startRename(workspace: WorkspaceDto) {
  editingWorkspaceId.value = workspace.id;
  editingWorkspaceName.value = workspace.displayName;
}

function commitRename(workspace: WorkspaceDto) {
  const nextName = editingWorkspaceName.value.trim();
  if (!nextName || nextName === workspace.displayName) {
    editingWorkspaceId.value = null;
    return;
  }
  emit('rename-workspace', workspace, nextName);
  editingWorkspaceId.value = null;
}

function projectTooltip(project: ProjectDto) {
  return project.demoProject ? 'Demo 使用独立目录，只用于理解系统操作方式。' : project.name;
}

function workspaceTooltip(workspace: WorkspaceDto) {
  const prefix = workspace.sourceType === 'imported' ? '系统副本路径' : '路径';
  return `${prefix}: ${workspace.path}`;
}

function isInstanceSelected(instance: SpiInstanceDto) {
  return instance.id === props.selectedInstanceId;
}

function isResourceMode(instance: SpiInstanceDto) {
  return isInstanceSelected(instance) && props.previewMode === 'resources';
}

function isAnnotationMode(instance: SpiInstanceDto) {
  return isInstanceSelected(instance) && ['annotation', 'prototype', 'html-prototype'].includes(props.previewMode);
}

function isUploadMode(instance: SpiInstanceDto) {
  return isInstanceSelected(instance) && props.previewMode === 'prototype-upload';
}

function selectInstance(instance: SpiInstanceDto) {
  emit('select-instance', instance);
}

function selectResourceManager(instance: SpiInstanceDto) {
  emit('select-resource-manager', instance);
}

function selectArchitectureManager(instance: SpiInstanceDto) {
  emit('select-architecture-manager', instance);
}

function selectGeneratedPreview(instance: SpiInstanceDto) {
  emit('select-generated-preview', instance);
}

function selectPrototypeUpload(instance: SpiInstanceDto) {
  emit('select-prototype-upload', instance);
}

function selectAnnotationWorkbench(instance: SpiInstanceDto) {
  emit('select-annotation-workbench', instance);
}

</script>

<template>
  <aside class="sidebar">
    <div class="brand-block">
      <div class="brand-mark">S</div>
      <div>
        <strong>ssf-autodesign</strong>
        <span>prototype to spec</span>
      </div>
    </div>

    <section class="side-section">
      <label>我的项目</label>
      <div class="inline-form" :class="{ shake: projectShake }">
        <input
          v-model="projectName"
          maxlength="20"
          :class="{ invalid: projectNameError }"
          placeholder="新项目名称"
          @keydown.enter="createProject"
          @input="projectNameError = ''"
        />
        <button class="icon-button" title="创建项目" @click="createProject">
          <FolderPlus :size="17" />
        </button>
      </div>
      <p v-if="projectNameError" class="field-error">{{ projectNameError }}</p>

      <div v-if="allProjects.length" class="project-list">
        <div
          v-for="project in allProjects"
          :key="project.id"
          class="project-row-shell"
          :class="{ selected: project.id === props.activeProjectId }"
        >
          <button class="project-row project-row-main" :title="projectTooltip(project)" @click="emit('select-project', project.id)">
            <span>{{ project.name }}</span>
            <em v-if="project.demoProject">DEMO</em>
          </button>
          <button
            v-if="!project.demoProject"
            class="row-action danger"
            title="删除项目"
            @click="emit('delete-project', project)"
          >
            <Trash2 :size="14" />
          </button>
          <span v-else class="row-action-spacer"></span>
        </div>
      </div>
      <p v-else class="empty-hint">还没有项目。先创建项目，再导入 Skill 生成的 ssf-workspace。</p>
    </section>

    <section class="side-section workspace-section">
      <div class="section-title-row">
        <label>工作目录</label>
        <button class="ghost-icon" title="重新扫描" :disabled="!props.activeProjectId" @click="emit('refresh-tree')">
          <RefreshCw :size="15" />
        </button>
      </div>

      <input
        ref="folderInput"
        class="hidden-file-input"
        type="file"
        webkitdirectory
        directory
        multiple
        @change="handleFolderSelected"
      />

      <button
        class="folder-button"
        title="请选择 ssf-product-pm Skill 生成的 ssf-workspace。Web 模式会创建系统托管副本；高级路径才直接使用原始本地目录。"
        :disabled="!props.activeProjectId || !!activeProject?.demoProject"
        @click="openFolderPicker"
      >
        <FolderOpen :size="16" />
        <span>选择 ssf-workspace 文件夹</span>
      </button>
      <p v-if="activeProject?.demoProject" class="field-note">演示项目使用内置 demo 目录，不支持导入新的 workspace。</p>

      <button class="text-button" :disabled="!props.activeProjectId || !!activeProject?.demoProject" @click="showAdvancedPath = !showAdvancedPath">
        {{ showAdvancedPath ? '收起高级路径' : '高级：粘贴本地路径' }}
      </button>

      <div v-if="showAdvancedPath" class="path-form" :class="{ shake: workspaceShake }">
        <input
          v-model="workspacePath"
          :class="{ invalid: workspacePathError }"
          :disabled="!props.activeProjectId || !!activeProject?.demoProject"
          placeholder="C:\\...\\ssf-workspace"
          @keydown.enter="addWorkspace"
          @input="workspacePathError = ''"
        />
        <button :disabled="!props.activeProjectId || !!activeProject?.demoProject" @click="addWorkspace">添加</button>
      </div>
      <p v-if="workspacePathError" class="field-error">{{ workspacePathError }}</p>

      <div v-if="props.tree" class="workspace-tree">
        <div v-if="!props.tree.workspaces.length" class="empty-hint block-hint">当前项目还没有 workspace。</div>
        <div v-for="workspace in props.tree.workspaces" :key="workspace.workspace.id" class="workspace-node">
          <div class="workspace-title" :title="workspaceTooltip(workspace.workspace)">
            <Server :size="15" />
            <input
              v-if="editingWorkspaceId === workspace.workspace.id"
              v-model="editingWorkspaceName"
              class="workspace-name-input"
              maxlength="80"
              @keydown.enter="commitRename(workspace.workspace)"
              @keydown.esc="editingWorkspaceId = null"
              @blur="commitRename(workspace.workspace)"
            />
            <span v-else>{{ workspace.workspace.displayName }}</span>
            <em>{{ sourceLabel(workspace.workspace.sourceType) }}</em>
            <button
              v-if="!activeProject?.demoProject"
              class="row-action"
              title="编辑显示名称"
              @click="startRename(workspace.workspace)"
            >
              <Pencil :size="14" />
            </button>
            <button
              v-if="!activeProject?.demoProject"
              class="row-action danger"
              title="移除工作目录"
              @click="emit('remove-workspace', workspace.workspace)"
            >
              <Trash2 :size="14" />
            </button>
          </div>

          <div v-for="instance in workspace.instances" :key="instance.id" class="instance-node">
            <div class="instance-row-shell">
              <button
                type="button"
                class="instance-row"
                :class="{ selected: isInstanceSelected(instance) }"
                @pointerdown.stop.prevent="selectInstance(instance)"
              >
                <Workflow :size="15" />
                <span>{{ instance.instanceCode }}</span>
                <small>{{ instance.productName || instance.instanceName }}</small>
              </button>
              <button
                type="button"
                class="instance-resource-button"
                :class="{ selected: isResourceMode(instance) }"
                title="资源管理"
                @pointerdown.stop.prevent="selectResourceManager(instance)"
              >
                <FileSearch :size="14" />
              </button>
            </div>

            <div v-if="isResourceMode(instance) && props.fileTree" class="file-tree-shell">
              <FileTreeView :node="props.fileTree" :selected-path="props.selectedFilePath" @select-file="(path) => emit('select-file', path)" />
            </div>

            <div v-else class="instance-actions">
              <button
                type="button"
                class="instance-action"
                :class="{ selected: isInstanceSelected(instance) && props.previewMode === 'architecture' }"
                @pointerdown.stop.prevent="selectArchitectureManager(instance)"
              >
                <Server :size="14" />
                产品架构管理
              </button>
              <button
                type="button"
                class="instance-action"
                :class="{ selected: isInstanceSelected(instance) && props.previewMode === 'generated' }"
                @pointerdown.stop.prevent="selectGeneratedPreview(instance)"
              >
                <Eye :size="14" />
                需求预览
              </button>
              <button
                type="button"
                class="instance-action"
                :class="{ selected: isUploadMode(instance) }"
                :disabled="activeProject?.demoProject"
                @pointerdown.stop.prevent="selectPrototypeUpload(instance)"
              >
                <Upload :size="14" />
                原型上传
              </button>
              <button
                type="button"
                class="instance-action"
                :class="{ selected: isAnnotationMode(instance) }"
                @pointerdown.stop.prevent="selectAnnotationWorkbench(instance)"
              >
                <Pencil :size="14" />
                原型标注
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </aside>
</template>
