<template>
  <Modal
    :open="open"
    title="上传课程资源"
    kicker="RESOURCE / UPLOAD"
    width="560px"
    :close-on-backdrop="!submitting"
    @update:open="onRequestOpen"
  >
    <input
      ref="fileInput"
      class="file-hidden"
      type="file"
      :accept="ACCEPT"
      aria-label="选择要上传的资源文件"
      @change="onPick"
    />
    <div class="file-row">
      <button class="btn btn-ghost" :disabled="submitting" @click="chooseFile">选择文件</button>
      <span class="file-name">{{ pickedFile ? pickedFile.name : '未选择文件' }}</span>
    </div>
    <p class="upload-hint">
      支持 PDF / Word / Excel / JPG / PNG / GIF / MP4 / MP3，单文件不超过
      50MB；上传后学生可在课程资源区下载。
    </p>
    <label class="field">
      <span>资源描述</span>
      <textarea
        v-model="description"
        class="textarea"
        rows="3"
        placeholder="选填，简要说明资源内容"
        :disabled="submitting"
      ></textarea>
    </label>
    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
    <template #footer>
      <button class="btn btn-ghost" :disabled="submitting" @click="requestClose">取消</button>
      <button class="btn btn-primary" :disabled="submitting || !pickedFile" @click="submit">
        {{ submitting ? '上传中…' : failed ? '重试上传' : '上传' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { uploadResource } from '@/api/resource'
import logger from '@/utils/logger'

const props = defineProps<{ open: boolean; courseId: number }>()
const emit = defineEmits<{
  (event: 'update:open', value: boolean): void
  (event: 'success'): void
}>()

/** 与后端 ResourceController.MAX_FILE_SIZE 对齐 */
const MAX_BYTES = 50 * 1024 * 1024
/** 与后端 ALLOWED_MIME_TYPES 覆盖的扩展名对齐（后端同时做 MIME 与魔数校验） */
const ALLOWED_EXT = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png', 'gif', 'mp4', 'mp3']
const ACCEPT = ALLOWED_EXT.map((ext) => `.${ext}`).join(',')

const { toast } = useUi()

const fileInput = ref<HTMLInputElement | null>(null)
const pickedFile = ref<File | null>(null)
const description = ref('')
const submitting = ref(false)
const failed = ref(false)
const formError = ref('')

// 每次打开都从干净状态开始；上传失败时 open 仍为 true，不触发重置，保留文件便于重试
watch(
  () => props.open,
  (open) => {
    if (open) reset()
  },
)

function reset() {
  pickedFile.value = null
  description.value = ''
  submitting.value = false
  failed.value = false
  formError.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function chooseFile() {
  const input = fileInput.value
  if (!input) return
  input.value = ''
  input.click()
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  formError.value = ''
  failed.value = false
  const ext = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() || '' : ''
  if (!ALLOWED_EXT.includes(ext)) {
    pickedFile.value = null
    formError.value = '不支持该文件格式：仅支持 PDF / Word / Excel / 图片 / 视频 / 音频'
    return
  }
  if (file.size > MAX_BYTES) {
    pickedFile.value = null
    formError.value = '文件超过 50MB 限制，请压缩后再上传'
    return
  }
  if (file.size === 0) {
    pickedFile.value = null
    formError.value = '文件为空，无法上传'
    return
  }
  pickedFile.value = file
}

function requestClose() {
  if (submitting.value) return
  emit('update:open', false)
}

/** Modal 的关闭出口统一走这里：上传中不允许关闭，避免请求与状态脱节 */
function onRequestOpen(next: boolean) {
  if (next) return
  requestClose()
}

/** 业务错误经 api 层以 Error(message) reject；HTTP 错误从 response.data.message 取可读文案 */
function reasonOf(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  const raw = err?.response?.data?.message || err?.message || ''
  if (!raw || /^Request failed/i.test(raw)) return '服务端拒绝了本次上传，请稍后重试'
  return raw
}

async function submit() {
  if (!pickedFile.value) {
    formError.value = '请选择要上传的文件'
    return
  }
  if (!props.courseId || props.courseId <= 0) {
    formError.value = '课程信息缺失，无法上传资源'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    // 单文件直传 /resources/upload：旧端资源上传同样走该端点（非分片），
    // api/resource.ts 的 buildUploadForm 已把 file/courseId/description 归一化为 FormData。
    await uploadResource({
      file: pickedFile.value,
      courseId: props.courseId,
      description: description.value,
    })
    emit('success')
    emit('update:open', false)
    reset()
  } catch (error) {
    failed.value = true
    formError.value = `上传失败：${reasonOf(error)}。对话框不会关闭，可修改后点「重试上传」。`
    toast('资源上传失败', 'danger')
    logger.error('上传课程资源失败', error)
  } finally {
    submitting.value = false
  }
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
.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.file-name {
  font-size: 11px;
  color: var(--ink-2);
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-hint {
  margin: 0 0 14px;
  font-size: 10.5px;
  line-height: 1.7;
  color: var(--ink-3);
}
</style>
