import { IA } from './demoSource'
import { findModule } from './modules'
import type { V2Metric, V2Module, V2Side, V2Table } from '../nav'

type Chip = { text: string; chip: string }
type Cell = string | Chip | null

export interface V2DemoBlock {
  metrics: V2Metric[]
  table?: V2Table
  side?: V2Side
  note?: string
}

const chip = (text: string, tone = 'mute'): Chip => ({ text, chip: tone })

const metricsOf = (module: V2Module | undefined, fallback: V2Metric[]): V2Metric[] =>
  module?.metrics?.length ? module.metrics : fallback

function moduleBlock(role: 'student' | 'teacher' | 'admin', id: string): V2DemoBlock | null {
  const module = findModule(role, id)
  if (!module) return null
  return {
    metrics: module.metrics || [],
    table: module.table,
    side: module.side,
    note: '示例数据：该页面的后端聚合端点尚未接入。',
  }
}

/* ------------------------------ student ------------------------------ */

function academicBlock(sub: string): V2DemoBlock | null {
  if (sub === 'wrong') return moduleBlock('student', 'wrong')
  if (sub === 'path') return moduleBlock('student', 'path')
  const grades = moduleBlock('student', 'grades')
  if (!grades) return null
  return {
    ...grades,
    note: '示例数据：成绩概览的后端聚合端点尚未接入；课程级成绩可在课程空间查看。',
  }
}

/* ------------------------------ teacher ------------------------------ */

function inboxBlock(sub: string): V2DemoBlock {
  const inbox = IA.teacherInbox as Record<string, Array<Record<string, unknown>>>
  const key = ['grading', 'publish', 'proctor', 'reminders'].includes(sub) ? sub : 'grading'
  const rows = inbox[key] || []
  const tables: Record<string, V2Table> = {
    grading: {
      title: '待批阅队列',
      columns: ['学生', '作业', '课程', '教学班', 'AI 建议', '状态'],
      rows: rows.map((row) => [
        String(row.student || ''),
        String(row.title || ''),
        String(row.course || ''),
        String(row.className || ''),
        String(row.ai || '—'),
        chip(String(row.status || ''), 'warn'),
      ]),
    },
    publish: {
      title: '待发布',
      columns: ['事项', '课程', '教学班', '状态'],
      rows: rows.map((row) => [
        String(row.title || ''),
        String(row.course || ''),
        String(row.className || ''),
        chip(String(row.status || ''), 'info'),
      ]),
    },
    proctor: {
      title: '考试与监考',
      columns: ['考试', '课程', '教学班', '在线', '风险', '剩余'],
      rows: rows.map((row) => [
        String(row.title || ''),
        String(row.course || ''),
        String(row.className || ''),
        String(row.online ?? ''),
        String(row.risks ?? ''),
        String(row.time || ''),
      ]),
    },
    reminders: {
      title: '提醒',
      columns: ['事项', '课程', '教学班', '时间'],
      rows: rows.map((row) => [
        String(row.title || ''),
        String(row.course || ''),
        String(row.className || ''),
        String(row.status || ''),
      ]),
    },
  }
  return {
    metrics: [
      {
        label: '待批阅',
        value: (inbox.grading || []).length,
        unit: '项',
        icon: 'check',
        tone: 'warn',
      },
      {
        label: '待发布',
        value: (inbox.publish || []).length,
        unit: '项',
        icon: 'upload',
        tone: 'info',
      },
      { label: '监考中', value: (inbox.proctor || []).length, unit: '场', icon: 'shield' },
    ],
    table: tables[key],
    side: {
      title: '跨课程聚合',
      body: '教学任务是跨课程视图；进入课程后侧栏切换为课程页签，批阅与发布都在课程上下文中完成。',
      items: ['每条记录带课程与教学班', '批阅以教师确认为准', '考试风险事件只作线索'],
    },
    note: '示例数据：教学任务聚合端点（/teacher/inbox）尚未实现。',
  }
}

