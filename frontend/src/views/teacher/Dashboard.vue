<template>
  <div>
    <PageHeader title="教师工作台" subtitle="课程概览、教学数据与学生情况一览" icon="dashboard" />

    <div class="stat-grid animate-in">
      <StatCard label="在授课程" icon="book"
        ><template #value><CountUp :value="Number(stats.courseCount ?? 0)" /></template
      ></StatCard>
      <StatCard label="学生总数" icon="layers" tone="var(--info-soft)" tone-color="var(--info)"
        ><template #value><CountUp :value="Number(stats.studentCount ?? 0)" /></template
      ></StatCard>
      <StatCard label="待批作业" icon="edit" tone="var(--warn-soft)" tone-color="var(--warn)"
        ><template #value><CountUp :value="Number(stats.pendingGrading ?? 0)" /></template
      ></StatCard>
      <StatCard
        label="平均成绩"
        :value="stats.avgScore ?? '—'"
        icon="chart"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <section class="l1-card animate-in d1">
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
            <path
              d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5c0 1.38 2.5 2.5 2.5 2.5H20V2H6.5A2.5 2.5 0 0 0 4 4.5z"
            /></svg
        ></span>
        <div>
          <div class="l1-title">我的课程</div>
          <div class="l1-sub">
            点击卡片进入课程空间 · 班级数据完全隔离 · 右侧更多菜单可进行课程操作
          </div>
        </div>
        <div class="l1-extra">
          <button class="btn btn-primary" @click="showWizard = true">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 5v14M5 12h14" />
            </svg>
            新建课程
          </button>
        </div>
      </div>
      <div class="l1-body">
        <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
        <div v-else-if="courses.length === 0" class="empty-state">
          <div class="e-title">还没有课程</div>
          <div class="e-desc">点击右上角「新建课程」创建第一门课程</div>
        </div>
        <div v-else class="course-grid">
          <div
            v-for="c in courses"
            :key="c.courseId"
            class="course-card"
            @click="$router.push(`/teacher/courses/${c.courseId}`)"
          >
            <div class="course-cover" :style="{ background: coverOf(c.coverImage) }">
              <span class="cc-code mono"
                >{{ c.code || '#' + c.courseId
                }}<span v-if="c.category"> · {{ c.category }}</span></span
              >
              <span class="cc-name">{{ c.name }}</span>
              <span v-if="c.pendingGrading" class="cc-menu"
                ><span class="tag red cc-badge">{{ c.pendingGrading }} 待批</span></span
              >
            </div>
            <div class="course-meta">
              <div class="cm-tags">
                <span class="tag blue">{{ c.studentCount ?? 0 }} 名学生</span>
                <span class="tag outline">平均进度 {{ c.avgProgress ?? 0 }}%</span>
                <span v-if="c.completionRate != null" class="tag green"
                  >完成率 {{ c.completionRate }}%</span
                >
              </div>
              <div class="course-foot">
                <span class="cf-label">{{ c.status === 'DRAFT' ? '草稿' : '已发布' }}</span>
                <span class="op-link" @click.stop="$router.push(`/teacher/courses/${c.courseId}`)"
                  >进入课程空间</span
                >
              </div>
            </div>
          </div>
          <div class="course-card add-card" @click="showWizard = true">
            <div class="add-card-inner">
              <div class="add-card-ico">
                <svg
                  viewBox="0 0 24 24"
                  width="22"
                  height="22"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </div>
              <div class="strong add-card-title">新建课程</div>
              <div class="small">三步向导快速开课</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 新建课程向导弹窗 -->
    <Modal v-if="showWizard" wide @close="showWizard = false">
      <template #title>新建课程</template>
      <template #sub>向导式开课：基本信息 → 确认发布</template>
      <div>
        <div class="steps">
          <div class="step" :class="{ active: step === 0, done: step > 0 }">
            <span class="s-dot">{{ step > 0 ? '✓' : '1' }}</span
            >基本信息
          </div>
          <div class="step-line"></div>
          <div class="step" :class="{ active: step === 1 }">
            <span class="s-dot">2</span>确认发布
          </div>
        </div>
        <div v-if="step === 0" class="form-grid">
          <div class="field">
            <label>课程名称<span class="req">*</span></label
            ><input v-model="form.name" class="input" placeholder="如：计算机网络" />
          </div>
          <div class="field">
            <label>课程号</label
            ><input v-model="form.code" class="input" placeholder="如：CS3101" />
          </div>
          <div class="field">
            <label>学分</label
            ><select v-model="form.credits" class="select">
              <option v-for="v in ['2.0', '2.5', '3.0', '3.5', '4.0', '5.0']" :key="v" :value="v">
                {{ v }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>课程分类</label
            ><select v-model="form.category" class="select">
              <option
                v-for="v in ['专业核心', '专业基础', '通识教育', '实践实训']"
                :key="v"
                :value="v"
              >
                {{ v }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>开课学期</label
            ><input v-model="form.semester" class="input" placeholder="如：2025-2026-2" />
          </div>
          <div class="field">
            <label>难度</label
            ><select v-model="form.difficulty" class="select">
              <option v-for="v in ['初级', '中级', '高级']" :key="v" :value="v">{{ v }}</option>
            </select>
          </div>
          <div class="field span-2">
            <label>课程简介</label
            ><textarea
              v-model="form.description"
              class="textarea"
              placeholder="课程目标、主要内容、先修要求…"
            ></textarea>
          </div>
        </div>
        <div v-if="step === 1" class="wizard-done">
          <div class="wizard-done-ico">
            <svg
              viewBox="0 0 24 24"
              width="26"
              height="26"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5" />
            </svg>
          </div>
          <div class="strong wizard-done-title">课程「{{ form.name || '未命名' }}」信息已就绪</div>
          <div class="small muted">发布后课程将出现在学生端课程大厅，可凭课程 ID 加入</div>
        </div>
      </div>
      <template #footer>
        <button v-if="step > 0" class="btn btn-plain" @click="step--">上一步</button>
        <button v-if="step < 1" class="btn btn-primary" @click="nextStep()">下一步</button>
        <button
          v-if="step === 1"
          class="btn btn-primary"
          :disabled="creating"
          @click="finishCreate()"
        >
          {{ creating ? '创建中…' : '发布课程' }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CountUp from '@/components/CountUp.vue'
import Modal from '@/components/ui/Modal.vue'
import { toast } from '@/composables/useToast'
import teacherApi from '@/api/teacher'

// 渐变封面：色值对齐设计令牌（赭红 / 蓝 / 绿 / 紫 / 琥珀 / 青）
const GRADIENTS = [
  'linear-gradient(135deg,#c13b30,#8f2a21)',
  'linear-gradient(135deg,#2563a8,#1a4a80)',
  'linear-gradient(135deg,#2e7d4f,#1f5c39)',
  'linear-gradient(135deg,#b57908,#8a5d06)',
  'linear-gradient(135deg,#6d4fa1,#4f3877)',
  'linear-gradient(135deg,#0f766e,#0a544f)',
]

const stats = ref({ courseCount: 0, studentCount: 0, pendingGrading: 0, avgScore: null })
const courses = ref([])
const loading = ref(true)

function hashOf(s) {
  let h = 0
  for (const ch of String(s || '')) h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return h
}
function coverOf(src) {
  if (typeof src === 'string' && src.startsWith('linear-gradient')) return src
  return GRADIENTS[hashOf(src || '') % GRADIENTS.length]
}

async function load() {
  loading.value = true
  try {
    const res = await teacherApi.getTeacherDashboardV2()
    const d = res?.data?.data || res?.data || {}
    stats.value = {
      courseCount: d.courseCount ?? 0,
      studentCount: d.studentCount ?? 0,
      pendingGrading: d.pendingGrading ?? 0,
      avgScore: d.avgScore != null ? d.avgScore : null,
    }
    courses.value = d.courseCards || []
  } catch {
    toast.show('课程数据加载失败', 'error')
  } finally {
    loading.value = false
  }
}

// ---------- 新建课程向导 ----------
const showWizard = ref(false)
const step = ref(0)
const creating = ref(false)
const form = reactive({
  name: '',
  code: '',
  credits: '3.0',
  category: '专业核心',
  semester: '',
  difficulty: '初级',
  description: '',
})

function nextStep() {
  if (!form.name.trim()) {
    toast.show('请填写课程名称', 'warning')
    return
  }
  step.value = 1
}

async function finishCreate() {
  creating.value = true
  try {
    const payload = {
      name: form.name.trim(),
      code: form.code.trim() || null,
      credits: parseFloat(form.credits),
      category: form.category,
      semester: form.semester.trim() || null,
      difficulty: form.difficulty,
      description: form.description.trim() || null,
    }
    const res = await teacherApi.createCourse(payload)
    if (res?.data?.code === 200 || res?.data?.data) {
      toast.show('课程已创建并发布', 'ok')
      showWizard.value = false
      step.value = 0
      Object.assign(form, {
        name: '',
        code: '',
        credits: '3.0',
        category: '专业核心',
        semester: '',
        difficulty: '初级',
        description: '',
      })
      load()
    } else {
      toast.show(res?.data?.message || '创建失败', 'error')
    }
  } catch {
    toast.show('创建失败，请检查课程信息', 'error')
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cc-badge {
  background: rgba(255, 255, 255, 0.92);
  color: var(--accent);
}

.add-card {
  align-items: center;
  justify-content: center;
  border: 1.5px dashed var(--line-strong);
  background: var(--surface-2);
  box-shadow: none;
  min-height: 220px;
  cursor: pointer;
}
.add-card-inner {
  text-align: center;
  color: var(--ink-3);
}
.add-card-ico {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--surface);
  display: grid;
  place-items: center;
  margin: 0 auto 10px;
  box-shadow: var(--sh-l2);
}
.add-card-title {
  color: var(--ink-2);
}

.wizard-done {
  text-align: center;
  padding: 26px 0;
}
.wizard-done-ico {
  width: 62px;
  height: 62px;
  border-radius: 18px;
  margin: 0 auto 12px;
  background: var(--ok-soft);
  color: var(--ok);
  display: grid;
  place-items: center;
}
.wizard-done-title {
  color: var(--ink-2);
  margin-bottom: 4px;
}
</style>
