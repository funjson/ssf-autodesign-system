<script setup lang="ts">
import { computed } from 'vue';
import { BoxSelect, Image, Layers3, MousePointer2 } from '@lucide/vue';
import type { ComponentNodeDto, SpecGraphDto } from '../types/models';

const props = defineProps<{
  graph: SpecGraphDto | null;
  activeScreenId: string;
  selectedComponentKey: string;
  hoveredComponentKey: string;
  previewMode: 'current' | 'architecture' | 'generated' | 'annotation' | 'resources' | 'prototype' | 'html-prototype';
}>();

const emit = defineEmits<{
  (event: 'select-screen', screenId: string): void;
  (event: 'select-component', component: ComponentNodeDto): void;
  (event: 'hover-component', component: ComponentNodeDto | null): void;
}>();

const activeScreen = computed(() => props.graph?.screens.find((screen) => screen.screenId === props.activeScreenId) ?? props.graph?.screens[0] ?? null);
const activeFrame = computed(() => {
  if (!props.graph || !activeScreen.value) return null;
  return props.graph.prototypeSource.frames.find((frame) => frame.screenId === activeScreen.value?.screenId) ?? null;
});
const useUserPrototype = computed(() => {
  if (!props.graph?.prototypeSource.userProvided) return false;
  return props.previewMode === 'current' || props.previewMode === 'prototype';
});
const sourceLabel = computed(() => {
  if (!props.graph) return '';
  return useUserPrototype.value ? props.graph.prototypeSource.label : '需求预览';
});
const sourceDescription = computed(() => {
  if (!props.graph) return '';
  if (useUserPrototype.value) return props.graph.prototypeSource.description;
  return '当前由产品设计文件和 prototype-input 的 SCR/CMP 契约合成需求视图，用于核对页面、组件和需求关系；这是只读预览，不代表真实 Figma 视觉稿。';
});
const components = computed(() => {
  // MVP renders a synthetic prototype from contracts; a later layer-map can replace only this view.
  if (!props.graph || !activeScreen.value) return [];
  return props.graph.components.filter((component) => component.screenId === activeScreen.value?.screenId);
});

function keyOf(component: ComponentNodeDto) {
  return `${component.screenId}::${component.componentId}`;
}
</script>

<template>
  <section class="canvas-panel">
    <div v-if="!props.graph" class="empty-state main-empty">
      <BoxSelect :size="32" />
      <h2>选择一个 SPI 实例</h2>
      <p>你可以先打开演示项目，也可以创建自己的项目并导入 ssf-workspace。</p>
    </div>

    <template v-else>
      <nav class="screen-tabs" aria-label="screens">
        <button
          v-for="screen in props.graph.screens"
          :key="screen.screenId"
          :class="{ selected: screen.screenId === activeScreen?.screenId }"
          @click="emit('select-screen', screen.screenId)"
        >
          <span>{{ screen.screenId }}</span>
          <strong>{{ screen.title || screen.frameName }}</strong>
        </button>
      </nav>

      <div
        class="prototype-source-banner"
        :class="{ user: useUserPrototype, generated: !useUserPrototype }"
      >
        <component :is="useUserPrototype ? Image : Layers3" :size="18" />
        <div>
          <strong>{{ sourceLabel }}</strong>
          <span>{{ sourceDescription }}</span>
        </div>
      </div>

      <div class="canvas-stage">
        <div class="phone-frame">
          <header class="phone-header">
            <div>
              <span class="screen-id">{{ activeScreen?.screenId }}</span>
              <h2>{{ activeScreen?.title || activeScreen?.frameName }}</h2>
            </div>
            <MousePointer2 :size="18" />
          </header>

          <div class="screen-goal">
            {{ activeScreen?.goal || '未解析到页面目标' }}
          </div>

          <div v-if="useUserPrototype && activeFrame?.exists" class="user-prototype-frame">
            <img :src="activeFrame.assetUrl" :alt="`${activeFrame.screenId} prototype frame`" />
          </div>

          <div
            class="prototype-blocks"
            :class="{ 'hotspot-list': useUserPrototype && activeFrame?.exists }"
          >
            <p v-if="useUserPrototype && activeFrame?.exists" class="hotspot-list-title">
              当前未接入 layer-map，先用下列组件作为可点击需求块。
            </p>
            <button
              v-for="component in components"
              :key="keyOf(component)"
              class="prototype-block"
              :class="{
                selected: props.selectedComponentKey === keyOf(component),
                hovered: props.hoveredComponentKey === keyOf(component)
              }"
              @click="emit('select-component', component)"
              @mouseenter="emit('hover-component', component)"
              @mouseleave="emit('hover-component', null)"
            >
              <span class="component-id">{{ component.componentId }}</span>
              <strong>{{ component.visibleText || component.name }}</strong>
              <small>{{ component.interaction || '展示组件' }}</small>
              <span v-if="component.relatedFeatures.length" class="feature-line">
                {{ component.relatedFeatures.join(' / ') }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
