<template>
  <header class="page-head">
    <div class="page-head-main">
      <h1>{{ title }}</h1>
      <p class="page-sub">{{ sub }}</p>
    </div>
    <div class="page-head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 页头只保留「中文大标题 + 副标题 + 动作区」。
 * 原来的英文 kicker（如 STUDENT / HOME 这类）与「接口来源章」已按展示层清理要求移除；
 * 页样式类 .page-kicker 仍留在全局 CSS 里，供以后需要时复用，不在此删除。
 */
withDefaults(defineProps<{ title: string; sub?: string }>(), { sub: '' })
</script>
