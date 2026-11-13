<template>
  <div class="admin-readonly">
    <div class="callout" role="status">
      <span>READONLY / 管理员只读</span>
      <p>{{ copy.head }}</p>
      <p class="admin-readonly-detail">{{ copy.detail }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ tab: string }>()

const COPY: Record<string, { head: string; detail: string }> = {
  content: {
    head: '管理员只读，无数据面：课程内容与资源由教师在课程内维护。',
    detail: '本页签不展示章节、资源清单与上传记录；需要核对内容时请进入教师端课程空间。',
  },
  bank: {
    head: '管理员只读，无数据面：题库维护在「题库管理」全局页完成。',
    detail: '课程内题库视图不返回本课程的题目清单；如需按课程筛题，请使用全局题库管理的课程筛选。',
  },
  exams: {
    head: '管理员只读，无数据面：考试治理在「考试治理」全局页完成。',
    detail: '课程内考试页签不返回本课程的试卷与会话读数；强制交卷与作弊检测请在全局页操作。',
  },
  grades: {
    head: '管理员只读，无数据面：成绩改分只发生在教师端与批阅流。',
    detail: '课程内成绩页签不返回学生成绩明细；成绩方案与总评由教师维护，管理员不参与改分。',
  },
  graph: {
    head: '管理员只读，无数据面：知识图谱由教师端构建与维护。',
    detail: '课程内图谱页签不返回节点与关系读数；图谱治理请在教师端课程空间查看。',
  },
  aiUsage: {
    head: '管理员只读，无数据面：跨课程 AI 用量聚合端点尚未提供。',
    detail: '本页签不展示调用量、配额与限制读数字段；相关统计属后端待接入项。',
  },
}

const copy = computed(
  () =>
    COPY[props.tab] || {
      head: '管理员只读，无数据面：该页签没有管理员可用的接口读数。',
      detail: '不展示示例数据，也不提供管理员写操作入口。',
    },
)
</script>

<style scoped>
.admin-readonly-detail {
  margin: 6px 0 0;
  color: var(--ink-3);
  font-size: 11.5px;
}
</style>
