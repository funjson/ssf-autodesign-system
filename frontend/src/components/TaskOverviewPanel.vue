<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ListChecks, PanelRightClose, PanelRightOpen } from '@lucide/vue';
import FeatureFlowDiagram from './FeatureFlowDiagram.vue';
import type { AcceptanceNodeDto, ComponentNodeDto, FeatureNodeDto, HtmlPrototypeBindingDto, RuleNodeDto, ScreenNodeDto, SpecGraphDto } from '../types/models';

const props = defineProps<{
  graph: SpecGraphDto | null;
  collapsed: boolean;
  focusBinding?: HtmlPrototypeBindingDto | null;
}>();

const emit = defineEmits<{
  (event: 'toggle-collapse'): void;
  (event: 'select-screen', screenId: string): void;
  (event: 'select-component', component: ComponentNodeDto): void;
}>();

type OverviewMode = 'screens' | 'features' | 'rules' | 'acceptances' | 'annotations';

type ScreenTaskGroup = {
  key: string;
  title: string;
  screen: ScreenNodeDto | null;
  features: FeatureNodeDto[];
  components: ComponentNodeDto[];
};

const overviewMode = ref<OverviewMode>('screens');

const hasFocus = computed(() => !!props.focusBinding);
const focusFeatureIds = computed(() => new Set(props.focusBinding?.relatedFeatures ?? []));
const focusRuleIds = computed(() => new Set(props.focusBinding?.relatedRules ?? []));
const focusAcceptanceIds = computed(() => new Set(props.focusBinding?.relatedAcceptances ?? []));

const focusedFeatures = computed(() => {
  if (!props.graph) return [];
  return props.graph.features.filter(featureInFocus);
});

const focusedRules = computed(() => {
  if (!props.graph) return [];
  return props.graph.rules.filter((rule) => {
    if (!hasFocus.value) return true;
    return focusRuleIds.value.has(rule.ruleId) || focusFeatureIds.value.has(rule.featureId);
  });
});

const focusedAcceptances = computed(() => {
  if (!props.graph) return [];
  return props.graph.acceptances.filter((acceptance) => {
    if (!hasFocus.value) return true;
    return focusAcceptanceIds.value.has(acceptance.acId) || focusFeatureIds.value.has(acceptance.featureId);
  });
});

const taskGroups = computed<ScreenTaskGroup[]>(() => {
  if (!props.graph) return [];
  const assigned = new Set<string>();
  const groups: ScreenTaskGroup[] = props.graph.screens.map((screen) => {
    const features = props.graph!.features.filter((feature) => {
      const matched = screen.relatedFeatures.includes(feature.featureId)
        || feature.relatedScreensText.includes(screen.screenId)
        || (!!screen.title && feature.relatedScreensText.includes(screen.title))
        || (!!screen.frameName && feature.relatedScreensText.includes(screen.frameName));
      if (matched) assigned.add(feature.featureId);
      return matched && featureInFocus(feature);
    });
    return {
      key: screen.screenId,
      title: `${screen.screenId} ${screen.title || screen.frameName}`,
      screen,
      features,
      components: props.graph!.components.filter((component) => (
        component.screenId === screen.screenId
        && (!props.focusBinding?.componentId || component.componentId === props.focusBinding.componentId)
      ))
    };
  }).filter((group) => !hasFocus.value || group.screen?.screenId === props.focusBinding?.screenId || group.features.length);

  const unassigned = props.graph.features.filter((feature) => !assigned.has(feature.featureId) && featureInFocus(feature));
  if (unassigned.length && !props.focusBinding?.screenId) {
    groups.push({
      key: 'unassigned',
      title: '未分配页面任务',
      screen: null,
      features: unassigned,
      components: []
    });
  }
  return groups;
});

const summaryItems = computed(() => {
  if (!props.graph) return [];
  return [
    { key: 'screens' as const, label: '页面', count: hasFocus.value && props.focusBinding?.screenId ? 1 : props.graph.screens.length },
    { key: 'features' as const, label: '任务', count: focusedFeatures.value.length },
    { key: 'rules' as const, label: '规则', count: focusedRules.value.length },
    { key: 'acceptances' as const, label: '验收', count: focusedAcceptances.value.length },
    { key: 'annotations' as const, label: '标注', count: props.focusBinding ? focusedFeatures.value.length : 0 }
  ];
});

