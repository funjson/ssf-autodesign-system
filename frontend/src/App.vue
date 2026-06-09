<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { AlertCircle, Bot, Code2, FileArchive, Images, Loader2 } from '@lucide/vue';
import { api, ApiClientError } from './api/client';
import InspectorPanel from './components/InspectorPanel.vue';
import FilePreview from './components/FilePreview.vue';
import HtmlPrototypeCanvas from './components/HtmlPrototypeCanvas.vue';
import ProjectSidebar from './components/ProjectSidebar.vue';
import PrototypeCanvas from './components/PrototypeCanvas.vue';
import TaskOverviewPanel from './components/TaskOverviewPanel.vue';
import type {
  AcceptanceNodeDto,
  AnnotationNodeDto,
  ChangeRequestDto,
  ComponentNodeDto,
  CreateChangeRequestPayload,
  FileContentDto,
  FileTreeNodeDto,
  HtmlElementSelection,
  HtmlPrototypeBindingDto,
  HtmlPrototypePackageDto,
  HtmlPrototypeTargetDto,
  SaveHtmlPrototypeBindingPayload,
  FeatureNodeDto,
  ProjectDto,
  ProjectTreeDto,
  RuleNodeDto,
  ScreenNodeDto,
  SpecGraphDto,
  SpiInstanceDto,
  WorkspaceDto
} from './types/models';

type PreviewMode = 'current' | 'architecture' | 'generated' | 'annotation' | 'resources' | 'prototype' | 'html-prototype' | 'prototype-upload';

const projects = ref<ProjectDto[]>([]);
const tree = ref<ProjectTreeDto | null>(null);
const selectedProjectId = ref<number | null>(null);
const selectedInstance = ref<SpiInstanceDto | null>(null);
const graph = ref<SpecGraphDto | null>(null);
const fileTree = ref<FileTreeNodeDto | null>(null);
const selectedFilePath = ref('');
const selectedFileContent = ref<FileContentDto | null>(null);

// HTML annotation state is intentionally owned here. The iframe bridge and
// HtmlPrototypeCanvas may request changes, but they should not become separate
// sources of truth for draft DOMs or saved ANN selection.
const htmlPackage = ref<HtmlPrototypePackageDto | null>(null);
const htmlBindings = ref<HtmlPrototypeBindingDto[]>([]);
const selectedHtmlElement = ref<HtmlElementSelection | null>(null);
const selectedHtmlBindingId = ref('');
const htmlInteractionMode = ref<'browse' | 'annotate'>('browse');
const draftHtmlTargets = ref<HtmlPrototypeTargetDto[]>([]);
const selectedScreenId = ref<string>('');
const selectedComponentKey = ref<string>('');
const hoveredComponentKey = ref<string>('');
const loading = ref(false);
const error = ref('');
const toast = ref('');
const createdChangeRequest = ref<ChangeRequestDto | null>(null);
const previewMode = ref<PreviewMode>('current');
const inspectorCollapsed = ref(false);
const inspectorWidth = ref(430);
const pendingPrototypeUploadInstance = ref<SpiInstanceDto | null>(null);
const prototypeUploadInput = ref<HTMLInputElement | null>(null);
const pendingHtmlUploadInstance = ref<SpiInstanceDto | null>(null);
const htmlFolderInput = ref<HTMLInputElement | null>(null);
const htmlFileInput = ref<HTMLInputElement | null>(null);
const deleteAnnTimeoutMessage = 'ANN_DELETE_TIMEOUT';
const saveAnnTimeoutMessage = 'ANN_SAVE_TIMEOUT';
const annSaveDebug = ref({
  bindingId: '',
  status: 'idle',
  detail: ''
});
const annDeleteDebug = ref({
  bindingId: '',
  status: 'idle',
  detail: ''
});

const appGridStyle = computed(() => ({
  gridTemplateColumns: `324px minmax(520px, 1fr) 6px ${inspectorCollapsed.value ? '54px' : `${inspectorWidth.value}px`}`
}));

const activeProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value) ?? null);
const inspectorReadOnly = computed(() => ['architecture', 'generated', 'prototype-upload'].includes(previewMode.value) || !!activeProject.value?.demoProject);
const annotationModeActive = computed(() => ['annotation', 'prototype', 'html-prototype'].includes(previewMode.value));
const annotationKind = computed<'html' | 'image' | 'unsupported'>(() => {
  if (previewMode.value === 'prototype' && activeInstanceHasPrototype.value) return 'image';
  if (previewMode.value === 'html-prototype' && activeInstanceHasHtmlPrototype.value) return 'html';
  if (activeInstanceHasHtmlPrototype.value) return 'html';
  if (activeInstanceHasPrototype.value) return 'image';
  return 'unsupported';
});
const annotationStatus = computed(() => {
  if (annotationKind.value === 'html') {
    return {
      title: 'HTML 原型包',
      description: '已找到 HTML 入口页，可做 DOM 级标注。支持 HTML 文件夹、Axure/Motiff 等多层级导出目录，以及单个 HTML/ZIP 上传；包内非 HTML 资源会保留给页面引用。',
      path: htmlPackage.value?.sourceRoot || 'requirement-prototype/packages/current/source'
    };
  }
  if (annotationKind.value === 'image') {
    return {
      title: '图片原型包',
      description: '已识别到按 SCR-xxx 命名的图片帧，当前用于人工区域标注和需求块绑定；图片文件夹、图片压缩包和坐标框标注还在后续能力中。',
      path: graph.value?.prototypeSource.rootPath || 'requirement-prototype'
    };
  }
  return {
    title: '暂无可标注原型',
    description: '请先进入“原型上传”导入 HTML 文件夹、HTML/ZIP、单张图片或多张按 SCR-xxx 命名的图片；系统会统一整理到 requirement-prototype 下。',
    path: selectedInstance.value?.rootPath ? `${selectedInstance.value.rootPath}\\requirement-prototype` : 'requirement-prototype'
  };
});
const htmlAnnotationDisabled = computed(() => annotationKind.value !== 'html');
const imageAnnotationDisabled = computed(() => annotationKind.value !== 'image');
const inspectorReadOnlyReason = computed(() => {
  if (activeProject.value?.demoProject) return '演示项目为只读项目，不会写入 CR 或覆盖原型。';
  if (previewMode.value === 'architecture') return '产品架构管理当前只是占位入口，后续再接入架构文件和编辑能力。';
  if (previewMode.value === 'prototype-upload') return '原型上传只负责导入和识别原型包，不在这里生成需求变更。进入原型标注后再绑定任务或生成 CR。';
  return '需求预览视图仅用于核对结构，不参与编辑和 AI 交互。';
});
const activeInstanceHasPrototype = computed(() => (
  !!selectedInstance.value
  && graph.value?.instanceId === selectedInstance.value.id
  && !!graph.value.prototypeSource.userProvided
));
const activeInstanceHasHtmlPrototype = computed(() => (
  !!selectedInstance.value
  && !!htmlPackage.value?.exists
));

const activeScreen = computed<ScreenNodeDto | null>(() => {
  if (!graph.value) return null;
  return graph.value.screens.find((screen) => screen.screenId === selectedScreenId.value) ?? graph.value.screens[0] ?? null;
});

const activeComponent = computed<ComponentNodeDto | null>(() => {
  if (!graph.value || !selectedComponentKey.value) return null;
  return graph.value.components.find((component) => componentKey(component) === selectedComponentKey.value) ?? null;
});

const activeHtmlBinding = computed<HtmlPrototypeBindingDto | null>(() => (
  htmlBindings.value.find((binding) => binding.bindingId === selectedHtmlBindingId.value) ?? null
));

// The inspector context is the union of the selected screen, selected component, and PANN annotations.
const relatedAnnotations = computed<AnnotationNodeDto[]>(() => {
  if (!graph.value || !activeScreen.value) return [];
  return graph.value.annotations.filter((annotation) => {
    if (annotation.screenId !== activeScreen.value?.screenId) return false;
    if (!activeComponent.value) return true;
    return !annotation.componentId || annotation.componentId === activeComponent.value.componentId;
  });
});

const relatedFeatureIds = computed(() => relatedIds('features'));
const relatedRuleIds = computed(() => relatedIds('rules'));
const relatedAcceptanceIds = computed(() => relatedIds('acceptances'));

const relatedFeatures = computed<FeatureNodeDto[]>(() => {
  if (!graph.value) return [];
  return graph.value.features.filter((feature) => relatedFeatureIds.value.includes(feature.featureId));
});

const relatedRules = computed<RuleNodeDto[]>(() => {
  if (!graph.value) return [];
  return graph.value.rules.filter((rule) => relatedRuleIds.value.includes(rule.ruleId) || relatedFeatureIds.value.includes(rule.featureId));
});

const relatedAcceptances = computed<AcceptanceNodeDto[]>(() => {
  if (!graph.value) return [];
  return graph.value.acceptances.filter((acceptance) => relatedAcceptanceIds.value.includes(acceptance.acId) || relatedFeatureIds.value.includes(acceptance.featureId));
});

onMounted(() => {
  void loadProjects();
});

async function loadProjects() {
  await withLoading(async () => {
    projects.value = await api.listProjects();
  });
}

async function createProject(name: string) {
  await withLoading(async () => {
    const project = await api.createProject(name);
    projects.value = await api.listProjects();
    await selectProject(project.id, false);
  });
}

async function deleteProject(project: ProjectDto) {
  if (project.demoProject) return;
  const confirmed = window.confirm(`确认删除项目「${project.name}」吗？\n\n这只会从系统列表中移除项目和关联目录，不会删除你的本地文件。`);
  if (!confirmed) return;
  await withLoading(async () => {
    await api.deleteProject(project.id);
    projects.value = await api.listProjects();
    if (selectedProjectId.value === project.id) {
      selectedProjectId.value = null;
      tree.value = null;
      selectedInstance.value = null;
      graph.value = null;
      resetFilePreview();
      resetHtmlPrototype();
      selectedScreenId.value = '';
      selectedComponentKey.value = '';
    }
    toast.value = '项目已从系统中移除，本地文件未删除';
  });
}

