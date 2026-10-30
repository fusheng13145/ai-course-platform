<template>
  <div class="settings-grid">
    <section v-if="role !== 'admin'" class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">BASIC / 课程</p>
          <h2>基本信息</h2>
        </div>
        <div class="course-tab-actions">
          <span class="chip chip-ok">{{ approvalLabel(course?.approvalStatus) }}</span>
          <button v-if="role === 'teacher'" class="btn btn-ghost" @click="openEdit">
            编辑课程
          </button>
        </div>
      </header>
      <div class="setting-form">
        <label><span>课程名称</span><input :value="course?.name || ''" readonly /></label>
        <label><span>课程编号</span><input :value="course?.code || ''" readonly /></label>
        <label><span>学期</span><input :value="course?.semester || ''" readonly /></label>
        <label><span>学分</span><input :value="course?.credits ?? ''" readonly /></label>
        <label><span>分类</span><input :value="course?.category || ''" readonly /></label>
        <label
          ><span>难度</span><input :value="difficultyLabel(record?.difficulty)" readonly
        /></label>
        <label
          ><span>报名方式</span><input :value="enrollmentLabel(record?.enrollmentType)" readonly
        /></label>
        <label><span>最大人数</span><input :value="record?.maxStudents ?? ''" readonly /></label>
        <label
          ><span>审批状态</span><input :value="approvalLabel(course?.approvalStatus)" readonly
        /></label>
      </div>
      <p class="hint">课程编号按学期唯一；修改只影响基本信息，不会改动章节、任务与成绩。</p>
      <p v-if="loadError" class="form-error">{{ loadError }}</p>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">WORKFLOW / 审批</p>
          <h2>课程状态</h2>
        </div>
      </header>
      <div class="workflow-list">
        <div class="workflow-step is-done">
          <span>01</span><b>教师创建课程</b><small>已完成</small>
        </div>
        <div class="workflow-step" :class="course?.approvalStatus === 'APPROVED' ? 'is-done' : ''">
          <span>02</span><b>院系管理员审批</b
          ><small>{{ approvalLabel(course?.approvalStatus) }}</small>
        </div>
        <div class="workflow-step">
          <span>03</span><b>发布教学班</b><small>教学班模型待接入</small>
        </div>
      </div>
      <p class="hint">审批记录不可编辑；管理员课程治理页只读。</p>
    </section>

    <section v-if="role !== 'admin'" class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">CLASSES / 教学班</p>
          <h2>教学班设置</h2>
        </div>
        <span class="chip chip-warn">待接入</span>
      </header>
      <div class="empty-state compact">
        <b>教学班接口待扩展</b>
        <p>
          teaching_class 与选课名单的作用域端点落地后，这里改为真实班级列表；当前不展示样本数据。
        </p>
      </div>
    </section>

    <!-- 编辑课程 -->
    <V2Modal v-model:open="editOpen" title="编辑课程" kicker="COURSE / SETTINGS" width="640px">
      <div v-if="editLoading" class="empty-state compact"><b>正在加载课程资料…</b></div>
      <template v-else>
        <div class="form-grid">
          <label class="field span-2">
            <span>课程名称 *</span>
            <input v-model="form.name" class="input" />
          </label>
          <label class="field">
            <span>课程编号</span>
            <input v-model="form.code" class="input" />
          </label>
          <label class="field">
            <span>课程分类 *</span>
            <select v-model="form.category" class="input">
              <option value="">选择分类</option>
              <option v-for="category in categoryOptions" :key="category" :value="category">
                {{ category }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>学分</span>
            <input
              v-model.number="form.credits"
              class="input"
              type="number"
              min="0"
              max="10"
              step="0.1"
            />
          </label>
          <label class="field">
            <span>难度</span>
            <select v-model="form.difficulty" class="input">
              <option v-for="option in difficultyOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>报名方式</span>
            <select v-model="form.enrollmentType" class="input">
              <option v-for="option in enrollmentOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>最大人数</span>
            <input v-model.number="form.maxStudents" class="input" type="number" min="0" />
          </label>
          <label class="field span-2">
            <span>课程简介</span>
            <textarea v-model="form.description" class="textarea" rows="3"></textarea>
          </label>
        </div>
        <p v-if="formError" class="form-error">{{ formError }}</p>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="editOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving || editLoading" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </V2Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import V2Modal from '../../../components/V2Modal.vue'
import { useV2Ui } from '../../../composables/ui'
import courseApi, { getCourseById } from '@/api/course'
import { unwrap } from '@/utils/response'
import { approvalLabel } from '../../../utils'
import logger from '@/utils/logger'
import type { V2Role } from '../../../nav'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  approvalStatus?: string
}