const featureList = computed(() => focusedFeatures.value);
const ruleGroups = computed(() => groupByFeatureId(focusedRules.value, (rule) => rule.featureId));
const acceptanceGroups = computed(() => groupByFeatureId(focusedAcceptances.value, (acceptance) => acceptance.featureId));

watch(() => props.focusBinding?.bindingId, (bindingId) => {
  if (bindingId) {
    overviewMode.value = 'annotations';
  }
});

function rulesFor(feature: FeatureNodeDto): RuleNodeDto[] {
  return focusedRules.value.filter((rule) => rule.featureId === feature.featureId);
}

function acceptancesFor(feature: FeatureNodeDto): AcceptanceNodeDto[] {
  return focusedAcceptances.value.filter((acceptance) => acceptance.featureId === feature.featureId);
}

function componentsFor(group: ScreenTaskGroup, feature: FeatureNodeDto): ComponentNodeDto[] {
  return group.components.filter((component) => component.relatedFeatures.includes(feature.featureId));
}

function selectScreen(group: ScreenTaskGroup) {
  if (group.screen) {
    emit('select-screen', group.screen.screenId);
  }
}

function featureName(featureId: string) {
  const feature = props.graph?.features.find((item) => item.featureId === featureId);
  return feature ? `${feature.featureId} ${feature.name}` : featureId || '未关联任务';
}

function focusLabel() {
  if (!props.focusBinding) return '';
  return props.focusBinding.componentId
    ? `${props.focusBinding.bindingId} · ${props.focusBinding.screenId}/${props.focusBinding.componentId}`
    : `${props.focusBinding.bindingId} · ${props.focusBinding.screenId}`;
}

function featureInFocus(feature: FeatureNodeDto) {
  if (!props.focusBinding) return true;
  if (focusFeatureIds.value.size) return focusFeatureIds.value.has(feature.featureId);
  return feature.relatedScreensText.includes(props.focusBinding.screenId);
}

function groupByFeatureId<T>(items: T[], featureIdOf: (item: T) => string) {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const featureId = featureIdOf(item) || 'unassigned';
    groups.set(featureId, [...(groups.get(featureId) ?? []), item]);
  }
  return [...groups.entries()].map(([featureId, entries]) => ({ featureId, entries }));
}
</script>

