<template>
  <header class="page-head">
    <div class="page-head-main">
      <h1>{{ title }}</h1>
    </div>
    <div class="page-head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 页头只保留「中文大标题 + 动作区」。
 * 英文 kicker、「接口数据」来源章与标题下那行副标题都已按展示层清理要求移除；
 * 全局 CSS 里的 .page-kicker / .page-sub 仍保留，供以后需要时复用，不在此删除。
 */
defineProps<{ title: string }>()
</script>
