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
            <option :value="null" disabled>请选择课程</option>
            <option v-for="course in courses" :key="String(course.id)" :value="course.id">
              {{ course.name }}（#{{ course.id }}）
            </option>
          </select>
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
              <td>{{ leadLabel(row.leadTeacherId) }}</td>
              <td>
                {{ row.memberCount ?? row.currentStudents ?? 0 }} / {{ row.maxStudents ?? '—' }}
              </td>
              <td>
                <span class="chip" :class="row.status === 'ACTIVE' ? 'chip-ok' : 'chip-mute'">
                  {{ row.status === 'ACTIVE' ? '启用' : row.status || '—' }}
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
        <b>该课程暂无教学班</b>
        <p>每门落库课程都会自动带一个默认教学班；此处为空属数据异常。</p>
      </div>
      <div v-else class="empty-state compact">
        <b>请先在右上角选择课程</b>
        <p>监管视图按课程逐个查看教学班与成员。</p>
      </div>

      <div v-if="openMembersId" class="members-panel">
        <p class="card-kicker">教学班 #{{ openMembersId }} 成员</p>
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
                <td>{{ member.approvalStatus || '—' }}</td>
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

      <p class="hint">
        教学班的建班/换主讲/名单导入由课程主讲教师在课程空间内操作（WP9
        只读矩阵：管理员只读监管，不代管教学数据）。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUi } from '../../composables/ui'
import { getCourseCatalog } from '../../data/api'
import { getTeachingClassesByCourse, getTeachingClassMembers } from '@/api/admin'
import { getAllUsers } from '@/api/admin'
import { pickList, unwrap } from '@/utils/response'

interface TcRow {
  id?: number | string
  name?: string
  code?: string
  status?: string
  leadTeacherId?: number | string | null
  memberCount?: number | null
  currentStudents?: number | null
  maxStudents?: number | null
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
const loading = ref(false)
const openMembersId = ref<string>('')
const members = ref<MemberRow[]>([])
const membersLoading = ref('')
const teacherNames = new Map<string, string>()
const studentNames = new Map<string, string>()

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function nameOf(map: Map<string, string>, id: unknown): string {
  return map.get(String(id)) || `#${String(id)}`
}

function leadLabel(id: unknown): string {
  return id ? `教师 ${nameOf(teacherNames, id)}` : '未指定'
}

function memberLabel(id: unknown): string {
  return nameOf(studentNames, id)
}

async function loadDirectory() {
  try {
    const users = await getAllUsers({})
    for (const user of users as Record<string, unknown>[]) {
      const id = String(user.id ?? '')
      if (id) {
        teacherNames.set(id, String(user.fullName || user.username || `用户 ${id}`))
        studentNames.set(id, String(user.fullName || user.username || `用户 ${id}`))
      }
    }
  } catch {
    /* 目录失败回退 #id */
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
  border-top: 1px dashed var(--line);
}
</style>
