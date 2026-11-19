<template>
  <section class="module">
    <PageHead title="新建开课任务" />

    <section class="card form-card">
      <div class="form-grid">
        <label class="field span-2">
          <span>课程库</span>
          <select v-model="form.definitionId" class="input">
            <option value="__new__">新建课程定义</option>
            <option v-for="item in definitions" :key="String(item.id)" :value="String(item.id)">
              {{ item.code }} · {{ item.name }}
            </option>
          </select>
        </label>

        <template v-if="isNewDefinition">
          <label class="field span-2">
            <span>课程名称 *</span>
            <input v-model="definition.name" class="input" type="text" />
          </label>
          <label class="field">
            <span>课程编码 *</span>
            <input v-model="definition.code" class="input" type="text" placeholder="如 CS101" />
          </label>
          <label class="field">
            <span>课程性质</span>
            <select v-model="definition.nature" class="input">
              <option v-for="item in NATURES" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            <span>课程分类</span>
            <select v-model="definition.category" class="input">
              <option value="">未分类</option>
              <option v-for="item in CATEGORIES" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            <span>学分</span>
            <input
              v-model.number="definition.credits"
              class="input"
              type="number"
              min="0"
              max="10"
              step="0.1"
            />
          </label>
          <label class="field">
            <span>难度</span>
            <select v-model="definition.difficulty" class="input">
              <option v-for="item in DIFFICULTY_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>课程简介</span>
            <textarea v-model="definition.description" class="textarea" rows="3"></textarea>
          </label>
          <div class="field span-2">
            <span>封面图片</span>
            <input
              ref="fileInput"
              class="file-hidden"
              type="file"
              accept="image/*"
              @change="handleCover"
            />
            <div class="cover-row">
              <button class="btn btn-ghost" type="button" @click="pickCover">
                {{ uploading ? '上传中…' : '选择图片' }}
              </button>
              <span class="cover-name">{{ fileName || '未选择文件' }}</span>
            </div>
            <span v-if="uploadError" class="form-error">{{ uploadError }}</span>
            <span v-if="coverRejected" class="form-error">封面地址协议不受支持</span>
            <img v-if="coverSrc" :src="coverSrc" class="cover-img" alt="课程封面" />
          </div>
        </template>

        <label class="field">
          <span>学期 *</span>
          <select v-model="form.semesterId" class="input">
            <option value="" disabled>选择学期</option>
            <option v-for="item in semesters" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label v-if="isAdmin" class="field">
          <span>开课教师 *</span>
          <select v-model="form.teacherId" class="input">
            <option value="" disabled>选择教师</option>
            <option v-for="item in teachers" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>计划教学班数</span>
          <input
            v-model.number="form.plannedSections"
            class="input"
            type="number"
            min="1"
            max="50"
          />
        </label>
        <label class="field">
          <span>报名方式</span>
          <select v-model="form.enrollmentType" class="input">
            <option v-for="item in ENROLLMENT_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>每班容量</span>
          <input v-model.number="form.maxStudents" class="input" type="number" min="1" step="1" />
        </label>
      </div>

      <p v-if="formError" class="form-error">{{ formError }}</p>

      <footer class="form-foot">
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交开课任务' }}
        </button>
        <button class="btn btn-ghost" @click="cancel">取消</button>
      </footer>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHead from '../../components/PageHead.vue'
import { useUi } from '../../composables/ui'
import { createCourse, createCourseDefinition, getCourseDefinitions } from '@/api/course'
import commonApi from '@/api/common'
import { pickList, unwrap } from '@/utils/response'
import { safeUrl } from '@/utils/safeUrl'

const CATEGORIES = ['编程开发', '人工智能', '数据分析', '设计创意', '其他']
const NATURES = ['必修', '选修', '公选']
const DIFFICULTY_OPTIONS = [
  { value: 'BEGINNER', label: '入门' },
  { value: 'EASY', label: '初级' },
  { value: 'MEDIUM', label: '中级' },
  { value: 'HARD', label: '高级' },
  { value: 'EXPERT', label: '专家' },
]
const ENROLLMENT_OPTIONS = [
  { value: 'FREE', label: '开放报名' },
  { value: 'APPROVAL', label: '需审核' },
  { value: 'RESTRICTED', label: '限制报名' },
]

interface Row {
  id?: number | string
  name?: string
  code?: string
  status?: string
}

const router = useRouter()
const { role, toast } = useUi()
const isAdmin = computed(() => role.value === 'admin')

