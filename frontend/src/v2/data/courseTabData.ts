import { IA } from './demoSource'
import { findModule } from './modules'
import type { V2Metric, V2Side, V2Table } from '../nav'
import type { V2DemoBlock } from './pageData'

type Chip = { text: string; chip: string }
const chip = (text: string, tone = 'mute'): Chip => ({ text, chip: tone })

interface DemoCourse {
  id: number
  name: string
  code: string
  classes: Array<{
    name: string
    lead: string
    students: number
    source: string
    progress: number
    status: string
  }>
}

const demoCourses = IA.courses as unknown as DemoCourse[]

function fromModule(
  role: 'student' | 'teacher' | 'admin',
  id: string,
  fallback: V2Metric[] = [],
): V2DemoBlock | null {
  const module = findModule(role, id)
  if (!module) return null
  return {
    metrics: module.metrics?.length ? module.metrics : fallback,
    table: module.table,
    side: module.side,
  }
}

function classesTable(courseId: number): V2Table {
  const course = demoCourses.find((item) => item.id === courseId) || demoCourses[0]
  return {
    title: '教学班',
    columns: ['教学班', '主讲', '学生', '来源', '进度', '状态'],
    rows: course.classes.map((cls) => [
      cls.name,
      cls.lead,
      String(cls.students),
      cls.source,
      `${cls.progress}%`,
      chip(cls.status === 'ACTIVE' ? '进行中' : '草稿', cls.status === 'ACTIVE' ? 'ok' : 'mute'),
    ]),
  }
}

const CLASSES_SIDE: V2Side = {
  title: '行政班 ≠ 教学班',
  body: '教学班是课程下的开课单元，学生来源可以是行政班导入，也可以是自助选课；行政班本身不绑定课程。',
  items: ['一个课程可以有多个教学班', '教学班只保留一个主讲教师', '名单快照导入，可手动重新同步'],
}

export function courseTabBlock(
  role: 'student' | 'teacher' | 'admin',
  tab: string,
  courseId: number,
): V2DemoBlock | null {
  if (role === 'student') {
    if (tab === 'tasks') return fromModule('student', 'tasks')
    if (tab === 'discussion') return fromModule('student', 'discussion')
    return null
  }
  if (role === 'teacher') {
    if (tab === 'chapters') return fromModule('teacher', 'chapters')
    if (tab === 'bank') return fromModule('teacher', 'bank')
    if (tab === 'discussion') return fromModule('teacher', 'discussion')
    if (tab === 'classes') {
      return {
        metrics: [
          { label: '教学班', value: 2, unit: '个', icon: 'users' },
          { label: '学生', value: 108, unit: '人', icon: 'users' },
          { label: '待批阅', value: 22, unit: '份', icon: 'check', tone: 'warn' },
        ],
        table: classesTable(courseId),
        side: CLASSES_SIDE,
      }
    }
    if (tab === 'tasks') {
      return {
        metrics: [
          { label: '作业', value: 3, unit: '项', icon: 'clipboard' },
          { label: '已发布', value: 2, unit: '项', icon: 'check', tone: 'ok' },
          { label: '待批阅', value: 22, unit: '份', icon: 'check', tone: 'warn' },
        ],
        table: {
          title: '作业与任务',
          columns: ['标题', '类型', '截止', '状态', '提交'],
          rows: [
            ['网络层拥塞控制', '作业', '今天 23:59', chip('已发布', 'ok'), '22 / 60'],
            ['传输层练习', '作业', '11-02 23:59', chip('草稿', 'mute'), '0 / 60'],
            ['第 5 章任务点', '章节任务', '随章节', chip('已发布', 'ok'), '38 / 60'],
          ],
        },
        side: {
          title: '当前范围',
          body: '作业是课程级定义，可以发布到一个或多个教学班；提交与成绩按教学班隔离。',
          items: ['已交 22 份，待批 22 份', 'AI 预批仅回填建议', '截止后可延长并记录理由'],
        },
      }
    }
    return null
  }
  if (tab === 'classes' || tab === 'overview') {
    return {
      metrics: [
        { label: '教学班', value: 2, unit: '个', icon: 'users' },
        { label: '学生', value: 108, unit: '人', icon: 'users' },
        { label: '审批', value: '已通过', icon: 'check', tone: 'ok' },
      ],
      table: classesTable(courseId),
      side: {
        title: '管理员视角',
        body: '查看课程、教学班与审批；不代教师批阅、不改成绩。',
        items: ['课程编号按学期唯一', '课程审批已通过', '教学班由教师维护'],
      },
    }
  }
  if (tab === 'content') return fromModule('teacher', 'chapters')
  if (tab === 'bank') return fromModule('teacher', 'bank')
  if (tab === 'exams') return fromModule('teacher', 'papers')
  if (tab === 'grades') return fromModule('teacher', 'grades')
  if (tab === 'aiUsage') return null
  return null
}

export const COURSE_TAB_NOTES: Record<string, string> = {
  content: '课程内容由授课教师在课程上下文中维护；管理员只读。',
  bank: '课程题库由授课教师维护；管理员只读。',
  exams: '试卷与考试由授课教师发布；管理员只读。',
  grades: '成绩以教师提交批阅为准；管理员只读。',
  analytics: '学情统计按课程聚合；管理员只读。',
  graph: '知识图谱由课程知识点构建；管理员只读。',
  aiUsage: 'AI 用量按课程聚合，数据源 ai_call_logs。',
  approval: '审批记录只读，驳回理由不可编辑。',
}
