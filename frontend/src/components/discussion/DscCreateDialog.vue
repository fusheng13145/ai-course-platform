<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item v-if="showChapter" label="章节" prop="chapterId">
        <el-input v-model="form.chapterId" placeholder="可选，填写章节标识" style="width: 100%" />
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="话题标题" maxlength="80" show-word-limit />
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          :placeholder="contentPlaceholder"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 发起/发布话题对话框（讨论域共享实现）
 * <p>学生端（发起讨论）与教师端（发布话题）共用同一组件；角色差异以 props 表达：
 * `dialogTitle`（标题）、`showChapter`（教师端「章节」字段）、`contentPlaceholder`（占位文案）。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 话题表单（共享对象：title/content，教师端另含 chapterId） */
  form: { type: Object, default: () => ({}) },
  /** 发布加载态 */
  submitting: { type: Boolean, default: false },
  /** 弹窗标题（默认学生端「发起讨论」） */
  dialogTitle: { type: String, default: '发起讨论' },
  /** 是否显示「章节」字段（教师端为 true） */
  showChapter: { type: Boolean, default: false },
  /** 内容框占位文案 */
  contentPlaceholder: { type: String, default: '想讨论什么？' },
})

defineEmits(['update:modelValue', 'submit'])
</script>