async function selectProject(projectId: number, scan = false) {
  selectedProjectId.value = projectId;
  tree.value = await api.getTree(projectId, scan);
  selectedInstance.value = null;
  graph.value = null;
  resetFilePreview();
  resetHtmlPrototype();
  selectedScreenId.value = '';
  selectedComponentKey.value = '';
  previewMode.value = 'current';
}

async function addWorkspace(path: string) {
  if (!selectedProjectId.value) return;
  await withLoading(async () => {
    await api.addWorkspace(selectedProjectId.value!, path);
    tree.value = await api.getTree(selectedProjectId.value!, true);
  });
}

async function importWorkspace(files: File[], relativePaths: string[], displayName: string) {
  if (!selectedProjectId.value) return;
  await withLoading(async () => {
    await api.importWorkspace(selectedProjectId.value!, files, relativePaths, displayName);
    tree.value = await api.getTree(selectedProjectId.value!, true);
    toast.value = 'workspace 已导入并完成扫描';
  });
}

async function renameWorkspace(workspace: WorkspaceDto, displayName: string) {
  await withLoading(async () => {
    await api.renameWorkspace(workspace.projectId, workspace.id, displayName);
    if (selectedProjectId.value) {
      tree.value = await api.getTree(selectedProjectId.value, true);
    }
    toast.value = '工作目录显示名称已更新，真实目录名未改变';
  });
}

async function removeWorkspace(workspace: WorkspaceDto) {
  const confirmed = window.confirm(`确认移除工作目录「${workspace.displayName}」吗？\n\n这只会取消系统中的可见性，不会删除本地文件或已导入的副本文件。`);
  if (!confirmed) return;
  await withLoading(async () => {
    await api.removeWorkspace(workspace.projectId, workspace.id);
    if (selectedInstance.value?.workspaceId === workspace.id) {
      selectedInstance.value = null;
      graph.value = null;
      resetFilePreview();
      resetHtmlPrototype();
      selectedScreenId.value = '';
      selectedComponentKey.value = '';
    }
    if (selectedProjectId.value) {
      tree.value = await api.getTree(selectedProjectId.value, true);
    }
    toast.value = '工作目录已从系统中移除，本地文件未删除';
  });
}

async function refreshTree() {
  if (!selectedProjectId.value) return;
  await withLoading(async () => {
    tree.value = await api.getTree(selectedProjectId.value!, true);
  });
}

async function selectInstance(instance: SpiInstanceDto, mode: PreviewMode = 'current') {
  if (mode !== 'html-prototype') {
    leaveHtmlAnnotationDraftState();
  }
  selectedInstance.value = instance;
  previewMode.value = mode;
  resetFilePreview();
  await withLoading(async () => {
    graph.value = await api.getSpecGraph(instance.id);
    selectedScreenId.value = graph.value.screens[0]?.screenId ?? '';
    selectedComponentKey.value = '';
    createdChangeRequest.value = null;
    await loadHtmlPrototype(instance.id);
  });
}

async function selectGeneratedPreview(instance: SpiInstanceDto) {
  await selectInstance(instance, 'generated');
}

async function selectArchitectureManager(instance: SpiInstanceDto) {
  await selectInstance(instance, 'architecture');
}

async function selectAnnotationWorkbench(instance: SpiInstanceDto) {
  await selectInstance(instance, 'annotation');
  if (htmlPackage.value?.exists) {
    previewMode.value = 'html-prototype';
    return;
  }
  if (graph.value?.prototypeSource.userProvided) {
    previewMode.value = 'prototype';
  }
}

async function selectPrototypeUpload(instance: SpiInstanceDto) {
  await selectInstance(instance, 'prototype-upload');
}

async function selectPrototypePreview(instance: SpiInstanceDto) {
  await selectInstance(instance, 'prototype');
  if (!graph.value?.prototypeSource.userProvided) {
    toast.value = '还没有上传原型图，当前显示需求预览';
  }
}

async function selectResourceManager(instance: SpiInstanceDto) {
  leaveHtmlAnnotationDraftState();
  if (selectedInstance.value?.id === instance.id && previewMode.value === 'resources') {
    await selectGeneratedPreview(instance);
    return;
  }
  selectedInstance.value = instance;
  previewMode.value = 'resources';
  resetFilePreview();
  await withLoading(async () => {
    graph.value = await api.getSpecGraph(instance.id);
    selectedScreenId.value = graph.value.screens[0]?.screenId ?? '';
    selectedComponentKey.value = '';
    createdChangeRequest.value = null;
    fileTree.value = await api.getFileTree(instance.id);
    await loadHtmlPrototype(instance.id);
    const firstFile = findFirstFile(fileTree.value);
    if (firstFile) {
      await loadFileContent(instance.id, firstFile);
    }
  });
}

