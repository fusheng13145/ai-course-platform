<template>
  <section class="tclasses-body">
    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>教学班监管</h2>
        </div>
        <div class="filter-row class-tools">
          <select
            v-model="courseId"
            class="input filter-select"
            aria-label="选择课程"
            @change="loadClasses"
          >
            <option :value="null" disabled>选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name }}
            </option>
          </select>
          <button
            v-if="courseId && hasDraft"
            class="btn btn-ghost"
            :disabled="publishing"
            @click="publishClasses"
          >
            {{ publishing ? '发布中…' : '发布教学班' }}
          </button>
        </div>
      </header>

      <div v-if="classes.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>教学班</th>
              <th class="w-140">主讲教师</th>
              <th class="w-96">人数 / 上限</th>
              <th class="w-80">状态</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in classes" :key="String(row.id)">
              <td>
                <b>{{ row.name || '—' }}</b
                ><small class="cell-sub">{{ row.code || '' }}</small>
              </td>
              <td>
                <select
                  class="input section-teacher"
                  :value="String(row.leadTeacherId ?? '')"
                  aria-label="主讲教师"
                  @change="assignLead(row, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="" disabled>选择主讲</option>
                  <option v-for="item in teachers" :key="String(item.id)" :value="String(item.id)">
                    {{ item.name }}
                  </option>
                </select>
              </td>
              <td>{{ row.memberCount ?? row.currentStudents ?? 0 }} / {{ row.capacity ?? '—' }}</td>
              <td>
                <span class="chip" :class="row.status === 'ACTIVE' ? 'chip-ok' : 'chip-mute'">
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td>
                <button
                  class="text-btn"
                  :disabled="membersLoading === String(row.id)"
                  @click="toggleMembers(row)"
                >
                  {{ openMembersId === String(row.id) ? '收起成员' : '查看成员' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载教学班…</b></div>
      <div v-else-if="courseId" class="empty-state compact">
        <b>暂无教学班</b>
      </div>
      <div v-else class="empty-state compact">
        <b>选择课程</b>
      </div>

      <div v-if="openMembersId" class="members-panel">
        <h3 class="members-title">教学班 #{{ openMembersId }} 成员</h3>
        <div v-if="members.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>学生</th>
                <th class="w-96">审批状态</th>
                <th class="w-80">在读</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="String(member.id)">
                <td>
                  <b>{{ memberLabel(member.studentId) }}</b
                  ><small class="cell-sub">#{{ member.studentId }}</small>
                </td>
                <td>{{ approvalLabel(member.approvalStatus) }}</td>
                <td>
                  <span class="chip" :class="member.isActive ? 'chip-ok' : 'chip-mute'">
                    {{ member.isActive ? '在读' : '已退' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state compact"><b>该教学班暂无成员</b></div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useUi } from '../../composables/ui'
import { getCourseCatalog } from '../../data/api'
import { getTeachingClassesByCourse, getTeachingClassMembers } from '@/api/admin'
import { getAllUsers } from '@/api/admin'
import { publishCourseClasses, assignLeadTeacher } from '@/api/teachingClass'
import { pickList, unwrap } from '@/utils/response'
import { approvalLabel } from '../../utils'

interface TcRow {
  id?: number | string
  name?: string
  code?: string
  status?: string
  leadTeacherId?: number | string | null
  memberCount?: number | null
  currentStudents?: number | null
  capacity?: number | null
}

interface MemberRow {
  id?: number | string
  studentId?: number | string
  approvalStatus?: string
  isActive?: boolean
}

interface OptionRow {
  id?: number | string
  name?: string
}

const { toast } = useUi()
const courses = ref<OptionRow[]>([])
const courseId = ref<number | string | null>(null)
const classes = ref<TcRow[]>([])
const teachers = ref<{ id?: number | string; name?: string }[]>([])
const loading = ref(false)
const publishing = ref(false)
const openMembersId = ref<string>('')
const members = ref<MemberRow[]>([])
const membersLoading = ref('')
const studentNames = new Map<string, string>()
const hasDraft = computed(() =>
  classes.value.some((row) => String(row.status || '').toUpperCase() === 'DRAFT'),
)

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function nameOf(map: Map<string, string>, id: unknown): string {
  return map.get(String(id)) || `#${String(id)}`
}

function memberLabel(id: unknown): string {
  return nameOf(studentNames, id)
}

function statusLabel(value: unknown): string {
  const key = String(value ?? '')
  if (key === 'ACTIVE') return '启用'
  if (key === 'DRAFT') return '草稿'
  if (key === 'CLOSED') return '结课'
  return key || '—'
}

async function loadDirectory() {
  try {
    const users = await getAllUsers({})
    teachers.value = []
    for (const user of users as Record<string, unknown>[]) {
      const id = String(user.id ?? '')
      if (!id) continue
      const name = String(user.fullName || user.username || `用户 ${id}`)
      studentNames.set(id, name)
      if (String(user.role || '').toUpperCase() === 'TEACHER') {
        teachers.value.push({ id, name })
      }
    }
  } catch {
    /* 目录失败回退选择器为空 */
  }
}

async function loadCourses() {
  try {
    const catalog = await getCourseCatalog()
    courses.value = (catalog || []).map((row) => ({ id: row.id, name: row.name }))
  } catch (error) {
    toast(errorText(error, '课程目录加载失败'), 'danger')
  }
}

async function loadClasses() {
  if (!courseId.value) return
  loading.value = true
  openMembersId.value = ''
  members.value = []
  try {
    const payload = unwrap(await getTeachingClassesByCourse(courseId.value))
    classes.value = pickList<TcRow>(payload)
  } catch (error) {
    toast(errorText(error, '教学班列表加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

async function publishClasses() {
  if (!courseId.value) return
  publishing.value = true
  try {
    await publishCourseClasses(courseId.value)
    await loadClasses()
    toast('教学班已发布', 'ok')
  } catch (error) {
    toast(errorText(error, '发布教学班失败'), 'danger')
  } finally {
    publishing.value = false
  }
}

async function assignLead(row: TcRow, teacherId: string) {
  if (!row.id || !teacherId) return
  try {
    const updated = unwrap(await assignLeadTeacher(row.id, teacherId)) as TcRow
    row.leadTeacherId = updated?.leadTeacherId ?? teacherId
    toast('主讲已更新', 'ok')
  } catch (error) {
    toast(errorText(error, '指定主讲失败'), 'danger')
  }
}

async function toggleMembers(row: TcRow) {
  if (row.id === undefined || row.id === null) return
  const key = String(row.id)
  if (openMembersId.value === key) {
    openMembersId.value = ''
    members.value = []
    return
  }
  membersLoading.value = key
  try {
    const payload = unwrap(await getTeachingClassMembers(row.id))
    members.value = pickList<MemberRow>(payload)
    openMembersId.value = key
  } catch (error) {
    toast(errorText(error, '成员列表加载失败'), 'danger')
  } finally {
    membersLoading.value = ''
  }
}

async function init() {
  await loadDirectory()
  await loadCourses()
}

init()
</script>

<style scoped>
.tclasses-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 260px;
  height: 30px;
}
.members-panel {
  padding: 8px 16px 14px;
  border-top: 1px solid var(--line);
}
.members-title {
  margin: 4px 0 8px;
  font-size: 13px;
  font-weight: 600;
}
.section-teacher {
  width: 132px;
  height: 28px;
}
</style>
