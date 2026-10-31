<template>
  <div class="wb-panel">
    <div class="wb-toolbar">
      <p class="academic-note">
        错题来自你在作业与考试中的错误作答（<code>/student/wrong-questions</code>），仅本人可见。
      </p>
      <div class="course-tab-actions">
        <select
          v-model="courseFilter"
          class="input wb-filter"
          aria-label="按课程筛选错题"
          @change="reload"
        >
          <option :value="null">全部课程</option>
          <option v-for="course in courses" :key="course.id" :value="course.id">
            {{ course.name || course.title || '课程 ' + course.id }}
          </option>
        </select>
        <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </div>

    <div v-if="error" class="callout danger" role="alert">
      <span>ERROR / 错题本</span>
      <p>{{ error }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
    </div>
    <div v-else-if="loading && !rows.length" class="empty-state compact"><b>正在加载错题…</b></div>
    <div v-else-if="!rows.length" class="empty-state compact">
      <b>{{ courseFilter == null ? '还没有错题' : '这门课没有错题' }}</b>
      <p>作业与考试里的错误作答会自动收进这里。</p>
    </div>
    <template v-else>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>题目</th>
              <th>来源</th>
              <th class="num">错误次数</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="row in rows" :key="row.id">
              <tr>
                <td>
                  <button
                    class="text-btn wb-stem"
                    :aria-expanded="expanded === row.id"
                    @click="toggle(row.id)"
                  >
                    {{ row.questionContent || '（题目内容缺失）' }}
                  </button>
                </td>
                <td>
                  <span class="chip chip-mute">{{ sourceText(row.sourceType) }}</span>
                </td>
                <td class="num">{{ row.practiceCount ?? 1 }}</td>
                <td>
                  <span class="chip" :class="row.mastered ? 'chip-ok' : 'chip-warn'">
                    {{ row.mastered ? '已掌握' : '待掌握' }}
                  </span>
                </td>
                <td>{{ row.createdAt || '—' }}</td>
                <td class="wb-actions">
                  <button
                    v-if="!row.mastered"
                    class="btn btn-ghost btn-sm"
                    :disabled="busyId === row.id"
                    @click="markMastered(row)"
                  >
                    标记掌握
                  </button>
                  <button
                    class="btn btn-ghost btn-sm"
                    :disabled="busyId === row.id"
                    @click="remove(row)"
                  >
                    移除
                  </button>
                </td>
              </tr>
              <tr v-if="expanded === row.id" class="wb-detail-row">
                <td colspan="6">
                  <dl class="wb-detail">
                    <dt>你的答案</dt>
                    <dd>{{ row.wrongAnswer || '—' }}</dd>
                    <dt>正确答案</dt>
                    <dd>{{ correctText(row.correctAnswer) }}</dd>
                    <dt>选项</dt>
                    <dd>{{ optionsText(row.questionOptions) }}</dd>
                    <dt>知识点</dt>
                    <dd>{{ row.knowledgePointName || '—' }}</dd>
                    <dt>解析</dt>
                    <dd>{{ row.questionAnalysis || '暂无解析' }}</dd>
                  </dl>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <div class="wb-pager">
        <span class="academic-note">共 {{ total }} 条</span>
        <div class="course-tab-actions">
          <button
            class="btn btn-ghost btn-sm"
            :disabled="page <= 1 || loading"
            @click="goto(page - 1)"
          >
            上一页
          </button>
          <span class="academic-note">{{ page }} / {{ totalPages || 1 }}</span>
          <button
            class="btn btn-ghost btn-sm"
            :disabled="page >= (totalPages || 1) || loading"
            @click="goto(page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  getMyWrongQuestions,
  markMyWrongQuestionMastered,
  removeMyWrongQuestion,
} from '@/api/wrongQuestion'
import { getAllMyCourses } from '@/api/student'
import { unwrap } from '@/utils/response'
import { useV2Ui } from '../../../composables/ui'
import { confirmWithV2 } from '../../../composables/v2Confirm'
import logger from '@/utils/logger'

