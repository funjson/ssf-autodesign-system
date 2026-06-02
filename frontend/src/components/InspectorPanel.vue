<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { FilePlus2, PanelRightClose, PanelRightOpen, Sparkles } from '@lucide/vue';
import FeatureFlowDiagram from './FeatureFlowDiagram.vue';
import type {
  AcceptanceNodeDto,
  AnnotationNodeDto,
  ChangeRequestDto,
  ComponentNodeDto,
  FeatureNodeDto,
  RuleNodeDto,
  ScreenNodeDto,
  SpecGraphDto,
  SpiInstanceDto,
  TableDto
} from '../types/models';

const props = defineProps<{
  instance: SpiInstanceDto | null;
  graph: SpecGraphDto | null;
  screen: ScreenNodeDto | null;
  component: ComponentNodeDto | null;
  annotations: AnnotationNodeDto[];
  features: FeatureNodeDto[];
  rules: RuleNodeDto[];
  acceptances: AcceptanceNodeDto[];
  createdChangeRequest: ChangeRequestDto | null;
  readOnly: boolean;
  collapsed: boolean;
  readOnlyReason: string;
}>();

const emit = defineEmits<{
  (event: 'create-change-request', payload: { title?: string; screenId?: string; componentId?: string; userIntent: string; extraNotes?: string }): void;
  (event: 'toggle-collapse'): void;
}>();

const title = ref('');
const userIntent = ref('');
const extraNotes = ref('');

const targetLabel = computed(() => {
  if (props.component) return `${props.component.screenId} / ${props.component.componentId}`;
  if (props.screen) return props.screen.screenId;
  return '未选择需求块';
});

watch(() => [props.screen?.screenId, props.component?.componentId, props.readOnly], () => {
  title.value = '';
  userIntent.value = '';
  extraNotes.value = '';
});

function submit() {
  if (props.readOnly || !userIntent.value.trim()) return;
  emit('create-change-request', {
    title: title.value.trim() || undefined,
    screenId: props.screen?.screenId,
    componentId: props.component?.componentId,
    userIntent: userIntent.value.trim(),
    extraNotes: extraNotes.value.trim() || undefined
  });
}

function tableKey(table: TableDto, index: number) {
  return `${table.title}-${index}-${table.headers.join('-')}`;
}
</script>

