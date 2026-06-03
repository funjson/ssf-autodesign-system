<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Code2, FileArchive, Layers, MousePointerClick, Plus, Trash2, X } from '@lucide/vue';
import { apiAssetUrl } from '../api/client';
import type {
  ComponentNodeDto,
  HtmlElementSelection,
  HtmlPrototypeBindingDto,
  HtmlPrototypePackageDto,
  HtmlPrototypeTargetDto,
  SaveHtmlPrototypeBindingPayload,
  ScreenNodeDto,
  SpecGraphDto
} from '../types/models';

const props = defineProps<{
  graph: SpecGraphDto | null;
  htmlPackage: HtmlPrototypePackageDto | null;
  bindings: HtmlPrototypeBindingDto[];
  selectedElement: HtmlElementSelection | null;
  draftTargets: HtmlPrototypeTargetDto[];
  selectedBindingId: string;
  interactionMode: 'browse' | 'annotate';
}>();

const emit = defineEmits<{
  (event: 'select-element', element: HtmlElementSelection): void;
  (event: 'save-binding', payload: SaveHtmlPrototypeBindingPayload): void;
  (event: 'delete-binding', binding: HtmlPrototypeBindingDto): void;
  (event: 'select-binding', bindingId: string): void;
  (event: 'set-draft-targets', targets: HtmlPrototypeTargetDto[]): void;
  (event: 'change-mode', mode: 'browse' | 'annotate'): void;
  (event: 'change-entry', entryPath: string): void;
}>();

const annotationName = ref('');
const screenChoice = ref('');
const componentChoice = ref('');
const iframeNonce = ref(Date.now());
const iframeRef = ref<HTMLIFrameElement | null>(null);
const parentLastSentMessage = ref('');
const parentLastReceivedMessage = ref('');
const lastDraftAction = ref('');
const draftActionSeq = ref(0);
const bridgeDebugState = ref({
  reason: '',
  pagePath: '',
  annotationMode: false,
  draftTargetCount: 0,
  annotationCount: 0,
  activeAnnotationId: '',
  lastParentMessageType: '',
  lastOutboundMessageType: '',
  lastDraftChangeCount: 0,
  lastSyncDraftCount: 0,
  lastToggleSelector: ''
});

// This component is an adapter between Vue state and the injected iframe bridge.
// Saved ANN data and draft DOM targets come from App.vue; local refs only cover
// form controls and iframe lifecycle details.
const entryUrl = computed(() => {
  if (!props.htmlPackage?.entryUrl) return '';
  return `${apiAssetUrl(props.htmlPackage.entryUrl)}?t=${iframeNonce.value}`;
});

const screens = computed(() => props.graph?.screens ?? []);

const selectedScreen = computed<ScreenNodeDto | null>(() => {
  if (!props.graph || !screenChoice.value) return null;
  return props.graph.screens.find((screen) => screen.screenId === screenChoice.value) ?? null;
});

const availableComponents = computed(() => {
  if (!props.graph || !screenChoice.value) return [];
  return props.graph.components.filter((component) => component.screenId === screenChoice.value);
});

const selectedComponent = computed(() => {
  if (!componentChoice.value || !props.graph) return null;
  return props.graph.components.find((component) => keyOf(component) === componentChoice.value) ?? null;
});

const canSaveNew = computed(() => !!screenChoice.value && props.draftTargets.length > 0);
const annotationSyncKey = computed(() => [
  props.htmlPackage?.entryPath ?? '',
  props.selectedBindingId,
  ...props.bindings.map((binding) => [
    binding.bindingId,
    binding.name,
    binding.screenId,
    binding.componentId,
    binding.targets.map((target) => `${target.pagePath}|${target.targetId}|${target.selector}`).join(';')
  ].join('|'))
].join('||'));

watch(() => props.graph?.instanceId, () => {
  screenChoice.value = props.graph?.screens[0]?.screenId ?? '';
  componentChoice.value = '';
}, { immediate: true });

