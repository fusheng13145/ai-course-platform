<template>
  <div v-loading="loading" class="assignment-tab">
    <!-- 加载失败持久提示（不自动消失） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="load-error-alert"
      show-icon
    />

    <EmptyState
      v-if="!loading && !loadError && rows.length === 0"
      title="该课程暂无已发布的作业任务"
      icon="list"
    />

    <div v-else class="assignment-list">
      <div v-for="row in rows" :key="row.taskId" class="assignment-card">
        <div class="ac-head">
          <span class="ac-title">{{ row.title }}</span>
          <span class="tag" :class="statusClass(row.status)">{{ row.statusLabel }}</span>
        </div>
        <div class="ac-meta small muted">
          <span v-if="row.dueDate">截止 {{ row.dueDate }}</span>
          <span v-if="row.maxScore != null">满分 {{ row.maxScore }}</span>
        </div>
        <p v-if="row.description" class="ac-desc small">{{ row.description }}</p>

        <!-- 我的提交与成绩/评语（S2） -->
        <div v-if="row.submission" class="ac-submission">
          <div class="ac-line">
            <span class="muted small">我的得分：</span>
            <b class="ac-score">{{ row.submission.score ?? '—' }}</b>
            <span v-if="row.submission.maxScore != null" class="muted small"
              >/ {{ row.submission.maxScore }}</span
            >
          </div>
          <div v-if="row.submission.content" class="ac-line small">
            <span class="muted">作答：</span>{{ row.submission.content }}
          </div>
          <div v-if="row.submission.fileUrls.length" class="ac-line small">
            <span class="muted">附件：</span>
            <a
              v-for="(url, index) in row.submission.fileUrls"
              :key="url"
              :href="safeUrl(url)"
              target="_blank"
              rel="noopener noreferrer"
              class="ac-file"
              >附件 {{ index + 1 }}</a
            >
          </div>
          <div v-if="row.submission.feedback" class="ac-feedback small">
            <span class="muted">教师评语：</span>{{ row.submission.feedback }}
          </div>
        </div>

        <div class="ac-ops">
          <button
            v-if="canSubmit(row.status)"
            type="button"
            class="btn btn-primary btn-sm"
            @click="openSubmit(row)"
          >
            {{ row.status === 'RETURNED' ? '重新提交' : '提交作业' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 提交弹层（自研 Modal：内部自接焦点，调用方不接 useDialogFocus） -->
    <Modal v-if="submitModal.visible" narrow @close="closeSubmit">
      <template #title>{{
        submitModal.task?.status === 'RETURNED' ? '重新提交' : '提交作业'
      }}</template>
      <template #sub
        ><b>{{ submitModal.task?.title }}</b
        ><span v-if="submitModal.task?.dueDate" class="muted">
          （截止 {{ submitModal.task.dueDate }}）</span
        ></template
      >
      <div class="form-grid">
        <div class="field span-2">
          <label>作答内容</label
          ><textarea
            v-model="submitModal.form.content"
            class="textarea"
            placeholder="填写你的作答内容…"
            maxlength="50000"
            :rows="5"
          ></textarea>
        </div>
        <div class="field span-2">
          <label>附件（可选）</label>
          <input type="file" multiple class="input" :disabled="uploading" @change="onFileChange" />
          <div v-if="submitModal.form.fileUrls.length" class="uploaded-list">
            <span
              v-for="(url, index) in submitModal.form.fileUrls"
              :key="url"
              class="tag uploaded-item"
            >
              附件 {{ index + 1 }}
              <button type="button" class="remove-btn" @click="removeAttachment(url)">×</button>
            </span>
          </div>
          <span v-if="uploading" class="small muted">附件上传中…</span>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" :disabled="submitting" @click="closeSubmit">取消</button>
        <button
          class="btn btn-primary"
          :disabled="submitting || uploading"
          @click="submitAssignment"
        >
          {{ submitting ? '提交中…' : '确认提交' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { PropType } from 'vue'
import Modal from '@/components/ui/Modal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import {
  useStudentAssignments,
  canSubmitStatus,
  type StudentAssignmentRow,
} from '@/composables/useStudentAssignments'

/**
 * 学生课程详情「作业任务」页签（S1 提交入口 + S2 成绩/评语展示，最小可用面）。
 * <p>编排层：数据与动作全部委托 useStudentAssignments；本组件只做渲染与事件接线。</p>
 */
const props = defineProps({
  /** 课程 ID（来自路由 :id） */
  courseId: { type: [String, Number] as PropType<string | number>, required: true },
})

const courseIdRef = computed(() => props.courseId)

const {
  loading,
  loadError,
  rows,
  load,
  submitModal,
  submitting,
  uploading,
  openSubmit,
  closeSubmit,
  uploadFiles,
  removeAttachment,
  submitAssignment,
} = useStudentAssignments({ courseId: courseIdRef })

onMounted(load)

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  if (input.files?.length) uploadFiles(input.files)
  // 允许重复选择同一文件
  input.value = ''
}

function canSubmit(status: StudentAssignmentRow['status']): boolean {
  return canSubmitStatus(status)
}

/** 状态 → 标签色（--ac-* token 色板，禁写死色值） */
function statusClass(status: StudentAssignmentRow['status']): string {
  if (status === 'GRADED') return 'green'
  if (status === 'RETURNED') return 'red'
  if (status === 'SUBMITTED' || status === 'RESUBMITTED') return 'amber'
  return ''
}

/** 附件链接安全化：仅放行 http(s)，非法值返回空串（与教师端同口径） */
function safeUrl(value: string): string {
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}
</script>

<style scoped>
.assignment-tab {
  min-height: 160px;
}
.load-error-alert {
  margin-bottom: var(--ac-spacing-md);
}
.assignment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.assignment-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px 16px;
}
.ac-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ac-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.ac-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}
.ac-desc {
  margin: 8px 0 0;
  color: var(--ac-text-secondary);
  line-height: 1.7;
}
.ac-submission {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px dashed var(--ac-border);
  border-radius: var(--ac-radius-sm);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ac-line {
  line-height: 1.7;
}
.ac-score {
  color: var(--ac-text-primary);
  font-size: 16px;
}
.ac-file {
  color: var(--ac-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
  margin-right: 8px;
}
.ac-feedback {
  color: var(--ac-text-secondary);
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 6px 8px;
  line-height: 1.7;
}
.ac-ops {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.uploaded-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.uploaded-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.remove-btn {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 0;
  color: inherit;
  font-family: inherit;
}
</style>
