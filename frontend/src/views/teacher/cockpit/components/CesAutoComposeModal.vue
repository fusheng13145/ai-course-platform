<template>
  <Modal :width="560" title="自动组卷" @close="emit('close')">
    <div class="compose-form">
      <div class="field">
        <label>试卷标题</label>
        <el-input v-model="form.title" placeholder="请输入新试卷标题" maxlength="100" />
      </div>
      <div class="field">
        <label>描述（可选）</label>
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="请输入试卷描述"
        />
      </div>
      <div class="field-row">
        <div class="field">
          <label>总分</label>
          <el-input-number
            v-model="form.totalScore"
            :min="1"
            :max="1000"
            controls-position="right"
          />
        </div>
        <div class="field">
          <label>时长（分钟）</label>
          <el-input-number v-model="form.duration" :min="1" :max="600" controls-position="right" />
        </div>
      </div>
      <!-- 题型 × 数量：typeRules key 与题库 Question.type 实际值一致（设计 F3） -->
      <div class="field">
        <label>题型 × 抽题数量</label>
        <div class="grid">
          <div v-for="opt in QUESTION_TYPE_OPTIONS" :key="opt.value" class="grid-cell">
            <span class="grid-label">{{ opt.label }}</span>
            <el-input-number
              v-model="counts[opt.value]"
              size="small"
              :min="0"
              :max="200"
              controls-position="right"
            />
          </div>
        </div>
      </div>
      <div class="field">
        <label>难度分布（%，可留空由后端默认；填写则三项合计须为 100）</label>
        <div class="grid">
          <div class="grid-cell">
            <span class="grid-label">简单</span>
            <el-input-number
              v-model="difficulty.easy"
              size="small"
              :min="0"
              :max="100"
              controls-position="right"
            />
          </div>
          <div class="grid-cell">
            <span class="grid-label">中等</span>
            <el-input-number
              v-model="difficulty.medium"
              size="small"
              :min="0"
              :max="100"
              controls-position="right"
            />
          </div>
          <div class="grid-cell">
            <span class="grid-label">困难</span>
            <el-input-number
              v-model="difficulty.hard"
              size="small"
              :min="0"
              :max="100"
              controls-position="right"
            />
          </div>
        </div>
      </div>
      <div class="shuffle-row">
        <el-checkbox v-model="form.shuffleQuestions">打乱题目顺序</el-checkbox>
        <el-checkbox v-model="form.shuffleOptions">打乱选项顺序</el-checkbox>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="composing" @click="confirm">开始组卷</el-button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import Modal from '@/components/ui/Modal.vue'
import { QUESTION_TYPE_OPTIONS } from '@/constants/questionTypes'
import type { AutoComposePayload } from '@/composables/useExamCompose'

/**
 * 课程试卷 — 自动组卷条件表单（P1-1）。
 * <p>小表单收集题型×数量 + 难度分布(%) + 总分/时长，组装 AutoComposeDTO 后
 * emit('compose') 交宿主调 useExamCompose.autoCompose（后端直接建新卷并写题目）。
 * 本组件不持有领域状态：成功后的「切编辑态 + 题目回显」由宿主与 composable 完成。</p>
 */
const props = defineProps({
  /** 新卷归属课程 */
  courseId: { type: Number, required: true },
  /** 组卷中（宿主 autoComposing 透传，按钮 loading） */
  composing: { type: Boolean, default: false },
  /** 标题预填（取宿主当前表单标题） */
  defaultTitle: { type: String, default: '' },
})

const emit = defineEmits<{
  (e: 'compose', payload: AutoComposePayload): void
  (e: 'close'): void
}>()

const form = reactive({
  title: props.defaultTitle,
  description: '',
  totalScore: 100,
  duration: 60,
  shuffleQuestions: true,
  shuffleOptions: false,
})

/** 题型抽题数量（key = 题库 Question.type 实际值，初始全 0 = 不抽） */
const counts = reactive<Record<string, number>>(
  Object.fromEntries(QUESTION_TYPE_OPTIONS.map((opt) => [opt.value, 0])),
)

/** 难度分布（null = 未填写；三项全空时整体交给后端默认策略）。
 *  key 为实库 Question.difficulty 实际值——小写 easy/medium/hard（QA 实查 + 后端
 *  ExamPaperServiceImpl 按原值精确分组，大写 EASY→400「没有匹配到符合条件的题目」）。 */
const difficulty = reactive<Record<'easy' | 'medium' | 'hard', number | null>>({
  easy: null,
  medium: null,
  hard: null,
})

/** 组装 AutoComposeDTO；校验失败给明确提示后不 emit */
function confirm(): void {
  if (!form.title.trim()) {
    ElMessage.warning('请输入新试卷标题')
    return
  }
  const typeRules: Record<string, number> = {}
  for (const opt of QUESTION_TYPE_OPTIONS) {
    const count = Number(counts[opt.value]) || 0
    if (count > 0) typeRules[opt.value] = count
  }
  if (Object.keys(typeRules).length === 0) {
    ElMessage.warning('请至少为一种题型设置抽题数量')
    return
  }
  // 难度分布：三项全空 → 省略；部分填写或不为 100 → 阻断并提示
  const ratioValues = [difficulty.easy, difficulty.medium, difficulty.hard]
  const hasRatio = ratioValues.some((v) => v != null)
  let difficultyRatio: Record<string, number> | undefined
  if (hasRatio) {
    if (ratioValues.some((v) => v == null)) {
      ElMessage.warning('难度比例三项需全部填写')
      return
    }
    const sum = ratioValues.reduce((total, v) => total + Number(v), 0)
    if (Math.abs(sum - 100) > 1e-9) {
      ElMessage.warning('难度比例合计需为 100%')
      return
    }
    difficultyRatio = {
      easy: Number(difficulty.easy),
      medium: Number(difficulty.medium),
      hard: Number(difficulty.hard),
    }
  }
  emit('compose', {
    courseId: props.courseId,
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    totalScore: form.totalScore,
    duration: form.duration,
    typeRules,
    difficultyRatio,
    shuffleQuestions: form.shuffleQuestions,
    shuffleOptions: form.shuffleOptions,
  })
}
</script>

<style scoped>
.compose-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.field label {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.field-row {
  display: flex;
  gap: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 14px;
}
.grid-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.grid-label {
  font-size: 12.5px;
  color: var(--ac-text-primary);
  flex-shrink: 0;
}
.shuffle-row {
  display: flex;
  gap: 18px;
}
</style>
