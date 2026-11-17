<template>
  <div class="admin-readonly">
    <div class="callout" role="status">
      <span>只读</span>
      <p>{{ copy }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ tab: string }>()

/* 一行说清「这里为什么没有数据、该找谁」；不再补第二段同义解释 */
const COPY: Record<string, string> = {
  content: '管理员只读，无数据面：课程内容与资源由教师在课程内维护。',
  bank: '管理员只读，无数据面：题库维护在「题库管理」全局页完成。',
  exams: '管理员只读，无数据面：考试治理在「考试治理」全局页完成。',
  grades: '管理员只读，无数据面：成绩改分只发生在教师端与批阅流。',
  graph: '管理员只读，无数据面：知识图谱由教师端构建与维护。',
  aiUsage: '管理员只读，无数据面：跨课程 AI 用量不在本页聚合。',
}

const copy = computed(
  () => COPY[props.tab] || '管理员只读，无数据面：该页签没有管理员可用的接口读数。',
)
</script>