const CATEGORIES = ['编程开发', '人工智能', '数据分析', '设计创意', '其他']
const DIFFICULTY_OPTIONS = [
  { value: 'BEGINNER', label: '入门' },
  { value: 'EASY', label: '初级' },
  { value: 'MEDIUM', label: '中级' },
  { value: 'HARD', label: '高级' },
  { value: 'EXPERT', label: '专家' },
]
const ENROLLMENT_OPTIONS = [
  { value: 'OPEN', label: '开放报名' },
  { value: 'APPROVAL', label: '需审核' },
  { value: 'RESTRICTED', label: '限制报名' },
]

const props = defineProps<{ role: V2Role; course: CourseLike | null; courseId: number }>()
const emit = defineEmits<{ (event: 'updated', course: Record<string, unknown>): void }>()
const { toast } = useV2Ui()

const record = ref<Record<string, unknown> | null>(null)
const loadError = ref('')
const editOpen = ref(false)
const editLoading = ref(false)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  name: '',
  code: '',
  category: '',
  credits: 2,
  difficulty: 'MEDIUM',
  enrollmentType: 'OPEN',
  maxStudents: 100,
  description: '',
})

function difficultyLabel(value: unknown): string {
  const key = String(value ?? '')
  if (!key) return '—'
  return DIFFICULTY_OPTIONS.find((option) => option.value === key)?.label || key
}

function enrollmentLabel(value: unknown): string {
  const key = String(value ?? '')
  if (!key) return '—'
  return ENROLLMENT_OPTIONS.find((option) => option.value === key)?.label || key
}

/** 库里存在枚举外的历史值（如难度「中等」、分类「计算机」），不能在选择器里被静默清空 */
const categoryOptions = computed(() => {
  const current = String(record.value?.category ?? form.category ?? '')
  return current && !CATEGORIES.includes(current) ? [current, ...CATEGORIES] : CATEGORIES
})
const difficultyOptions = computed(() => {
  const current = String(record.value?.difficulty ?? form.difficulty ?? '')
  const known = DIFFICULTY_OPTIONS.some((option) => option.value === current)
  return current && !known
    ? [{ value: current, label: current }, ...DIFFICULTY_OPTIONS]
    : DIFFICULTY_OPTIONS
})
const enrollmentOptions = computed(() => {
  const current = String(record.value?.enrollmentType ?? form.enrollmentType ?? '')
  const known = ENROLLMENT_OPTIONS.some((option) => option.value === current)
  return current && !known
    ? [{ value: current, label: current }, ...ENROLLMENT_OPTIONS]
    : ENROLLMENT_OPTIONS
})

async function fetchRecord() {
  loadError.value = ''
  try {
    record.value = unwrap(await getCourseById(props.courseId)) as Record<string, unknown>
  } catch (error) {
    logger.error('加载课程资料失败', error)
    record.value = null
    loadError.value = '课程资料加载失败，编辑与部分读数不可用'
  }
  return record.value
}

async function openEdit() {
  editOpen.value = true
  editLoading.value = true
  formError.value = ''
  const body = (record.value || (await fetchRecord())) as Record<string, unknown>
  if (body) {
    form.name = String(body?.name ?? '')
    form.code = String(body?.code ?? '')
    form.category = String(body?.category ?? '')
    form.credits = body?.credits == null ? 2 : Number(body.credits)
    form.difficulty = String(body?.difficulty ?? 'MEDIUM')
    form.enrollmentType = String(body?.enrollmentType ?? 'OPEN')
    form.maxStudents = body?.maxStudents == null ? 100 : Number(body.maxStudents)
    form.description = String(body?.description ?? '')
  }
  editLoading.value = false
}

async function save() {
  if (!form.name.trim()) {
    formError.value = '请填写课程名称'
    return
  }
  if (!form.category) {
    formError.value = '请选择课程分类'
    return
  }
  saving.value = true
  try {
    // 后端 updateCourse 走 this.updateById，缓存失效注解被自调用绕过，
    // 随后重新 GET 会拿到旧值——所以用 PUT 响应体回填视图，而不是再读一次
    const body = unwrap(
      await courseApi.updateCourse(props.courseId, { ...form, name: form.name.trim() }),
    ) as Record<string, unknown>
    record.value = body
    toast('课程信息已更新', 'ok')
    editOpen.value = false
    emit('updated', body)
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('更新课程失败', error)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (props.role !== 'admin') void fetchRecord()
})
</script>

<style scoped>
.settings-grid .course-tab-actions {
  align-items: center;
}
</style>
