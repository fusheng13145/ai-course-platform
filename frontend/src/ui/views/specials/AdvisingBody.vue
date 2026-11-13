<template>
  <section class="advising-body">
    <div class="filter-row class-tools">
      <select
        v-model="classId"
        class="input filter-select"
        aria-label="选择行政班"
        @change="loadClassData"
      >
        <option :value="null" disabled>请选择行政班</option>
        <option v-for="ac in classes" :key="String(ac.id)" :value="ac.id">
          {{ ac.name }}（{{ ac.studentCount ?? 0 }} 人）
        </option>
      </select>
      <button class="btn btn-sm" :disabled="loading" @click="loadClassData">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </div>

    <div v-if="listError" class="callout" role="alert">
      <span>班级治理</span>
      <p>{{ listError }}</p>
    </div>

    <!-- 我的班级 -->
    <section v-if="mode === 'advising'" class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            我的班级 <span class="chip chip-mute">{{ classes.length }} 个</span>
          </h2>
        </div>
      </header>
      <div v-if="classes.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>行政班</th>
              <th class="w-140">编码</th>
              <th class="w-96">在册人数</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ac in classes" :key="String(ac.id)">
              <td>
                <b>{{ ac.name || '—' }}</b
                ><small class="cell-sub">{{ ac.code || '' }}</small>
              </td>
              <td class="mono">{{ ac.code || '—' }}</td>
              <td>{{ ac.studentCount ?? 0 }}</td>
              <td>
                <button class="text-btn" @click="goProfile(ac.id!)">查看画像</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载班级…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无你担任班主任的行政班</b>
        <p>行政班的班主任由管理端「行政班」页指派。</p>
      </div>
    </section>

    <!-- 班级画像 -->
    <section v-if="mode === 'advisingProfile'" class="card table-card">
      <header class="card-head">
        <div>
          <h2>班级画像（聚合）</h2>
        </div>
      </header>
      <div v-if="profile" class="stat-grid kpi-grid">
        <Stat
          label="在册学生"
          :metric="{
            label: '在册学生',
            value: profile.studentCount ?? 0,
            unit: '人',
            icon: 'users',
          }"
          role="teacher"
        />
        <Stat
          label="平均进度"
          :metric="{
            label: '平均进度',
            value: profile.avgProgress ?? 0,
            unit: '%',
            icon: 'chart',
          }"
          role="teacher"
        />
        <Stat
          label="平均总评"
          :metric="{
            label: '平均总评',
            value: profile.avgFinalScore ?? 0,
            unit: '分',
            icon: 'award',
            tone: 'ok',
          }"
          role="teacher"
        />
        <Stat
          label="预警数"
          :metric="{
            label: '预警数',
            value: profile.atRiskCount ?? 0,
            unit: '条',
            icon: 'layers',
            tone: 'warn',
          }"
          role="teacher"
        />
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载画像…</b></div>
      <div v-else class="empty-state compact"><b>请先在右上角选择班级</b></div>
      <p class="hint">
        6A 口径：画像只含聚合值；学生个人成绩在「学生预警」以线索形式给出，由班主任核实。
      </p>
    </section>

    <!-- 学生预警 -->
    <section v-if="mode === 'advisingAlerts'" class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            学生预警 <span class="chip chip-mute">{{ alerts.length }} 条</span>
          </h2>
        </div>
      </header>
      <div v-if="alerts.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th>课程</th>
              <th class="w-96">进度</th>
              <th class="w-96">总评</th>
              <th class="w-140">预警原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in alerts" :key="String(index)">
              <td>
                <b>{{ row.studentName }}</b
                ><small class="cell-sub">#{{ row.studentId }}</small>
              </td>
              <td>{{ row.courseName }}</td>
              <td>{{ row.progress != null ? row.progress + '%' : '—' }}</td>
              <td>{{ row.finalScore != null ? row.finalScore : '—' }}</td>
              <td>
                <span class="chip chip-warn">{{ row.reasons }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载预警…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无预警</b>
        <p>预警口径：进度 &lt; 40 或 总评 &lt; 60。</p>
      </div>
    </section>

    <!-- 班级通知 -->
    <section v-if="mode === 'advisingNotices'" class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            班级通知 <span class="chip chip-mute">{{ notices.length }} 条</span>
          </h2>
        </div>
      </header>
      <div v-if="notices.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题 / 摘要</th>
              <th class="w-140">发布人</th>
              <th class="w-140">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in notices" :key="String(row.id)">
              <td>
                <b>{{ row.title }}</b
                ><small class="cell-sub">{{ brief(row.content) }}</small>
              </td>
              <td>{{ row.publisherRole || '—' }} #{{ row.publisherId ?? '' }}</td>
              <td>{{ brief(row.createdAt, 16) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载通知…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无面向你班级的公告</b>
        <p>管理端「通知公告」发布后按受众扇出，这里自动出现。</p>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Stat from '../../components/Stat.vue'
import request from '@/api/request'
import { useUi } from '../../composables/ui'
import { pickList, unwrap } from '@/utils/response'

interface ClassRow {
  id?: number | string
  name?: string
  code?: string
  studentCount?: number | null
}

interface ProfileRow {
  studentCount?: number
  avgProgress?: number | string | null
  avgFinalScore?: number | string | null
  atRiskCount?: number
}

interface AlertRow {
  studentId?: number | string
  studentName?: string
  courseId?: number | string
  courseName?: string
  progress?: number | null
  finalScore?: number | string | null
  reasons?: string
}

interface NoticeRow {
  id?: number | string
  title?: string
  content?: string
  adminClassId?: number | string | null
  publisherRole?: string
  publisherId?: number | string
  createdAt?: string
}

const route = useRoute()
const { toast } = useUi()
const mode = computed(() => String(route.meta.pageKey || 'advising'))

const classes = ref<ClassRow[]>([])
const classId = ref<number | string | null>(null)
const profile = ref<ProfileRow | null>(null)
const alerts = ref<AlertRow[]>([])
const notices = ref<NoticeRow[]>([])
const loading = ref(false)
const listError = ref('')

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function goProfile(id: NonNullable<ClassRow['id']>) {
  classId.value = id
}

function brief(text: unknown, cap = 60): string {
  const raw = String(text ?? '').replace('T', ' ')
  return raw.length > cap ? raw.slice(0, cap) + '…' : raw || '—'
}

async function loadClasses() {
  loading.value = true
  listError.value = ''
  try {
    classes.value = pickList<ClassRow>(unwrap(await request.get('/advisor/classes')))
    if (classes.value.length && classId.value === null) {
      classId.value = classes.value[0].id ?? null
    }
  } catch (error) {
    listError.value = errorText(error, '班级列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadClassData() {
  if (!classId.value) return
  loading.value = true
  try {
    if (mode.value === 'advisingProfile') {
      profile.value = unwrap(
        await request.get(`/advisor/classes/${classId.value}/profile`),
      ) as ProfileRow
    } else if (mode.value === 'advisingAlerts') {
      alerts.value = pickList<AlertRow>(
        unwrap(await request.get(`/advisor/classes/${classId.value}/alerts`)),
      )
    } else if (mode.value === 'advisingNotices') {
      const payload = unwrap(await request.get('/announcements', { params: { page: 1, size: 20 } }))
      const envelope = payload as { records?: NoticeRow[] }
      notices.value = (envelope?.records || pickList<NoticeRow>(payload)).filter(
        (row) => row.adminClassId != null && String(row.adminClassId) === String(classId.value),
      )
    }
  } catch (error) {
    toast(errorText(error, '班级数据加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

watch(
  () => route.meta.pageKey,
  () => void loadClassData(),
)
watch(classId, () => void loadClassData())

onMounted(async () => {
  await loadClasses()
  await loadClassData()
})
</script>

<style scoped>
.advising-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 240px;
  height: 30px;
}
</style>
