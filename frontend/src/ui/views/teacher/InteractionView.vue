<template>
  <section class="module">
    <PageHead
      kicker="TEACHER / INTERACTION · 2026 秋"
      title="课堂互动"
      sub="发起签到与投票，实时掌握课堂参与情况；签到支持发布、结束与手动补签。"
      source="api"
    >
      <template #actions>
        <select v-model="courseId" class="input course-pick" aria-label="选择课程">
          <option value="">请选择课程</option>
          <option v-for="course in courses" :key="course.id" :value="course.id">
            {{ course.name || course.title }}
          </option>
        </select>
      </template>
    </PageHead>

    <div v-if="!courseId" class="callout">
      <span>STEP 1 / 选择课程</span>
      <p>选择一门课程后，即可发起签到与投票互动。</p>
    </div>

    <template v-else>
      <div v-if="loadError" class="callout">
        <span>ERROR / 加载失败</span>
        <p>{{ loadError }}</p>
      </div>

      <div class="tabs" role="tablist" aria-label="互动类型">
        <button :class="{ 'is-active': tab === 'sign' }" @click="tab = 'sign'">
          签到 <span class="chip chip-mute">{{ signList.length }}</span>
        </button>
        <button :class="{ 'is-active': tab === 'vote' }" @click="tab = 'vote'">
          投票 <span class="chip chip-mute">{{ voteList.length }}</span>
        </button>
      </div>

      <section v-show="tab === 'sign'" class="card table-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">INTERACTION / SIGN-IN</p>
            <h2>签到列表</h2>
          </div>
          <div class="course-tab-actions">
            <button class="btn btn-ghost" @click="loadSignInList">刷新</button>
            <button class="btn btn-primary" @click="openSignIn">发起签到</button>
          </div>
        </header>

        <div v-if="signList.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>标题</th>
                <th class="w-96">类型</th>
                <th class="w-96">状态</th>
                <th class="w-80">已签</th>
                <th class="w-80">应到</th>
                <th class="w-260 th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in signList" :key="rowKey(row, index)">
                <td>
                  <b class="i-text">{{ row.title || `签到 #${row.id}` }}</b>
                </td>
                <td>{{ signTypeText(row.type) }}</td>
                <td>
                  <span class="chip" :class="statusChip(row.status)">{{
                    statusText(row.status)
                  }}</span>
                </td>
                <td>{{ row.signedCount ?? '—' }}</td>
                <td>{{ row.totalCount ?? '—' }}</td>
                <td>
                  <div class="row-actions">
                    <button class="text-btn" @click="openRecords(row)">记录</button>
                    <button v-if="canPublish(row)" class="text-btn" @click="publishSign(row)">
                      发布
                    </button>
                    <button v-if="canClose(row)" class="text-btn danger" @click="askCloseSign(row)">
                      结束
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="signLoading" class="empty-state compact"><b>正在加载签到…</b></div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="check" /></span>
          <b>{{ signError ? '签到记录未能加载' : '暂无签到，点击「发起签到」' }}</b>
          <p>{{ signError ? '请查看上方提示后重试。' : '发起后学生端即时可见。' }}</p>
        </div>
      </section>

      <section v-show="tab === 'vote'" class="card table-card">
        <header class="card-head">
          <div>
            <p class="card-kicker">INTERACTION / VOTE</p>
            <h2>投票列表</h2>
          </div>
          <div class="course-tab-actions">
            <button class="btn btn-ghost" @click="loadVoteList">刷新</button>
            <button class="btn btn-primary" @click="openVote">发起投票</button>
          </div>
        </header>

        <div v-if="voteList.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>标题</th>
                <th class="w-96">类型</th>
                <th class="w-96">状态</th>
                <th class="w-220 th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in voteList" :key="rowKey(row, index)">
                <td>
                  <b class="i-text">{{ row.title || `投票 #${row.id}` }}</b>
                </td>
                <td>{{ voteModeText(row.voteMode) }}</td>
                <td>
                  <span class="chip" :class="statusChip(row.status)">{{
                    statusText(row.status)
                  }}</span>
                </td>
                <td>
                  <div class="row-actions">
                    <button class="text-btn" @click="openResult(row)">结果</button>
                    <button v-if="canClose(row)" class="text-btn danger" @click="askCloseVote(row)">
                      结束
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="voteLoading" class="empty-state compact"><b>正在加载投票…</b></div>
        <div v-else class="empty-state compact">
          <span class="e-ico"><Icon name="clipboard" /></span>
          <b>{{ voteError ? '投票记录未能加载' : '暂无投票，点击「发起投票」' }}</b>
          <p>{{ voteError ? '请查看上方提示后重试。' : '至少填写 2 个选项即可发起。' }}</p>
        </div>
      </section>
    </template>

    <!-- 发起签到 -->
    <Modal v-model:open="signDialog" title="发起签到" kicker="SIGN-IN / CREATE" width="520px">
      <div class="form-grid">
        <label class="field span-2">
          <span>签到标题</span>
          <input v-model="signForm.title" class="input" placeholder="如：第3章课堂签到" />
        </label>
        <label class="field">
          <span>时长（秒）</span>
          <input v-model.number="signForm.duration" class="input" type="number" min="10" />
        </label>
        <label class="field">
          <span>签到类型</span>
          <select v-model="signForm.type" class="input">
            <option value="NORMAL">普通签到</option>
            <option value="QR">二维码签到</option>
            <option value="LOCATION">定位签到</option>
          </select>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="signDialog = false">取消</button>
        <button class="btn btn-primary" :disabled="signSubmitting" @click="submitSignIn">
          {{ signSubmitting ? '提交中…' : '发起并发布' }}
        </button>
      </template>
    </Modal>

    <!-- 发起投票 -->
    <Modal v-model:open="voteDialog" title="发起投票" kicker="VOTE / CREATE" width="560px">
      <div class="form-grid">
        <label class="field span-2">
          <span>投票标题</span>
          <input v-model="voteForm.title" class="input" placeholder="如：本章难点是哪个？" />
        </label>
        <div class="field span-2">
          <span>选项（至少 2 个）</span>
          <div v-for="(option, index) in voteForm.options" :key="index" class="option-row">
            <input
              v-model="voteForm.options[index]"
              class="input"
              :placeholder="`选项 ${index + 1}`"
            />
            <button
              class="text-btn danger"
              :disabled="voteForm.options.length <= 2"
              @click="removeOption(index)"
            >
              删除
            </button>
          </div>
          <button class="btn btn-ghost btn-sm add-option" @click="voteForm.options.push('')">
            添加选项
          </button>
        </div>
        <label class="field span-2 check-row">
          <input v-model="voteForm.multiple" type="checkbox" />
          <span>允许多选</span>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="voteDialog = false">取消</button>
        <button class="btn btn-primary" :disabled="voteSubmitting" @click="submitVote">
          {{ voteSubmitting ? '提交中…' : '发起投票' }}
        </button>
      </template>
    </Modal>

    <!-- 签到记录 -->
    <Modal
      v-model:open="recordsDialog"
      :title="currentSign ? `签到记录 · ${currentSign.title || ''}` : '签到记录'"
      kicker="SIGN-IN / RECORDS"
      width="620px"
    >
      <div class="course-tab-actions record-actions">
        <span class="chip chip-ok">已签到 {{ countBy('SIGNED') }}</span>
        <span class="chip chip-warn">迟到 {{ countBy('LATE') }}</span>
        <span class="chip chip-mute">缺勤 {{ countBy('ABSENT') }}</span>
        <button class="text-btn" @click="openMarkForm">手动标记</button>
      </div>
      <div v-if="markVisible" class="form-grid mark-form">
        <label class="field">
          <span>学生</span>
          <select v-model="markForm.studentId" class="input">
            <option value="">选择学生</option>
            <option
              v-for="record in recordList"
              :key="String(record.studentId)"
              :value="record.studentId"
            >
              {{ record.studentName || `学生 #${record.studentId}` }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model="markForm.status" class="input">
            <option value="SIGNED">已签到</option>
            <option value="LATE">迟到</option>
            <option value="ABSENT">缺勤</option>
          </select>
        </label>
        <div class="field span-2">
          <button class="btn btn-primary btn-sm" :disabled="markSubmitting" @click="submitMark">
            {{ markSubmitting ? '提交中…' : '确认标记' }}
          </button>
        </div>
      </div>
      <div v-if="recordLoading" class="empty-state compact"><b>正在加载签到记录…</b></div>
      <div v-else-if="recordList.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th class="w-96">状态</th>
              <th class="w-220">签到时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(record, index) in recordList" :key="index">
              <td>{{ record.studentName || `学生 #${record.studentId}` }}</td>
              <td>
                <span class="chip" :class="statusChip(record.status)">{{
                  statusText(record.status)
                }}</span>
              </td>
              <td>{{ fmt(record.signInTime || record.time || record.createdAt) || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact"><b>暂无签到记录</b></div>
      <template #footer>
        <button class="btn btn-ghost" @click="recordsDialog = false">关闭</button>
      </template>
    </Modal>

    <!-- 投票结果 -->
    <Modal
      v-model:open="resultDialog"
      :title="voteRow ? `投票结果 · ${voteRow.title || ''}` : '投票结果'"
      kicker="VOTE / RESULT"
      width="560px"
    >
      <div v-if="!resultRows.length" class="empty-state compact"><b>暂无结果数据</b></div>
      <ul v-else class="result-list">
        <li v-for="(item, index) in resultRows" :key="index">
          <span class="r-label">{{ item.label }}</span>
          <span class="r-track"><i :style="{ width: `${item.pct}%` }" /></span>
          <b class="r-num">{{ item.count }}</b>
        </li>
      </ul>
      <p v-if="voteResult && voteResult.total != null" class="modal-hint">
        参与 {{ voteResult.total }} 人
      </p>
      <template #footer>
        <button class="btn btn-ghost" @click="resultDialog = false">关闭</button>
      </template>
    </Modal>

    <!-- 结束确认 -->
    <Modal
      v-model:open="confirm.open"
      :title="confirm.title"
      kicker="CONFIRM / ACTION"
      width="460px"
    >
      <p class="modal-text">{{ confirm.text }}</p>
      <p v-if="confirm.note" class="modal-hint">{{ confirm.note }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runConfirm">确定</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import PageHead from '../../components/PageHead.vue'
import Modal from '../../components/Modal.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'
import interactionApi from '@/api/interaction'
import { getAllMyCourses } from '@/api/course'
import { unwrap, pickList } from '@/utils/response'
import logger from '@/utils/logger'

interface CourseOption {
  id: number | string
  name?: string
  title?: string
  [key: string]: unknown
}

const { toast } = useUi()

const courses = ref<CourseOption[]>([])
const courseId = ref<number | string>('')
const tab = ref<'sign' | 'vote'>('sign')

const signList = ref<Record<string, unknown>[]>([])
const voteList = ref<Record<string, unknown>[]>([])
const signLoading = ref(false)
const voteLoading = ref(false)
const signError = ref('')
const voteError = ref('')
const formError = ref('')

const signDialog = ref(false)
const signSubmitting = ref(false)
const signForm = ref<{ title: string; duration: number; type: string }>({
  title: '',
  duration: 300,
  type: 'NORMAL',
})

const voteDialog = ref(false)
const voteSubmitting = ref(false)
const voteForm = ref<{ title: string; options: string[]; multiple: boolean }>({
  title: '',
  options: ['', ''],
  multiple: false,
})

const recordsDialog = ref(false)
const recordLoading = ref(false)
const recordList = ref<Record<string, unknown>[]>([])
const currentSign = ref<Record<string, unknown> | null>(null)
const markVisible = ref(false)
const markSubmitting = ref(false)
const markForm = ref<{ studentId: string; status: string }>({ studentId: '', status: 'SIGNED' })

const resultDialog = ref(false)
const voteResult = ref<Record<string, unknown> | null>(null)
const voteRow = ref<Record<string, unknown> | null>(null)

const confirm = reactive({
  open: false,
  title: '结束',
  text: '',
  note: '',
  busy: false,
  action: null as null | (() => Promise<void>),
})

function rowKey(row: Record<string, unknown>, index: number): string {
  return String(row.id ?? index)
}

const loadError = computed(() => signError.value || voteError.value)
// 后端 VoteResultVO: { total, counts: { 选项: 票数 }, textAnswers, mode }
const resultRows = computed(() => {
  const counts = voteResult.value?.counts
  const entries = Object.entries((counts || {}) as Record<string, number>)
  const max = Math.max(1, ...entries.map(([, count]) => Number(count) || 0))
  return entries.map(([label, raw]) => {
    const count = Number(raw) || 0
    return { label, count, pct: Math.round((count / max) * 100) }
  })
})

function statusText(status: unknown): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待开始',
    PUBLISHED: '进行中',
    ONGOING: '进行中',
    STARTED: '进行中',
    CLOSED: '已结束',
    ENDED: '已结束',
    FINISHED: '已结束',
    SIGNED: '已签到',
    LATE: '迟到',
    ABSENT: '缺勤',
  }
  const key = String(status ?? '').toUpperCase()
  return map[key] || String(status ?? '—')
}

function statusChip(status: unknown): string {
  const key = String(status ?? '').toUpperCase()
  if (['PUBLISHED', 'ONGOING', 'STARTED', 'SIGNED'].includes(key)) return 'chip-ok'
  if (['CLOSED', 'ENDED', 'FINISHED', 'ABSENT'].includes(key)) return 'chip-mute'
  if (['DRAFT', 'LATE'].includes(key)) return 'chip-warn'
  return 'chip-info'
}

function signTypeText(type: unknown): string {
  const map: Record<string, string> = { NORMAL: '普通', QR: '二维码', LOCATION: '定位' }
  return map[String(type ?? '').toUpperCase()] || String(type || '普通')
}

function voteModeText(mode: unknown): string {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TEXT: '开放作答',
  }
  return map[String(mode ?? '').toUpperCase()] || String(mode || '—')
}

function canPublish(row: Record<string, unknown>): boolean {
  return String(row.status ?? '').toUpperCase() === 'PENDING'
}

function canClose(row: Record<string, unknown>): boolean {
  return String(row.status ?? '').toUpperCase() === 'ONGOING'
}

function countBy(status: string): number {
  return recordList.value.filter((row) => String(row.status ?? '').toUpperCase() === status).length
}

function fmt(value: unknown): string {
  if (!value) return ''
  const date = new Date(String(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

async function loadCourses() {
  try {
    courses.value = (await getAllMyCourses()) as CourseOption[]
  } catch (error) {
    logger.error('加载教师课程失败', error)
    courses.value = []
  }
}

async function loadSignInList() {
  if (!courseId.value) return
  signLoading.value = true
  signError.value = ''
  try {
    const body = unwrap(await interactionApi.getSignInList(courseId.value))
    const rows = pickList<Record<string, unknown>>(body)
    signList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    logger.error('加载签到列表失败', error)
    signList.value = []
    signError.value = '签到记录加载失败，请检查网络后重试'
  } finally {
    signLoading.value = false
  }
}

async function loadVoteList() {
  if (!courseId.value) return
  voteLoading.value = true
  voteError.value = ''
  try {
    const body = unwrap(await interactionApi.getVoteList(courseId.value))
    const rows = pickList<Record<string, unknown>>(body)
    voteList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    logger.error('加载投票列表失败', error)
    voteList.value = []
    voteError.value = '投票记录加载失败，请检查网络后重试'
  } finally {
    voteLoading.value = false
  }
}

function openSignIn() {
  formError.value = ''
  signForm.value = { title: '', duration: 300, type: 'NORMAL' }
  signDialog.value = true
}

async function submitSignIn() {
  if (!signForm.value.title.trim()) {
    formError.value = '请输入签到标题'
    return
  }
  if (!courseId.value) {
    formError.value = '请先选择课程'
    return
  }
  signSubmitting.value = true
  try {
    const body = unwrap(
      await interactionApi.createSignIn({
        courseId: courseId.value,
        title: signForm.value.title.trim(),
        duration: signForm.value.duration,
        type: signForm.value.type,
      }),
    )
    const id = (body as { id?: number | string })?.id
    if (id) await interactionApi.publishSignIn(id)
    toast('签到已发起并发布', 'ok')
    signDialog.value = false
    await loadSignInList()
  } catch (error) {
    formError.value = '发起签到失败，请稍后重试'
    logger.error('发起签到失败', error)
  } finally {
    signSubmitting.value = false
  }
}

async function publishSign(row: Record<string, unknown>) {
  try {
    await interactionApi.publishSignIn(row.id as number | string)
    toast('已发布', 'ok')
    await loadSignInList()
  } catch (error) {
    toast('发布失败，请稍后重试', 'danger')
    logger.error('发布签到失败', error)
  }
}

function askCloseSign(row: Record<string, unknown>) {
  confirm.title = '结束签到'
  confirm.text = `确定结束「${row.title || `签到 #${row.id}`}」？结束后学生将无法继续签到，已签到记录不受影响。`
  confirm.note = '结束后暂不支持重新开启'
  confirm.action = async () => {
    try {
      await interactionApi.closeSignIn(row.id as number | string)
      toast('已结束', 'ok')
      await loadSignInList()
    } catch (error) {
      toast('结束失败，请稍后重试', 'danger')
      logger.error('结束签到失败', error)
    }
  }
  confirm.open = true
}

function askCloseVote(row: Record<string, unknown>) {
  confirm.title = '结束投票'
  confirm.text = `确定结束「${row.title || `投票 #${row.id}`}」？学生将无法继续投票，已投票的记录不受影响。`
  confirm.note = '结束后暂不支持重新开启'
  confirm.action = async () => {
    try {
      await interactionApi.closeVote(row.id as number | string)
      toast('已结束', 'ok')
      await loadVoteList()
    } catch (error) {
      toast('结束失败，请稍后重试', 'danger')
      logger.error('结束投票失败', error)
    }
  }
  confirm.open = true
}

async function runConfirm() {
  if (!confirm.action) return
  confirm.busy = true
  try {
    await confirm.action()
    confirm.open = false
    confirm.action = null
  } finally {
    confirm.busy = false
  }
}

function openRecords(row: Record<string, unknown>) {
  currentSign.value = row
  markVisible.value = false
  recordsDialog.value = true
  void loadRecords(row)
}

async function loadRecords(row: Record<string, unknown>) {
  recordLoading.value = true
  try {
    const body = unwrap(await interactionApi.getSignInRecords(row.id as number | string))
    const rows = pickList<Record<string, unknown>>(body)
    recordList.value = Array.isArray(rows) ? rows : []
  } catch (error) {
    logger.error('加载签到记录失败', error)
    recordList.value = []
    toast('签到记录加载失败', 'danger')
  } finally {
    recordLoading.value = false
  }
}

function openMarkForm() {
  markForm.value = { studentId: '', status: 'SIGNED' }
  markVisible.value = true
}

async function submitMark() {
  if (!markForm.value.studentId) {
    toast('请选择学生', 'warn')
    return
  }
  if (!currentSign.value) return
  markSubmitting.value = true
  try {
    await interactionApi.markSignIn(currentSign.value.id as number | string, {
      studentId: markForm.value.studentId,
      status: markForm.value.status,
    })
    toast('已标记', 'ok')
    markVisible.value = false
    await loadRecords(currentSign.value)
  } catch (error) {
    toast('标记失败，请稍后重试', 'danger')
    logger.error('手动标记失败', error)
  } finally {
    markSubmitting.value = false
  }
}

function openVote() {
  formError.value = ''
  voteForm.value = { title: '', options: ['', ''], multiple: false }
  voteDialog.value = true
}

function removeOption(index: number) {
  if (voteForm.value.options.length <= 2) return
  voteForm.value.options.splice(index, 1)
}

async function submitVote() {
  if (!voteForm.value.title.trim()) {
    formError.value = '请输入投票标题'
    return
  }
  const options = voteForm.value.options.map((option) => option.trim()).filter(Boolean)
  if (options.length < 2) {
    formError.value = '至少填写 2 个选项'
    return
  }
  if (!courseId.value) {
    formError.value = '请先选择课程'
    return
  }
  voteSubmitting.value = true
  try {
    const body = unwrap(
      await interactionApi.createVote({
        courseId: courseId.value,
        title: voteForm.value.title.trim(),
        options: JSON.stringify(options),
        voteMode: voteForm.value.multiple ? 'MULTIPLE_CHOICE' : 'SINGLE_CHOICE',
      }),
    )
    const id = (body as { id?: number | string })?.id
    if (id) await interactionApi.publishVote(id)
    toast('投票已发起并发布', 'ok')
    voteDialog.value = false
    await loadVoteList()
  } catch (error) {
    formError.value = '发起投票失败，请稍后重试'
    logger.error('发起投票失败', error)
  } finally {
    voteSubmitting.value = false
  }
}

async function openResult(row: Record<string, unknown>) {
  voteRow.value = row
  voteResult.value = null
  resultDialog.value = true
  try {
    const body = unwrap(await interactionApi.getVoteResult(row.id as number | string)) as Record<
      string,
      unknown
    > | null
    const inner = (body?.result || body) as Record<string, unknown> | undefined
    voteResult.value = inner || null
  } catch (error) {
    logger.error('加载投票结果失败', error)
    toast('投票结果加载失败', 'danger')
  }
}

watch(courseId, () => {
  signList.value = []
  voteList.value = []
  signError.value = ''
  voteError.value = ''
  if (courseId.value) {
    void loadSignInList()
    void loadVoteList()
  }
})

void loadCourses()
</script>

<style scoped>
.course-pick {
  height: 34px;
  width: 220px;
  font-size: 11px;
}
.i-text {
  display: block;
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.add-option {
  align-self: flex-start;
}
.check-row {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.record-actions {
  align-items: center;
  margin-bottom: 10px;
}
.mark-form {
  padding: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface-2);
}
.result-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.result-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 2fr 34px;
  align-items: center;
  gap: 10px;
}
.r-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.r-track {
  height: 8px;
  border-radius: var(--r-xs);
  background: var(--surface-3);
  overflow: hidden;
}
.r-track i {
  display: block;
  height: 100%;
  border-radius: var(--r-xs);
  background: var(--accent);
}
.r-num {
  text-align: right;
  font-family: var(--font-display);
  font-size: 14px;
}
</style>
