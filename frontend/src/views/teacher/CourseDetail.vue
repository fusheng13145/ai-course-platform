<template>
  <div class="course-detail">
    <!-- 页头：返回 + 课程名 + 状态 + 操作 -->
    <div class="detail-header">
      <div class="header-left">
        <el-button text @click="$router.push('/teacher/courses')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <div class="header-title-group">
          <h1 class="header-title">{{ course.name }}</h1>
          <el-tag
            size="small"
            :type="
              course.approvalStatus === 'APPROVED'
                ? 'success'
                : course.approvalStatus === 'REJECTED'
                  ? 'danger'
                  : 'warning'
            "
            effect="light"
            round
          >
            {{ getStatusText(course.approvalStatus) }}
          </el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button plain @click="showEditDialog"
          ><el-icon><Edit /></el-icon>编辑课程</el-button
        >
        <el-button type="primary" plain @click="showNotificationDialog"
          ><el-icon><Bell /></el-icon>发送通知</el-button
        >
      </div>
    </div>

    <!-- 统计卡（带容错，不暴露后端错误） -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue"><User :size="20" /></div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalStudents ?? '-' }}</span
          ><span class="stat-label">学生人数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green"><FolderOpened :size="20" /></div>
        <div class="stat-info">
          <span class="stat-value">{{ resourceCount ?? '-' }}</span
          ><span class="stat-label">资料</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange"><Document :size="20" /></div>
        <div class="stat-info">
          <span class="stat-value">{{ taskCount ?? '-' }}</span
          ><span class="stat-label">作业</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple"><Notebook :size="20" /></div>
        <div class="stat-info">
          <span class="stat-value">{{ examCount ?? '-' }}</span
          ><span class="stat-label">考试</span>
        </div>
      </div>
    </div>

    <!-- 主 Tab 区 -->
    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- 1) 课程概览 -->
      <el-tab-pane label="课程首页" name="info">
        <div class="tab-card">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">课程编号</span
              ><span class="info-val">{{ course.code || '–' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">分类</span
              ><span class="info-val">{{ course.category || '未分类' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">难度</span
              ><span class="info-val"
                ><el-tag size="small" effect="light" round>{{
                  getDifficultyName(course.difficulty)
                }}</el-tag></span
              >
            </div>
            <div class="info-item">
              <span class="info-label">学分</span
              ><span class="info-val">{{ course.credits ?? '–' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">学期</span
              ><span class="info-val">{{ course.semester || '–' }} / {{ course.year || '' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">报名方式</span
              ><span class="info-val">{{ getEnrollmentTypeName(course.enrollmentType) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最大人数</span
              ><span class="info-val">{{
                course.maxStudents ? `${course.maxStudents} 人` : '不限'
              }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">开课时间</span
              ><span class="info-val">{{ formatDate(course.startDate) }}</span>
            </div>
          </div>
          <div v-if="course.description" class="info-desc-section">
            <h4 class="section-subtitle">课程简介</h4>
            <p class="desc-text">{{ course.description }}</p>
          </div>
        </div>
      </el-tab-pane>

      <!-- 2) 教学分析 -->
      <el-tab-pane label="教学分析" name="analytics">
        <CourseAnalyticsTab :course-id="course.id" />
      </el-tab-pane>

      <!-- 3) 章节管理 -->
      <el-tab-pane label="章节管理" name="chapters">
        <div class="tab-card">
          <div class="tab-toolbar">
            <el-button type="primary" plain @click="showAddChapterDialog"
              ><Plus :size="14" /> 添加章节</el-button
            >
            <span v-if="dragChanged" class="drag-hint">排序已变更，请保存</span>
          </div>
          <el-table v-loading="chaptersLoading" :data="chapters" row-key="id" style="width: 100%">
            <el-table-column width="46" align="center"
              ><template #default><Rank class="drag-handle" /></template
            ></el-table-column>
            <el-table-column prop="title" label="章节名称" min-width="200" />
            <el-table-column prop="duration" label="时长(分)" width="90"
              ><template #default="{ row }">{{ row.duration || 0 }}</template></el-table-column
            >
            <el-table-column prop="isPublished" label="状态" width="90">
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="row.isPublished ? 'status-dot--active' : 'status-dot--inactive'"
                  ><i />{{ row.isPublished ? '已发布' : '草稿' }}
                </span></template
              ></el-table-column
            >
            <el-table-column width="200" align="right">
              <template #default="{ row }">
                <el-button text size="small" @click="editChapter(row)">编辑</el-button>
                <el-button text size="small" type="danger" @click="deleteChapter(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <div v-if="dragChanged" class="order-actions">
            <el-button type="warning" size="small" :loading="savingOrder" @click="saveChapterOrder"
              >保存排序</el-button
            >
            <el-button size="small" @click="resetChapterOrder">取消</el-button>
          </div>
          <el-empty
            v-if="!chaptersLoading && !chapters.length"
            description="暂无章节，点击上方添加"
          />
        </div>
      </el-tab-pane>

      <!-- 3) 资料管理 -->
      <el-tab-pane label="资料管理" name="resources">
        <CourseResourcePanel
          :course-id="Number(course.id)"
          @resource-updated="updateResourceCount"
        />
      </el-tab-pane>

      <!-- 4) 作业管理 -->
      <el-tab-pane label="作业管理" name="tasks">
        <CourseTaskPanel :course-id="Number(course.id)" @task-updated="updateTaskCount" />
      </el-tab-pane>

      <!-- 5) 考试管理 -->
      <el-tab-pane label="考试管理" name="exams">
        <CourseExamPanel :course-id="Number(course.id)" @exam-updated="updateExamCount" />
      </el-tab-pane>

      <!-- 6) 成绩管理 -->
      <el-tab-pane label="成绩管理" name="grades">
        <CourseGradesTab :course-id="course.id" />
      </el-tab-pane>

      <!-- 7) 学生管理 -->
      <el-tab-pane label="学生管理" name="students">
        <div class="tab-card">
          <el-table v-loading="studentsLoading" :data="students" style="width: 100%">
            <el-table-column prop="studentId" label="学号" width="120" />
            <el-table-column prop="studentName" label="姓名" min-width="110" />
            <el-table-column label="进度" width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round((row.progress || 0) * 100)"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 100px; vertical-align: middle"
                />
                <span style="font-size: 12px; color: var(--ac-text-secondary); margin-left: 6px"
                  >{{ Math.round((row.progress || 0) * 100) }}%</span
                >
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <span
                  class="status-dot"
                  :class="
                    row.approvalStatus === 'APPROVED'
                      ? 'status-dot--active'
                      : 'status-dot--inactive'
                  "
                  ><i />{{
                    row.approvalStatus === 'APPROVED'
                      ? '已通过'
                      : row.approvalStatus === 'REJECTED'
                        ? '已拒绝'
                        : '待审核'
                  }}</span
                >
              </template>
            </el-table-column>
            <el-table-column width="160" align="right">
              <template v-if="row.approvalStatus === 'PENDING'" #default="{ row }">
                <el-button text size="small" type="success" @click="approveStudent(row)"
                  >通过</el-button
                >
                <el-button text size="small" type="danger" @click="rejectStudent(row)"
                  >拒绝</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!studentsLoading && !students.length" description="暂无学生" />
        </div>
      </el-tab-pane>

      <!-- 8) 知识管理 -->
      <el-tab-pane label="知识管理" name="knowledge">
        <CourseKnowledgeTab :course-id="course.id" />
      </el-tab-pane>

      <!-- 9) 通知管理 -->
      <el-tab-pane label="通知管理" name="notifications">
        <div class="tab-card">
          <div class="tab-toolbar">
            <el-button type="primary" plain @click="showNotificationDialog"
              ><Plus :size="14" /> 发送通知</el-button
            >
          </div>
          <el-table v-loading="notificationsLoading" :data="notifications" style="width: 100%">
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="发送时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column width="80" align="right">
              <template #default="{ row }">
                <el-button text type="danger" size="small" @click="deleteNotification(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!notificationsLoading && !notifications.length" description="暂无通知" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ====== 编辑课程对话框 ====== -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑课程信息"
      width="600px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="editForm" label-width="100px" style="padding-right: 12px">
        <el-form-item label="课程名称"
          ><el-input v-model="editForm.name" placeholder="输入课程名称" maxlength="64"
        /></el-form-item>
        <el-form-item label="课程编码"
          ><el-input v-model="editForm.code" placeholder="可选，如 CS101" maxlength="24"
        /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editForm.difficulty" style="width: 100%">
            <el-option label="入门(BEGINNER)" value="BEGINNER" /><el-option
              label="初级(EASY)"
              value="EASY"
            />
            <el-option label="中级(MEDIUM)" value="MEDIUM" /><el-option
              label="高级(HARD)"
              value="HARD"
            />
            <el-option label="专家(EXPERT)" value="EXPERT" />
          </el-select>
        </el-form-item>
        <el-form-item label="学分"
          ><el-input-number v-model="editForm.credits" :min="0" :max="10" :precision="1"
        /></el-form-item>
        <el-form-item label="报名方式">
          <el-select v-model="editForm.enrollmentType" style="width: 100%">
            <el-option label="开放报名" value="OPEN" /><el-option
              label="需审核"
              value="APPROVAL"
            /><el-option label="限制报名" value="RESTRICTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大人数"
          ><el-input-number v-model="editForm.maxStudents" :min="0" :step="10"
        /></el-form-item>
        <el-form-item label="课程简介"
          ><el-input
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCourse" @click="saveCourse">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- ====== 章节对话框 ====== -->
    <el-dialog
      v-model="chapterDialogVisible"
      :title="editingChapter ? '编辑章节' : '添加章节'"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="chapterForm" label-width="88px">
        <el-form-item label="章节名称"
          ><el-input v-model="chapterForm.title" placeholder="如：第1章 基础语法"
        /></el-form-item>
        <el-form-item label="时长(分钟)"
          ><el-input-number v-model="chapterForm.duration" :min="0"
        /></el-form-item>
        <el-form-item label="内容说明"
          ><el-input
            v-model="chapterForm.content"
            type="textarea"
            :rows="4"
            placeholder="章节概述或备注"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingChapter" @click="saveChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- ====== 发送通知对话框 ====== -->
    <el-dialog
      v-model="notificationDialogVisible"
      title="发送课程通知"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="notificationForm" label-width="76px">
        <el-form-item label="标题"
          ><el-input v-model="notificationForm.title" placeholder="通知标题" maxlength="120"
        /></el-form-item>
        <el-form-item label="内容"
          ><el-input
            v-model="notificationForm.content"
            type="textarea"
            :rows="5"
            placeholder="通知正文…"
            maxlength="2000"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notificationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sendingNotification" @click="sendNotification"
          >发送</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Edit,
  Bell,
  Plus,
  Rank,
  User,
  FolderOpened,
  Document,
  Notebook,
} from '@element-plus/icons-vue'

import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'
import notificationApi from '@/api/notification'
import resourceApi from '@/api/resource'
import taskApi from '@/api/task'
import examApi from '@/api/exam'

import CourseResourcePanel from '@/components/CourseResourcePanel.vue'
import CourseTaskPanel from '@/components/CourseTaskPanel.vue'
import CourseExamPanel from '@/components/CourseExamPanel.vue'
import CourseAnalyticsTab from '@/components/CourseAnalyticsTab.vue'
import CourseGradesTab from '@/components/CourseGradesTab.vue'
import CourseKnowledgeTab from '@/components/CourseKnowledgeTab.vue'

const route = useRoute()
const activeTab = ref('info')

/* ---- 课程数据 ---- */
const course = reactive({
  id: null,
  name: '',
  code: '',
  category: '',
  description: '',
  credits: null,
  difficulty: '',
  approvalStatus: '',
  enrollmentType: '',
  maxStudents: null,
  semester: '',
  year: '',
  startDate: '',
  endDate: '',
})
const stats = reactive({ totalStudents: null })
const resourceCount = ref(null)
const taskCount = ref(null)
const examCount = ref(null)

/* ---- 子模块列表 ---- */
const chapters = ref([])
const students = ref([])
const notifications = ref([])
const chaptersLoading = ref(false)
const studentsLoading = ref(false)
const notificationsLoading = ref(false)

/* ---- 对话框状态 ---- */
const categories = ref([
  '编程开发',
  '人工智能',
  '数据分析',
  '设计创意',
  '数学物理',
  '语言文学',
  '其他',
])

// 编辑课程
const editDialogVisible = ref(false)
const editForm = reactive({
  name: '',
  code: '',
  category: '',
  description: '',
  credits: null,
  difficulty: 'MEDIUM',
  enrollmentType: 'OPEN',
  maxStudents: 100,
})
const savingCourse = ref(false)

// 章节
const chapterDialogVisible = ref(false)
const editingChapter = ref(null)
const chapterForm = reactive({ title: '', duration: 0, orderIndex: 0, content: '' })
const savingChapter = ref(false)
const dragChanged = ref(false)
const originalChapters = ref([])
const savingOrder = ref(false)

// 通知
const notificationDialogVisible = ref(false)
const notificationForm = reactive({ title: '', content: '' })
const sendingNotification = ref(false)

/* ---- 加载（路由参数变化时重新加载） ---- */
async function initData() {
  const courseId = route.params.id
  if (!courseId) {
    ElMessage.error('缺少课程 ID')
    return
  }
  // 重置状态
  course.id = courseId
  activeTab.value = 'info'
  // 清空旧数据避免闪烁
  Object.assign(course, {
    name: '',
    code: '',
    category: '',
    description: '',
    credits: null,
    difficulty: '',
    approvalStatus: '',
    enrollmentType: '',
    maxStudents: null,
    semester: '',
    year: '',
    startDate: '',
    endDate: '',
  })
  stats.totalStudents = null
  resourceCount.value = null
  taskCount.value = null
  examCount.value = null
  chapters.value = []
  students.value = []
  notifications.value = []

  await Promise.allSettled([
    loadCourse(),
    loadChapters(),
    loadStudents(),
    loadNotifications(),
    loadStats(),
    loadResourceCount(),
    loadTaskCount(),
    loadExamCount(),
  ])
}

onMounted(() => initData())
watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId && newId !== oldId) initData()
  },
)

