import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
/** 获取考试试卷列表 */
export const getExamPaperList = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/exam-papers', { params }) as Promise<ApiEnvelope<ApiData>>
/** 读取所有匹配的试卷，供无分页控件的列表/计数页面使用。 */
export const getAllExamPapers = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    getExamPaperList({ ...filters, page, size }),
  ) as Promise<ApiData[]>
/**
 * 按课程分页获取试卷（后端 GET /exam-papers/course/{courseId}）。
 * CourseExamPanel 一直按这个名字调用，此前 api 层缺该导出，面板加载试卷列表必然 TypeError。
 */
export const listByCourse = (
  courseId: number | string,
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-papers/course/${courseId}`, { params }) as Promise<ApiEnvelope<ApiData>>
/** 读取指定课程的全部试卷。 */
export const getAllExamPapersByCourse = (
  courseId: number | string,
  filters: Record<string, unknown> = {},
): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    listByCourse(courseId, { ...filters, page, size }),
  ) as Promise<ApiData[]>
/** 根据ID获取试卷 */
export const getExamPaperById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-papers/${id}`) as Promise<ApiEnvelope<ApiData>>
/** 创建考试试卷 */
export const createExamPaper = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/exam-papers', data) as Promise<ApiEnvelope<ApiData>>
/** 更新考试试卷 */
export const updateExamPaper = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/exam-papers/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/** 删除考试试卷 */
export const deleteExamPaper = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/exam-papers/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 自动组卷（教师/管理员）：按题型数量与难度比例从题库抽题，后端直接建卷并写入题目。
 * body 为 AutoComposeDTO；typeRules 的 key 必须是题库 Question.type 实际值
 * （SINGLE/MULTIPLE/JUDGE/FILL/ESSAY/CODE，注意不是 DTO 注释里的 *_CHOICE 风格）。
 * → ExamPaper（DRAFT，题目已写入）
 */
export const autoComposeExamPaper = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/exam-papers/auto-compose', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 批量替换试卷题目（批量替换语义：先清空该卷原有关联行再写入）。
 * body: { questions: [{ questionId, points, orderIndex }] }，与后端 ExamQuestion 实体对齐。
 * 返回写入后的关联行列表；后端会同步回填卷面 questionCount / totalScore。
 */
export const saveExamQuestions = (
  id: number | string,
  questions: unknown,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-papers/${id}/questions`, { questions }) as Promise<ApiEnvelope<ApiData>>
/**
 * 试卷题目解包归一化。
 *
 * 后端 `/exam-papers/{id}/questions` 返回的是「试卷-题目关联」包装结构
 * （`{ id, orderIndex, points, question: {…} }`），而调用方（学生答题页、教师题目预览）
 * 一律按**扁平题目对象**读取 `type / content / options / score`。
 *
 * 历史缺陷（2026-09-21 修复）：此前未做归一化，导致
 * ① 答题页题干空白、题型显示「未知题型」、分值回落到默认值；
 * ② 提交答案时误用关联行 id 而非真实题目 id，服务端判卷取不到作答。
 * 统一在 API 层收敛，一次覆盖全部调用方；已是扁平结构的返回项原样透传。
 *
 * 导出供单测直接验证契约（`src/api/__tests__/exam.test.ts`）——这是 P0-2 的护栏，
 * 若归一化被移除或改写，测试会立刻失败。
 */
export function normalizeExamQuestions(payload: unknown): ApiData[] {
  const list = Array.isArray(payload) ? payload : []
  return list.map((item: ApiData) => {
    const nested = item && item.question
    if (!nested || typeof nested !== 'object') return item
    return {
      ...nested,
      // 分值以关联行上的 points 为准；乱序序号保留供展示
      score: item.points ?? nested.score,
      orderIndex: item.orderIndex ?? nested.orderIndex,
    }
  })
}

/** 获取试卷题目（返回已归一化的扁平题目对象） */
export const getExamQuestions = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-papers/${id}/questions`).then((res: ApiData) => ({
    ...res,
    data: normalizeExamQuestions(res && res.data),
  })) as Promise<ApiEnvelope<ApiData>>