watch(() => props.selectedElement, (element) => {
  if (!element || !props.graph || props.interactionMode !== 'annotate') return;
  const nextScreen = element.screenId && props.graph.screens.some((screen) => screen.screenId === element.screenId)
    ? element.screenId
    : screenChoice.value || props.graph.screens[0]?.screenId || '';
  if (nextScreen && screenChoice.value !== nextScreen) {
    screenChoice.value = nextScreen;
    componentChoice.value = '';
  }
  if (element.componentId) {
    const component = props.graph.components.find((item) => item.screenId === screenChoice.value && item.componentId === element.componentId);
    componentChoice.value = component ? keyOf(component) : componentChoice.value;
  }
});

watch(availableComponents, (components) => {
  if (!componentChoice.value) return;
  if (!components.some((component) => keyOf(component) === componentChoice.value)) {
    componentChoice.value = '';
  }
});

watch(() => props.htmlPackage?.entryUrl, () => {
  iframeNonce.value = Date.now();
});

watch(() => props.interactionMode, syncBridgeMode);
watch(annotationSyncKey, scheduleBridgeAnnotationSync, { flush: 'post' });

onMounted(() => {
  window.addEventListener('message', handleMessage);
});
onBeforeUnmount(() => {
  window.removeEventListener('message', handleMessage);
});

function handleMessage(event: MessageEvent) {
  const data = event.data;
  parentLastReceivedMessage.value = data?.type || '';
  if (data?.type === 'ssf-prototype-debug-state') {
    bridgeDebugState.value = normalizeBridgeDebugState(data);
    return;
  }
  if (data?.type === 'ssf-prototype-ready') {
    syncBridgeMode();
    syncBridgeAnnotations();
    return;
  }
  if (data?.type === 'ssf-prototype-draft-targets-change') {
    const element = elementFromBridgePayload(data);
    emit('select-element', element);
    const targets = normalizeDraftTargets(data.draftTargets);
    recordDraftAction(`iframe-toggle-to-${targets.length}`);
    emit('set-draft-targets', targets);
    return;
  }
  if (!data || data.type !== 'ssf-prototype-element-select') return;
  emit('select-element', elementFromBridgePayload(data));
}

function elementFromBridgePayload(data: Record<string, unknown>): HtmlElementSelection {
  return {
    targetId: stringValue(data.targetId),
    pagePath: stringValue(data.pagePath),
    selector: stringValue(data.selector),
    tagName: stringValue(data.tagName),
    elementId: stringValue(data.elementId),
    className: stringValue(data.className),
    text: stringValue(data.text),
    screenId: normalizeScreenId(stringValue(data.screenId)),
    componentId: stringValue(data.componentId),
    annotationIds: Array.isArray(data.annotationIds) ? data.annotationIds : [],
    featureIds: Array.isArray(data.featureIds) ? data.featureIds : [],
    ruleIds: Array.isArray(data.ruleIds) ? data.ruleIds : [],
    acceptanceIds: Array.isArray(data.acceptanceIds) ? data.acceptanceIds : []
  } as HtmlElementSelection;
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : '';
}