async function loadCourse() {
  try {
    const res = await courseApi.getCourseById(course.id)
    if (res.data) Object.assign(course, res.data)
  } catch (e) {
    logger.warn('[CourseDetail] loadCourse 失败:', e.message || e)
  }
}

async function loadChapters() {
  chaptersLoading.value = true
  try {
    const res = await chapterApi.getChapters(course.id)
    const raw = res.data?.records || res.data?.list || res.data || []
    chapters.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.warn('[CourseDetail] loadChapters 失败:', e.message || e)
    chapters.value = []
  } finally {
    chaptersLoading.value = false
    originalChapters.value = JSON.parse(JSON.stringify(chapters.value))
  }
}

async function loadStudents() {
  studentsLoading.value = true
  try {
    const res = await courseApi.getStudents(course.id)
    const raw = res.data?.records || res.data?.list || res.data || []
    students.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.warn('[CourseDetail] loadStudents 失败:', e.message || e)
    students.value = []
  } finally {
    studentsLoading.value = false
  }
}

async function loadNotifications() {
  notificationsLoading.value = true
  try {
    const res = await notificationApi.getNotifications()
    const raw = res.data?.list || res.data || []
    notifications.value = Array.isArray(raw) ? raw : []
  } catch (e) {
    logger.warn('[CourseDetail] loadNotifications 失败:', e.message || e)
    notifications.value = []
  } finally {
    notificationsLoading.value = false
  }
}