interface WrongRow {
  id?: number | string
  questionId?: number
  courseId?: number | null
  wrongAnswer?: string
  questionContent?: string
  questionOptions?: string
  correctAnswer?: string
  questionAnalysis?: string | null
  knowledgePointName?: string | null
  mastered?: boolean
  sourceType?: string
  practiceCount?: number
  createdAt?: string
}

const PAGE_SIZE = 10
const SOURCE_LABEL: Record<string, string> = {
  EXAM: '考试',
  HOMEWORK: '作业',
  PRACTICE: '练习',
}

const { toast } = useV2Ui()
const rows = ref<WrongRow[]>([])
const courses = ref<{ id?: number | string; name?: string; title?: string }[]>([])
const courseFilter = ref<number | string | null>(null)
const page = ref(1)
const total = ref(0)
const totalPages = ref(1)
const loading = ref(false)
const error = ref('')
const expanded = ref<number | string | null>(null)
const busyId = ref<number | string | null>(null)

function sourceText(type?: string) {
  return (type && SOURCE_LABEL[type]) || type || '—'
}
function correctText(raw?: string) {
  if (!raw) return '—'
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.join('、') : String(parsed)
  } catch {
    return raw
  }
}
function optionsText(raw?: string) {
  if (!raw) return '—'
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((item, index) => `${String.fromCharCode(65 + index)}. ${item}`).join('  ')
    }
    return String(parsed)
  } catch {
    return raw
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, number | string> = { page: page.value, size: PAGE_SIZE }
    if (courseFilter.value != null) params.courseId = courseFilter.value
    const body = (unwrap(await getMyWrongQuestions(params)) || {}) as {
      list?: WrongRow[]
      total?: number
      totalPages?: number
    }
    rows.value = Array.isArray(body.list) ? body.list : []
    total.value = Number(body.total || 0)
    totalPages.value = Number(body.totalPages || 1)
  } catch (err) {
    logger.error('加载错题本失败', err)
    error.value = '错题本加载失败，请稍后重试'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  try {
    const body = unwrap(await getAllMyCourses())
    courses.value = Array.isArray(body) ? (body as { id?: number; name?: string }[]) : []
  } catch {
    courses.value = []
  }
}

function reload() {
  page.value = 1
  expanded.value = null
  void load()
}
function goto(target: number) {
  page.value = target
  expanded.value = null
  void load()
}
function toggle(id?: number | string) {
  const key = id ?? null
  expanded.value = expanded.value === key ? null : key
}

async function markMastered(row: WrongRow) {
  if (row.id == null) return
  busyId.value = row.id
  try {
    await markMyWrongQuestionMastered(row.id)
    row.mastered = true
    toast('已标记为掌握', 'ok')
  } catch (err) {
    logger.error('标记掌握失败', err)
    toast('标记失败，请稍后重试', 'warn')
  } finally {
    busyId.value = null
  }
}

async function remove(row: WrongRow) {
  if (row.id == null) return
  const ok = await confirmWithV2(
    '移除后该题不再出现在错题本；已产生的作答记录不受影响。',
    '从错题本移除这道题？',
    { danger: true, confirmButtonText: '移除' },
  )
  if (!ok) return
  busyId.value = row.id
  try {
    await removeMyWrongQuestion(row.id)
    toast('已从错题本移除', 'ok')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (err) {
    logger.error('移除错题失败', err)
    toast('移除失败，请稍后重试', 'warn')
  } finally {
    busyId.value = null
  }
}

onMounted(() => {
  void loadCourses()
  void load()
})
</script>

<style scoped>
.wb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.wb-filter {
  height: 34px;
  width: 180px;
  font-size: 11px;
}
.wb-stem {
  text-align: left;
  white-space: normal;
}
.wb-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.wb-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.wb-detail {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 4px 12px;
  margin: 0;
  font-size: 12px;
}
.wb-detail dt {
  color: var(--ink-3);
}
.wb-detail dd {
  margin: 0;
  color: var(--ink-1);
  white-space: pre-wrap;
}
table td.num,
table th.num {
  text-align: right;
}
</style>
