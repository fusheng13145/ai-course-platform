<template>
  <Modal
   
    :model-value="modelValue"
    :title="isEdit ? '编辑题目' : '新建题目'"
    width="720px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" scroll-to-error>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="题型" prop="type">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="判断题" value="TRUE_FALSE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="SHORT_ANSWER" />
              <el-option label="编程题" value="CODING" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="form.difficulty" style="width: 100%">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="所属课程" prop="courseId">
        <el-select
          v-model="form.courseId"
          filterable
          clearable
          placeholder="选择课程（可选）"
          style="width: 100%"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="题干" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题干内容" />
      </el-form-item>
      <el-form-item label="参考答案" prop="answer">
        <el-input v-model="form.answer" type="textarea" :rows="2" placeholder="请输入参考答案" />
      </el-form-item>
      <el-form-item label="解析">
        <el-input
          v-model="form.explanation"
          type="textarea"
          :rows="2"
          placeholder="可选：题目解析"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('save')">{{
        isEdit ? '保存修改' : '创建题目'
      }}</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 题库管理 — 新建/编辑题目对话框
 * <p>题型/难度/课程/题干/答案/解析表单；父组件经 ref 调用 validate() 做提交前校验。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 是否编辑态 */
  isEdit: { type: Boolean, default: false },
  /** 表单对象（共享引用） */
  form: { type: Object, required: true },
  /** 校验规则 */
  rules: { type: Object, required: true },
  /** 课程列表 */
  courseList: { type: Array, required: true },
  /** 保存加载态 */
  submitting: { type: Boolean, default: false },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）


defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)

/** 暴露表单校验给父组件（提交前调用） */
defineExpose({
  validate: () => formRef.value?.validate(),
})
</script>
