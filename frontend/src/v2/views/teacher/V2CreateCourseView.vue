<template>
  <section class="module">
    <V2PageHead
      kicker="TEACHER / NEW COURSE · 2026 秋"
      title="创建课程"
      sub="填写课程基本信息，提交后进入审核流程；审核通过后才对学生开放报名。"
    />

    <section class="card form-card">
      <div class="form-grid">
        <label class="field span-2">
          <span>课程名称 *</span>
          <input v-model="form.name" class="input" type="text" placeholder="输入课程名称" />
        </label>
        <label class="field">
          <span>课程编码</span>
          <input
            v-model="form.code"
            class="input"
            type="text"
            placeholder="输入课程编码（如：CS101）"
          />
        </label>
        <label class="field">
          <span>课程分类 *</span>
          <select v-model="form.category" class="input">
            <option value="">选择分类</option>
            <option v-for="category in CATEGORIES" :key="category" :value="category">
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
            <option v-for="option in DIFFICULTY_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>报名方式</span>
          <select v-model="form.enrollmentType" class="input">
            <option v-for="option in ENROLLMENT_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>最大人数</span>
          <input v-model.number="form.maxStudents" class="input" type="number" min="0" step="1" />
        </label>
        <label class="field span-2">
          <span>课程简介</span>
          <textarea
            v-model="form.description"
            class="textarea"
            rows="4"
            placeholder="输入课程简介"
          ></textarea>
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
          <span v-else class="modal-hint">仅支持图片，大小不超过 2 MB。</span>
          <img v-if="form.coverImage" :src="form.coverImage" class="cover-img" alt="课程封面" />
        </div>
      </div>

      <p v-if="formError" class="form-error">{{ formError }}</p>

      <footer class="form-foot">
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交审核' }}
        </button>
        <button class="btn btn-ghost" @click="cancel">取消</button>
      </footer>
    </section>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import V2PageHead from '../../components/V2PageHead.vue'
import { useV2Ui } from '../../composables/ui'
import { createCourse } from '@/api/course'
import commonApi from '@/api/common'

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

const DEFAULTS = {
  name: '',
  code: '',
  category: '',
  description: '',
  credits: 2,
  difficulty: 'MEDIUM',
  enrollmentType: 'OPEN',
  maxStudents: 100,
  coverImage: '',
}

const router = useRouter()
const { toast } = useV2Ui()

const form = reactive({ ...DEFAULTS })
const submitting = ref(false)
const uploading = ref(false)
const uploadError = ref('')
const formError = ref('')
const fileName = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

function pickCover() {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

function errMsg(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  return message || fallback
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
    const response = await commonApi.uploadFile(file)
    const url = (response as { data?: { url?: string } })?.data?.url
    if (url) {
      form.coverImage = url
      toast('封面上传成功', 'ok')
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

async function submit() {
  formError.value = ''
  if (!form.name.trim()) {
    formError.value = '请输入课程名称'
    return
  }
  if (!form.category) {
    formError.value = '请选择课程分类'
    return
  }
  submitting.value = true
  try {
    await createCourse({ ...form, status: 'DRAFT', approvalStatus: 'PENDING' })
    toast('提交成功，等待审核', 'ok')
    Object.assign(form, DEFAULTS)
    fileName.value = ''
    uploadError.value = ''
    router.push('/teacher/courses')
  } catch (error) {
    formError.value = errMsg(error, '提交失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  router.push('/teacher/courses')
}
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
  font-size: 11px;
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
  border-radius: 8px;
}
</style>