function advisingBlock(sub: string): V2DemoBlock {
  const data = IA.advising as {
    className: string
    students: number
    courses: number
    avgProgress: number
    alerts: number
    courseRows: Array<{ name: string; progress: number; avg: number; teacher: string }>
    alertsList: Array<{
      student: string
      type: string
      detail: string
      level: string
      time: string
    }>
    notices: Array<{ title: string; body: string; time: string; status: string }>
  }
  const page = sub || 'overview'
  if (page === 'alerts') {
    return {
      metrics: [
        { label: '预警学生', value: data.alerts, unit: '人', icon: 'alert', tone: 'danger' },
        { label: '班级', value: data.className, icon: 'users' },
        { label: '本周新增', value: 3, unit: '条', icon: 'clock', tone: 'warn' },
      ],
      table: {
        title: '学生预警',
        columns: ['学生', '类型', '详情', '等级', '时间'],
        rows: data.alertsList.map((row) => [
          row.student,
          row.type,
          row.detail,
          chip(row.level === 'danger' ? '高' : '中', row.level),
          row.time,
        ]),
      },
      side: {
        title: '预警口径',
        body: '预警是线索不是结论；班主任核实后再联系学生，不自动写入成绩或处分。',
        items: ['低于 60 分进入成绩预警', '7 天未进入课程进入活跃预警', '预警只读，不改变教学数据'],
      },
      note: '示例数据：班主任端点（/advisor/classes）尚未实现。',
    }
  }
  if (page === 'notices') {
    return {
      metrics: [
        { label: '班级', value: data.className, icon: 'users' },
        { label: '已发送', value: data.notices.length, unit: '条', icon: 'bell', tone: 'ok' },
        { label: '草稿', value: 0, unit: '条', icon: 'file' },
      ],
      table: {
        title: '班级通知',
        columns: ['标题', '内容', '时间', '状态'],
        rows: data.notices.map((row) => [row.title, row.body, row.time, chip(row.status, 'ok')]),
      },
      side: {
        title: '受众边界',
        body: '班级通知面向行政班学生，与课程通知、系统公告不是同一种受众。',
        items: ['班主任发布', '按行政班扇出', '不进入课程讨论区'],
      },
      note: '示例数据：班级通知端点尚未实现。',
    }
  }
  if (page === 'profile') {
    return {
      metrics: [
        { label: '学生', value: data.students, unit: '人', icon: 'users' },
        { label: '课程', value: data.courses, unit: '门', icon: 'book' },
        { label: '平均进度', value: data.avgProgress, unit: '%', icon: 'chart', tone: 'ok' },
      ],
      table: {
        title: '班级课程画像',
        columns: ['课程', '进度', '均分', '主讲'],
        rows: data.courseRows.map((row) => [
          row.name,
          `${row.progress}%`,
          String(row.avg),
          row.teacher,
        ]),
      },
      side: {
        title: '只读画像',
        body: '班级画像用于班主任了解整体情况；不提供改分或改进度的入口。',
        items: ['行政班不绑定课程', '学生属于一个行政班', '可加入多个教学班'],
      },
      note: '示例数据：班级画像端点尚未实现。',
    }
  }
  return {
    metrics: [
      { label: '班级', value: data.className, icon: 'users' },
      { label: '学生', value: data.students, unit: '人', icon: 'users' },
      { label: '待核实预警', value: data.alerts, unit: '条', icon: 'alert', tone: 'warn' },
    ],
    table: {
      title: '班级课程',
      columns: ['课程', '进度', '均分', '主讲'],
      rows: data.courseRows.map((row) => [
        row.name,
        `${row.progress}%`,
        String(row.avg),
        row.teacher,
      ]),
    },
    side: {
      title: '班主任视角',
      body: '班主任负责行政班的学生归属与通知；课程的批阅与成绩仍由授课教师完成。',
      items: ['班级与教学班分离', '班级通知单独发布', '学生预警需人工核实'],
    },
    note: '示例数据：班主任端点尚未实现。',
  }
}

