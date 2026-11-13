<template>
  <!-- 课程空间里「还没有正文组件」的页签落点：只给明确的待扩展说明，不给任何示例读数。
       原先这里挂的是一个「示例块 + 空态」两态面板；示例块那一半在真实组件接管后
       已不可达（batch 24 死示例层清算），只保留空态这一半。 -->
  <div class="card empty-state">
    <span class="e-ico"><Icon name="layers" /></span>
    <b>该页签暂不提供数据</b>
    <p>{{ note || '本页签暂无可展示内容。' }}</p>
  </div>
</template>

<script setup lang="ts">
import Icon from '../../../components/Icon.vue'

defineProps<{ note?: string }>()
</script>
