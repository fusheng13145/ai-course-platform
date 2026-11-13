<template>
  <section class="enrollments-body">
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">ADMIN ENROLLMENTS / LIST</p>
          <h2>
            选课与名单 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="学生姓名 / 用户名"
            aria-label="按学生搜索"
            @keyup.enter="load(true)"
          />
          <select
            v-model="status"
            class="input filter-select"
            aria-label="按审批状态筛选"
            @change="load(true)"
          >
            <option value="">全部状态</option>
            <option value="PENDING">待审批</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已驳回</option>
          </select>
          <input
            v-model.number="courseId"
            class="input filter-select"
            type="number"
            min="1"
            placeholder="课程 ID"
            aria-label="按课程 ID 筛选"
            @keyup.enter="load(true)"
          />
          <button class="btn btn-sm" :disabled="loading" @click="load(true)">
            {{ loading ? '查询中…' : '查询' }}
          </button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th class="w-80">课程</th>
              <th class="w-140">教学班</th>
              <th class="w-96">状态</th>
              <th class="w-140">审批人</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.id)">
              <td>
                <b>{{ studentLabel(row) }}</b
                ><small class="cell-sub">#{{ row.studentId }}</small>
              </td>
              <td>{{ row.courseId }}</td>
              <td>{{ row.teachingClassId ? `#${row.teachingClassId}` : '未分班' }}</td>
              <td>
                <span class="chip" :class="statusClass(row.approvalStatus)">{{
                  statusLabel(row.approvalStatus)
                }}</span>
              </td>
              <td>{{ row.approvedBy ? `#${row.approvedBy}` : '—' }}</td>
              <td>
                <div v-if="row.approvalStatus === 'PENDING'" class="row-actions">
                  <button
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="act(row, 'approve')"
                  >
                    通过
                  </button>
                  <button
                    class="text-btn danger"
                    :disabled="acting === String(row.id)"
                    @click="act(row, 'reject')"
                  >
                    驳回
                  </button>
                </div>
                <span v-else class="hint-inline">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载选课记录…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无符合条件的选课记录</b>
        <p>调整筛选后重试。</p>
      </div>

      <footer class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="turnPage(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page * pageSize >= total || loading"
            @click="turnPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
      <p class="hint">
        审批通过 = 写 approved_by 并置
        APPROVED；教学班在选课时已解析（未分班的记录来自教学班模型上线前的历史数据）。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Stat from '../../components/Stat.vue'
import { useUi } from '../../composables/ui'
import { getAllUsers, listAllEnrollments } from '@/api/admin'
import { approveEnrollment, rejectEnrollment } from '@/api/enrollment'
import { unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface EnrollmentRow {
  id?: number | string
  studentId?: number | string
  courseId?: number | string
  teachingClassId?: number | string | null
  approvalStatus?: string
  approvedBy?: number | string | null
  [key: string]: unknown
}

const { toast } = useUi()
const rows = ref<EnrollmentRow[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const status = ref('')
const keyword = ref('')
const courseId = ref<number | null>(null)
const acting = ref('')
const studentNames = new Map<string, string>()

const pendingCount = computed(
  () => rows.value.filter((row) => row.approvalStatus === 'PENDING').length,
)

const metrics = computed<Metric[]>(() => [
  { label: '记录总数', value: total.value, unit: '条', note: '当前筛选', icon: 'layers' },
  {
    label: '本页待审批',
    value: pendingCount.value,
    unit: '条',
    note: 'PENDING',
    icon: 'users',
    tone: 'warn',
  },
  {
    label: '本页条数',
    value: rows.value.length,
    unit: '条',
    note: `每页 ${pageSize}`,
    icon: 'chart',
  },
  {
    label: '课程筛选',
    value: courseId.value ?? '全部',
    unit: '',
    note: 'courseId',
    icon: 'building',
  },
])

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function statusLabel(status: unknown): string {
  return (
    ({ PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' } as Record<string, string>)[
      String(status ?? '')
    ] || String(status ?? '—')
  )
}

function statusClass(status: unknown): string {
  return status === 'APPROVED' ? 'chip-ok' : status === 'REJECTED' ? 'chip-mute' : 'chip-warn'
}

function studentLabel(row: EnrollmentRow): string {
  return studentNames.get(`${row.studentId}`) || `学生 #${row.studentId}`
}

async function loadStudentDirectory() {
  // 学生姓名目录：一次全量拉取后本地映射（getAllUsers 已按 fetchAllPages 解包为数组）
  try {
    const list = await getAllUsers({})
    for (const user of list as Record<string, unknown>[]) {
      const id = String(user.id ?? '')
      if (id) studentNames.set(id, String(user.fullName || user.username || `用户 ${id}`))
    }
  } catch {
    /* 姓名映射失败不阻断列表，回退 #id 展示 */
  }
}

async function load(resetPage = false) {
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const payload = unwrap(
      await listAllEnrollments({
        page: page.value,
        size: pageSize,
        status: status.value || undefined,
        courseId: courseId.value || undefined,
        keyword: keyword.value.trim() || undefined,
      }),
    )
    const envelope = payload as { records?: EnrollmentRow[]; total?: number }
    rows.value = (envelope?.records || []) as EnrollmentRow[]
    total.value = Number(envelope?.total ?? rows.value.length)
    // 学生名缓存：后端按 keyword 返回的行已过姓名匹配；此处只对已出现的学生补名
    for (const row of rows.value) {
      const key = `${row.studentId}`
      if (!studentNames.has(key)) studentNames.set(key, `学生 #${row.studentId}`)
    }
  } catch (error) {
    toast(errorText(error, '选课列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

function turnPage(next: number) {
  page.value = next
  void load()
}

async function act(row: EnrollmentRow, action: 'approve' | 'reject') {
  if (row.id === undefined || row.id === null) return
  acting.value = String(row.id)
  try {
    if (action === 'approve') {
      await approveEnrollment(row.id)
      toast(`已通过 ${studentLabel(row)} 的选课`, 'ok')
    } else {
      await rejectEnrollment(row.id)
      toast(`已驳回 ${studentLabel(row)} 的选课`, 'ok')
    }
    await load()
  } catch (error) {
    toast(errorText(error, '审批操作失败'), 'danger')
  } finally {
    acting.value = ''
  }
}

onMounted(() => {
  void loadStudentDirectory()
  void load()
})
</script>

<style scoped>
.enrollments-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-input {
  width: 170px;
  height: 30px;
}
.filter-select {
  width: 120px;
  height: 30px;
}
input.filter-select {
  width: 100px;
}
</style>
