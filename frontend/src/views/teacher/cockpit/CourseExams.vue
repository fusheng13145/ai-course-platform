<template>
  <div class="course-exams fade-in">
    <PageHeader compact title="课程试卷" subtitle="试卷管理、发布与统计" icon="document">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建试卷</el-button>
    </PageHeader>
    <div v-loading="loading">
      <!-- 试卷表格（列表 + 分页 + 行操作） -->
      <CeExamTable
        :list="list"
        :total="total"
        :page="page"
        :size="size"
        @publish="publish"
        @view-questions="viewQuestions"
        @view-stats="viewStats"
        @copy="copy"
        @edit="openEdit"
        @remove="remove"
        @page-change="applyPage"
        @size-change="onSizeChange"
      />
    </div>

    <!-- 新建 / 编辑 对话框 -->
    <CeExamFormDialog
      v-model="dialog.visible"
      :editing="dialog.editing"
      :saving="dialog.saving"
      :form="form"
      @save="save"
      @closed="resetForm"
    />

    <!-- 查看题目 对话框 -->
    <!-- v-loading 不能挂在 <el-dialog> 上：弹层渲染在 teleport 的非元素根上，
         runtime directive 无处安放（Vue 告警 + 遮罩实际不生效），改由 dialog 内的包裹层承担。 -->
    <el-dialog v-model="ques.visible" title="试卷题目" width="640px">
      <div v-loading="ques.loading">
        <el-table :data="ques.list" size="small" empty-text="暂无题目" max-height="420">
          <el-table-column label="#" width="56">
            <template #default="{ row }">{{ row.orderIndex ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="题干" min-width="320">
            <template #default="{ row }">{{
              row.question?.content || row.question?.stem || row.question?.title || '（无题干）'
            }}</template>
          </el-table-column>
          <el-table-column label="类型" width="92">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.question?.type)" effect="light">{{
                typeLabel(row.question?.type)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分值" width="70">
            <template #default="{ row }">{{ row.points ?? '--' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 统计 对话框 -->
    <el-dialog v-model="stat.visible" title="试卷统计" width="620px">
      <div v-loading="stat.loading">
        <div v-if="stat.items.length" class="stat-grid">
          <div v-for="(it, i) in stat.items" :key="i" class="mini-stat">
            <span class="mini-num">{{ it.value }}</span>
            <span class="mini-label">{{ it.label }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无统计数据" :image-size="70" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import PageHeader from '@/components/PageHeader.vue'
import CeExamTable from './components/CeExamTable.vue'
import CeExamFormDialog from './components/CeExamFormDialog.vue'

/**
 * 课程试卷页（拆分子组件后页面壳）。
 * <p>职责收敛：试卷数据加载/分页切片/CRUD 操作；表格（CeExamTable）
 * 与新建编辑表单（CeExamFormDialog）分别下沉。</p>
 */
const route = useRoute()
const courseId = computed(() => Number(route.params.id))

const loading = ref(false)
const all = ref([])
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const TYPE_OPTS = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTIPLE', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'ESSAY', label: '简答题' },
  { value: 'CODE', label: '编程题' },
]
function typeLabel(t) {
  return TYPE_OPTS.find((o) => o.value === t)?.label || t || '未知'
}
function typeTag(t) {
  return (
    {
      SINGLE: 'primary',
      MULTIPLE: 'success',
      JUDGE: 'warning',
      FILL: 'info',
      ESSAY: '',
      CODE: 'danger',
    }[t] || 'info'
  )
}

async function load() {
  loading.value = true
  try {
    all.value = await examApi.getAllExamPapersByCourse(courseId.value)
    total.value = all.value.length
    applyPage()
  } catch (e) {
    logger.error('加载试卷失败', e)
  } finally {
    loading.value = false
  }
}

// listByCourse 仅按课程返回，前端做分页切片保证 el-pagination 可用
function applyPage() {
  const start = (page.value - 1) * size.value
  list.value = all.value.slice(start, start + size.value)
}