const definitions = ref<Row[]>([])
const semesters = ref<Row[]>([])
const teachers = ref<Row[]>([])
const submitting = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const formError = ref('')
const fileName = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  definitionId: '__new__',
  semesterId: '',
  teacherId: '',
  plannedSections: 1,
  maxStudents: 100,
  enrollmentType: 'FREE',
})
const definition = reactive({
  code: '',
  name: '',
  description: '',
  credits: 2,
  nature: '选修',
  category: '',
  difficulty: 'MEDIUM',
  coverImage: '',
})
const isNewDefinition = computed(() => form.definitionId === '__new__')
const coverSrc = computed(() => safeUrl(definition.coverImage))
const coverRejected = computed(() => Boolean(definition.coverImage) && !coverSrc.value)

function errMsg(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message || fallback
}

function pickCover() {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

async function handleCover(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadError.value = ''
  fileName.value = file.name
  if (!file.type.startsWith('image/')) {
    uploadError.value = '只能上传图片文件'
    fileName.value = ''
    return
  }
  if (file.size / 1024 / 1024 >= 2) {
    uploadError.value = '图片大小不能超过 2 MB'
    fileName.value = ''
    return
  }
  uploading.value = true
  try {
    const response = (await commonApi.uploadFile(file)) as { data?: { url?: string } }
    const url = response?.data?.url
    if (url) {
      definition.coverImage = url
    } else {
      uploadError.value = '上传失败：响应缺少文件地址'
    }
  } catch (error) {
    uploadError.value = errMsg(error, '封面上传失败，请稍后重试')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function load() {
  try {
    const [definitionEnvelope, semesterEnvelope] = await Promise.all([
      getCourseDefinitions({ status: 'ACTIVE' }),
      commonApi.getSemesters(),
    ])
    definitions.value = pickList<Row>(unwrap(definitionEnvelope))
    semesters.value = pickList<Row>(unwrap(semesterEnvelope))
    const active = semesters.value.find(
      (item) => String(item.status || '').toUpperCase() === 'ACTIVE',
    )
    form.semesterId = String(active?.id ?? semesters.value[0]?.id ?? '')
    form.definitionId = definitions.value.length ? String(definitions.value[0].id) : '__new__'
    if (isAdmin.value) {
      teachers.value = pickList<Row>(unwrap(await commonApi.getTeachers()))
      form.teacherId = String(teachers.value[0]?.id ?? '')
    }
  } catch (error) {
    formError.value = errMsg(error, '课程库或学期加载失败')
  }
}

async function resolveDefinitionId(): Promise<number | string> {
  if (!isNewDefinition.value) return form.definitionId
  if (!definition.name.trim()) throw new Error('请输入课程名称')
  if (!definition.code.trim()) throw new Error('请输入课程编码')
  const created = unwrap<Row>(
    await createCourseDefinition({
      code: definition.code.trim(),
      name: definition.name.trim(),
      description: definition.description,
      credits: definition.credits,
      nature: definition.nature,
      category: definition.category || null,
      difficulty: definition.difficulty,
      coverImage: definition.coverImage || null,
    }),
  )
  const id = created?.id
  if (!id) throw new Error('课程定义创建失败：缺少 id')
  return id
}

async function submit() {
  formError.value = ''
  if (!form.semesterId) {
    formError.value = '请选择学期'
    return
  }
  if (isAdmin.value && !form.teacherId) {
    formError.value = '请选择开课教师'
    return
  }
  if (form.plannedSections < 1 || form.plannedSections > 50) {
    formError.value = '计划教学班数需在 1–50 之间'
    return
  }
  submitting.value = true
  try {
    const definitionId = await resolveDefinitionId()
    await createCourse({
      definitionId,
      semesterId: Number(form.semesterId),
      teacherId: isAdmin.value ? Number(form.teacherId) : undefined,
      plannedSections: form.plannedSections,
      maxStudents: form.maxStudents,
      enrollmentType: form.enrollmentType,
    })
    toast('开课任务已提交，等待审核', 'ok')
    router.push(isAdmin.value ? '/admin/courses' : '/teacher/courses')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : errMsg(error, '提交失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  router.push(isAdmin.value ? '/admin/courses' : '/teacher/courses')
}

onMounted(load)
</script>

<style scoped>
.file-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.cover-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}
.cover-name {
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.cover-img {
  width: 148px;
  height: 148px;
  margin-top: 8px;
  display: block;
  object-fit: cover;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
}
</style>
