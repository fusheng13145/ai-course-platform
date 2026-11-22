<template>
  <!-- 沉浸式作业页：独立路由，全屏展示题目要求 + 作答提交 -->
  <div class="task-room">
    <header class="room-head">
      <button class="btn btn-ghost btn-sm" @click="backToCourse">返回</button>
      <h1>{{ detail?.title || '作业' }}</h1>
      <span v-if="detail" class="chip" :class="statusChip">{{ statusLabel }}</span>
    </header>

    <main class="room-body">
      <p v-if="loading" class="hint">正在加载作业…</p>
      <p v-else-if="loadError" class="callout danger">{{ loadError }}</p>

      <template v-else-if="detail">
        <p class="meta-row">
          <span>截止 {{ fmtDate(detail.dueDate) }}</span>
          <span v-if="detail.maxScore != null">满分 {{ detail.maxScore }}</span>
          <span v-if="attemptHint">{{ attemptHint }}</span>
        </p>

        <section v-if="detail.description" class="block">
          <h2>作业说明</h2>
          <p class="body-text">{{ detail.description }}</p>
        </section>

        <section v-if="detail.requirements" class="block">
          <h2>要求</h2>
          <p class="body-text">{{ detail.requirements }}</p>
        </section>

        <section v-if="detail.attachmentUrl" class="block">
          <h2>教师附件</h2>
          <a :href="safeUrl(detail.attachmentUrl)" target="_blank" rel="noopener noreferrer">
            下载附件
          </a>
        </section>

        <section v-if="submission" class="block">
          <h2>我的提交</h2>
          <p>
            得分：<b>{{ submission.score ?? '—' }}</b>
            <span v-if="submission.maxScore != null">/ {{ submission.maxScore }}</span>
            <span v-if="submission.submittedAt" class="meta-inline">
              · {{ fmtDate(submission.submittedAt) }}
            </span>
          </p>
          <p v-if="submission.content" class="body-text">
            <span class="label">作答：</span>{{ submission.content }}
          </p>
          <p v-if="submission.fileUrls.length" class="body-text">
            <span class="label">附件：</span>
            <a
              v-for="(url, index) in submission.fileUrls"
              :key="url"
              :href="safeUrl(url)"
              target="_blank"
              rel="noopener noreferrer"
              class="file-link"
            >
              附件 {{ index + 1 }}
            </a>
          </p>
          <p v-if="submission.feedback" class="body-text">
            <span class="label">教师评语：</span>{{ submission.feedback }}
          </p>
        </section>

        <section v-if="canSubmit" class="block submit-block">
          <h2>{{ submission ? '重新提交' : '提交作业' }}</h2>
          <label class="field">
            <span>作答内容</span>
            <textarea
              v-model="form.content"
              class="input"
              rows="8"
              maxlength="50000"
              placeholder="填写你的作答内容…"
              :disabled="submitting"
            ></textarea>
          </label>
          <label class="field">
            <span>附件（可选）</span>
            <input
              type="file"
              multiple
              class="input"
              :disabled="uploading || submitting"
              @change="onFileChange"
            />
          </label>
          <div v-if="form.fileUrls.length" class="uploaded">
            <span v-for="(url, index) in form.fileUrls" :key="url" class="chip chip-mute">
              附件 {{ index + 1 }}
              <button type="button" class="text-btn" @click="removeAttachment(url)">移除</button>
            </span>
          </div>
          <p v-if="uploading" class="hint">附件上传中…</p>
          <p v-if="formError" class="form-error">{{ formError }}</p>
        </section>
        <p v-else class="callout">{{ blockedHint }}</p>
      </template>
    </main>

    <footer v-if="detail && canSubmit" class="room-foot">
      <span v-if="hasDraft" class="draft-hint">已有草稿，提交前可继续修改</span>
      <span class="foot-space"></span>
      <button
        class="btn btn-ghost"
        :disabled="submitting || uploading || draftSaving"
        @click="saveDraftNow"
      >
        {{ draftSaving ? '保存中…' : '保存草稿' }}
      </button>
      <button
        class="btn btn-primary"
        :disabled="submitting || uploading || draftSaving"
        @click="submit"
      >
        {{ submitting ? '提交中…' : submission ? '重新提交作业' : '提交作业' }}
      </button>
    </footer>

    <!-- 本页在 Shell 之外，toast 渲染器必须自己挂，否则保存/提交提示看不到 -->
    <Toasts />
  </div>
</template>

<script setup lang="ts">
// 基础样式原本在 Shell.vue 里 import；本页在 Shell 之外，必须自己引入
import '../../styles/record.css'
import '../../styles/shell-fix.css'
import '../../styles/typography.css'
import '../../styles/form.css'
import '../../styles/consistency.css'
import '../../styles/coursera.css'

