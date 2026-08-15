<template>
  <div>
    <PageHeader
      title="课程大厅"
      subtitle="选择课程查看学习概览，或凭课程 ID 加入新课"
      icon="book"
    />
    <!-- 分割视图：左课程树 + 右内容 -->
    <div class="split-view animate-in d1">
      <aside class="split-aside">
        <div class="sa-head">
          <span class="strong sa-title">课程列表</span>
          <div class="sa-spacer"></div>
          <button class="btn btn-primary btn-sm" @click="showJoin = true">
            <svg
              viewBox="0 0 24 24"
              width="13"
              height="13"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 5v14M5 12h14" />
            </svg>
            加入课程
          </button>
        </div>
        <div class="sa-body">
          <el-skeleton v-if="loading" :rows="3" animated />
          <el-empty v-else-if="courses.length === 0" description="暂无课程" :image-size="70" />
          <template v-else>
            <div
              v-for="c in courses"
              :key="c.courseId"
              class="tree-row"
              :class="{ selected: activeCourse?.courseId === c.courseId }"
              @click="selectCourse(c)"
            >
              <span
                class="tree-ico"
                :style="{ color: activeCourse?.courseId === c.courseId ? 'var(--accent)' : '' }"
              >
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
                  />
                </svg>
              </span>
              <span class="tree-label" :title="c.name">{{ c.name }}</span>
              <span class="tree-count">{{ c.progress }}%</span>
            </div>
          </template>
        </div>
      </aside>

      <!-- 右侧内容 -->
      <div class="split-main">
        <!-- 课程详情头卡 -->
        <section v-if="activeCourse" class="l1-card detail-card">
          <div class="l1-body detail-body">
            <div class="detail-cover" :style="{ background: activeCourse.coverStyle }">
              <svg
                viewBox="0 0 24 24"
                width="28"
                height="28"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
                />
              </svg>
            </div>
            <div class="detail-info">
              <div class="flex gap-8">
                <span class="detail-name">{{ activeCourse.name }}</span
                ><span v-if="activeCourse.code" class="tag outline mono">{{
                  activeCourse.code
                }}</span>
              </div>
              <div class="small muted mt-8">
                {{ activeCourse.meta }} · 我的进度 {{ activeCourse.progress }}%
              </div>
            </div>
            <div class="flex gap-8">
              <button
                class="btn btn-primary btn-sm"
                @click="$router.push(`/student/courses/${activeCourse.courseId}/overview`)"
              >
                进入学习
              </button>
            </div>
          </div>
        </section>

        <section v-if="activeCourse" class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"
              ><svg
                viewBox="0 0 24 24"
                width="17"
                height="17"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" /></svg
            ></span>
            <div>
              <div class="l1-title">课程概览</div>
              <div class="l1-sub">本学期进度与待办事项</div>
            </div>
            <div class="l1-extra">
              <span class="tag green">{{ activeCourse.progress }}%</span>
            </div>
          </div>
          <div class="l1-body">
            <div class="grid-3">
              <div class="l3-block ring-block">
                <RingProgress :pct="activeCourse.progress" :size="74" label="章节进度" />
              </div>
              <div class="l3-block">
                <div class="l3-title">
                  <svg
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.9"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2" />
                  </svg>
                  待办事项
                </div>
                <!-- 已净化：nextTask 由服务端可输入的标题拼接，先经 sanitizeHtml 清洗 -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="todo-text" v-html="sanitizeHtml(activeCourse.nextTask)"></div>
              </div>
              <div class="l3-block">
                <div class="l3-title">
                  <svg
                    viewBox="0 0 24 24"
                    width="14"
                    height="14"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.9"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 15a7 7 0 1 0 0-14 7 7 0 0 0 0 14z" />
                  </svg>
                  快速入口
                </div>
                <div class="flex flex-col gap-8">
                  <button
                    class="btn btn-soft btn-sm btn-block"
                    @click="$router.push(`/student/courses/${activeCourse.courseId}/overview`)"
                  >
                    📖 继续学习
                  </button>
                  <button
                    class="btn btn-plain btn-sm btn-block"
                    @click="$router.push('/student/exams')"
                  >
                    📝 作业考试
                  </button>
                  <button
                    class="btn btn-plain btn-sm btn-block"
                    @click="$router.push('/student/grades')"
                  >
                    📊 成绩查询
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <EmptyState
          v-if="!activeCourse"
          title="从左侧选择课程"
          desc="点击左侧课程列表中的课程查看详情"
          icon="book"
        />
      </div>
    </div>

    <!-- 加入课程弹窗 -->
    <Modal v-if="showJoin" narrow @close="showJoin = false">
      <template #title>加入新课程</template>
      <template #sub>输入课程 ID（可在教师端课程列表查看）</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>课程 ID<span class="req">*</span></label
          ><input v-model="joinCourseId" class="input mono join-input" placeholder="如：1" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="showJoin = false">取消</button>
        <button class="btn btn-primary" :disabled="joining" @click="doJoin()">
          {{ joining ? '加入中…' : '加入课程' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Modal from '@/components/ui/Modal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import RingProgress from '@/components/charts/RingProgress.vue'
import { toast } from '@/composables/useToast'
import { sanitizeHtml } from '@/utils/sanitize'
import studentApi from '@/api/student'

// 渐变对齐 AppShell avatarStyle 6 色渐变风格（令牌色系：赭红/蓝/绿/琥珀/紫/青）
const GRADIENTS = [
  'linear-gradient(135deg,#c13b30,#8f2a21)',
  'linear-gradient(135deg,#2563a8,#1a4a80)',
  'linear-gradient(135deg,#2e7d4f,#1f5c39)',
  'linear-gradient(135deg,#b57908,#8a5d06)',
  'linear-gradient(135deg,#6d4fa1,#4f3877)',
  'linear-gradient(135deg,#0f766e,#0a544f)',
]

const courses = ref([])
const todos = ref({ assignments: [], exams: [], signIns: [] })
const activeCourse = ref(null)
const loading = ref(true)
const showJoin = ref(false)
const joinCourseId = ref('')
const joining = ref(false)

function hashOf(str) {
  return [...(str || '')].reduce((s, c) => s + c.charCodeAt(0), 0)
}
function coverOf(cover) {
  if (
    typeof cover === 'string' &&
    (cover.startsWith('#') || cover.startsWith('linear-gradient') || cover.startsWith('http'))
  )
    return cover
  return GRADIENTS[hashOf(cover) % GRADIENTS.length]
}
function nextTaskText(courseId) {
  const todo = todos.value.assignments.find((t) => Number(t.courseId) === Number(courseId))
  if (todo)
    return todo.dueDate
      ? `作业「${todo.title}」 ${String(todo.dueDate).slice(0, 10)} 截止`
      : `作业「${todo.title}」待提交`
  const exam = todos.value.exams.find((e) => Number(e.courseId) === Number(courseId))
  if (exam)
    return exam.startTime
      ? `考试「${exam.title}」 ${String(exam.startTime).slice(0, 10)} 开考`
      : `考试「${exam.title}」已发布`
  return '暂无待办'
}

async function load() {
  loading.value = true
  try {
    const res = await studentApi.getStudentDashboard()
    const d = res?.data?.data || res?.data || {}
    todos.value = d.todos || { assignments: [], exams: [], signIns: [] }
    courses.value = (d.courses || []).map((c) => ({
      courseId: c.courseId,
      name: c.courseName || `课程 #${c.courseId}`,
      progress: Math.round(c.progress || 0),
      cover: c.cover,
      coverStyle: coverOf(c.cover),
      meta: '已加入',
      nextTask: '',
    }))
    if (courses.value.length) selectCourse(courses.value[0])
  } catch (e) {
    toast.show('课程数据加载失败：' + (e?.message || '网络错误'), 'error')
  } finally {
    loading.value = false
  }
}

async function selectCourse(c) {
  activeCourse.value = { ...c, nextTask: nextTaskText(c.courseId) }
  try {
    const res = await studentApi.getCourseDetail(c.courseId)
    const d = res?.data?.data || res?.data || {}
    const course = d.course || {}
    const parts = []
    if (course.semester) parts.push(course.semester)
    if (course.credits) parts.push(`${course.credits} 学分`)
    if (course.category) parts.push(course.category)
    activeCourse.value = {
      ...activeCourse.value,
      code: course.code,
      meta: parts.length ? parts.join(' · ') : '已加入',
      description: course.description,
    }
  } catch (e) {
    /* 详情补全失败不阻塞列表展示 */
  }
}

async function doJoin() {
  const courseId = Number(joinCourseId.value)
  if (!courseId) {
    toast.show('请输入有效的课程 ID', 'error')
    return
  }
  joining.value = true
  try {
    await studentApi.enrollCourse(courseId)
    toast.show('已加入课程', 'ok')
    showJoin.value = false
    joinCourseId.value = ''
    await load()
  } catch (e) {
    toast.show(e?.response?.data?.message || '加入失败，请确认课程 ID 是否正确', 'error')
  } finally {
    joining.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sa-title {
  font-size: 13.5px;
}
.sa-spacer {
  flex: 1;
}
.detail-card {
  margin-bottom: 14px;
}
.detail-body {
  display: flex;
  gap: 16px;
  align-items: center;
}
.detail-cover {
  width: 72px;
  height: 72px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  color: #fff;
  flex-shrink: 0;
}
.detail-info {
  flex: 1;
  min-width: 0;
}
.detail-name {
  font-size: 17px;
  font-weight: 700;
}
.ring-block {
  text-align: center;
}
.todo-text {
  font-size: 12px;
  color: var(--ink-2);
  line-height: 2;
}
.join-input {
  letter-spacing: 2px;
  text-align: center;
  font-size: 16px;
}
</style>
