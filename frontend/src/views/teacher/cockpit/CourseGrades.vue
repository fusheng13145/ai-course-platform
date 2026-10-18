<template>
  <div class="course-grades fade-in">
    <PageHeader compact title="课程成绩" subtitle="成绩方案配置与成绩报告" icon="award">
      <template v-if="policy">
        <el-button :icon="Edit" @click="policyVisible = true">编辑方案</el-button>
        <el-button type="primary" :icon="Refresh" :loading="reportLoading" @click="loadReport"
          >刷新报告</el-button
        >
      </template>
    </PageHeader>
    <!-- 加载失败提示：见 loadError 注释（失败不得渲染成「尚未配置成绩方案 / 暂无学生成绩」） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />
    <div v-loading="loading">
      <CgReportTable
        :policy="policy"
        :report="report"
        :report-loading="reportLoading"
        :empty-text="
          reportError ? '成绩报告未能加载，请查看上方提示后重试' : '暂无学生成绩'
        "
        :policy-empty-text="
          policyError ? '成绩方案未能加载，请查看上方提示后重试' : '该课程尚未配置成绩方案'
        "
        @open-policy="policyVisible = true"
        @open-score="openScoreDialog"
        @preview="openPreview"
      />
    </div>

    <!-- 成绩方案对话框 -->
    <CgPolicyDialog
      v-model="policyVisible"
      :policy="policy"
      :course-id="courseId"
      @saved="onPolicySaved"
    />

    <!-- 录入/编辑分数对话框 -->
    <CgScoreDialog
      v-model="scoreVisible"
      :target="scoreTarget"
      :policy-id="policy?.id"
      @saved="loadReport"
    />

    <!-- 总评预览对话框 -->
    <CgPreviewDialog v-model="previewVisible" :policy-id="policy?.id" :student="previewStudent" />
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import gradeApi from '@/api/grade'
import PageHeader from '@/components/PageHeader.vue'
import { Edit, Refresh } from '@element-plus/icons-vue'
import CgReportTable from './components/CgReportTable.vue'
import CgPolicyDialog from './components/CgPolicyDialog.vue'
import CgScoreDialog from './components/CgScoreDialog.vue'
import CgPreviewDialog from './components/CgPreviewDialog.vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const reportLoading = ref(false)

const policy = ref(null)
const report = ref([])
/**
 * 成绩方案 / 成绩报告两路失败信号独立记录，再聚合为 loadError 供页面持久提示。
 * 原实现失败仅 logger.error 后静默 → 页面显示「该课程尚未配置成绩方案 / 暂无学生成绩」，属误导。
 */
const policyError = ref('')
const reportError = ref('')
const loadError = computed(() => policyError.value || reportError.value)

const policyVisible = ref(false)
const scoreVisible = ref(false)
const previewVisible = ref(false)

const scoreTarget = reactive({
  studentId: '',
  studentName: '',
  componentKey: '',
  componentName: '',
  score: 0,
})
const previewStudent = ref({})

function openScoreDialog(row, component) {
  scoreTarget.studentId = row.studentId
  scoreTarget.studentName = row.studentName
  scoreTarget.componentKey = component.key
  scoreTarget.componentName = component.name
  const existing = (row.components || []).find((x) => x.key === component.key)
  // existing 是构成项对象，取它的 score；未评分（null）从 0 起录
  scoreTarget.score = existing?.score != null ? Number(existing.score) : 0
  scoreVisible.value = true
}

function openPreview(row) {
  previewStudent.value = {
    studentId: row.studentId,
    studentName: row.studentName,
    finalScore: row.finalScore,
  }
  previewVisible.value = true
}

function onPolicySaved(saved) {
  policy.value = saved
  loadReport()
}

async function loadReport() {
  if (!policy.value) return
  reportLoading.value = true
  reportError.value = ''
  try {
    const r = await gradeApi.getCourseReport(courseId)
    const body = unwrap(r)
    report.value = body?.records || body?.list || body || []
  } catch (e) {
    logger.error('加载成绩报告失败', e)
    report.value = []
    reportError.value = '成绩报告加载失败，请检查网络后重试'
  } finally {
    reportLoading.value = false
  }
}

async function loadPolicy() {
  loading.value = true
  policyError.value = ''
  try {
    const r = await gradeApi.getGradePolicy(courseId)
    const body = unwrap(r)
    policy.value = body || null
    if (policy.value) await loadReport()
  } catch (e) {
    logger.error('加载成绩方案失败', e)
    policy.value = null
    policyError.value = '成绩方案加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadPolicy)
watch(
  () => route.params.id,
  () => {
    policy.value = null
    report.value = []
    loadPolicy()
  },
)
</script>

<style scoped>
.course-grades {
  padding: 4px 0;
}
</style>
