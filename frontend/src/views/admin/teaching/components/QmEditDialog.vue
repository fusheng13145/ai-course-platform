<template>
  <Modal
    :model-value="modelValue"
    :title="isEditing ? '编辑题目' : '添加题目'"
    width="720px"
    @update:model-value="emit('update:model-value', $event)"
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
        <el-select v-model="form.courseId" filterable style="width: 100%" placeholder="选择课程">
          <el-option
            v-for="course in courseList"
            :key="course.id"
            :label="course.title || course.name"
            :value="course.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="题目内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="3"
          placeholder="请输入题目内容（支持Markdown格式）"
        />
      </el-form-item>

      <!-- 选择题选项 -->
      <template v-if="isChoiceType(form.type)">
        <el-form-item label="选项设置">
          <div class="options-editor">
            <div v-for="(option, idx) in form.options" :key="idx" class="option-item">
              <span class="option-label">{{ optionLabels[idx] }}</span>
              <el-input v-model="option.content" placeholder="选项内容" />
              <el-checkbox v-model="option.isCorrect">正确答案</el-checkbox>
              <el-button
                v-if="form.options.length > 2"
                text
                type="danger"
                size="small"
                @click="emit('remove-option', idx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button
              v-if="form.options.length < 8 && form.type === 'SINGLE_CHOICE'"
              text
              type="primary"
              size="small"
              @click="emit('add-option')"
              ><el-icon><Plus /></el-icon>添加选项</el-button
            >
          </div>
        </el-form-item>
      </template>

      <!-- 判断题答案 -->
      <el-form-item v-if="form.type === 'TRUE_FALSE'" label="正确答案">
        <el-radio-group v-model="form.answer">
          <el-radio :value="true">正确</el-radio>
          <el-radio :value="false">错误</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 填空/简答/编程题答案 -->
      <el-form-item v-if="!isChoiceType(form.type) && form.type !== 'TRUE_FALSE'" label="参考答案">
        <el-input v-model="form.answer" type="textarea" :rows="2" placeholder="请输入参考答案" />
      </el-form-item>

      <el-form-item label="解析说明">
        <el-input
          v-model="form.explanation"
          type="textarea"
          :rows="2"
          placeholder="可选：输入题目解析"
        />
      </el-form-item>

      <el-form-item label="分值" prop="score">
        <el-input-number v-model="form.score" :min="1" :max="100" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:model-value', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { ref } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 创建/编辑题目对话框
 * <p>题型/难度/课程/内容/选项编排（选择题）/答案（判断与填空简答编程）/解析/分值表单。
 * 表单实例经 defineExpose 暴露 validate 供父组件触发校验（useQuestions.saveQuestion）。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  isEditing: { type: Boolean, default: false },
  form: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  courseList: {
    type: Array as PropType<{ id: number | string; name?: string; title?: string }[]>,
    default: () => [],
  },
  optionLabels: { type: Array, default: () => ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'] },
  saving: { type: Boolean, default: false },
  isChoiceType: { type: Function, required: true },
})

const emit = defineEmits(['update:model-value', 'save', 'add-option', 'remove-option'])

const formRef = ref(null)

defineExpose({
  validate: () => formRef.value?.validate(),
})
</script>

<style scoped>
.options-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.option-label {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.option-item .el-input {
  flex: 1;
}
</style>