function resourcesBlock(): V2DemoBlock {
  const courses = IA.courses as Array<{
    id: number
    name: string
    code: string
    teacher: string
    classes: unknown[]
  }>
  return {
    metrics: [
      { label: '课程', value: courses.length, unit: '门', icon: 'book' },
      {
        label: '教学班',
        value: courses.reduce((n, c) => n + c.classes.length, 0),
        unit: '个',
        icon: 'users',
      },
      { label: '资源', value: 128, unit: '份', icon: 'layers', tone: 'info' },
    ],
    table: {
      title: '课程资源',
      columns: ['课程', '编号', '主讲', '教学班', '最近更新'],
      rows: courses.map((course, index) => [
        course.name,
        course.code,
        course.teacher,
        String(course.classes.length),
        `10-${27 - index} 14:0${index}`,
      ]),
    },
    side: {
      title: '资源共享边界',
      body: '章节与资源是课程级内容，所有教学班共享；题库与试卷可按教学班发布。',
      items: ['课程级定义', '教学班目标是发布范围', '删除资源需二次确认'],
    },
    note: '示例数据：资源中心聚合端点尚未接入。',
  }
}

/* ------------------------------ admin ------------------------------ */

function collegesBlock(): V2DemoBlock {
  const rows = IA.colleges as Array<{
    name: string
    code: string
    majors: string[]
    teachers: number
    students: number
    classes: number
  }>
  return {
    metrics: [
      { label: '学院', value: rows.length, unit: '个', icon: 'building' },
      {
        label: '专业',
        value: rows.reduce((n, r) => n + r.majors.length, 0),
        unit: '个',
        icon: 'layers',
      },
      {
        label: '行政班',
        value: rows.reduce((n, r) => n + r.classes, 0),
        unit: '个',
        icon: 'users',
        tone: 'info',
      },
    ],
    table: {
      title: '学院与专业',
      columns: ['学院', '编号', '专业', '教师', '学生', '行政班'],
      rows: rows.map((r) => [
        r.name,
        r.code,
        r.majors.join(' / '),
        String(r.teachers),
        String(r.students),
        String(r.classes),
      ]),
    },
    side: {
      title: '实体链',
      body: '学校 → 学院 → 专业 → 行政班；行政班只负责学生归属与班主任，不绑定课程。',
      items: ['学生属于一个行政班', '学生可加入多个教学班', '课程归属学院与学期'],
    },
    note: '示例数据：/org/tree 与 /admin/colleges 端点尚未实现。',
  }
}

function adminClassesBlock(): V2DemoBlock {
  const rows = IA.adminClasses as Array<{
    id: number
    name: string
    college: string
    major: string
    grade: string
    advisor: string
    students: number
    courses: number
    teachingClasses: number
    status: string
  }>
  return {
    metrics: [
      { label: '行政班', value: rows.length, unit: '个', icon: 'building' },
      { label: '学生', value: rows.reduce((n, r) => n + r.students, 0), unit: '人', icon: 'users' },
      {
        label: '班主任',
        value: new Set(rows.map((r) => r.advisor)).size,
        unit: '人',
        icon: 'user',
        tone: 'info',
      },
    ],
    table: {
      title: '行政班',
      columns: ['行政班', '学院', '专业', '年级', '班主任', '学生', '状态'],
      rows: rows.map((r) => [
        r.name,
        r.college,
        r.major,
        r.grade,
        r.advisor,
        String(r.students),
        chip(r.status, 'ok'),
      ]),
    },
    side: {
      title: '行政班 ≠ 教学班',
      body: '行政班负责学生归属与班主任；教学班是课程下的开课单元，学生来源可以是行政班导入或自助选课。',
      items: ['行政班不绑定课程', '一个课程可有多个教学班', '名单快照可重新同步'],
    },
    note: '示例数据：/admin/admin-classes 端点尚未实现。',
  }
}

function approvalsBlock(): V2DemoBlock {
  const rows = IA.pendingCourses as Array<{
    id: number
    code: string
    name: string
    teacher: string
    college: string
    credits: number
    classes: number
    submitted: string
    risk: string
  }>
  return {
    metrics: [
      { label: '待审批', value: rows.length, unit: '门', icon: 'clipboard', tone: 'warn' },
      {
        label: '本院',
        value: rows.filter((r) => r.college === '计算机学院').length,
        unit: '门',
        icon: 'building',
      },
      {
        label: '风险提示',
        value: rows.filter((r) => r.risk !== '无').length,
        unit: '条',
        icon: 'alert',
        tone: 'danger',
      },
    ],
    table: {
      title: '课程审批队列',
      columns: ['课程', '编号', '主讲', '学院', '学分', '提交时间', '风险'],
      rows: rows.map((r) => [
        r.name,
        r.code,
        r.teacher,
        r.college,
        String(r.credits),
        r.submitted,
        chip(r.risk === '无' ? '无' : r.risk, r.risk === '无' ? 'ok' : 'warn'),
      ]),
    },
    side: {
      title: '审批即发布前置',
      body: '课程审批通过后才能发布教学班；驳回必须填写理由并写审批流水。',
      items: ['审批动作写 approved_by', '驳回理由必填', '审批记录不可编辑'],
    },
    note: '示例数据：/admin/courses/pending 端点尚未接入。',
  }
}