async function selectHtmlPrototype(instance: SpiInstanceDto) {
  selectedInstance.value = instance;
  previewMode.value = 'html-prototype';
  resetFilePreview();
  selectedHtmlElement.value = null;
  selectedHtmlBindingId.value = '';
  draftHtmlTargets.value = [];
  htmlInteractionMode.value = 'browse';
  await withLoading(async () => {
    graph.value = await api.getSpecGraph(instance.id);
    selectedScreenId.value = graph.value.screens[0]?.screenId ?? '';
    selectedComponentKey.value = '';
    createdChangeRequest.value = null;
    await loadHtmlPrototype(instance.id);
    if (!htmlPackage.value?.exists) {
      toast.value = '还没有 HTML 原型包，请先上传 HTML 文件夹或 HTML/ZIP 文件';
    }
  });
}

function requestPrototypeUpload(instance: SpiInstanceDto) {
  if (activeProject.value?.demoProject) {
    error.value = '演示项目是只读的，请在自己的项目中上传原型图。';
    return;
  }
  pendingPrototypeUploadInstance.value = instance;
  prototypeUploadInput.value?.click();
}

function requestHtmlFolderUpload(instance: SpiInstanceDto) {
  if (activeProject.value?.demoProject) {
    error.value = '演示项目是只读的，请在自己的项目中上传 HTML 原型。';
    return;
  }
  pendingHtmlUploadInstance.value = instance;
  htmlFolderInput.value?.click();
}

function requestHtmlFileUpload(instance: SpiInstanceDto) {
  if (activeProject.value?.demoProject) {
    error.value = '演示项目是只读的，请在自己的项目中上传 HTML 原型。';
    return;
  }
  pendingHtmlUploadInstance.value = instance;
  htmlFileInput.value?.click();
}

async function handlePrototypeUploadSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (!pendingPrototypeUploadInstance.value || !files.length) return;

  const instance = pendingPrototypeUploadInstance.value;
  pendingPrototypeUploadInstance.value = null;
  await uploadPrototypeFiles(instance, files, false);
}

async function handleHtmlUploadSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  const relativePaths = files.map((file) => {
    const webkitFile = file as File & { webkitRelativePath?: string };
    return webkitFile.webkitRelativePath || file.name;
  });
  input.value = '';
  if (!pendingHtmlUploadInstance.value || !files.length) return;

  const instance = pendingHtmlUploadInstance.value;
  pendingHtmlUploadInstance.value = null;
  await uploadHtmlPrototypeFiles(instance, files, relativePaths, false);
}

async function uploadPrototypeFiles(instance: SpiInstanceDto, files: File[], overwrite: boolean) {
  loading.value = true;
  error.value = '';
  toast.value = '';
  try {
    await api.uploadPrototypeFrames(instance.id, files, overwrite);
    await selectInstance(instance, 'prototype');
    toast.value = overwrite ? '原型图已覆盖并刷新' : '原型图已上传并刷新';
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 409) {
      const confirmed = window.confirm(`${err.message}\n\n是否确认覆盖？`);
      if (confirmed) {
        await uploadPrototypeFiles(instance, files, true);
        return;
      }
    } else {
      error.value = err instanceof Error ? err.message : String(err);
    }
  } finally {
    loading.value = false;
  }
}

async function uploadHtmlPrototypeFiles(instance: SpiInstanceDto, files: File[], relativePaths: string[], overwrite: boolean) {
  loading.value = true;
  error.value = '';
  toast.value = '';
  try {
    const result = await api.uploadHtmlPrototype(instance.id, files, relativePaths, overwrite);
    await selectHtmlPrototype(instance);
    htmlPackage.value = result.prototypePackage;
    htmlBindings.value = result.prototypePackage.bindings;
    toast.value = overwrite ? 'HTML 原型包已覆盖并刷新' : 'HTML 原型包已上传并刷新';
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 409) {
      const confirmed = window.confirm(`${err.message}\n\n是否确认覆盖？`);
      if (confirmed) {
        await uploadHtmlPrototypeFiles(instance, files, relativePaths, true);
        return;
      }
    } else {
      error.value = err instanceof Error ? err.message : String(err);
    }
  } finally {
    loading.value = false;
  }
}

async function selectFile(path: string) {
  if (!selectedInstance.value) return;
  await withLoading(async () => {
    await loadFileContent(selectedInstance.value!.id, path);
  });
}

async function loadFileContent(instanceId: number, path: string) {
  selectedFilePath.value = path;
  selectedFileContent.value = await api.getFileContent(instanceId, path);
}

function resetFilePreview() {
  fileTree.value = null;
  selectedFilePath.value = '';
  selectedFileContent.value = null;
}

function resetHtmlPrototype() {
  htmlPackage.value = null;
  htmlBindings.value = [];
  selectedHtmlElement.value = null;
  selectedHtmlBindingId.value = '';
  htmlInteractionMode.value = 'browse';
  draftHtmlTargets.value = [];
}

async function loadHtmlPrototype(instanceId: number) {
  htmlPackage.value = await api.getHtmlPrototype(instanceId);
  htmlBindings.value = htmlPackage.value.bindings;
  if (selectedHtmlBindingId.value && !htmlBindings.value.some((binding) => binding.bindingId === selectedHtmlBindingId.value)) {
    selectedHtmlBindingId.value = '';
  }
}

