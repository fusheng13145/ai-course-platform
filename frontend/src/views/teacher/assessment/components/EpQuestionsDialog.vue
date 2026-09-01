<template>
  <!-- 查看题目对话框 -->
  <el-dialog
    :model-value="modelValue"
    title="试卷题目"
    width="720px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="currentExam">
      <div class="dialog-head">
        <span class="dialog-title">{{ currentExam.title }}</span>
        <el-tag size="small" :type="getStatusType(currentExam.status)" effect="light">{{
          getStatusText(currentExam.status)
        }}</el-tag>
      </div>
      <el-table :data="examQuestions" size="small" border max-height="380">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="题目" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{
            row.content || row.stem || row.title || '—'
          }}</template>
        </el-table-column>
        <el-table-column label="题型" width="90" align="center">
          <template #default="{ row }"
            ><el-tag size="small" type="info">{{
              getQuestionTypeName(row.type)
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="score" label="分值" width="80" align="center" />
      </el-table>
      <el-empty v-if="!examQuestions.length" description="暂无题目" />
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps({
  modelValue: { type: Boolean, default: false },
  currentExam: { type: Object, default: null },
  examQuestions: { type: Array, default: () => [] },
})
defineEmits(['update:modelValue'])

function getStatusType(s) {
  const m = { DRAFT: 'info', PUBLISHED: 'success', ENDED: 'danger' }
  return m[s] || 'info'
}
function getStatusText(s) {
  const m = { DRAFT: '草稿', PUBLISHED: '已发布', ENDED: '已结束' }
  return m[s] || s || '草稿'
}
function getQuestionTypeName(t) {
  const m = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
    ESSAY: '简答',
    CODING: '编程',
  }
  return m[t] || t || '未知'
}
</script>

<style scoped>
.dialog-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
</style>