/** 开始考试 */
export const startExam = (examPaperId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post('/exam-sessions/start', { examPaperId }) as Promise<ApiEnvelope<ApiData>>
/** 提交考试答案 */
export const submitExam = (
  sessionId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-sessions/${sessionId}/submit`, data) as Promise<ApiEnvelope<ApiData>>
/** 获取考试结果 */
export const getExamResult = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-sessions/${sessionId}/result`) as Promise<ApiEnvelope<ApiData>>
/** 获取我的考试会话 */
export const getMyExamSession = (examPaperId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-sessions/paper/${examPaperId}`) as Promise<ApiEnvelope<ApiData>>
/** 获取我的所有考试会话 */
export const getMyExamSessions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/exam-sessions/my', { params }) as Promise<ApiEnvelope<ApiData>>

// 考试增强 API
/** 获取考试详情 */
export const getExamDetail = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-papers/${id}/detail`) as Promise<ApiEnvelope<ApiData>>
/** 发布考试 / 提交审核（管理员=直接发布；教师=提交审核） */
export const publishExam = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-papers/${id}/publish`) as Promise<ApiEnvelope<ApiData>>
/** 审核通过试卷（管理员） */
export const approveExam = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/exam-papers/${id}/approve`) as Promise<ApiEnvelope<ApiData>>
/** 审核驳回试卷（管理员，可带驳回原因） */
export const rejectExam = (id: number | string, reason = ''): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-papers/${id}/reject`, { reason }) as Promise<ApiEnvelope<ApiData>>
/** 复制考试 */
export const copyExam = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-papers/${id}/copy`, data) as Promise<ApiEnvelope<ApiData>>
/** 获取考试统计数据 */
export const getExamStatistics = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-papers/${id}/statistics`) as Promise<ApiEnvelope<ApiData>>
/** 自动保存答题记录 */
export const autoSaveAnswers = (
  sessionId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-sessions/${sessionId}/auto-save`, data) as Promise<ApiEnvelope<ApiData>>
/** 强制交卷 */
export const forceSubmit = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-sessions/${sessionId}/force-submit`) as Promise<ApiEnvelope<ApiData>>
/** 获取剩余时间 */
export const getRemainingTime = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-sessions/${sessionId}/time-remaining`) as Promise<ApiEnvelope<ApiData>>
/** 自动批改 */
export const autoGrade = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-sessions/${sessionId}/auto-grade`) as Promise<ApiEnvelope<ApiData>>
/** 获取成绩报告 */
export const getScoreReport = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-sessions/${sessionId}/report`) as Promise<ApiEnvelope<ApiData>>
/** 获取活跃考试 */
export const getActiveExams = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/exam-sessions/active') as Promise<ApiEnvelope<ApiData>>
/** 手动批阅 */
export const manualGrade = (
  sessionId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/exam-sessions/${sessionId}/manual-grade`, data) as Promise<ApiEnvelope<ApiData>>
/** 获取作弊检测信息 */
export const getCheatingDetect = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/exam-sessions/${sessionId}/cheating-detect`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getExamPaperList,
  getAllExamPapers,
  listByCourse,
  getAllExamPapersByCourse,
  getExamPaperById,
  createExamPaper,
  updateExamPaper,
  deleteExamPaper,
  autoComposeExamPaper,
  saveExamQuestions,
  getExamQuestions,
  startExam,
  submitExam,
  getExamResult,
  getMyExamSession,
  getMyExamSessions,
  getExamDetail,
  publishExam,
  approveExam,
  rejectExam,
  copyExam,
  getExamStatistics,
  autoSaveAnswers,
  forceSubmit,
  getRemainingTime,
  autoGrade,
  getScoreReport,
  getActiveExams,
  manualGrade,
  getCheatingDetect,
}