function applyHtmlBindings(bindings: HtmlPrototypeBindingDto[]) {
  htmlBindings.value = bindings;
  if (htmlPackage.value) {
    htmlPackage.value = {
      ...htmlPackage.value,
      bindings
    };
  }
  if (selectedHtmlBindingId.value && !bindings.some((binding) => binding.bindingId === selectedHtmlBindingId.value)) {
    selectedHtmlBindingId.value = '';
  }
}

async function refreshHtmlBindings(instanceId: number) {
  applyHtmlBindings(await api.getHtmlPrototypeBindings(instanceId));
}

function selectHtmlElement(element: HtmlElementSelection) {
  selectedHtmlElement.value = element;
}

function setDraftHtmlTargets(targets: HtmlPrototypeTargetDto[]) {
  draftHtmlTargets.value = uniqueHtmlTargets(targets);
}

function changeHtmlInteractionMode(mode: 'browse' | 'annotate') {
  htmlInteractionMode.value = mode;
}

function leaveHtmlAnnotationDraftState() {
  htmlInteractionMode.value = 'browse';
  draftHtmlTargets.value = [];
  selectedHtmlElement.value = null;
  selectedHtmlBindingId.value = '';
}

function selectHtmlBinding(bindingId: string) {
  selectedHtmlBindingId.value = bindingId;
  const binding = htmlBindings.value.find((item) => item.bindingId === bindingId);
  if (!binding) return;
  if (binding.screenId) {
    selectedScreenId.value = binding.screenId;
  }
  if (binding.componentId && graph.value) {
    const component = graph.value.components.find((candidate) => (
      candidate.screenId === binding.screenId && candidate.componentId === binding.componentId
    ));
    if (component) {
      selectComponent(component);
      return;
    }
  }
  selectedComponentKey.value = '';
  createdChangeRequest.value = null;
}

