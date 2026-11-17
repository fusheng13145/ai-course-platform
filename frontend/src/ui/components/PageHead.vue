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
 * 页头只有「标题 + 动作区」，一个字段，一条渲染路径。
 * 副标题、kicker、来源章都不在这里，也不在全局 CSS 里——要表达上下文就改布局，
 * 不要往标题下面塞一行解释（守卫见 __tests__/copyLaw.test.ts）。
 */
defineProps<{ title: string }>()
</script>
