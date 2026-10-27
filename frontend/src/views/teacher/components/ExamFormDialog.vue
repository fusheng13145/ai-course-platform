<template>
  <Modal
    :model-value="modelValue"
    :title="isEdit ? '编辑试卷' : '创建试卷'"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="examForm" :rules="formRules" label-width="100px" scroll-to-error>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="试卷标题" prop="title">
            <el-input
              v-model="examForm.title"
              placeholder="输入试卷标题"
              maxlength="80"
              show-word-limit
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属课程" prop="courseId">
            <el-select v-model="examForm.courseId" placeholder="选择课程" class="w-100" filterable>
              <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="说明">
        <el-input
          v-model="examForm.description"
          type="textarea"
          :rows="2"
          placeholder="试卷说明（可选）"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="考试时长" prop="duration">
            <el-input-number v-model="examForm.duration" :min="10" :max="300" :step="5" /> 分钟
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="总分" prop="totalScore">
            <el-input-number v-model="examForm.totalScore" :min="1" :max="500" :step="5" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="开始时间">
            <el-date-picker
              v-model="examForm.startTime"
              type="datetime"
              placeholder="选择开始时间"
              class="w-100"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="截止时间">
            <el-date-picker
              v-model="examForm.endTime"
              type="datetime"
              placeholder="选择截止时间"
              class="w-100"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 题目管理 -->
      <el-divider content-position="left">题目管理</el-divider>
      <div class="question-bar">
        <span class="q-bar-info"
          >已选 {{ selectedQuestions.length }} 道题，共 {{ totalSelectedScore }} 分</span
        >
        <el-button size="small" type="primary" plain @click="emit('open-picker')"
          >从题库选题</el-button
        >
      </div>
      <div v-if="selectedQuestions.length > 0" class="selected-questions">
        <div v-for="(q, idx) in selectedQuestions" :key="q.id || idx" class="question-item">
          <div class="q-index">{{ idx + 1 }}</div>
          <div class="q-content">
            <span class="q-type-tag" :class="'type-' + (q.type || 'choice')">{{
              getQuestionTypeName(q.type)
            }}</span>
            <span class="q-text">{{ q.content || q.stem || q.title || '未命名题目' }}</span>
          </div>
          <el-input-number
            v-model="q.score"
            :min="1"
            :max="50"
            size="small"
            class="q-score-input"
          />
          <el-button text type="danger" size="small" @click="emit('remove-question', idx)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
      <EmptyState v-else title="尚未添加题目，请从题库中选择" icon="list" size="compact" />

      <!-- 设置 -->
      <el-divider content-position="left">设置</el-divider>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="打乱题目"
            ><el-switch v-model="examForm.shuffleQuestions"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="打乱选项"
            ><el-switch v-model="examForm.shuffleOptions"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="显示分数"><el-switch v-model="examForm.showScore" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="最大尝试次数">
        <el-input-number v-model="examForm.maxAttempts" :min="1" :max="10" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">{{
        isEdit ? '保存修改' : '创建试卷'
      }}</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { ref } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 试卷创建/编辑对话框
 * <p>表单对象/校验/课程/已选题目经 props 注入（共享 reactive 对象编辑嵌套属性）；
 * 题库入口/移除题目/保存经事件上抛。表单校验能力经 defineExpose 暴露。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  examForm: { type: Object, required: true },
  formRules: { type: Object, default: () => ({}) },
  /** 课程下拉（id/name） */
  courseOptions: {
    type: Array as PropType<{ id: number | string; name: string }[]>,
    default: () => [],
  },
  /** 已选题目（id/type/content/stem/title/score；score 经 v-model 就地编辑） */
  selectedQuestions: {
    type: Array as PropType<
      {
        id?: number | string
        type?: string
        content?: string
        stem?: string
        title?: string
        score?: number
      }[]
    >,
    default: () => [],
  },
  totalSelectedScore: { type: Number, default: 0 },
  getQuestionTypeName: { type: Function, required: true },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

const emit = defineEmits(['update:modelValue', 'open-picker', 'remove-question', 'submit'])

const formRef = ref(null)
defineExpose({
  validate: (cb) => formRef.value?.validate(cb),
})
</script>

<style scoped>
.w-100 {
  width: 100%;
}
.question-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.q-bar-info {
  font-size: 13px;
  color: var(--ac-text-muted);
}
.selected-questions {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 8px;
}
.question-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ac-border);
  transition: background var(--ac-transition-fast);
  border-radius: 6px;
  margin: 3px 0;
}
.question-item:last-child {
  border-bottom: none;
}
.question-item:hover {
  background: var(--ac-bg-hover);
}
.q-index {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.q-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.q-type-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}
.type-SINGLE_CHOICE {
  background: var(--info-soft);
  color: var(--info);
}
.type-MULTIPLE_CHOICE {
  background: var(--violet-soft);
  color: var(--violet);
}
.type-TRUE_FALSE {
  background: var(--ok-soft);
  color: var(--ok);
}
.type-FILL_BLANK {
  background: var(--warn-soft);
  color: var(--warn);
}
.type-ESSAY {
  background: var(--accent-soft);
  color: var(--accent);
}
.q-text {
  font-size: 13px;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.q-score-input {
  width: 90px;
}
</style>