import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUi } from '../../composables/ui'
import Toasts from '../../components/Toasts.vue'
import { useRoot } from '../../composables/useRoot'
import studentApi from '@/api/student'
import submissionApi from '@/api/submission'
import commonApi from '@/api/common'
import { unwrap } from '@/utils/response'
import { safeUrl } from '@/utils/safeUrl'
import logger from '@/utils/logger'

// 本页挂在 Shell 之外，自己挂主题属性，保证全局组件样式生效
const theme = ref<'light' | 'dark'>(localStorage.getItem('theme') === 'dark' ? 'dark' : 'light')
useRoot(theme, 'assignment')

interface AssignmentDetail {
  taskId: number
  title: string
  description: string
  requirements: string
  attachmentUrl: string
  dueDate: string
  maxScore: number | null
  maxSubmissions: number | null
  isPublished: boolean
  courseId: number | null
}

interface SubmissionView {
  id: number
  status: string
  score: number | null
  maxScore: number | null
  feedback: string | null
  content: string | null
  fileUrls: string[]
  submittedAt: string | null
  attemptNumber: number | null
}

const route = useRoute()
const router = useRouter()
const { toast } = useUi()

const taskId = Number(route.params.taskId)
const loading = ref(true)
const loadError = ref('')
const detail = ref<AssignmentDetail | null>(null)
const submission = ref<SubmissionView | null>(null)
const form = reactive({ content: '', fileUrls: [] as string[] })
const formError = ref('')
const submitting = ref(false)
const uploading = ref(false)
const draftSaving = ref(false)
/** 是否已有草稿（决定按钮文案与提示） */
const hasDraft = ref(false)

const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: '已提交待批改',
  RESUBMITTED: '重新提交待批改',
  GRADED: '已批改',
  RETURNED: '已退回',
}

const statusKey = computed(() => (submission.value?.status || '').toUpperCase())
const statusLabel = computed(() => {
  if (!submission.value) return '待提交'
  return STATUS_LABEL[statusKey.value] || statusKey.value || '已提交'
})
const statusChip = computed(() => {
  if (!submission.value) return 'chip-warn'
  return statusKey.value === 'GRADED' ? 'chip-ok' : 'chip-info'
})

function usedAttempts(): number {
  return Number(submission.value?.attemptNumber ?? 0) || 0
}

const attemptHint = computed(() => {
  if (!detail.value) return ''
  const used = usedAttempts()
  const max = Number(detail.value.maxSubmissions ?? 0)
  if (max > 0) return `已提交 ${used} / ${max} 次`
  return used > 0 ? `已提交 ${used} 次，可继续重新提交` : ''
})

const canSubmit = computed(() => {
  if (!detail.value || !detail.value.isPublished) return false
  const max = Number(detail.value.maxSubmissions ?? 0)
  if (Number.isFinite(max) && max > 0 && usedAttempts() >= max) return false
  return true
})

const blockedHint = computed(() => {
  const max = Number(detail.value?.maxSubmissions ?? 0)
  if (max > 0 && usedAttempts() >= max) return `已达到最大提交次数（${max} 次），无法再次提交。`
  return '该作业当前不可提交；如需补交请联系任课教师。'
})

function fmtDate(value?: string | null): string {
  const text = String(value ?? '')
  return text ? text.replace('T', ' ').slice(0, 16) : '—'
}

function normalizeUrls(value: unknown): string[] {
  if (Array.isArray(value))
    return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
  if (typeof value !== 'string' || !value.trim()) return []
  const raw = value.trim()
  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed))
      return parsed.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
  } catch {
    /* 历史数据为逗号分隔 URL */
  }
  return raw
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean)
}

function toSubmission(raw: Record<string, unknown> | null): SubmissionView | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    status: String(raw.status ?? '').toUpperCase(),
    score: raw.score == null ? null : Number(raw.score),
    maxScore: raw.maxScore == null ? null : Number(raw.maxScore),
    feedback: (raw.feedback as string | null) ?? null,
    content: (raw.content as string | null) ?? null,
    fileUrls: normalizeUrls(raw.fileUrls),
    submittedAt: (raw.submittedAt as string | null) ?? null,
    attemptNumber: raw.attemptNumber == null ? null : Number(raw.attemptNumber),
  }
}