function teachingClassesBlock(): V2DemoBlock {
  const courses = IA.courses as Array<{
    name: string
    code: string
    teacher: string
    classes: Array<{ name: string; students: number; source: string; status: string }>
  }>
  const rows = courses.flatMap((course) =>
    course.classes.map((cls) => [
      cls.name,
      course.name,
      course.code,
      course.teacher,
      String(cls.students),
      cls.source,
      chip(cls.status === 'ACTIVE' ? '进行中' : '草稿', cls.status === 'ACTIVE' ? 'ok' : 'mute'),
    ]),
  ) as Cell[][]
  return {
    metrics: [
      { label: '教学班', value: rows.length, unit: '个', icon: 'users' },
      {
        label: '学生',
        value: rows.reduce((n, r) => n + Number(r[4] || 0), 0),
        unit: '人',
        icon: 'users',
      },
      { label: '课程', value: courses.length, unit: '门', icon: 'book', tone: 'info' },
    ],
    table: {
      title: '教学班监管',
      columns: ['教学班', '课程', '编号', '主讲', '学生', '名单来源', '状态'],
      rows,
    },
    side: {
      title: '监管只读',
      body: '管理员查看教学班规模与名单来源；不代教师维护教学班。',
      items: ['按学院过滤', '名单来源可回溯', '课程负责人维护'],
    },
    note: '示例数据：/admin/teaching-classes 端点尚未实现。',
  }
}

function enrollmentsBlock(): V2DemoBlock {
  const rows = IA.enrollments as Array<{
    student: string
    course: string
    className: string
    source: string
    status: string
    submitted: string
  }>
  return {
    metrics: [
      {
        label: '待审批',
        value: rows.filter((r) => r.status === '待审批').length,
        unit: '条',
        icon: 'clock',
        tone: 'warn',
      },
      {
        label: '已通过',
        value: rows.filter((r) => r.status === '已通过').length,
        unit: '条',
        icon: 'check',
        tone: 'ok',
      },
      { label: '记录', value: rows.length, unit: '条', icon: 'users' },
    ],
    table: {
      title: '选课与名单',
      columns: ['学生', '课程', '教学班', '来源', '状态', '提交时间'],
      rows: rows.map((r) => [
        r.student,
        r.course,
        r.className,
        r.source,
        chip(r.status, r.status === '待审批' ? 'warn' : r.status === '已通过' ? 'ok' : 'danger'),
        r.submitted,
      ]),
    },
    side: {
      title: '名单口径',
      body: '行政班导入是快照，可重新同步；自助选课按教学班策略落状态，需教师或管理员审批。',
      items: ['不物理删除选课记录', '退课置 is_active=0', '冲突需人工处理'],
    },
    note: '示例数据：/admin/enrollments 端点尚未实现。',
  }
}

function supervisionBlock(): V2DemoBlock {
  const courses = IA.courses as Array<{
    name: string
    teacher: string
    progress: number
    pending: number
    status: string
  }>
  return {
    metrics: [
      { label: '课程', value: courses.length, unit: '门', icon: 'book' },
      {
        label: '平均进度',
        value: Math.round(courses.reduce((n, c) => n + c.progress, 0) / courses.length),
        unit: '%',
        icon: 'chart',
        tone: 'ok',
      },
      { label: '待处理', value: 6, unit: '项', icon: 'alert', tone: 'warn' },
    ],
    table: {
      title: '课程运行',
      columns: ['课程', '主讲', '进度', '待批阅', '状态'],
      rows: courses.map((c) => [
        c.name,
        c.teacher,
        `${c.progress}%`,
        String(c.pending),
        chip(c.status === 'ACTIVE' ? '运行中' : '未开课', c.status === 'ACTIVE' ? 'ok' : 'mute'),
      ]),
    },
    side: {
      title: '监督只读',
      body: '运行监管看完成度与异常，不改教学数据；异常需回到课程上下文由教师处理。',
      items: ['进度来自章节完成', '待批阅来自提交记录', '异常只作线索'],
    },
    note: '示例数据：/admin/supervision/summary 端点尚未接入。',
  }
}

