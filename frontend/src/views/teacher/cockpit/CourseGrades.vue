<template>
  <div class="course-grades fade-in">
    <PageHeader compact title="课程成绩" subtitle="成绩方案配置与成绩报告" icon="award">
      <template v-if="policy">
        <el-button :icon="Edit" @click="openPolicyDialog(policy)">编辑方案</el-button>
        <el-button type="primary" :icon="Refresh" :loading="reportLoading" @click="loadReport"
          >刷新报告</el-button
        >
      </template>
    </PageHeader>
    <div v-loading="loading">
      <!-- 无成绩方案：空状态 + 新建 -->
      <div v-if="!policy" class="l1-card">
        <div class="l1-body">
          <el-empty description="该课程尚未配置成绩方案">
            <el-button type="primary" :icon="Plus" @click="openPolicyDialog()"
              >新建成绩方案</el-button
            >
          </el-empty>
        </div>
      </div>

      <!-- 有方案：报告表格 -->
      <template v-else>
        <div v-loading="reportLoading" class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"><el-icon><DataAnalysis /></el-icon></span>
            <div>
              <div class="l1-title">
                课程成绩报告
                <el-tag size="small" type="primary" effect="light" round>{{ policy.name }}</el-tag>
              </div>
              <div v-if="report.length" class="l1-sub">共 {{ report.length }} 名学生</div>
            </div>
          </div>
          <div class="l1-body">
            <el-table v-if="report.length" :data="report" size="small" border max-height="560">
            <el-table-column prop="studentName" label="学生" min-width="110" fixed />
            <el-table-column
              v-for="c in policy.components"
              :key="c.key"
              :label="c.name + ' (' + c.weight + '%)'"
              min-width="120"
              align="center"
            >
              <template #default="{ row }">
                <span class="score-cell" @click="openScoreDialog(row, c)">
                  {{ findScore(row, c.key) ?? '—' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="总评" min-width="90" align="center" fixed="right">
              <template #default="{ row }">
                <span class="final-score">{{ row.finalScore ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="previewFinal(row)"
                  >预览总评</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无学生成绩" :image-size="72" />
          </div>
        </div>
      </template>
    </div>

    <!-- 成绩方案对话框 -->
    <el-dialog
      v-model="policyVisible"
      :title="policyForm.id ? '编辑成绩方案' : '新建成绩方案'"
      width="560px"
    >
      <el-form :model="policyForm" label-width="92px">
        <el-form-item label="方案名称" required>
          <el-input v-model="policyForm.name" placeholder="如：平时成绩+期末" maxlength="40" />
        </el-form-item>
        <el-form-item label="构成项">
          <div class="comp-list">
            <div v-for="(c, i) in policyForm.components" :key="i" class="comp-row">
              <el-input v-model="c.key" placeholder="标识 key" class="comp-key" />
              <el-input v-model="c.name" placeholder="名称" class="comp-name" />
              <el-input-number
                v-model="c.weight"
                :min="0"
                :max="100"
                :step="5"
                controls-position="right"
                class="comp-weight"
              />
              <el-button :icon="Delete" circle plain type="danger" @click="removeComponent(i)" />
            </div>
            <el-button :icon="Plus" plain @click="addComponent">添加构成项</el-button>
          </div>
        </el-form-item>
        <el-alert
          v-if="weightSum !== 100"
          :title="'构成项权重合计为 ' + weightSum + '%，建议合计 100%'"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 录入/编辑分数对话框 -->
    <el-dialog v-model="scoreVisible" title="录入成绩" width="420px">
      <el-descriptions v-if="scoreTarget.studentName" :column="1" border size="small">
        <el-descriptions-item label="学生">{{ scoreTarget.studentName }}</el-descriptions-item>
        <el-descriptions-item label="构成项">{{ scoreTarget.componentName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px" class="form-top">
        <el-form-item label="分数" required>
          <el-input-number
            v-model="scoreTarget.score"
            :min="0"
            :max="100"
            :precision="1"
            :step="1"
            class="w-100"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveScore">保存</el-button>
      </template>
    </el-dialog>

    <!-- 总评预览对话框 -->
    <el-dialog v-model="previewVisible" title="总评预览" width="420px">
      <el-descriptions v-if="previewData" :column="1" border size="small">
        <el-descriptions-item label="学生">{{
          previewData.studentName || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="总评分数">{{
          previewData.finalScore ?? '—'
        }}</el-descriptions-item>
        <el-descriptions-item v-if="previewData.detail" label="明细">{{
          previewData.detail
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import gradeApi from '@/api/grade'
import PageHeader from '@/components/PageHeader.vue'
import { Plus, Edit, Refresh, Delete, View, DataAnalysis } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const reportLoading = ref(false)
const saving = ref(false)

const policy = ref(null)
const report = ref([])

/* 成绩方案对话框 */
const policyVisible = ref(false)
const policyForm = reactive({ id: null, courseId: courseId, name: '', components: [] })
const weightSum = computed(() =>
  policyForm.components.reduce((s, c) => s + (Number(c.weight) || 0), 0),
)

/* 分数录入对话框 */
const scoreVisible = ref(false)
const scoreTarget = reactive({
  studentId: '',
  studentName: '',
  componentKey: '',
  componentName: '',
  score: 0,
})

/* 总评预览 */
const previewVisible = ref(false)
const previewData = ref(null)

function findScore(row, key) {
  const c = (row.components || []).find((x) => x.key === key)
  return c ? c.score : null
}

function addComponent() {
  policyForm.components.push({ key: '', name: '', weight: 0 })
}
function removeComponent(i) {
  policyForm.components.splice(i, 1)
}

function openPolicyDialog(existing) {
  if (existing) {
    policyForm.id = existing.id
    policyForm.name = existing.name
    policyForm.components = (existing.components || []).map((c) => ({ ...c }))
  } else {
    policyForm.id = null
    policyForm.name = ''
    policyForm.components = [
      { key: 'usual', name: '平时', weight: 40 },
      { key: 'final', name: '期末', weight: 60 },
    ]
  }
  policyVisible.value = true
}

async function savePolicy() {
  if (!policyForm.name.trim()) {
    ElMessage.warning('请填写方案名称')
    return
  }
  if (!policyForm.components.length) {
    ElMessage.warning('请至少添加一个构成项')
    return
  }
  for (const c of policyForm.components) {
    if (!c.key.trim() || !c.name.trim()) {
      ElMessage.warning('构成项的标识与名称均不能为空')
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      courseId: courseId,
      name: policyForm.name.trim(),
      components: policyForm.components.map((c) => ({
        key: c.key.trim(),
        name: c.name.trim(),
        weight: Number(c.weight) || 0,
      })),
    }
    const r = await gradeApi.saveGradePolicy(payload)
    const body = unwrap(r)
    ElMessage.success('成绩方案已保存')
    policyVisible.value = false
    policy.value = body || {
      id: policyForm.id,
      courseId,
      name: payload.name,
      components: payload.components,
    }
    await loadReport()
  } catch (e) {
    logger.error('保存成绩方案失败', e)
  } finally {
    saving.value = false
  }
}

async function loadReport() {
  if (!policy.value) return
  reportLoading.value = true
  try {
    const r = await gradeApi.getCourseReport(courseId)
    const body = unwrap(r)
    report.value = body?.records || body?.list || body || []
  } catch (e) {
    logger.error('加载成绩报告失败', e)
  } finally {
    reportLoading.value = false
  }
}

function openScoreDialog(row, component) {
  scoreTarget.studentId = row.studentId
  scoreTarget.studentName = row.studentName
  scoreTarget.componentKey = component.key
  scoreTarget.componentName = component.name
  const existing = findScore(row, component.key)
  scoreTarget.score = existing != null ? Number(existing) : 0
  scoreVisible.value = true
}

async function saveScore() {
  if (!policy.value) return
  saving.value = true
  try {
    const payload = {
      policyId: policy.value.id,
      studentId: scoreTarget.studentId,
      componentKey: scoreTarget.componentKey,
      score: scoreTarget.score,
    }
    const r = await gradeApi.saveGradeItem(payload)
    const body = unwrap(r)
    ElMessage.success('成绩已保存')
    scoreVisible.value = false
    await loadReport()
    void body
  } catch (e) {
    logger.error('保存成绩项失败', e)
  } finally {
    saving.value = false
  }
}

async function previewFinal(row) {
  if (!policy.value) return
  try {
    const r = await gradeApi.computeFinal(policy.value.id, row.studentId)
    const body = unwrap(r)
    previewData.value = body || { studentName: row.studentName, finalScore: row.finalScore }
    previewVisible.value = true
  } catch (e) {
    logger.error('计算总评失败', e)
  }
}

async function loadPolicy() {
  loading.value = true
  try {
    const r = await gradeApi.getGradePolicy(courseId)
    const body = unwrap(r)
    policy.value = body || null
    if (policy.value) await loadReport()
  } catch (e) {
    logger.error('加载成绩方案失败', e)
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
.score-cell {
  display: inline-block;
  min-width: 44px;
  padding: 2px 8px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  cursor: pointer;
  font-weight: 600;
  transition: background var(--ac-transition-fast);
}
.score-cell:hover {
  background: var(--ac-primary);
  color: var(--ac-on-primary);
}
.final-score {
  font-weight: 700;
  color: var(--ac-text-primary);
}

.form-top {
  margin-top: 16px;
}
.w-100 {
  width: 100%;
}

.comp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.comp-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.comp-key {
  width: 130px;
}
.comp-name {
  flex: 1;
}
.comp-weight {
  width: 130px;
}
</style>