function backToCourse() {
  router.push(detail.value?.courseId ? `/student/courses/${detail.value.courseId}/tasks` : '/student/courses')
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [detailRes, mySubmissions] = await Promise.all([
      studentApi.getAssignmentDetail(taskId),
      // 草稿不在详情 VO 里兜底时，从「我的提交」里补：草稿不算正式提交，只用于回填内容
      submissionApi.getAllMySubmissions().catch(() => []),
    ])
    const body = unwrap<{ assignment?: Record<string, unknown>; submission?: Record<string, unknown> }>(
      detailRes,
    )
    const assignment = body?.assignment
    if (!assignment) throw new Error('作业不存在或不可访问')
    detail.value = {
      taskId: Number(assignment.id ?? taskId),
      title: String(assignment.title ?? `作业 #${taskId}`),
      description: String(assignment.description ?? ''),
      requirements: String(assignment.requirements ?? ''),
      attachmentUrl: String(assignment.attachmentUrl ?? ''),
      dueDate: String(assignment.dueDate ?? ''),
      maxScore: assignment.maxScore == null ? null : Number(assignment.maxScore),
      maxSubmissions: assignment.maxSubmissions == null ? null : Number(assignment.maxSubmissions),
      isPublished: assignment.isPublished === true,
      courseId: assignment.courseId == null ? null : Number(assignment.courseId),
    }
    const formal = toSubmission(body.submission ?? null)
    submission.value = formal && formal.status !== 'DRAFT' ? formal : null
    const draftRow = (mySubmissions as Record<string, unknown>[]).find(
      (row) =>
        Number(row.taskId) === taskId && String(row.status ?? '').toUpperCase() === 'DRAFT',
    )
    const draft = toSubmission(draftRow ?? null)
    // 有正式提交时以正式提交为准；否则回填草稿内容，让学生接着写
    form.content = submission.value?.content ?? draft?.content ?? ''
    form.fileUrls = []
    hasDraft.value = Boolean(draft)
  } catch (reason) {
    logger.error('加载作业详情失败', reason)
    loadError.value = reason instanceof Error ? reason.message : '作业加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) return
  uploading.value = true
  formError.value = ''
  try {
    for (const file of files) {
      const data = unwrap<Record<string, string> | string>(await commonApi.uploadFile(file))
      const url = typeof data === 'string' ? data : String(data?.url || data?.fileUrl || '')
      if (url) form.fileUrls.push(url)
    }
  } catch (error) {
    logger.error('附件上传失败', error)
    formError.value = '附件上传失败，请重试（已上传的附件仍保留）'
  } finally {
    uploading.value = false
  }
}

function removeAttachment(url: string) {
  form.fileUrls = form.fileUrls.filter((item) => item !== url)
}

/** 保存草稿：不计入成绩与提交次数，正式提交时服务端复用同一行转正 */
async function saveDraftNow() {
  if (!detail.value || draftSaving.value) return
  const content = form.content.trim()
  if (!content && !form.fileUrls.length) {
    formError.value = '草稿也需要填写作答内容或上传附件'
    return
  }
  draftSaving.value = true
  formError.value = ''
  try {
    await submissionApi.saveDraft(detail.value.taskId, { content, fileUrls: [...form.fileUrls] })
    hasDraft.value = true
    toast('草稿已保存', 'ok')
  } catch (error) {
    logger.error('保存草稿失败', error)
    formError.value = '草稿保存失败，请稍后重试'
  } finally {
    draftSaving.value = false
  }
}

async function submit() {  if (!detail.value || submitting.value) return
  const content = form.content.trim()
  const fileUrls = [...form.fileUrls]
  if (!content && !fileUrls.length) {
    formError.value = '请填写作答内容或上传附件'
    return
  }
  const isResubmission = Boolean(submission.value)
  submitting.value = true
  formError.value = ''
  try {
    await submissionApi.submitTask(detail.value.taskId, { content, fileUrls })
    toast(isResubmission ? '已重新提交，等待教师批改' : '提交成功，等待教师批改', 'ok')
    // 重新拉取，拿到新的 attemptNumber / 状态
    await load()
    form.fileUrls = []
    form.content = ''
  } catch (error) {
    logger.error('作业提交失败', error)
    formError.value = '提交失败，请稍后重试（已保留填写内容）'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.task-room {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}
.room-head {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}
.room-head h1 {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.room-body {
  width: min(880px, 100%);
  margin: 0 auto;
  padding: 20px 24px 40px;
  flex: 1;
}
.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--ink-3);
  font-size: 12.5px;
  margin: 0 0 16px;
}
.block {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  padding: 18px 20px;
  margin-bottom: 14px;
}
.block h2 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
}
.body-text {
  margin: 6px 0;
  line-height: 1.75;
  white-space: pre-wrap;
}
.label {
  color: var(--ink-3);
}
.meta-inline {
  color: var(--ink-3);
  font-size: 12px;
  margin-left: 8px;
}
.file-link {
  margin-right: 12px;
}
.field {
  display: block;
  margin-bottom: 12px;
}
.field > span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--ink-2);
}
.field .input {
  width: 100%;
}
.uploaded {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.form-error {
  color: var(--danger);
  font-size: 13px;
}
.room-foot {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}
.foot-space {
  flex: 1;
}
.draft-hint {
  color: var(--ink-3);
  font-size: 12px;
}
</style>