function examGovernanceBlock(): V2DemoBlock {
  const rows: Cell[][] = [
    [
      '计算机网络 · 期中测验',
      '计算机网络',
      '计科 2301 教学班',
      chip('进行中', 'ok'),
      '58 / 60',
      '2',
    ],
    [
      '数据结构 · 单元测',
      '数据结构与算法',
      '计科 2302 教学班',
      chip('已结束', 'mute'),
      '48 / 48',
      '0',
    ],
    ['操作系统 · 随堂测', '操作系统', '计科 2301 教学班', chip('已结束', 'mute'), '52 / 52', '1'],
  ]
  return {
    metrics: [
      { label: '进行中', value: 1, unit: '场', icon: 'shield', tone: 'ok' },
      { label: '在线', value: 58, unit: '人', icon: 'users' },
      { label: '风险待核实', value: 2, unit: '条', icon: 'alert', tone: 'danger' },
    ],
    table: {
      title: '考试会话',
      columns: ['考试', '课程', '教学班', '状态', '在线', '风险'],
      rows,
    },
    side: {
      title: '风险事件边界',
      body: '切屏、多设备与实时通道异常只作为线索，不自动判定作弊；由教师核实。',
      items: ['管理员不代教师判定', '事件留痕不可编辑', '考试结束只读'],
    },
    note: '示例数据：/admin/exam-sessions 端点尚未接入。',
  }
}

function adminGradesBlock(): V2DemoBlock {
  const courses = IA.courses as Array<{
    name: string
    teacher: string
    classes: Array<{ students: number }>
  }>
  return {
    metrics: [
      { label: '课程', value: courses.length, unit: '门', icon: 'book' },
      {
        label: '学生',
        value: courses.reduce((n, c) => n + c.classes.reduce((m, x) => m + x.students, 0), 0),
        unit: '人',
        icon: 'users',
      },
      { label: '预警', value: 10, unit: '人', icon: 'alert', tone: 'warn' },
    ],
    table: {
      title: '成绩概览',
      columns: ['课程', '教师', '学生', '均分', '及格率', '预警'],
      rows: courses.map((c) => [
        c.name,
        c.teacher,
        String(c.classes.reduce((n, x) => n + x.students, 0)),
        '82.4',
        '91%',
        '2',
      ]),
    },
    side: {
      title: '成绩口径',
      body: '总评由教师提交批阅后生效；管理员只做汇总与预警。',
      items: ['AI 建议分不计入总评', '低于 60 分进入预警', '导出脱敏'],
    },
    note: '示例数据：/admin/grades/summary 端点尚未接入。',
  }
}

function noticesBlock(): V2DemoBlock {
  const rows = IA.notices as Array<{
    title: string
    scope: string
    status: string
    time: string
    author: string
  }>
  return {
    metrics: [
      { label: '公告', value: rows.length, unit: '条', icon: 'bell' },
      {
        label: '已发布',
        value: rows.filter((r) => r.status === '已发布').length,
        unit: '条',
        icon: 'check',
        tone: 'ok',
      },
      {
        label: '待发布',
        value: rows.filter((r) => r.status !== '已发布').length,
        unit: '条',
        icon: 'clock',
        tone: 'warn',
      },
    ],
    table: {
      title: '公告',
      columns: ['标题', '范围', '发布人', '时间', '状态'],
      rows: rows.map((r) => [
        r.title,
        r.scope,
        r.author,
        r.time,
        chip(r.status, r.status === '已发布' ? 'ok' : 'warn'),
      ]),
    },
    side: {
      title: '三种通知',
      body: '系统公告、课程通知、班级通知的发送者与受众不同，不能混用一个入口。',
      items: ['系统：管理员', '课程：授课教师', '班级：班主任'],
    },
    note: '示例数据：/admin/announcements 端点尚未接入。',
  }
}