<template>
  <aside class="inspector" :class="{ collapsed: props.collapsed }">
    <header class="inspector-header">
      <div v-if="!props.collapsed">
        <p class="eyebrow">上下文</p>
        <h2>{{ targetLabel }}</h2>
      </div>
      <button class="ghost-icon" :title="props.collapsed ? '展开右侧栏' : '收起右侧栏'" @click="emit('toggle-collapse')">
        <component :is="props.collapsed ? PanelRightOpen : PanelRightClose" :size="18" />
      </button>
    </header>

    <template v-if="props.collapsed">
      <div class="collapsed-label">Context</div>
    </template>

    <div v-else-if="!props.graph" class="empty-state inspector-empty">
      <Sparkles :size="24" />
      <p>选择 SPI 实例后，这里会显示页面、组件、功能、规则、验收标准和原型标注。</p>
    </div>

    <template v-else>
      <div v-if="props.readOnly" class="readonly-note">
        {{ props.readOnlyReason }}
      </div>

      <section class="context-card">
        <h3>页面</h3>
        <dl v-if="props.screen">
          <dt>Frame</dt>
          <dd>{{ props.screen.frameName || props.screen.screenId }}</dd>
          <dt>目标</dt>
          <dd>{{ props.screen.goal || '未解析到目标' }}</dd>
          <dt>入口</dt>
          <dd>{{ props.screen.entry || '未解析' }}</dd>
          <dt>出口</dt>
          <dd>{{ props.screen.exit || '未解析' }}</dd>
        </dl>
      </section>

      <section v-if="props.component" class="context-card selected-card">
        <h3>组件</h3>
        <dl>
          <dt>Layer</dt>
          <dd>{{ props.component.name || props.component.componentId }}</dd>
          <dt>文案</dt>
          <dd>{{ props.component.visibleText || '无' }}</dd>
          <dt>交互</dt>
          <dd>{{ props.component.interaction || '无' }}</dd>
          <dt>关联</dt>
          <dd>{{ props.component.relationText || '无' }}</dd>
        </dl>
      </section>

      <section class="context-card">
        <h3>功能任务</h3>
        <div v-if="props.features.length" class="feature-detail-list">
          <details v-for="(feature, index) in props.features" :key="feature.featureId" class="feature-detail" :open="index === 0">
            <summary>
              <span>{{ feature.featureId }} {{ feature.name }}</span>
              <small>{{ feature.relatedScreensText }}</small>
            </summary>

            <div class="feature-goals">
              <strong>功能目标</strong>
              <p v-for="goal in feature.goals" :key="goal">{{ goal }}</p>
              <p v-if="!feature.goals.length" class="muted">未解析到功能目标。</p>
            </div>

            <FeatureFlowDiagram
              :main-flow="feature.mainFlow"
              :branch-flows="feature.branchFlows"
              :exception-flows="feature.exceptionFlows"
            />

            <div v-for="section in feature.sections" :key="`${feature.featureId}-${section.sectionNo}`" class="feature-section">
              <h4>{{ section.sectionNo }} {{ section.title }}</h4>
              <p v-if="section.content" class="section-content">{{ section.content }}</p>
              <ul v-if="section.bullets.length" class="section-bullets">
                <li v-for="bullet in section.bullets" :key="bullet">{{ bullet }}</li>
              </ul>
              <div v-for="(table, tableIndex) in section.tables" :key="tableKey(table, tableIndex)" class="spec-table-wrap">
                <strong v-if="table.title">{{ table.title }}</strong>
                <table class="spec-table">
                  <thead>
                    <tr>
                      <th v-for="header in table.headers" :key="header">{{ header }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in table.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </details>
        </div>
        <p v-else class="muted">暂无直接关联 FEAT。</p>
      </section>

      <section class="context-card">
        <h3>业务规则</h3>
        <div v-if="props.rules.length" class="stack-list compact">
          <article v-for="rule in props.rules" :key="rule.ruleId" class="mini-item">
            <strong>{{ rule.ruleId }} · {{ rule.type }}</strong>
            <p>{{ rule.rule }}</p>
            <small>{{ rule.testFocus }}</small>
          </article>
        </div>
        <p v-else class="muted">暂无直接关联 BR。</p>
      </section>

      <section class="context-card">
        <h3>验收标准</h3>
        <div v-if="props.acceptances.length" class="stack-list compact">
          <article v-for="ac in props.acceptances" :key="ac.acId" class="mini-item">
            <strong>{{ ac.acId }} · {{ ac.point }}</strong>
            <p>{{ ac.thenText }}</p>
          </article>
        </div>
        <p v-else class="muted">暂无直接关联 AC。</p>
      </section>

      <section class="context-card">
        <h3>原型标注</h3>
        <div v-if="props.annotations.length" class="stack-list compact">
          <article v-for="annotation in props.annotations" :key="`${annotation.pannoId}-${annotation.componentId ?? 'page'}`" class="mini-item">
            <strong>{{ annotation.pannoId }} {{ annotation.layerName }}</strong>
            <p>{{ annotation.note }}</p>
          </article>
        </div>
        <p v-else class="muted">暂无直接关联 PANN。</p>
      </section>

      <section v-if="!props.readOnly" class="change-box">
        <div class="change-title">
          <FilePlus2 :size="18" />
          <h3>生成 Change Request</h3>
        </div>
        <input v-model="title" placeholder="标题，可选" />
        <textarea v-model="userIntent" rows="5" placeholder="描述你希望如何调整这个需求块、规则或原型表达"></textarea>
        <textarea v-model="extraNotes" rows="3" placeholder="补充约束或背景，可选"></textarea>
        <button :disabled="!props.instance || !userIntent.trim()" @click="submit">生成 CR 文件</button>
        <div v-if="props.createdChangeRequest" class="success-box">
          已生成 {{ props.createdChangeRequest.changeCode }}
          <span>{{ props.createdChangeRequest.filePath }}</span>
        </div>
      </section>
    </template>
  </aside>
</template>