async function saveHtmlAnnotation(payload: SaveHtmlPrototypeBindingPayload) {
  if (!selectedInstance.value) return;
  const instanceId = selectedInstance.value.id;
  loading.value = false;
  error.value = '';
  toast.value = 'ANN 保存中...';
  annSaveDebug.value = {
    bindingId: '',
    status: 'saving',
    detail: '正在请求后端保存 ANN'
  };
  try {
    const binding = await withTimeout(
      api.saveHtmlPrototypeBinding(instanceId, payload),
      5000,
      saveAnnTimeoutMessage
    );
    applyHtmlBindings([
      ...htmlBindings.value.filter((item) => item.bindingId !== binding.bindingId),
      binding
    ]);
    // Saving creates a persisted ANN record, but should not automatically enter
    // ANN selection mode. Keeping this neutral avoids mixing "draft DOM" work
    // with saved ANN navigation/highlight behavior.
    selectedHtmlBindingId.value = '';
    draftHtmlTargets.value = [];
    annSaveDebug.value = {
      bindingId: binding.bindingId,
      status: 'save-ok',
      detail: 'ANN 已保存到后端并更新当前列表'
    };
    toast.value = `${binding.bindingId} 已创建，包含 ${binding.targets?.length ?? 0} 个 DOM`;
  } catch (err) {
    if (isSaveTimeout(err)) {
      annSaveDebug.value = {
        bindingId: '',
        status: 'save-timeout',
        detail: '保存请求未按时返回，稍后自动校准 bindings'
      };
      toast.value = 'ANN 保存确认超时，稍后自动校准';
      window.setTimeout(() => {
        void refreshHtmlBindings(instanceId).catch(() => undefined);
      }, 3000);
      return;
    }
    annSaveDebug.value = {
      bindingId: '',
      status: 'save-error',
      detail: err instanceof Error ? err.message : String(err)
    };
    toast.value = '';
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

async function deleteHtmlAnnotation(binding: HtmlPrototypeBindingDto) {
  if (!selectedInstance.value) return;
  annDeleteDebug.value = {
    bindingId: binding.bindingId,
    status: 'delete-intent',
    detail: '删除入口已触发，等待用户二次确认'
  };
  const confirmed = window.confirm(`确认删除标注「${binding.name || binding.bindingId}」吗？\n\n删除后不会删除原型文件，只会移除系统里的 ANN 关系。`);
  if (!confirmed) {
    annDeleteDebug.value = {
      bindingId: binding.bindingId,
      status: 'delete-cancelled',
      detail: '用户取消了二次确认'
    };
    return;
  }
  const instanceId = selectedInstance.value.id;
  const previousBindings = htmlBindings.value;
  const previousSelectedBindingId = selectedHtmlBindingId.value;
  loading.value = false;
  error.value = '';
  toast.value = `${binding.bindingId} 删除中...`;
  annDeleteDebug.value = {
    bindingId: binding.bindingId,
    status: 'optimistic-remove',
    detail: '已先从当前列表移除，正在请求后端删除'
  };
  applyHtmlBindings(htmlBindings.value.filter((item) => item.bindingId !== binding.bindingId));
  if (selectedHtmlBindingId.value === binding.bindingId) {
    selectedHtmlBindingId.value = '';
  }

  try {
    await withTimeout(
      api.deleteHtmlPrototypeBinding(instanceId, binding.bindingId),
      4000,
      deleteAnnTimeoutMessage
    );
    annDeleteDebug.value = {
      bindingId: binding.bindingId,
      status: 'delete-ok',
      detail: '后端删除已确认，后台校准 bindings'
    };
    toast.value = `${binding.bindingId} 已删除`;
    void refreshHtmlBindings(instanceId).catch((err) => {
      annDeleteDebug.value = {
        bindingId: binding.bindingId,
        status: 'refresh-failed',
        detail: err instanceof Error ? err.message : String(err)
      };
    });
  } catch (err) {
    if (isDeleteTimeout(err)) {
      annDeleteDebug.value = {
        bindingId: binding.bindingId,
        status: 'delete-timeout',
        detail: '删除请求未按时返回，已保持当前列表移除状态'
      };
      toast.value = `${binding.bindingId} 已从当前列表移除；后端确认稍后自动校准`;
      window.setTimeout(() => {
        void refreshHtmlBindings(instanceId).catch(() => undefined);
      }, 3000);
      return;
    }
    if (err instanceof ApiClientError && err.status === 404) {
      annDeleteDebug.value = {
        bindingId: binding.bindingId,
        status: 'delete-404-as-ok',
        detail: '后端已不存在该 ANN，按删除成功处理'
      };
      toast.value = `${binding.bindingId} 已删除`;
      void refreshHtmlBindings(instanceId).catch(() => undefined);
      return;
    }
    applyHtmlBindings(previousBindings);
    selectedHtmlBindingId.value = previousSelectedBindingId;
    annDeleteDebug.value = {
      bindingId: binding.bindingId,
      status: 'delete-error',
      detail: err instanceof Error ? err.message : String(err)
    };
    toast.value = '';
    error.value = err instanceof Error ? err.message : String(err);
  }
}

function changeHtmlEntry(entryPath: string) {
  if (!htmlPackage.value || !selectedInstance.value) return;
  htmlPackage.value = {
    ...htmlPackage.value,
    entryPath,
    entryUrl: `/api/instances/${selectedInstance.value.id}/prototype-html/files/${entryPath}`,
    htmlFiles: htmlPackage.value.htmlFiles.map((file) => ({ ...file, entry: file.path === entryPath }))
  };
  selectedHtmlElement.value = null;
  selectedHtmlBindingId.value = '';
  draftHtmlTargets.value = [];
}

function htmlTargetMatches(left: HtmlPrototypeTargetDto, right: HtmlPrototypeTargetDto) {
  if (left.pagePath !== right.pagePath) return false;
  if (left.targetId && right.targetId && left.targetId === right.targetId) return true;
  return !!left.selector && !!right.selector && left.selector === right.selector;
}

function uniqueHtmlTargets(targets: HtmlPrototypeTargetDto[]) {
  const result: HtmlPrototypeTargetDto[] = [];
  for (const target of targets) {
    if (!target.selector || result.some((item) => htmlTargetMatches(item, target))) continue;
    result.push(target);
  }
  return result;
}

async function createChangeRequest(payload: CreateChangeRequestPayload) {
  if (!selectedInstance.value || inspectorReadOnly.value) return;
  await withLoading(async () => {
    createdChangeRequest.value = await api.createChangeRequest(selectedInstance.value!.id, payload);
  });
}

async function withLoading(work: () => Promise<void>) {
  loading.value = true;
  error.value = '';
  toast.value = '';
  try {
    await work();
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    loading.value = false;
  }
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  let timeoutId = 0;
  const timeoutPromise = new Promise<T>((_, reject) => {
    timeoutId = window.setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => {
    if (timeoutId) {
      window.clearTimeout(timeoutId);
    }
  });
}

function isDeleteTimeout(err: unknown) {
  return err instanceof Error && err.message === deleteAnnTimeoutMessage;
}

function isSaveTimeout(err: unknown) {
  return err instanceof Error && err.message === saveAnnTimeoutMessage;
}

function toggleInspector() {
  inspectorCollapsed.value = !inspectorCollapsed.value;
}

function startInspectorResize(event: PointerEvent) {
  if (inspectorCollapsed.value) return;
  event.preventDefault();
  const onMove = (moveEvent: PointerEvent) => {
    const nextWidth = Math.min(680, Math.max(320, window.innerWidth - moveEvent.clientX));
    inspectorWidth.value = nextWidth;
  };
  const onUp = () => {
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp);
}

function componentKey(component: ComponentNodeDto) {
  return `${component.screenId}::${component.componentId}`;
}

function selectScreen(screenId: string) {
  selectedScreenId.value = screenId;
  selectedComponentKey.value = '';
  createdChangeRequest.value = null;
}

function selectComponent(component: ComponentNodeDto) {
  selectedScreenId.value = component.screenId;
  selectedComponentKey.value = componentKey(component);
  createdChangeRequest.value = null;
}

function setHoveredComponent(component: ComponentNodeDto | null) {
  hoveredComponentKey.value = component ? componentKey(component) : '';
}

function findFirstFile(node: FileTreeNodeDto | null): string {
  if (!node) return '';
  if (!node.directory) return node.relativePath;
  for (const child of node.children) {
    const found = findFirstFile(child);
    if (found) return found;
  }
  return '';
}

function relatedIds(type: 'features' | 'rules' | 'acceptances') {
  // Keep relationship merging on the client so hover/click feedback stays instant.
  const set = new Set<string>();
  if (activeScreen.value) {
    const ids = type === 'features'
      ? activeScreen.value.relatedFeatures
      : type === 'rules'
        ? activeScreen.value.relatedRules
        : activeScreen.value.relatedAcceptances;
    ids.forEach((id) => set.add(id));
  }
  if (activeComponent.value) {
    const ids = type === 'features'
      ? activeComponent.value.relatedFeatures
      : type === 'rules'
        ? activeComponent.value.relatedRules
        : activeComponent.value.relatedAcceptances;
    ids.forEach((id) => set.add(id));
  }
  for (const annotation of relatedAnnotations.value) {
    const ids = type === 'features'
      ? annotation.relatedFeatures
      : type === 'rules'
        ? annotation.relatedRules
        : annotation.relatedAcceptances;
    ids.forEach((id) => set.add(id));
  }
  return [...set].sort();
}
</script>

<template>
  <main class="app-shell" :style="appGridStyle">
    <input
      ref="prototypeUploadInput"
      class="hidden-file-input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/svg+xml"
      multiple
      @change="handlePrototypeUploadSelected"
    />
    <input
      ref="htmlFolderInput"
      class="hidden-file-input"
      type="file"
      webkitdirectory
      directory
      multiple
      @change="handleHtmlUploadSelected"
    />
    <input
      ref="htmlFileInput"
      class="hidden-file-input"
      type="file"
      accept=".html,.htm,.zip,text/html,application/zip"
      multiple
      @change="handleHtmlUploadSelected"
    />
    <ProjectSidebar
      :projects="projects"
      :tree="tree"
      :active-project-id="selectedProjectId"
      :selected-instance-id="selectedInstance?.id ?? null"
      :preview-mode="previewMode"
      :file-tree="fileTree"
      :selected-file-path="selectedFilePath"
      @create-project="createProject"
      @delete-project="deleteProject"
      @select-project="(id) => selectProject(id, false)"
      @add-workspace="addWorkspace"
      @import-workspace="importWorkspace"
      @remove-workspace="removeWorkspace"
      @rename-workspace="renameWorkspace"
      @refresh-tree="refreshTree"
      @select-instance="selectGeneratedPreview"
      @select-architecture-manager="selectArchitectureManager"
      @select-generated-preview="selectGeneratedPreview"
      @select-annotation-workbench="selectAnnotationWorkbench"
      @select-resource-manager="selectResourceManager"
      @select-prototype-upload="selectPrototypeUpload"
      @select-file="selectFile"
      @upload-prototype="requestPrototypeUpload"
      @upload-html-folder="requestHtmlFolderUpload"
      @upload-html-file="requestHtmlFileUpload"
    />

    <section class="workspace-panel">
      <header class="topbar">
        <div>
          <p class="eyebrow">SSF Autodesign</p>
          <h1>{{ selectedInstance?.instanceName ?? '选择一个项目或演示项目' }}</h1>
        </div>
        <div class="status-chip" :class="{ active: graph }">
          <Bot :size="16" />
          <span>{{ graph ? 'SpecGraph loaded' : 'waiting' }}</span>
        </div>
      </header>

      <div v-if="error" class="error-banner">
        <AlertCircle :size="18" />
        <span>{{ error }}</span>
      </div>

      <div v-if="toast" class="success-banner">
        <span>{{ toast }}</span>
      </div>

      <div v-if="loading" class="loading-mask">
        <Loader2 class="spin" :size="22" />
        <span>处理中</span>
      </div>

      <FilePreview
        v-if="previewMode === 'resources'"
        :file="selectedFileContent"
        :selected-path="selectedFilePath"
      />

      <section v-else-if="previewMode === 'architecture'" class="architecture-placeholder">
        <p class="eyebrow">Product Architecture</p>
        <h2>产品架构管理</h2>
        <p>这个工作区后续用于管理产品模块、领域边界、关键对象和上下游交付关系。当前版本先保留入口，不写入任何文件。</p>
      </section>

      <section v-else-if="previewMode === 'prototype-upload'" class="prototype-upload-workbench">
        <div class="prototype-upload-hero">
          <p class="eyebrow">Prototype Package</p>
          <h2>原型上传</h2>
          <p>把 UI 工具导出的结果统一导入到当前 SPI 的 <strong>requirement-prototype</strong> 目录。当前可识别 HTML 原型包和按 SCR 编号命名的图片帧。</p>
        </div>

        <div class="prototype-upload-grid">
          <article>
            <Code2 :size="20" />
            <h3>HTML 文件夹</h3>
            <p>适合 Axure、墨刀、Motiff 等导出的多层级 HTML 目录；系统会寻找入口页，并保留 CSS、JS、图片等内部资源路径。</p>
            <button :disabled="!selectedInstance || !!activeProject?.demoProject" @click="selectedInstance && requestHtmlFolderUpload(selectedInstance)">选择文件夹</button>
          </article>
          <article>
            <FileArchive :size="20" />
            <h3>HTML / ZIP</h3>
            <p>适合单个 HTML 文件或 HTML 导出压缩包；后端会解包，按入口页规则生成 manifest，后续可在标注区切换 HTML 页面。</p>
            <button :disabled="!selectedInstance || !!activeProject?.demoProject" @click="selectedInstance && requestHtmlFileUpload(selectedInstance)">选择文件</button>
          </article>
          <article>
            <Images :size="20" />
            <h3>图片原型</h3>
            <p>适合单张或多张页面截图，文件名需要包含 SCR-001 这样的页面 ID；第一版用于人工区域标注，暂不支持图片 ZIP 自动解包。</p>
            <button :disabled="!selectedInstance || !!activeProject?.demoProject" @click="selectedInstance && requestPrototypeUpload(selectedInstance)">选择图片</button>
          </article>
        </div>

        <div class="prototype-upload-note">
          <strong>当前存放策略</strong>
          <span>HTML 包：requirement-prototype/packages/current/source；图片帧：requirement-prototype/frames。覆盖上传前会再次确认。</span>
        </div>
      </section>

      <template v-else-if="annotationModeActive">
        <section class="annotation-workbench">
          <header class="annotation-toolbar">
            <div>
              <p class="eyebrow">Requirement Annotation</p>
              <h2>原型标注</h2>
              <span>在这里查看当前可标注的原型类型，再把视觉区域绑定到 CMP、FEAT、BR 和 AC。</span>
            </div>
            <div class="annotation-actions">
              <button
                :class="{ selected: annotationKind === 'image' }"
                :disabled="imageAnnotationDisabled"
                @click="selectedInstance && !imageAnnotationDisabled && selectPrototypePreview(selectedInstance)"
              >
                <Images :size="14" />
                图片标注
              </button>
              <button
                :class="{ selected: annotationKind === 'html' }"
                :disabled="htmlAnnotationDisabled"
                @click="selectedInstance && !htmlAnnotationDisabled && selectHtmlPrototype(selectedInstance)"
              >
                <Code2 :size="14" />
                HTML 标注
              </button>
            </div>
          </header>

          <section class="annotation-package-card" :class="annotationKind">
            <div>
              <strong>{{ annotationStatus.title }}</strong>
              <p>{{ annotationStatus.description }}</p>
            </div>
            <span>{{ annotationStatus.path }}</span>
          </section>

          <HtmlPrototypeCanvas
            v-if="previewMode === 'html-prototype'"
            :graph="graph"
            :html-package="htmlPackage"
            :bindings="htmlBindings"
            :selected-element="selectedHtmlElement"
            :draft-targets="draftHtmlTargets"
            :selected-binding-id="selectedHtmlBindingId"
            :interaction-mode="htmlInteractionMode"
            :save-debug="annSaveDebug"
            :delete-debug="annDeleteDebug"
            @select-element="selectHtmlElement"
            @save-binding="saveHtmlAnnotation"
            @delete-binding="deleteHtmlAnnotation"
            @select-binding="selectHtmlBinding"
            @set-draft-targets="setDraftHtmlTargets"
            @change-mode="changeHtmlInteractionMode"
            @change-entry="changeHtmlEntry"
          />

          <PrototypeCanvas
            v-else-if="previewMode === 'prototype'"
            :graph="graph"
            :active-screen-id="selectedScreenId"
            :selected-component-key="selectedComponentKey"
            :hovered-component-key="hoveredComponentKey"
            :preview-mode="previewMode"
            @select-screen="selectScreen"
            @select-component="selectComponent"
            @hover-component="setHoveredComponent"
          />

          <div v-else class="annotation-empty-state">
            <h3>还没有可标注的原型</h3>
            <p>可以先上传 HTML 原型包；如果只有静态图，也可以上传图片原型后再做人工区域标注。</p>
          </div>
        </section>
      </template>

      <PrototypeCanvas
        v-else
        :graph="graph"
        :active-screen-id="selectedScreenId"
        :selected-component-key="selectedComponentKey"
        :hovered-component-key="hoveredComponentKey"
        :preview-mode="previewMode"
        @select-screen="selectScreen"
        @select-component="selectComponent"
        @hover-component="setHoveredComponent"
      />
    </section>

    <div class="inspector-resize-handle" @pointerdown="startInspectorResize"></div>

    <TaskOverviewPanel
      v-if="annotationModeActive"
      :graph="graph"
      :collapsed="inspectorCollapsed"
      :focus-binding="activeHtmlBinding"
      @toggle-collapse="toggleInspector"
      @select-screen="selectScreen"
      @select-component="selectComponent"
    />

    <InspectorPanel
      v-else
      :instance="selectedInstance"
      :graph="graph"
      :screen="activeScreen"
      :component="activeComponent"
      :annotations="relatedAnnotations"
      :features="relatedFeatures"
      :rules="relatedRules"
      :acceptances="relatedAcceptances"
      :created-change-request="createdChangeRequest"
      :read-only="inspectorReadOnly"
      :collapsed="inspectorCollapsed"
      :read-only-reason="inspectorReadOnlyReason"
      @toggle-collapse="toggleInspector"
      @create-change-request="createChangeRequest"
    />
  </main>
</template>
