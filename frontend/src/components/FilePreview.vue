<script setup lang="ts">
import { computed } from 'vue';
import { FileCode2, FileQuestion, FileText, TriangleAlert } from '@lucide/vue';
import MarkdownIt from 'markdown-it';
import type { FileContentDto } from '../types/models';

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true
});

const props = defineProps<{
  file: FileContentDto | null;
  selectedPath: string;
}>();

const typeLabel = computed(() => {
  if (!props.file) return '';
  const labels: Record<string, string> = {
    markdown: 'Markdown',
    html: 'HTML',
    json: 'JSON',
    css: 'CSS',
    code: 'Code',
    text: 'Text',
    binary: 'Binary'
  };
  return labels[props.file.contentType] ?? props.file.contentType;
});

const showHtmlFrame = computed(() => props.file?.previewable && !props.file.tooLarge && props.file.contentType === 'html');
const showMarkdown = computed(() => props.file?.previewable && !props.file.tooLarge && props.file.contentType === 'markdown');
const renderedMarkdown = computed(() => {
  if (!showMarkdown.value || !props.file) return '';
  return markdown.render(props.file.content);
});
</script>

<template>
  <section class="file-preview-panel">
    <div v-if="!props.selectedPath" class="empty-state main-empty">
      <FileText :size="32" />
      <h2>选择一个文件</h2>
      <p>在左侧资源管理树中选择 txt、Markdown、HTML、JSON 等文件后，这里会显示内容预览。</p>
    </div>

    <template v-else-if="props.file">
      <header class="file-preview-header">
        <div>
          <p class="eyebrow">{{ typeLabel }}</p>
          <h2>{{ props.file.name }}</h2>
          <span>{{ props.file.relativePath }}</span>
        </div>
        <FileCode2 :size="22" />
      </header>

      <div v-if="!props.file.previewable" class="file-preview-message">
        <FileQuestion :size="24" />
        <strong>当前文件暂不支持预览</strong>
        <p>第一版先支持文本类文件，二进制或大型资源可以继续通过本地目录查看。</p>
      </div>

      <div v-else-if="props.file.tooLarge" class="file-preview-message">
        <TriangleAlert :size="24" />
        <strong>文件过大，已停止加载正文</strong>
        <p>为避免浏览器卡顿，当前文本预览限制在 512KB 以内。</p>
      </div>

      <div v-else-if="showHtmlFrame" class="html-preview-layout">
        <!-- Keep local HTML isolated from the app while still letting the user inspect generated prototype exports. -->
        <iframe class="html-preview-frame" sandbox="" :srcdoc="props.file.content"></iframe>
        <details class="source-details">
          <summary>查看 HTML 源码</summary>
          <pre>{{ props.file.content }}</pre>
        </details>
      </div>

      <div v-else-if="showMarkdown" class="markdown-preview-layout">
        <article class="markdown-preview" v-html="renderedMarkdown"></article>
        <details class="source-details">
          <summary>查看 Markdown 源码</summary>
          <pre>{{ props.file.content }}</pre>
        </details>
      </div>

      <pre v-else class="text-preview">{{ props.file.content }}</pre>
    </template>

    <div v-else class="empty-state main-empty">
      <FileText :size="32" />
      <h2>正在读取文件</h2>
      <p>如果文件较大，可能需要稍等一下。</p>
    </div>
  </section>
</template>