async function loadStats() {
  try {
    const res = await courseApi.getStats(course.id)
    if (res.data) Object.assign(stats, res.data)
  } catch (e) {
    // 统计加载失败时静默，统计卡显示 "-" 不阻塞页面
    logger.warn('[CourseDetail] loadStats 失败:', e.message || e)
  }
}

async function loadResourceCount() {
  try {
    const res = await resourceApi.getResourceList(course.id)
    resourceCount.value = (Array.isArray(res.data) ? res.data : []).length
  } catch (e) {
    resourceCount.value = null
  }
}
async function loadTaskCount() {
  try {
    const res = await taskApi.listByCourse(course.id)
    const d = res.data?.records || res.data?.list || res.data || []
    taskCount.value = Array.isArray(d) ? d.length : 0
  } catch (e) {
    taskCount.value = null
  }
}
async function loadExamCount() {
  try {
    const res = await examApi.listByCourse(course.id)
    const d = res.data?.records || res.data?.list || res.data || []
    examCount.value = Array.isArray(d) ? d.length : 0
  } catch (e) {
    examCount.value = null
  }
}

function updateResourceCount() {
  loadResourceCount()
}
function updateTaskCount() {
  loadTaskCount()
}
function updateExamCount() {
  loadExamCount()
}

/* ---- 辅助函数 ---- */
function getStatusText(s) {
  return s === 'APPROVED' ? '已通过' : s === 'REJECTED' ? '已拒绝' : '审核中'
}
function getDifficultyName(d) {
  return (
    { BEGINNER: '入门', EASY: '初级', MEDIUM: '中级', HARD: '高级', EXPERT: '专家' }[d] ||
    d ||
    '未设置'
  )
}
function getEnrollmentTypeName(t) {
  return { OPEN: '开放报名', APPROVAL: '需审核', RESTRICTED: '限制报名' }[t] || t || '开放报名'
}
function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '–'
}
function formatDateTime(d) {
  return d ? new Date(d).toLocaleString('zh-CN') : '–'
}