<template>
  <aside class="inspector task-overview-panel" :class="{ collapsed: props.collapsed }">
    <header class="inspector-header">
      <div v-if="!props.collapsed">
        <p class="eyebrow">任务总览</p>
        <h2>所有 SCR / FEAT</h2>
      </div>
      <button class="ghost-icon" :title="props.collapsed ? '展开右侧栏' : '收起右侧栏'" @click="emit('toggle-collapse')">
        <component :is="props.collapsed ? PanelRightOpen : PanelRightClose" :size="18" />
      </button>
    </header>

    <template v-if="props.collapsed">
      <div class="collapsed-label">Tasks</div>
    </template>

    <div v-else-if="!props.graph" class="empty-state inspector-empty">
      <ListChecks :size="24" />
      <p>选择 SPI 实例后，这里会按页面展示 product-spec 中解析出的全部功能任务。</p>
    </div>

    <template v-else>
      <div v-if="props.focusBinding" class="task-focus-banner">
        <strong>已定位标注</strong>
        <span>{{ focusLabel() }}</span>
      </div>

      <section class="task-overview-summary">
        <button
          v-for="item in summaryItems"
          :key="item.key"
          :class="{ selected: overviewMode === item.key }"
          @click="overviewMode = item.key"
        >
          <strong>{{ item.count }}</strong>
          <span>{{ item.label }}</span>
        </button>
      </section>

      <div v-if="overviewMode === 'screens'" class="task-group-list">
        <details v-for="(group, groupIndex) in taskGroups" :key="group.key" class="task-screen-group" :open="groupIndex === 0">
          <summary @click="selectScreen(group)">
            <span class="task-screen-link">{{ group.title }}</span>
            <small>{{ group.features.length }} tasks</small>
          </summary>

          <div v-if="group.components.length" class="task-component-strip">
            <button
              v-for="component in group.components.slice(0, 8)"
              :key="`${component.screenId}-${component.componentId}`"
              @click="emit('select-component', component)"
            >
              {{ component.componentId }}
            </button>
            <span v-if="group.components.length > 8">+{{ group.components.length - 8 }}</span>
          </div>

          <p v-if="!group.features.length" class="muted task-empty">这个页面暂时没有解析到直接关联任务。</p>

          <details
            v-for="(feature, featureIndex) in group.features"
            :key="feature.featureId"
            class="task-feature-card"
            :open="groupIndex === 0 && featureIndex === 0"
          >
            <summary>
              <span>{{ feature.featureId }} {{ feature.name }}</span>
              <small>{{ feature.relatedScreensText || '未解析页面关系' }}</small>
            </summary>

            <div class="task-feature-body">
              <p v-if="feature.userStory" class="task-user-story">{{ feature.userStory }}</p>
              <div v-if="feature.goals.length" class="task-goals">
                <strong>目标</strong>
                <p v-for="goal in feature.goals" :key="goal">{{ goal }}</p>
              </div>

              <FeatureFlowDiagram
                :main-flow="feature.mainFlow"
                :branch-flows="feature.branchFlows"
                :exception-flows="feature.exceptionFlows"
              />

              <div v-if="componentsFor(group, feature).length" class="task-linked-row">
                <strong>组件</strong>
                <button
                  v-for="component in componentsFor(group, feature)"
                  :key="`${component.screenId}-${component.componentId}`"
                  @click="emit('select-component', component)"
                >
                  {{ component.componentId }}
                </button>
              </div>

              <div v-if="rulesFor(feature).length" class="task-linked-list">
                <strong>业务规则</strong>
                <p v-for="rule in rulesFor(feature)" :key="rule.ruleId">
                  <span>{{ rule.ruleId }}</span>{{ rule.rule }}
                </p>
              </div>

              <div v-if="acceptancesFor(feature).length" class="task-linked-list">
                <strong>验收标准</strong>
                <p v-for="ac in acceptancesFor(feature)" :key="ac.acId">
                  <span>{{ ac.acId }}</span>{{ ac.thenText || ac.point }}
                </p>
              </div>
            </div>
          </details>
        </details>
      </div>

      <div v-else-if="overviewMode === 'features'" class="task-group-list">
        <details v-for="(feature, index) in featureList" :key="feature.featureId" class="task-feature-card overview-card" :open="index === 0">
          <summary>
            <span>{{ feature.featureId }} {{ feature.name }}</span>
            <small>{{ feature.relatedScreensText || '未解析页面关系' }}</small>
          </summary>
          <div class="task-feature-body">
            <p v-if="feature.userStory" class="task-user-story">{{ feature.userStory }}</p>
            <div v-if="feature.goals.length" class="task-goals">
              <strong>目标</strong>
              <p v-for="goal in feature.goals" :key="goal">{{ goal }}</p>
            </div>
            <FeatureFlowDiagram
              :main-flow="feature.mainFlow"
              :branch-flows="feature.branchFlows"
              :exception-flows="feature.exceptionFlows"
            />
            <div v-if="rulesFor(feature).length" class="task-linked-list">
              <strong>业务规则</strong>
              <p v-for="rule in rulesFor(feature)" :key="rule.ruleId">
                <span>{{ rule.ruleId }}</span>{{ rule.rule }}
              </p>
            </div>
            <div v-if="acceptancesFor(feature).length" class="task-linked-list">
              <strong>验收标准</strong>
              <p v-for="ac in acceptancesFor(feature)" :key="ac.acId">
                <span>{{ ac.acId }}</span>{{ ac.thenText || ac.point }}
              </p>
            </div>
          </div>
        </details>
      </div>

      <div v-else-if="overviewMode === 'rules'" class="task-group-list">
        <details v-for="(group, index) in ruleGroups" :key="group.featureId" class="task-screen-group" :open="index === 0">
          <summary>
            <span class="task-screen-link">{{ featureName(group.featureId) }}</span>
            <small>{{ group.entries.length }} rules</small>
          </summary>
          <div class="overview-record-list">
            <article v-for="rule in group.entries" :key="rule.ruleId" class="overview-record">
              <strong>{{ rule.ruleId }} · {{ rule.type || '规则' }}</strong>
              <dl>
                <dt>触发</dt>
                <dd>{{ rule.trigger || '未解析' }}</dd>
                <dt>规则</dt>
                <dd>{{ rule.rule || '未解析' }}</dd>
                <dt>结果</dt>
                <dd>{{ rule.result || '未解析' }}</dd>
                <dt>测试关注</dt>
                <dd>{{ rule.testFocus || '未解析' }}</dd>
              </dl>
            </article>
          </div>
        </details>
      </div>

      <div v-else-if="overviewMode === 'acceptances'" class="task-group-list">
        <details v-for="(group, index) in acceptanceGroups" :key="group.featureId" class="task-screen-group" :open="index === 0">
          <summary>
            <span class="task-screen-link">{{ featureName(group.featureId) }}</span>
            <small>{{ group.entries.length }} AC</small>
          </summary>
          <div class="overview-record-list">
            <article v-for="ac in group.entries" :key="ac.acId" class="overview-record">
              <strong>{{ ac.acId }} · {{ ac.point || ac.type || '验收标准' }}</strong>
              <dl>
                <dt>Given</dt>
                <dd>{{ ac.given || '未解析' }}</dd>
                <dt>When</dt>
                <dd>{{ ac.when || '未解析' }}</dd>
                <dt>Then</dt>
                <dd>{{ ac.thenText || '未解析' }}</dd>
              </dl>
            </article>
          </div>
        </details>
      </div>

      <div v-else class="task-group-list">
        <template v-if="props.focusBinding">
          <div class="annotation-task-focus">
            <strong>{{ props.focusBinding.bindingId }} · {{ props.focusBinding.name || '未命名标注' }}</strong>
            <span>{{ props.focusBinding.screenId }}{{ props.focusBinding.componentId ? ` / ${props.focusBinding.componentId}` : ' / 页面级标注' }}</span>
          </div>

          <details
            v-for="(feature, index) in focusedFeatures"
            :key="feature.featureId"
            class="task-feature-card overview-card"
            :open="index === 0"
          >
            <summary>
              <span>{{ feature.featureId }} {{ feature.name }}</span>
              <small>{{ feature.relatedScreensText || '未解析页面关系' }}</small>
            </summary>
            <div class="task-feature-body">
              <p v-if="feature.userStory" class="task-user-story">{{ feature.userStory }}</p>
              <div v-if="feature.goals.length" class="task-goals">
                <strong>目标</strong>
                <p v-for="goal in feature.goals" :key="goal">{{ goal }}</p>
              </div>
              <FeatureFlowDiagram
                :main-flow="feature.mainFlow"
                :branch-flows="feature.branchFlows"
                :exception-flows="feature.exceptionFlows"
              />
              <div v-if="rulesFor(feature).length" class="task-linked-list">
                <strong>业务规则</strong>
                <p v-for="rule in rulesFor(feature)" :key="rule.ruleId">
                  <span>{{ rule.ruleId }}</span>{{ rule.rule }}
                </p>
              </div>
              <div v-if="acceptancesFor(feature).length" class="task-linked-list">
                <strong>验收标准</strong>
                <p v-for="ac in acceptancesFor(feature)" :key="ac.acId">
                  <span>{{ ac.acId }}</span>{{ ac.thenText || ac.point }}
                </p>
              </div>
            </div>
          </details>

          <p v-if="!focusedFeatures.length" class="muted task-empty">这个 ANN 暂时没有关联任务。</p>
        </template>

        <p v-else class="muted task-empty">选择一个已保存 ANN 后，这里会显示标注关系。</p>
      </div>
    </template>
  </aside>
</template>
