<template>
  <Modal
    :model-value="modelValue"
    title="课程详情"
    width="700px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions v-if="course" :column="2" border>
      <el-descriptions-item label="课程ID">{{ course.id }}</el-descriptions-item>
      <el-descriptions-item label="课程名称">{{ course.name }}</el-descriptions-item>
      <el-descriptions-item label="课程编码">{{ course.code || '无' }}</el-descriptions-item>
      <el-descriptions-item label="分类">{{ course.category || '未分类' }}</el-descriptions-item>
      <el-descriptions-item label="学分">{{ course.credits || 0 }}</el-descriptions-item>
      <el-descriptions-item label="难度">{{
        getDifficultyText(course.difficulty)
      }}</el-descriptions-item>
      <el-descriptions-item label="审核状态">
        <el-tag :type="getStatusType(course.approvalStatus)" size="small">{{
          getStatusText(course.approvalStatus)
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="启用状态">
        <el-tag :type="course.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{
          course.status === 'ACTIVE' ? '已启用' : '已停用'
        }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="报名方式">{{
        getEnrollmentTypeText(course.enrollmentType)
      }}</el-descriptions-item>
      <el-descriptions-item label="最大人数">{{
        course.maxStudents || '不限'
      }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ course.createdAt }}</el-descriptions-item>
      <el-descriptions-item label="课程简介" :span="2">{{
        course.description || '暂无简介'
      }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" @click="emit('manage')">管理课程</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import Modal from '@/components/ui/Modal.vue'
/**
 * 教师课程管理 — 课程详情对话框。
 * <p>拆分子组件：展示类组件，文案映射（难度/报名方式/状态）自包含。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  course: { type: Object, default: null },
})

// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

const emit = defineEmits(['update:modelValue', 'manage'])

function getStatusType(status) {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function getStatusText(status) {
  return status === 'APPROVED' ? '已通过' : status === 'REJECTED' ? '已拒绝' : '待审核'
}

function getDifficultyText(diff) {
  const map = { BEGINNER: '入门', EASY: '初级', MEDIUM: '中级', HARD: '高级', EXPERT: '专家' }
  return map[diff] || diff || '未设置'
}

function getEnrollmentTypeText(type) {
  const map = { OPEN: '开放报名', APPROVAL: '需审核', RESTRICTED: '限制报名' }
  return map[type] || type || '开放报名'
}
</script>