function onSizeChange() {
  page.value = 1
  applyPage()
}

// 新建 / 编辑
const dialog = reactive({ visible: false, editing: false, saving: false })
const form = reactive({ id: null, title: '', description: '', duration: 60, totalScore: 100 })

function resetForm() {
  form.id = null
  form.title = ''
  form.description = ''
  form.duration = 60
  form.totalScore = 100
  dialog.editing = false
}
function openCreate() {
  resetForm()
  dialog.editing = false
  dialog.visible = true
}
function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    title: row.title || '',
    description: row.description || '',
    duration: row.duration || 60,
    totalScore: row.totalScore || 100,
  })
  dialog.editing = true
  dialog.visible = true
}
async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入试卷标题')
    return
  }
  dialog.saving = true
  try {
    if (dialog.editing) {
      await examApi.updateExamPaper(form.id, {
        title: form.title,
        description: form.description,
        duration: form.duration,
        totalScore: form.totalScore,
      })
      ElMessage.success('试卷已更新')
    } else {
      await examApi.createExamPaper({
        courseId: courseId.value,
        title: form.title,
        description: form.description,
        duration: form.duration,
        totalScore: form.totalScore,
      })
      ElMessage.success('试卷已创建')
    }
    dialog.visible = false
    load()
  } catch (e) {
    logger.error('保存试卷失败', e)
  } finally {
    dialog.saving = false
  }
}

async function publish(row) {
  try {
    await examApi.publishExam(row.id)
    ElMessage.success('试卷已发布')
    load()
  } catch (e) {
    logger.error('发布试卷失败', e)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除试卷「${row.title}」？此操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await examApi.deleteExamPaper(row.id)
    ElMessage.success('试卷已删除')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.toLowerCase) logger.error('删除试卷失败', e)
  }
}

async function copy(row) {
  try {
    await examApi.copyExam(row.id, { title: `${row.title}（副本）` })
    ElMessage.success('已复制试卷')
    load()
  } catch (e) {
    logger.error('复制试卷失败', e)
  }
}

// 查看题目
const ques = reactive({ visible: false, loading: false, list: [] })
async function viewQuestions(row) {
  ques.visible = true
  ques.loading = true
  ques.list = []
  try {
    const r = await examApi.getExamQuestions(row.id)
    const body = unwrap(r)
    ques.list = Array.isArray(body) ? body : body?.records || body?.list || []
  } catch (e) {
    logger.error('加载试卷题目失败', e)
  } finally {
    ques.loading = false
  }
}

// 统计
const STAT_LABELS = {
  totalStudents: '参考人数',
  submitted: '已提交',
  averageScore: '平均分',
  highestScore: '最高分',
  lowestScore: '最低分',
  passCount: '及格人数',
  passRate: '及格率',
  excellentCount: '优秀人数',
  excellentRate: '优秀率',
  avgDuration: '平均用时',
  submitRate: '提交率',
}
const stat = reactive({ visible: false, loading: false, items: [] })
async function viewStats(row) {
  stat.visible = true
  stat.loading = true
  stat.items = []
  try {
    const r = await examApi.getExamStatistics(row.id)
    const body = unwrap(r) ?? {}
    stat.items = Object.entries(body)
      .filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([k, v]) => ({
        label: STAT_LABELS[k] || k,
        value: typeof v === 'number' ? v : String(v),
      }))
  } catch (e) {
    logger.error('加载试卷统计失败', e)
  } finally {
    stat.loading = false
  }
}

onMounted(load)
watch(
  () => route.params.id,
  () => {
    page.value = 1
    load()
  },
)
</script>

<style scoped>
.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 14px 8px;
  background: var(--ac-surface-2);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
}
.mini-num {
  font-size: 18px;
  font-weight: 660;
  color: var(--ac-text-primary);
}
.mini-label {
  font-size: 11.5px;
  color: var(--ac-text-muted);
}
</style>
