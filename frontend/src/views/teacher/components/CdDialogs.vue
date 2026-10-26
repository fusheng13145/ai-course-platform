<template>
  <!-- 编辑课程对话框 -->
  <Modal

    :model-value="editVisible"
    title="编辑课程信息"
    width="600px"
    append-to-body
    @update:model-value="emit('update:editVisible', $event)"
  >
    <el-form :model="editForm" label-width="100px" class="edit-form">
      <el-form-item label="课程名称"
        ><el-input v-model="editForm.name" placeholder="输入课程名称" maxlength="64"
      /></el-form-item>
      <el-form-item label="课程编码"
        ><el-input v-model="editForm.code" placeholder="可选，如 CS101" maxlength="24"
      /></el-form-item>
      <el-form-item label="分类">
        <el-select v-model="editForm.category" placeholder="选择分类" class="w-100">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="editForm.difficulty" class="w-100">
          <el-option label="入门(BEGINNER)" value="BEGINNER" />
          <el-option label="初级(EASY)" value="EASY" />
          <el-option label="中级(MEDIUM)" value="MEDIUM" />
          <el-option label="高级(HARD)" value="HARD" />
          <el-option label="专家(EXPERT)" value="EXPERT" />
        </el-select>
      </el-form-item>
      <el-form-item label="学分"
        ><el-input-number v-model="editForm.credits" :min="0" :max="10" :precision="1"
      /></el-form-item>
      <el-form-item label="报名方式">
        <el-select v-model="editForm.enrollmentType" class="w-100">
          <el-option label="开放报名" value="OPEN" />
          <el-option label="需审核" value="APPROVAL" />
          <el-option label="限制报名" value="RESTRICTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="最大人数"
        ><el-input-number v-model="editForm.maxStudents" :min="0" :step="10"
      /></el-form-item>
      <el-form-item label="课程简介">
        <el-input
          v-model="editForm.description"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:editVisible', false)">取消</el-button>
      <el-button type="primary" :loading="savingCourse" @click="emit('save-course')"
        >保存修改</el-button
      >
    </template>
  </Modal>

  <!-- 章节对话框 -->
  <Modal

    :model-value="chapterVisible"
    :title="editingChapter ? '编辑章节' : '添加章节'"
    width="520px"
    append-to-body
    @update:model-value="emit('update:chapterVisible', $event)"
  >
    <el-form :model="chapterForm" label-width="88px">
      <el-form-item label="章节名称"
        ><el-input v-model="chapterForm.title" placeholder="如：第1章 基础语法"
      /></el-form-item>
      <el-form-item label="时长(分钟)"
        ><el-input-number v-model="chapterForm.duration" :min="0"
      /></el-form-item>
      <el-form-item label="内容说明"
        ><el-input
          v-model="chapterForm.content"
          type="textarea"
          :rows="4"
          placeholder="章节概述或备注"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:chapterVisible', false)">取消</el-button>
      <el-button type="primary" :loading="savingChapter" @click="emit('save-chapter')"
        >保存</el-button
      >
    </template>
  </Modal>

  <!-- 发送通知对话框 -->
  <Modal

    :model-value="noticeVisible"
    title="发送课程通知"
    width="520px"
    append-to-body
    @update:model-value="emit('update:noticeVisible', $event)"
  >
    <el-form :model="notificationForm" label-width="76px">
      <el-form-item label="标题"
        ><el-input v-model="notificationForm.title" placeholder="通知标题" maxlength="120"
      /></el-form-item>
      <el-form-item label="内容"
        ><el-input
          v-model="notificationForm.content"
          type="textarea"
          :rows="5"
          placeholder="通知正文…"
          maxlength="2000"
          show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:noticeVisible', false)">取消</el-button>
      <el-button type="primary" :loading="sendingNotification" @click="emit('send-notice')"
        >发送</el-button
      >
    </template>
  </Modal>
</template>

<script setup lang="ts">
import Modal from '@/components/ui/Modal.vue'
/**
 * 课程详情对话框组（编辑课程 / 章节 / 发送通知）
 * <p>表单对象经 props 注入（共享 reactive 对象编辑嵌套属性），可见性 v-model 双向，
 * 提交动作经事件上抛（useCourseDetail）。</p>
 */
defineProps({
  editVisible: { type: Boolean, default: false },
  editForm: { type: Object, required: true },
  categories: { type: Array, default: () => [] },
  savingCourse: { type: Boolean, default: false },
  chapterVisible: { type: Boolean, default: false },
  editingChapter: { type: Object, default: null },
  chapterForm: { type: Object, required: true },
  savingChapter: { type: Boolean, default: false },
  noticeVisible: { type: Boolean, default: false },
  notificationForm: { type: Object, required: true },
  sendingNotification: { type: Boolean, default: false },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）


const emit = defineEmits([
  'update:editVisible',
  'update:chapterVisible',
  'update:noticeVisible',
  'save-course',
  'save-chapter',
  'send-notice',
])
</script>

<style scoped>
.edit-form {
  padding-right: 12px;
}
.w-100 {
  width: 100%;
}
</style>
