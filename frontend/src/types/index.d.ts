/* ============================================
 * AI 课程平台 - 全局类型声明
 * ============================================ */

/** 通用 API 响应结构 */
interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
interface PagedResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 分页查询参数 */
interface PageParams {
  page?: number
  size?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

/** 用户角色 */
type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT'

/** 用户实体 */
interface User {
  id: number
  username: string
  fullName: string
  email?: string
  phone?: string
  avatar?: string
  role: UserRole
  status: number
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

/** 课程实体 */
interface Course {
  id: number
  name: string
  code: string
  description: string
  category: string
  credits: number
  difficulty: number
  coverImage?: string
  teacherId: number
  teacherName?: string
  status: string
  maxStudents: number
  enrolledCount: number
  createdAt: string
}

/** 章节实体 */
interface Chapter {
  id: number
  courseId: number
  title: string
  description?: string
  sortOrder: number
  contentType: string
  contentUrl?: string
  duration?: number
  isPublished: boolean
}

/** 任务/作业实体 */
interface Task {
  id: number
  courseId: number
  title: string
  description: string
  type: string
  dueDate: string
  maxScore: number
  isPublished: boolean
  status: string
  createdAt: string
}

/** 提交记录实体 */
interface Submission {
  id: number
  taskId: number
  studentId: number
  studentName?: string
  content: string
  fileUrl?: string
  score?: number
  feedback?: string
  aiFeedback?: string
  status: string
  isLate: boolean
  latePenalty?: number
  submittedAt: string
  gradedAt?: string
  plagiarismScore?: number
}

/** 试卷实体 */
interface ExamPaper {
  id: number
  courseId: number
  title: string
  description: string
  timeLimit: number
  totalScore: number
  questionCount: number
  status: string
  startTime?: string
  endTime?: string
  shuffleQuestions: boolean
  showScore: boolean
  maxAttempts: number
}

/** 考试会话实体 */
interface ExamSession {
  id: number
  examPaperId: number
  studentId: number
  status: string
  answers: Record<number, any>
  score?: number
  percentage?: number
  timeSpent?: number
  startedAt: string
  submittedAt?: string
  ipAddress?: string
  cheatingFlags?: string[]
}

/** 题目实体 */
interface Question {
  id: number
  courseId: number
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER'
  content: string
  options?: string[]
  answer: string | string[]
  score: number
  difficulty: number
  analysis?: string
  knowledgePointIds?: number[]
}

/** 错题实体 */
interface WrongQuestion {
  id: number
  studentId: number
  questionId: number
  questionContent: string
  wrongAnswer: string | string[]
  correctAnswer: string | string[]
  reason?: string
  practiceCount: number
  lastPracticedAt?: string
  isReviewed: boolean
  createdAt: string
}

/** 知识点实体 */
interface KnowledgePoint {
  id: number
  courseId: number
  name: string
  description?: string
  parentId?: number
  difficultyLevel: number
  importance: number
  masteryRate?: number
}

/** 学习路径实体 */
interface LearningPath {
  id: number
  studentId: number
  courseId: number
  title: string
  status: string
  completionRate: number
  dailyTasks?: any
  stages?: any
  createdAt: string
}

/** 学习路径节点实体 */
interface LearningPathNode {
  id: number
  pathId: number
  knowledgePointId: number
  knowledgePointName?: string
  nodeType: string
  orderIndex: number
  status: string
  score?: number
  actualTime?: number
  plannedTime?: number
}

/** 通知实体 */
interface Notification {
  id: number
  userId: number
  title: string
  content: string
  category: string
  priority: string
  actionUrl?: string
  isRead: boolean
  createdAt: string
}

/** 资源实体 */
interface Resource {
  id: number
  courseId: number
  chapterId?: number
  name: string
  fileType: string
  fileSize: number
  url: string
  isVisibleToAll: boolean
  downloadCount: number
  uploadTime: string
}

/** 系统配置实体 */
interface SystemConfig {
  id: number
  configKey: string
  configValue: string
  configGroup: string
  description?: string
  isPublic: boolean
}

/** 登录日志 */
interface LoginLog {
  id: number
  userId: number
  ipAddress: string
  userAgent?: string
  loginTime: string
  isSuccess: boolean
  failureReason?: string
}

/** 统计数据 */
interface DashboardStats {
  totalUsers: number
  totalCourses: number
  activeUsers: number
  completionRate: number
  growthRate: number
}

/** 趋势数据点 */
interface TrendDataPoint {
  date: string
  value: number
  label?: string
}

/** 图表配置选项 */
interface ChartOption {
  color?: string[]
  backgroundColor?: string
  tooltip?: any
  legend?: any
  xAxis?: any
  yAxis?: any
  series: any[]
}
