<template>
  <div class="fade-in question-management">
    <PageHeader
      title="题库管理"
      subtitle="题目库维护与AI智能出题"
      icon="doc"
      icon-bg="accent"
    >
      <el-button type="primary" plain @click="showAIDialog = true">
        <el-icon><MagicStick /></el-icon>AI 智能出题
      </el-button>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>添加题目
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="总题目数" :value="questionStats[0].value" icon="doc" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="今日新增" :value="questionStats[1].value" icon="clock" tone="var(--info-soft)" tone-color="var(--info)" />
      <StatCard label="待审核" :value="questionStats[2].value" icon="warn" tone="var(--warn-soft)" tone-color="var(--warn)" />
      <StatCard label="易错题数" :value="questionStats[3].value" icon="chart" tone="var(--ok-soft)" tone-color="var(--ok)" />
    </div>

    <!-- 筛选工具栏 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background:var(--accent-soft);color:var(--accent)">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
        </span>
        <div><div class="l1-title">题目列表</div><div class="l1-sub">筛选题型与难度，维护题目库</div></div>
      </div>
      <div class="l1-body">
    <div class="filter-bar">
      <div class="filter-left">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索题目内容"
          clearable
          style="width:240px"
          @keyup.enter="fetchQuestions"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.type" placeholder="题型筛选" clearable style="width:130px" @change="fetchQuestions">
          <el-option label="全部" value="" />
          <el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" />
          <el-option label="判断题" value="TRUE_FALSE" />
          <el-option label="填空题" value="FILL_BLANK" />
          <el-option label="简答题" value="SHORT_ANSWER" />
          <el-option label="编程题" value="CODING" />
        </el-select>
        <el-select v-model="filters.difficulty" placeholder="难度筛选" clearable style="width:120px" @change="fetchQuestions">
          <el-option label="全部" value="" />
          <el-option label="简单" value="EASY" />
          <el-option label="中等" value="MEDIUM" />
          <el-option label="困难" value="HARD" />
        </el-select>
        <el-select v-model="filters.courseId" placeholder="课程筛选" clearable style="width:160px" @change="fetchQuestions">
          <el-option
            v-for="course in courseList"
            :key="course.id"
            :label="course.title || course.name"
            :value="course.id"
          />
        </el-select>
        <el-button type="primary" @click="fetchQuestions"><el-icon><Search /></el-icon></el-button>
      </div>
      <div class="filter-right">
        <el-button text type="primary" size="small" @click="viewErrorProne">易错题榜</el-button>
        <el-button text type="primary" size="small" @click="handleExport">批量导出</el-button>
        <el-button text type="primary" size="small" @click="handleImport">批量导入</el-button>
      </div>
    </div>

    <!-- 题目列表 -->
    <div class="content-card">
      <el-table v-loading="loading" :data="questionList" style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />

        <el-table-column label="题型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="getTypeTagType(row.type)">
              {{ getTypeText(row.type) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="question-content">{{ row.content }}</span>
          </template>
        </el-table-column>

        <el-table-column label="难度" width="110" align="center">
          <template #default="{ row }">
            <el-rate
              :model-value="getDifficultyValue(row.difficulty)"
              disabled
              size="small"
              :colors="['#8BA888', '#CBB58A', '#C48080']"
              show-text
              :texts="['简单', '中等', '困难']"
            />
          </template>
        </el-table-column>

        <el-table-column prop="courseName" label="所属课程" width="140" show-overflow-tooltip />

        <el-table-column label="使用次数" width="90" align="center">
          <template #default="{ row }">
            <span class="usage-count">{{ row.usageCount || 0 }}次</span>
          </template>
        </el-table-column>

        <el-table-column label="正确率" width="90" align="center">
          <template #default="{ row }">
            <span
              class="accuracy"
              :class="getAccuracyClass(row.correctRate)"
            >{{ row.correctRate ? (row.correctRate * 100).toFixed(0) + '%' : '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="editQuestion(row)">编辑</el-button>
            <el-button text type="warning" size="small" @click="viewStats(row)">统计</el-button>
            <el-button text type="danger" size="small" @click="deleteQuestion(row)">删除</el-button>
          </template>
        </el-table-column>
        <!-- 空状态 -->
        <template #empty>
          <el-empty description="暂无题目数据">
            <template #image><div class="empty-deco">📚</div></template>
          </el-empty>
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="fetchQuestions"
          @current-change="fetchQuestions"
        />
      </div>
    </div>
    </div>
    </section>

    <!-- 创建/编辑题目对话框 -->
    <el-dialog
      v-model="showEditDialog"
      :title="isEditing ? '编辑题目' : '添加题目'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="questionFormRef" :model="questionForm" :rules="questionRules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="题型" prop="type">
              <el-select v-model="questionForm.type" style="width:100%">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
                <el-option label="编程题" value="CODING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="questionForm.difficulty" style="width:100%">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="questionForm.courseId" filterable style="width:100%" placeholder="选择课程">
            <el-option
              v-for="course in courseList"
              :key="course.id"
              :label="course.title || course.name"
              :value="course.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题目内容" prop="content">
          <el-input
            v-model="questionForm.content"
            type="textarea"
            :rows="3"
            placeholder="请输入题目内容（支持Markdown格式）"
          />
        </el-form-item>

        <!-- 选择题选项 -->
        <template v-if="isChoiceType(questionForm.type)">
          <el-form-item label="选项设置">
            <div class="options-editor">
              <div
                v-for="(option, idx) in questionForm.options"
                :key="idx"
                class="option-item"
              >
                <span class="option-label">{{ optionLabels[idx] }}</span>
                <el-input v-model="option.content" placeholder="选项内容" />
                <el-checkbox v-model="option.isCorrect">正确答案</el-checkbox>
                <el-button
                  v-if="questionForm.options.length > 2"
                  text
                  type="danger"
                  size="small"
                  @click="removeOption(idx)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button
                v-if="questionForm.options.length < 8 && questionForm.type === 'SINGLE_CHOICE'"
                text
                type="primary"
                size="small"
                @click="addOption"
              ><el-icon><Plus /></el-icon>添加选项</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 判断题答案 -->
        <el-form-item v-if="questionForm.type === 'TRUE_FALSE'" label="正确答案">
          <el-radio-group v-model="questionForm.answer">
            <el-radio :value="true">正确</el-radio>
            <el-radio :value="false">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 填空/简答/编程题答案 -->
        <el-form-item
          v-if="!isChoiceType(questionForm.type) && questionForm.type !== 'TRUE_FALSE'"
          label="参考答案"
        >
          <el-input
            v-model="questionForm.answer"
            type="textarea"
            :rows="2"
            placeholder="请输入参考答案"
          />
        </el-form-item>

        <el-form-item label="解析说明">
          <el-input
            v-model="questionForm.explanation"
            type="textarea"
            :rows="2"
            placeholder="可选：输入题目解析"
          />
        </el-form-item>

        <el-form-item label="分值" prop="score">
          <el-input-number v-model="questionForm.score" :min="1" :max="100" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI智能出题对话框 -->
    <el-dialog v-model="showAIDialog" title="AI 智能出题" width="650px" destroy-on-close>
      <el-alert
        title="AI将根据您提供的信息自动生成高质量题目，支持多种题型和难度级别"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:20px"
      />

      <el-form ref="aiFormRef" :model="aiForm" :rules="aiRules" label-position="top">
        <el-form-item label="知识点/主题" prop="topic">
          <el-input v-model="aiForm.topic" placeholder="例如：Python基础语法、机器学习算法等" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="题型" prop="type">
              <el-select v-model="aiForm.type" style="width:100%">
                <el-option label="自动混合" value="MIXED" />
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="aiForm.difficulty" style="width:100%">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
                <el-option label="混合难度" value="MIXED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="生成数量" prop="count">
          <el-slider v-model="aiForm.count" :min="1" :max="20" :step="1" show-stops />
        </el-form-item>

        <el-form-item label="所属课程">
          <el-select v-model="aiForm.courseId" filterable clearable style="width:100%" placeholder="选择关联课程（可选）">
            <el-option
              v-for="course in courseList"
              :key="course.id"
              :label="course.title || course.name"
              :value="course.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="额外要求">
          <el-input
            v-model="aiForm.requirements"
            type="textarea"
            :rows="2"
            placeholder="可选：描述特殊要求，如'需要包含代码示例'、'侧重实际应用场景'等"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAIDialog = false">取消</el-button>
        <el-button type="primary" :loading="aiGenerating" @click="generateByAI">
          <el-icon><MagicStick /></el-icon>开始生成
        </el-button>
      </template>
    </el-dialog>

    <!-- 题目统计对话框 -->
    <el-dialog v-model="showStatsDialog" title="题目统计分析" width="550px" destroy-on-close>
      <div v-if="currentStats" class="stats-content">
        <div class="stats-grid">
          <div class="stats-item">
            <span class="label">使用次数</span>
            <span class="value primary">{{ currentStats.usageCount || 0 }}次</span>
          </div>
          <div class="stats-item">
            <span class="label">正确率</span>
            <span class="value success">{{ currentStats.correctRate ? (currentStats.correctRate * 100).toFixed(1) + '%' : '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">平均用时</span>
            <span class="value warning">{{ currentStats.avgTime || '--' }}秒</span>
          </div>
          <div class="stats-item">
            <span class="label">错误次数</span>
            <span class="value danger">{{ currentStats.errorCount || 0 }}次</span>
          </div>
        </div>

        <div v-if="currentStats.recentUsage?.length > 0" class="recent-usage">
          <h4>近期使用记录</h4>
          <el-timeline>
            <el-timeline-item
              v-for="(record, idx) in currentStats.recentUsage.slice(0, 5)"
              :key="idx"
              :timestamp="record.time || record.date"
              placement="top"
              size="small"
            >
              {{ record.examTitle || record.context }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  Plus,
  Search,
  MagicStick,
  Delete
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import questionApi from '@/api/question'
import courseApi from '@/api/course'
import request from '@/api/request'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

// 加载状态
const loading = ref(false)
const saving = ref(false)
const aiGenerating = ref(false)

// 统计数据
const questionStats = ref([
  { label: '总题目数', value: 0 },
  { label: '今日新增', value: 0 },
  { label: '待审核', value: 0 },
  { label: '易错题数', value: 0 }
])

// 筛选条件
const filters = reactive({
  keyword: '',
  type: '',
  difficulty: '',
  courseId: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 数据列表
const questionList = ref([])
const courseList = ref([])

// 对话框状态
const showEditDialog = ref(false)
const showAIDialog = ref(false)
const showStatsDialog = ref(false)
const isEditing = ref(false)
const currentStats = ref(null)
const editingId = ref(null)

// 选项标签
const optionLabels = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']

// 题目表单
const questionFormRef = ref(null)
const questionForm = reactive({
  type: 'SINGLE_CHOICE',
  difficulty: 'MEDIUM',
  courseId: '',
  content: '',
  options: [
    { content: '', isCorrect: false },
    { content: '', isCorrect: false },
    { content: '', isCorrect: false },
    { content: '', isCorrect: false }
  ],
  answer: '',
  explanation: '',
  score: 5
})

const questionRules = {
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  score: [{ required: true, message: '请设置分值', trigger: 'blur' }]
}

// AI表单
const aiFormRef = ref(null)
const aiForm = reactive({
  topic: '',
  type: 'MIXED',
  difficulty: 'MIXED',
  count: 5,
  courseId: '',
  requirements: ''
})

const aiRules = {
  topic: [{ required: true, message: '请输入知识点或主题', trigger: 'blur' }]
}

/**
 * 获取课程列表
 */
async function fetchCourses() {
  try {
    const res = await courseApi.getCourseList({ page: 0, size: 100 })
    const data = res.data?.list || res.data || res || []
    courseList.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.warn('获取课程列表失败:', error)
    courseList.value = []
  }
}

/**
 * 获取题目列表
 */
async function fetchQuestions() {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    }

    if (filters.keyword) params.keyword = filters.keyword
    if (filters.type) params.type = filters.type
    if (filters.difficulty) params.difficulty = filters.difficulty
    if (filters.courseId) params.courseId = filters.courseId

    const res = await questionApi.getQuestionList(params)
    const rawData = res.data?.records || res.data?.list || res.data || res

    if (Array.isArray(rawData)) {
      questionList.value = rawData
      pagination.total = rawData.length
    } else if (rawData?.records || rawData?.items) {
      questionList.value = rawData.records || rawData.items
      pagination.total = rawData.total || questionList.value.length
    }

    // 更新统计
    updateQuestionStats()
  } catch (error) {
    console.error('获取题目列表失败:', error)
    ElMessage.error('加载题目列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 更新题目统计
 */
function updateQuestionStats() {
  questionStats.value[0].value = pagination.total
  // 其他统计数据可以从API获取或计算
}

/**
 * 打开创建对话框
 */
function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  Object.assign(questionForm, {
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: '',
    content: '',
    options: [
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false }
    ],
    answer: '',
    explanation: '',
    score: 5
  })
  showEditDialog.value = true
}

/**
 * 编辑题目
 */
function editQuestion(row) {
  isEditing.value = true
  editingId.value = row.id

  // 填充表单数据
  Object.assign(questionForm, {
    type: row.type || 'SINGLE_CHOICE',
    difficulty: row.difficulty || 'MEDIUM',
    courseId: row.courseId || '',
    content: row.content || '',
    answer: row.answer || '',
    explanation: row.explanation || '',
    score: row.score || 5
  })

  // 处理选择题选项
  if (row.options && Array.isArray(row.options)) {
    questionForm.options = row.options.map(opt => ({
      content: opt.content || opt.text || '',
      isCorrect: opt.isCorrect === true || opt.isAnswer === true
    }))
  } else {
    questionForm.options = [
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false }
    ]
  }

  showEditDialog.value = true
}

/**
 * 保存题目
 */
async function saveQuestion() {
  if (!questionFormRef.value) return

  await questionFormRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      const payload = {
        type: questionForm.type,
        difficulty: questionForm.difficulty,
        courseId: questionForm.courseId,
        content: questionForm.content,
        score: questionForm.score,
        explanation: questionForm.explanation
      }

      // 根据题型处理答案
      if (isChoiceType(questionForm.type)) {
        payload.options = questionForm.options.map(opt => opt.content)
        payload.answer = questionForm.options
          .map((opt, idx) => (opt.isCorrect ? optionLabels[idx] : null))
          .filter(Boolean)
          .join(',')
      } else if (questionForm.type === 'TRUE_FALSE') {
        payload.answer = String(questionForm.answer)
      } else {
        payload.answer = questionForm.answer
      }

      if (isEditing.value && editingId.value) {
        await questionApi.updateQuestion(editingId.value, payload)
        ElMessage.success('更新成功')
      } else {
        await questionApi.createQuestion(payload)
        ElMessage.success('创建成功')
      }

      showEditDialog.value = false
      fetchQuestions()
    } catch (error) {
      console.error('保存题目失败:', error)
      ElMessage.error('保存失败')
    } finally {
      saving.value = false
    }
  })
}

/**
 * 删除题目
 */
async function deleteQuestion(row) {
  try {
    await ElMessageBox.confirm(`确定删除该题目？\n「${row.content.slice(0, 50)}...」`, '确认删除', { type: 'warning' })

    await questionApi.deleteQuestion(row.id)
    ElMessage.success('删除成功')
    fetchQuestions()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

/**
 * AI生成题目
 */
async function generateByAI() {
  if (!aiFormRef.value) return

  await aiFormRef.value.validate(async (valid) => {
    if (!valid) return

    aiGenerating.value = true
    try {
      const payload = {
        topic: aiForm.topic,
        type: aiForm.type,
        difficulty: aiForm.difficulty,
        count: aiForm.count,
        courseId: aiForm.courseId || undefined,
        requirements: aiForm.requirements || undefined
      }

      const res = await questionApi.generateByAI(payload)
      const generated = res.data?.questions || res.data || []

      if (Array.isArray(generated) && generated.length > 0) {
        ElMessage.success(`成功生成 ${generated.length} 道题目`)
        showAIDialog.value = false
        fetchQuestions()
      } else {
        ElMessage.warning('未生成有效题目')
      }
    } catch (error) {
      console.error('AI生成题目失败:', error)
      ElMessage.error('AI生成失败，请稍后重试')
    } finally {
      aiGenerating.value = false
    }
  })
}

/**
 * 查看题目统计
 */
async function viewStats(row) {
  showStatsDialog.value = true
  currentStats.value = null

  try {
    const res = await request.get(`/questions/${row.id}/stats`)
    currentStats.value = res.data || res
  } catch (error) {
    console.warn('获取题目统计失败:', error)
    // API 不可用时使用空数据，不再伪造统计值
    currentStats.value = {
      usageCount: 0,
      correctRate: null,
      avgTime: '--',
      errorCount: 0,
      recentUsage: []
    }
  }
}

/**
 * 查看易错题
 */
async function viewErrorProne() {
  ElMessage.info('正在加载易错题榜单...')
  try {
    const res = await questionApi.getErrorProne({ limit: 10 })
    const list = res.data?.list || res.data || res || []
    if (Array.isArray(list) && list.length > 0) {
      ElMessage.success(`获取到 ${list.length} 道易错题`)
    } else {
      ElMessage.info('暂无易错题数据')
    }
  } catch (error) {
    console.warn('获取易错题失败:', error)
    ElMessage.error('获取易错题失败，请稍后重试')
  }
}

/**
 * 批量导出
 */
function handleExport() {
  ElMessage.info('导出功能开发中...')
}

/**
 * 批量导入
 */
function handleImport() {
  ElMessage.info('导入功能开发中...')
}

/**
 * 判断是否为选择题类型
 */
function isChoiceType(type) {
  return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)
}

/**
 * 添加选项
 */
function addOption() {
  questionForm.options.push({ content: '', isCorrect: false })
}

/**
 * 移除选项
 */
function removeOption(index) {
  questionForm.options.splice(index, 1)
}

/**
 * 获取题型文本
 */
function getTypeText(type) {
  const map = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
    CODING: '编程'
  }
  return map[type] || type
}

/**
 * 获取题型标签类型
 */
function getTypeTagType(type) {
  const map = {
    SINGLE_CHOICE: '',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info',
    SHORT_ANSWER: '',
    CODING: 'danger'
  }
  return map[type] || ''
}

/**
 * 获取难度数值
 */
function getDifficultyValue(difficulty) {
  const map = { EASY: 1, MEDIUM: 2, HARD: 3 }
  return map[difficulty] || 1
}

/**
 * 获取正确率样式类
 */
function getAccuracyClass(rate) {
  if (rate == null) return ''
  if (rate >= 0.8) return 'high'
  if (rate >= 0.6) return 'medium'
  return 'low'
}

// 初始化
onMounted(() => {
  fetchCourses()
  fetchQuestions()
})
</script>

<style scoped>
.question-management {
  max-width: 1440px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--ac-spacing-lg);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.header-right {
  display: flex;
  gap: var(--ac-spacing-sm);
}

/* 统计行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-mini-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}

.stat-mini-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}

.stat-mini-card .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.stat-mini-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
}

.filter-right {
  display: flex;
  gap: var(--ac-spacing-xs);
  flex-shrink: 0;
}

/* 内容卡片 */
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

/* 分页 */
.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: flex-end;
}

/* 题目内容样式 */
.question-content {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.usage-count {
  color: var(--ac-text-secondary);
  font-weight: 500;
}

.accuracy {
  font-weight: 600;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}

.accuracy.high { color: var(--ac-success); background: rgba(139, 168, 136, 0.12); }
.accuracy.medium { color: var(--ac-warning); background: rgba(203, 181, 138, 0.12); }
.accuracy.low { color: var(--ac-danger); background: rgba(196, 128, 128, 0.12); }

/* 选项编辑器 */
.options-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.option-label {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.option-item .el-input {
  flex: 1;
}

/* 统计详情 */
.stats-content {
  padding: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stats-item {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 16px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stats-item .label {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.stats-item .value {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stats-item .value.primary { color: var(--ac-primary); }
.stats-item .value.success { color: var(--ac-success); }
.stats-item .value.warning { color: var(--ac-warning); }
.stats-item .value.danger { color: var(--ac-danger); }

.recent-usage h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(.2);
}

/* 响应式 */
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-left {
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