/* ---- 编辑课程 ---- */
function showEditDialog() {
  Object.assign(editForm, {
    name: course.name || '',
    code: course.code || '',
    category: course.category || '',
    description: course.description || '',
    credits: course.credits ?? 0,
    difficulty: course.difficulty || 'MEDIUM',
    enrollmentType: course.enrollmentType || 'OPEN',
    maxStudents: course.maxStudents ?? 100,
  })
  editDialogVisible.value = true
}
async function saveCourse() {
  if (!editForm.name.trim()) return ElMessage.warning('请输入课程名称')
  savingCourse.value = true
  try {
    await courseApi.updateCourse(course.id, editForm)
    Object.assign(course, editForm)
    editDialogVisible.value = false
    ElMessage.success('课程信息已更新')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    savingCourse.value = false
  }
}

/* ---- 章节操作 ---- */
function showAddChapterDialog() {
  editingChapter.value = null
  Object.assign(chapterForm, {
    title: '',
    duration: 0,
    orderIndex: chapters.value.length,
    content: '',
  })
  chapterDialogVisible.value = true
}
function editChapter(row) {
  editingChapter.value = row
  Object.assign(chapterForm, {
    title: row.title,
    duration: row.duration || 0,
    orderIndex: row.orderIndex || 0,
    content: row.content || '',
  })
  chapterDialogVisible.value = true
}
async function saveChapter() {
  if (!chapterForm.title.trim()) return ElMessage.warning('请输入章节名称')
  savingChapter.value = true
  try {
    const payload = { ...chapterForm, courseId: Number(course.id) }
    if (editingChapter.value) {
      await chapterApi.updateChapter(editingChapter.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await chapterApi.createChapter(payload)
      ElMessage.success('添加成功')
    }
    chapterDialogVisible.value = false
    await loadChapters()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    savingChapter.value = false
  }
}
async function deleteChapter(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」？`, '提示', { type: 'warning' })
    await chapterApi.deleteChapter(row.id)
    chapters.value = chapters.value.filter((c) => c.id !== row.id)
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function saveChapterOrder() {
  savingOrder.value = true
  try {
    await chapterApi.batchReorder({
      orderList: chapters.value.map((c, idx) => ({ id: c.id, orderIndex: idx })),
    })
    dragChanged.value = false
    originalChapters.value = JSON.parse(JSON.stringify(chapters.value))
    ElMessage.success('排序已保存')
  } catch (e) {
    ElMessage.error('保存排序失败')
  } finally {
    savingOrder.value = false
  }
}
function resetChapterOrder() {
  chapters.value = JSON.parse(JSON.stringify(originalChapters.value))
  dragChanged.value = false
}

/* ---- 学生操作 ---- */
async function approveStudent(row) {
  try {
    await enrollmentApi.approveEnrollment(row.id)
    row.approvalStatus = 'APPROVED'
    ElMessage.success('已通过')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
async function rejectStudent(row) {
  try {
    await ElMessageBox.confirm('确定拒绝该学生的选课申请？', '提示', { type: 'warning' })
    await enrollmentApi.rejectEnrollment(row.id)
    row.approvalStatus = 'REJECTED'
    ElMessage.success('已拒绝')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

/* ---- 通知操作 ---- */
function showNotificationDialog() {
  notificationForm.title = ''
  notificationForm.content = ''
  notificationDialogVisible.value = true
}
async function sendNotification() {
  if (!notificationForm.title.trim() || !notificationForm.content.trim())
    return ElMessage.warning('请填写完整')
  sendingNotification.value = true
  try {
    await notificationApi.sendNotification({
      title: notificationForm.title,
      content: notificationForm.content,
      courseId: Number(course.id),
      category: 'COURSE',
    })
    ElMessage.success('发送成功')
    notificationDialogVisible.value = false
    await loadNotifications()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发送失败')
  } finally {
    sendingNotification.value = false
  }
}
async function deleteNotification(row) {
  try {
    await ElMessageBox.confirm('确定删除该通知？', '提示', { type: 'warning' })
    await notificationApi.deleteNotification(row.id)
    notifications.value = notifications.value.filter((n) => n.id !== row.id)
    ElMessage.success('已删除')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.course-detail {
  padding: 0;
}

/* ====== 页头 ====== */
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--ac-border);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.header-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
  line-height: 1.3;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ====== 统计卡（4 列） ====== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 22px;
}
.stat-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md, 10px);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 13px;
  transition: box-shadow 0.2s ease;
}
.stat-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.stat-icon--blue {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}
.stat-icon--green {
  background: linear-gradient(135deg, #10b981, #34d399);
}
.stat-icon--orange {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}
.stat-icon--purple {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-value {
  display: block;
  font-size: 23px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-label {
  display: block;
  font-size: 12.5px;
  color: var(--ac-text-secondary, #64748b);
  margin-top: 2px;
}

/* ====== 主 Tabs ====== */
.main-tabs {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg, 12px);
  border: 1px solid var(--ac-border);
  padding: 6px 20px 16px;
}
:deep(.main-tabs .el-tabs__header) {
  margin-bottom: 16px;
}
:deep(.main-tabs .el-tabs__item) {
  height: 42px;
  line-height: 42px;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ac-text-secondary);
  transition: color 0.15s;
}
:deep(.main-tabs .el-tabs__item.is-active) {
  color: var(--ac-primary);
  font-weight: 600;
}
:deep(.main-tabs .el-tabs__active-bar) {
  height: 3px;
  border-radius: 2px;
}

/* ====== Tab 内容卡片 ====== */
.tab-card {
  padding: 8px 4px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.drag-hint {
  font-size: 12.5px;
  color: var(--ac-warning, #b45309);
  font-weight: 500;
}
.order-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  justify-content: flex-end;
}

/* ====== 课程首页：信息网格 ====== */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 28px;
  margin-bottom: 22px;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.25);
}
.info-label {
  font-size: 13px;
  color: var(--ac-text-secondary, #64748b);
  white-space: nowrap;
  min-width: 72px;
}
.info-val {
  font-size: 14px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
.info-desc-section {
  margin-top: 4px;
}
.section-subtitle {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 8px;
}
.desc-text {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ====== 拖拽手柄 ====== */
.drag-handle {
  cursor: grab;
  color: var(--ac-text-tertiary, #94a3b8);
}
.drag-handle:active {
  cursor: grabbing;
}

/* ====== 响应式 ====== */
@media (max-width: 1024px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
  .info-grid {
    grid-template-columns: 1fr;
  }
  .detail-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  .header-actions {
    width: 100%;
  }
  .header-actions .el-button {
    flex: 1;
  }
}
@media (max-width: 640px) {
  .stats-row {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
  .stat-card {
    padding: 12px;
  }
  .stat-value {
    font-size: 19px;
  }
}
</style>