function sessionsBlock(): V2DemoBlock {
  const rows = IA.sessions as Array<{
    user: string
    role: string
    college: string
    device: string
    ip: string
    active: string
    last: string
  }>
  return {
    metrics: [
      {
        label: '在线',
        value: rows.filter((r) => r.active === '在线').length,
        unit: '个',
        icon: 'user',
        tone: 'ok',
      },
      {
        label: '离线',
        value: rows.filter((r) => r.active !== '在线').length,
        unit: '个',
        icon: 'user',
      },
      { label: '会话', value: rows.length, unit: '条', icon: 'lock', tone: 'info' },
    ],
    table: {
      title: '在线会话',
      columns: ['用户', '角色', '学院', '设备', 'IP', '状态', '最近活动'],
      rows: rows.map((r) => [
        r.user,
        r.role,
        r.college,
        r.device,
        r.ip,
        chip(r.active, r.active === '在线' ? 'ok' : 'mute'),
        r.last,
      ]),
    },
    side: {
      title: '安全提示',
      body: '异常会话与登录失败写入审计；管理员操作必须留痕。',
      items: ['强制下线需二次确认', '会话记录只读', '登录日志单独留档'],
    },
    note: '示例数据：/user-sessions 端点尚未接入。',
  }
}

function profileBlock(): V2DemoBlock {
  return {
    metrics: [
      { label: '账号状态', value: '正常', icon: 'shield', tone: 'ok' },
      { label: '双因素', value: '未开启', icon: 'lock', tone: 'warn' },
      { label: '登录设备', value: 2, unit: '台', icon: 'user' },
    ],
    table: {
      title: '账号与安全',
      columns: ['项目', '状态', '说明'],
      rows: [
        ['登录密码', chip('已设置', 'ok'), '建议每 90 天更换一次'],
        ['双因素认证', chip('未开启', 'warn'), '开启后登录需二次验证'],
        ['通知偏好', chip('站内信', 'info'), '可在个人设置中调整'],
        ['最近登录', '今天 14:31', 'Chrome / Windows'],
      ],
    },
    side: {
      title: '身份来自登录会话',
      body: '新版沿用现有认证与权限守卫；页面上的姓名与角色取自服务端会话，不提供前端角色切换。',
      items: ['角色由服务端回传', '权限变更需重新登录', '旧版入口保留'],
    },
    note: '示例数据：本页读数未取自接口。账号与安全的后端接口已存在，本版仅展示会话信息。',
  }
}

export function demoBlock(
  role: 'student' | 'teacher' | 'admin',
  page: string,
  sub = '',
): V2DemoBlock | null {
  if (page === 'profile') return profileBlock()
  if (role === 'student') {
    if (page === 'academic') return academicBlock(sub)
    const module = findModule(role, page)
    return module
      ? { metrics: metricsOf(module, []), table: module.table, side: module.side, note: '示例数据' }
      : null
  }
  if (role === 'teacher') {
    if (page === 'inbox') return inboxBlock(sub)
    if (page.startsWith('advising'))
      return advisingBlock(page.replace('advising', '').toLowerCase())
    if (page === 'resources') return resourcesBlock()
    const module = findModule(role, page)
    return module
      ? { metrics: metricsOf(module, []), table: module.table, side: module.side, note: '示例数据' }
      : null
  }
  if (page === 'colleges') return collegesBlock()
  if (page === 'adminClasses') return adminClassesBlock()
  if (page === 'approvals') return approvalsBlock()
  if (page === 'teachingClasses') return teachingClassesBlock()
  if (page === 'enrollments') return enrollmentsBlock()
  if (page === 'supervision') return supervisionBlock()
  if (page === 'examGovernance') return examGovernanceBlock()
  if (page === 'grades') return adminGradesBlock()
  if (page === 'notices') return noticesBlock()
  if (page === 'sessions') return sessionsBlock()
  const module = findModule(role, page)
  return module
    ? { metrics: metricsOf(module, []), table: module.table, side: module.side, note: '示例数据' }
    : null
}
