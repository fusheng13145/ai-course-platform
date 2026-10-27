<template>
  <Modal
    v-model="visible"
    :width="640"
    :title="editing ? '编辑试卷' : '新建试卷'"
    @closed="onClosed"
  >
    <el-form :model="form" label-width="84px">
      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="请输入试卷标题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="请输入试卷描述"
        />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number
          v-model="form.duration"
          :min="1"
          :max="600"
          controls-position="right"
          class="w-100"
        />
      </el-form-item>
      <el-form-item label="总分">
        <el-input-number
          v-model="form.totalScore"
          :min="1"
          :max="1000"
          controls-position="right"
          class="w-100"
        />
      </el-form-item>
    </el-form>

    <!-- 选题区：题库勾选 + 已选合计与总分校验 + 自动组卷入口 -->
    <div class="picker-head">
      <div class="picker-summary">
        <span class="picker-title">试卷题目</span>
        <el-tag size="small" type="info" effect="light" round>{{
          selectedQuestions.length
        }}</el-tag>
        <span class="summary-total">
          已选合计 <b :class="{ 'total-warn': scoreMismatch }">{{ selectedTotalScore }}</b> / 总分
          {{ form.totalScore }} 分
        </span>
        <span v-if="scoreMismatch" class="mismatch-hint">选题合计与总分不一致</span>
      </div>
      <el-button
        size="small"
        :disabled="autoComposing || !courseIdNumber"
        @click="autoVisible = true"
        >自动组卷</el-button
      >
    </div>
    <CeExamQuestionPicker
      :questions="bankQuestions"
      :loading="bankLoading || restoring"
      :total="bankTotal"
      :page="bankPage"
      :type="bankType"
      :selected="selectedQuestions"
      @toggle="toggleQuestion"
      @update-points="updatePoints"
      @page-change="onBankPage"
      @type-change="onBankType"
    />

    <!-- 自动组卷条件表单（P1-1）：成功后切编辑态 + 题目回显 -->
    <CesAutoComposeModal
      v-if="autoVisible"
      :course-id="courseIdNumber"
      :default-title="form.title"
      :composing="autoComposing"
      @compose="onCompose"
      @close="autoVisible = false"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSaveClick">保存</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PropType } from 'vue'
import { ElMessage } from 'element-plus'
import Modal from '@/components/ui/Modal.vue'
import CeExamQuestionPicker from './CeExamQuestionPicker.vue'
import CesAutoComposeModal from './CesAutoComposeModal.vue'
import logger from '@/utils/logger'
import {
  useExamCompose,
  type AutoComposePayload,
  type ExamFormSavePayload,
  type ExamPaper,
} from '@/composables/useExamCompose'

/**
 * 课程试卷 — 新建/编辑对话框（本批自研 Modal 迁移：Modal 内部自接焦点，
 * 调用方无 ref + useDialogFocus，无 destroy-on-close——Modal v-if 卸载天然覆盖）。
 * <p>表单对象由页面壳传入（共享引用）；选题区挂 useExamCompose；
 * 保存动作 emit('save', payload) 交还页面壳（试卷字段 + 题目清单两段式保存）。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editing: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  /** 表单对象（页面壳持有共享引用，表单双向绑定直接生效） */
  form: {
    type: Object as PropType<{
      id: number | null
      title: string
      description: string
      duration: number
      totalScore: number
    }>,
    required: true,
  },
  /** 归属课程（题库按课程过滤 + 自动组卷建卷） */
  courseId: { type: Number, default: null },
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', payload: ExamFormSavePayload): void
  (e: 'adopted', paper: ExamPaper): void
  (e: 'closed'): void
}>()

// Modal 三态语义：undefined 与 true 都是「开」，false 是「关」
const visible = computed<boolean>({
  get: () => props.modelValue !== false,
  set: (value) => emit('update:modelValue', value),
})

const courseIdNumber = computed<number | null>(() => {
  const id = Number(props.courseId)
  return Number.isFinite(id) && id > 0 ? id : null
})

// 宿主 form 是共享 reactive 对象：桥接成 composable 需要的 Ref 形状
const formId = computed<number | null>({
  get: () => (props.form.id == null ? null : Number(props.form.id)),
  set: (value) => {
    props.form.id = value
  },
})
const formTotalScore = computed<number>(() => Number(props.form.totalScore) || 0)

const {
  bankQuestions,
  bankLoading,
  bankTotal,
  bankPage,
  bankType,
  loadBank,
  selectedQuestions,
  selectedTotalScore,
  toggleQuestion,
  updatePoints,
  restoreFromPaper,
  autoCompose,
  autoComposing,
  reset,
} = useExamCompose({
  courseId: computed<number | null>(() => courseIdNumber.value),
  form: { id: formId, totalScore: formTotalScore },
})

/** 题目是否已从后端回显（保存时据此决定是否提交题目清单批量替换） */
const questionsLoaded = ref(false)
/** 回显中（并入选题区 loading） */
const restoring = ref(false)
/** 自动组卷条件弹层 */
const autoVisible = ref(false)

/**
 * 分值合计 vs 总分校验：选题合计 ≠ 总分时阻断保存（与现有表单风格一致的
 * ElMessage.warning + return；在合计行同步给出警示文案）。
 */
const scoreMismatch = computed<boolean>(() => {
  if (selectedQuestions.value.length === 0) return false
  return Math.abs(selectedTotalScore.value - formTotalScore.value) > 1e-9
})

// 打开时：重置选题状态 → 加载题库 → 编辑态回显该卷已有题目
watch(
  () => props.modelValue,
  async (opened) => {
    if (!opened) return
    reset()
    questionsLoaded.value = false
    restoring.value = true
    try {
      await loadBank()
      if (props.editing && props.form.id != null) {
        await restoreFromPaper(Number(props.form.id))
        questionsLoaded.value = true
      }
    } catch (error) {
      logger.error('试卷题目回显失败', error)
      ElMessage.warning('试卷题目回显失败：保存时将不会改动该卷已有题目')
    } finally {
      restoring.value = false
    }
  },
)

async function onBankPage(page: number): Promise<void> {
  bankPage.value = page
  await loadBank()
}

async function onBankType(type: string): Promise<void> {
  bankType.value = String(type || '')
  bankPage.value = 1
  await loadBank()
}

function onSaveClick(): void {
  if (scoreMismatch.value) {
    ElMessage.warning(
      `选题合计 ${selectedTotalScore.value} 分与试卷总分 ${formTotalScore.value} 分不一致，` +
        '请调整题目分值或试卷总分后再保存',
    )
    return
  }
  emit('save', {
    selectedQuestions: selectedQuestions.value.map((q) => ({ ...q })),
    questionsLoaded: questionsLoaded.value,
  })
}

/** 自动组卷：成功后关闭条件弹层并通知宿主切编辑态（form.id 已由 composable 写入） */
async function onCompose(payload: AutoComposePayload): Promise<void> {
  const paper = await autoCompose(payload)
  if (!paper) return
  autoVisible.value = false
  ElMessage.success('自动组卷完成，已生成新试卷并载入题目，可继续调整后保存')
  emit('adopted', paper)
}

function onClosed(): void {
  autoVisible.value = false
  questionsLoaded.value = false
  reset()
  emit('closed')
}
</script>

<style scoped>
.w-100 {
  width: 100%;
}
.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.picker-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.picker-title {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.summary-total b {
  color: var(--ac-primary);
}
.summary-total .total-warn {
  color: var(--ac-danger, #e6553f);
}
.mismatch-hint {
  font-size: 11.5px;
  color: var(--ac-danger, #e6553f);
}
</style>