function boolValue(value: unknown) {
  return value === true;
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function optionalNumberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function normalizeBridgeDebugState(data: Record<string, unknown>) {
  return {
    reason: stringValue(data.reason),
    pagePath: stringValue(data.pagePath),
    annotationMode: boolValue(data.annotationMode),
    draftTargetCount: numberValue(data.draftTargetCount),
    annotationCount: numberValue(data.annotationCount),
    activeAnnotationId: stringValue(data.activeAnnotationId),
    lastParentMessageType: stringValue(data.lastParentMessageType),
    lastOutboundMessageType: stringValue(data.lastOutboundMessageType),
    lastDraftChangeCount: numberValue(data.lastDraftChangeCount),
    lastSyncDraftCount: numberValue(data.lastSyncDraftCount),
    lastToggleSelector: stringValue(data.lastToggleSelector)
  };
}

function keyOf(component: ComponentNodeDto) {
  return `${component.screenId}::${component.componentId}`;
}

function normalizeScreenId(screenId: string) {
  const match = screenId.toUpperCase().match(/SCR-\d{3}/);
  return match ? match[0] : screenId;
}

function targetMatches(left: HtmlPrototypeTargetDto, right: HtmlPrototypeTargetDto | HtmlElementSelection) {
  if (left.pagePath !== right.pagePath) return false;
  const rightTargetId = 'targetId' in right ? right.targetId : '';
  if (left.targetId && rightTargetId && left.targetId === rightTargetId) return true;
  return !!left.selector && !!right.selector && left.selector === right.selector;
}

function targetKey(target: HtmlPrototypeTargetDto) {
  return [
    target.pagePath,
    target.targetId || target.selector,
    target.selector,
    target.textFingerprint,
    target.rectX ?? '',
    target.rectY ?? '',
    target.rectWidth ?? '',
    target.rectHeight ?? ''
  ].join('::');
}

function normalizeDraftTargets(targets: unknown): HtmlPrototypeTargetDto[] {
  if (!Array.isArray(targets)) return [];
  return uniqueTargets(targets.map((raw) => {
    const target = raw as Partial<HtmlPrototypeTargetDto>;
    return {
      targetId: typeof target.targetId === 'string' ? target.targetId : '',
      kind: typeof target.kind === 'string' && target.kind ? target.kind : 'dom',
      pagePath: typeof target.pagePath === 'string' ? target.pagePath : '',
      screenSelector: typeof target.screenSelector === 'string' ? target.screenSelector : '',
      selector: typeof target.selector === 'string' ? target.selector : '',
      textFingerprint: typeof target.textFingerprint === 'string' ? target.textFingerprint : '',
      tagName: typeof target.tagName === 'string' ? target.tagName : '',
      elementId: typeof target.elementId === 'string' ? target.elementId : '',
      className: typeof target.className === 'string' ? target.className : '',
      rectX: optionalNumberValue(target.rectX),
      rectY: optionalNumberValue(target.rectY),
      rectWidth: optionalNumberValue(target.rectWidth),
      rectHeight: optionalNumberValue(target.rectHeight)
    };
  }));
}

function relationSummary(binding: HtmlPrototypeBindingDto) {
  return binding.componentId
    ? `${binding.screenId}/${binding.componentId}`
    : `${binding.screenId}/页面`;
}

function componentName(binding: HtmlPrototypeBindingDto) {
  if (!props.graph || !binding.componentId) return '';
  return props.graph.components.find((component) => component.screenId === binding.screenId && component.componentId === binding.componentId)?.name ?? '';
}

function saveNewAnnotation() {
  const payload = buildPayload(undefined, props.draftTargets);
  if (payload) emit('save-binding', payload);
}

function buildPayload(bindingId: string | undefined, targets: HtmlPrototypeTargetDto[]): SaveHtmlPrototypeBindingPayload | null {
  if (!screenChoice.value || !targets.length) return null;
  const component = selectedComponent.value;
  const screen = selectedScreen.value;
  return {
    bindingId,
    name: annotationName.value.trim() || undefined,
    screenId: component ? component.screenId : screenChoice.value,
    componentId: component?.componentId ?? '',
    targets: uniqueTargets(targets),
    relatedFeatures: component ? component.relatedFeatures : screen?.relatedFeatures ?? [],
    relatedRules: component ? component.relatedRules : screen?.relatedRules ?? [],
    relatedAcceptances: component ? component.relatedAcceptances : screen?.relatedAcceptances ?? []
  };
}

function uniqueTargets(targets: HtmlPrototypeTargetDto[]) {
  const result: HtmlPrototypeTargetDto[] = [];
  for (const target of targets) {
    if (!target.selector || result.some((item) => targetMatches(item, target))) continue;
    result.push(target);
  }
  return result;
}

function clearDraftTargets() {
  recordDraftAction('clear-to-0');
  updateDraftTargets([]);
}

function removeDraftTarget(target: HtmlPrototypeTargetDto) {
  const nextTargets = props.draftTargets.filter((item) => !targetMatches(item, target));
  recordDraftAction(`remove-to-${nextTargets.length}`);
  updateDraftTargets(nextTargets);
}

function requestBindingSelection(bindingId: string) {
  emit('select-binding', bindingId);
}

function updateDraftTargets(targets: HtmlPrototypeTargetDto[]) {
  const nextTargets = uniqueTargets(targets);
  emit('set-draft-targets', nextTargets);
  // Only parent-originated edits, such as deleting from the right panel, are
  // pushed back into the iframe. Iframe-originated toggles are accepted as-is.
  syncBridgeAnnotations(nextTargets);
  window.setTimeout(() => syncBridgeAnnotations(nextTargets), 80);
}

function recordDraftAction(action: string) {
  draftActionSeq.value += 1;
  lastDraftAction.value = `${draftActionSeq.value}. ${action}`;
}

function changeMode(mode: 'browse' | 'annotate') {
  emit('change-mode', mode);
  syncBridgeMode(mode);
  window.setTimeout(() => syncBridgeMode(mode), 40);
  window.setTimeout(() => syncBridgeMode(mode), 160);
}

function syncBridgeMode(mode = props.interactionMode) {
  parentLastSentMessage.value = 'ssf-prototype-set-mode';
  iframeRef.value?.contentWindow?.postMessage({
    type: 'ssf-prototype-set-mode',
    mode: mode === 'annotate' ? 'annotate' : 'browse'
  }, '*');
}

function syncBridgeAnnotations(draftTargets = props.draftTargets) {
  // One full snapshot keeps the iframe rendering deterministic: saved ANN
  // overlays, the active ANN, and the temporary draft DOM set are sent together.
  parentLastSentMessage.value = 'ssf-prototype-sync-annotations';
  iframeRef.value?.contentWindow?.postMessage({
    type: 'ssf-prototype-sync-annotations',
    activeAnnotationId: props.selectedBindingId,
    draftTargets,
    annotations: props.bindings.map((binding) => ({
      bindingId: binding.bindingId,
      name: binding.name,
      screenId: binding.screenId,
      componentId: binding.componentId,
      targets: binding.targets
    }))
  }, '*');
}

function scheduleBridgeAnnotationSync() {
  // ANN changes are driven by async saves. Sending after the Vue flush, then
  // retrying briefly, prevents a stale iframe from keeping draft-only state.
  syncBridgeAnnotations();
  window.setTimeout(() => syncBridgeAnnotations(), 60);
  window.setTimeout(() => syncBridgeAnnotations(), 180);
}
</script>

<template>
  <section class="html-prototype-panel">
    <div v-if="!props.htmlPackage?.exists" class="empty-state main-empty">
      <FileArchive :size="34" />
      <h2>还没有 HTML 原型包</h2>
      <p>在左侧选择“上传 HTML 文件夹”或“上传 HTML/ZIP 文件”，系统会识别入口页并进入 DOM 标注模式。</p>
    </div>

    <template v-else>
      <header class="html-prototype-toolbar">
        <div>
          <p class="eyebrow">HTML Prototype</p>
          <h2>{{ props.htmlPackage.entryPath || '未选择入口页' }}</h2>
          <span>{{ props.htmlPackage.sourceRoot }}</span>
        </div>
        <div class="html-prototype-controls">
          <div class="prototype-mode-toggle" aria-label="HTML 原型交互模式">
            <button type="button" :class="{ selected: props.interactionMode === 'browse' }" @pointerdown.stop.prevent="changeMode('browse')">浏览</button>
            <button type="button" :class="{ selected: props.interactionMode === 'annotate' }" @pointerdown.stop.prevent="changeMode('annotate')">标注</button>
          </div>
          <select :value="props.htmlPackage.entryPath" @change="emit('change-entry', ($event.target as HTMLSelectElement).value)">
            <option v-for="file in props.htmlPackage.htmlFiles" :key="file.path" :value="file.path">
              {{ file.entry ? '入口 · ' : '' }}{{ file.path }}
            </option>
          </select>
        </div>
      </header>

      <div class="prototype-mode-note" :class="props.interactionMode">
        <span v-if="props.interactionMode === 'browse'">浏览模式：点击执行原型自己的跳转和按钮逻辑；已保存 ANN 标注框会保留显示。</span>
        <span v-else>标注模式：点击 DOM 会加入或移出“待保存 DOM”。可以一次选多个 DOM，再保存为一个 ANN。</span>
      </div>

      <div v-if="props.htmlPackage.warnings.length" class="prototype-warning-list">
        <div v-for="warning in props.htmlPackage.warnings" :key="warning">{{ warning }}</div>
      </div>

      <div class="html-preview-grid">
        <div class="html-frame-stage">
          <iframe
            v-if="entryUrl"
            ref="iframeRef"
            class="html-prototype-frame"
            sandbox="allow-scripts allow-forms allow-popups allow-modals"
            :src="entryUrl"
            @load="() => { syncBridgeMode(); syncBridgeAnnotations(); }"
          ></iframe>

        </div>

        <aside class="html-binding-panel" :class="props.interactionMode">
          <div class="binding-panel-title">
            <MousePointerClick :size="18" />
            <h3>标注管理</h3>
          </div>

          <div v-if="props.interactionMode === 'annotate'" class="binding-empty">
            <p>标注模式下点击原型 DOM 会加入或移出待保存集合；多个 DOM 可以保存成同一个 ANN。</p>
          </div>

          <section class="annotation-debug-panel">
            <div class="binding-panel-title">
              <Code2 :size="15" />
              <h3>调试状态</h3>
            </div>
            <dl>
              <dt>父页面模式</dt>
              <dd>{{ props.interactionMode }}</dd>
              <dt>iframe 模式</dt>
              <dd>{{ bridgeDebugState.annotationMode ? 'annotate' : 'browse' }}</dd>
              <dt>父页面 draft</dt>
              <dd>{{ props.draftTargets.length }}</dd>
              <dt>父页面 ANN</dt>
              <dd>{{ props.bindings.length }}</dd>
              <dt>选中 ANN</dt>
              <dd>{{ props.selectedBindingId || '-' }}</dd>
              <dt>iframe draft</dt>
              <dd>{{ bridgeDebugState.draftTargetCount }}</dd>
              <dt>iframe ANN</dt>
              <dd>{{ bridgeDebugState.annotationCount }}</dd>
              <dt>iframe toggle 数</dt>
              <dd>{{ bridgeDebugState.lastDraftChangeCount }}</dd>
              <dt>父回灌 draft</dt>
              <dd>{{ bridgeDebugState.lastSyncDraftCount }}</dd>
              <dt>iframe → parent</dt>
              <dd>{{ parentLastReceivedMessage || '-' }}</dd>
              <dt>parent → iframe</dt>
              <dd>{{ parentLastSentMessage || '-' }}</dd>
              <dt>草稿操作</dt>
              <dd>{{ lastDraftAction || '-' }}</dd>
              <dt>toggle selector</dt>
              <dd>{{ bridgeDebugState.lastToggleSelector || '-' }}</dd>
              <dt>ANN ids</dt>
              <dd>{{ props.bindings.map((binding) => binding.bindingId).join(', ') || '-' }}</dd>
              <dt>draft ids</dt>
              <dd>{{ props.draftTargets.map((target) => target.targetId || target.selector).join(', ') || '-' }}</dd>
              <dt>bridge reason</dt>
              <dd>{{ bridgeDebugState.reason || '-' }}</dd>
            </dl>
          </section>

          <section v-if="props.interactionMode === 'annotate'" class="draft-target-panel">
            <div class="binding-panel-title">
              <Layers :size="16" />
              <h3>待保存 DOM</h3>
              <span>{{ props.draftTargets.length }}</span>
            </div>
            <p v-if="!props.draftTargets.length">还没有选择 DOM。</p>
            <article v-for="target in props.draftTargets" :key="targetKey(target)">
              <strong>{{ target.textFingerprint || target.selector }}</strong>
              <small>{{ target.tagName }} · {{ target.selector }}</small>
              <button type="button" title="移除这个 DOM" @click.stop.prevent="removeDraftTarget(target)">
                <X :size="13" />
              </button>
            </article>
            <button v-if="props.draftTargets.length" type="button" class="text-mini-button" @click.stop.prevent="clearDraftTargets">
              <Trash2 :size="13" />
              清空待保存 DOM
            </button>
          </section>

          <section v-if="props.interactionMode === 'annotate'" class="annotation-form-panel">
            <div class="binding-field">
              <label>ANN 名称</label>
              <input v-model="annotationName" maxlength="40" placeholder="例如：首页主题按钮组" />
            </div>

            <div class="binding-field">
              <label>SCR 页面（必选）</label>
              <select v-model="screenChoice">
                <option value="">选择 SCR</option>
                <option v-for="screen in screens" :key="screen.screenId" :value="screen.screenId">
                  {{ screen.screenId }} · {{ screen.title || screen.frameName }}
                </option>
              </select>
            </div>

            <div class="binding-field">
              <label>CMP 组件（可选，不选就是页面级 SCR 标注）</label>
              <select v-model="componentChoice">
                <option value="">不绑定 CMP，仅标注 SCR</option>
                <option v-for="component in availableComponents" :key="keyOf(component)" :value="keyOf(component)">
                  {{ component.componentId }} · {{ component.visibleText || component.name }}
                </option>
              </select>
            </div>

            <button type="button" class="bind-button" :disabled="!canSaveNew" @click="saveNewAnnotation">
              <Plus :size="15" />
              新建 ANN
            </button>
          </section>

          <section class="saved-annotation-list">
            <div class="binding-panel-title">
              <Layers :size="16" />
              <h3>已保存 ANN</h3>
            </div>
            <p v-if="!props.bindings.length">暂无保存记录。</p>
            <template v-for="binding in props.bindings" :key="binding.bindingId">
              <a
                v-if="props.interactionMode === 'browse'"
                class="saved-ann-row saved-ann-row-button readonly"
                :class="{ selected: binding.bindingId === props.selectedBindingId }"
                :data-binding-id="binding.bindingId"
                :href="`#ann-${binding.bindingId}`"
                @focus="requestBindingSelection(binding.bindingId)"
                @pointerdown="requestBindingSelection(binding.bindingId)"
                @mousedown="requestBindingSelection(binding.bindingId)"
                @click="requestBindingSelection(binding.bindingId)"
              >
                <strong>{{ binding.name || binding.bindingId }}</strong>
                <span>{{ binding.bindingId }} · {{ relationSummary(binding) }} · {{ binding.targets.length }} DOM</span>
                <small v-if="componentName(binding)">{{ componentName(binding) }}</small>
              </a>

              <article
                v-else
                class="saved-ann-row"
                :class="{ selected: binding.bindingId === props.selectedBindingId }"
                :data-binding-id="binding.bindingId"
              >
                <button class="saved-ann-main" type="button" @click="requestBindingSelection(binding.bindingId)">
                  <strong>{{ binding.name || binding.bindingId }}</strong>
                  <span>{{ binding.bindingId }} · {{ relationSummary(binding) }} · {{ binding.targets.length }} DOM</span>
                  <small v-if="componentName(binding)">{{ componentName(binding) }}</small>
                </button>
                <button
                  class="saved-ann-delete"
                  type="button"
                  title="删除 ANN"
                  @pointerdown.stop
                  @click.stop="emit('delete-binding', binding)"
                >
                  <Trash2 :size="13" />
                </button>
              </article>
            </template>
          </section>

          <div v-if="props.interactionMode === 'annotate'" class="binding-count">
            <Code2 :size="14" />
            已保存 {{ props.bindings.length }} 个 ANN 标注
          </div>
        </aside>
      </div>
    </template>
  </section>
</template>
