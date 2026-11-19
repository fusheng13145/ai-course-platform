import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import StudentReportPanel from '../views/specials/academic/StudentReportPanel.vue'

const authApi = vi.hoisted(() => ({ getCurrentUser: vi.fn() }))
const studentApi = vi.hoisted(() => ({
  getGrades: vi.fn(),
  getAllMyCourses: vi.fn(),
  getVisualizationData: vi.fn(),
}))
const transcript = vi.hoisted(() => ({ openTranscript: vi.fn() }))
const xlsxExport = vi.hoisted(() => ({ downloadWorkbook: vi.fn() }))
const uiState = vi.hoisted(() => ({ toast: vi.fn() }))

vi.mock('@/api/auth', () => ({ default: authApi }))
vi.mock('@/api/student', () => ({ default: studentApi }))
vi.mock('@/utils/transcript', () => transcript)
vi.mock('@/utils/xlsxExport', () => xlsxExport)
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast: uiState.toast }) }))

const PROFILE = {
  fullName: '李昂',
  studentId: '2023010101',
  schoolName: '江苏科技大学',
  collegeName: '计算机学院',
  majorName: '软件工程',
  grade: '2023',
}

const GRADES = {
  courses: [
    {
      courseId: 4178,
      courseName: '计算机网络',
      finalScore: 86.5,
      progress: 72,
    },
    {
      courseId: 4007,
      courseName: 'Python 程序设计',
      finalScore: 92,
      progress: 100,
    },
  ],
}

const MY_COURSES = [
  {
    id: 4178,
    name: '计算机网络',
    code: 'CN202',
    credits: 3.5,
    semester: '2026-2027-1',
  },
  {
    id: 4007,
    name: 'Python 程序设计',
    code: 'CS101',
    credits: 2,
    semester: '2025-2026-2',
  },
]

const VISUALIZATION = {
  durationTrend: [
    { date: '2026-11-12', seconds: 1800 },
    { date: '2026-11-13', seconds: 0 },
    { date: '2026-11-14', seconds: 2400 },
  ],
  radarData: [
    { dimension: '计算机网络', weakRate: 62.5 },
    { dimension: 'Python 程序设计', weakRate: 21 },
  ],
}

function envelope(data: unknown) {
  return { code: 200, message: 'ok', data }
}

function findButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((node) => node.text().includes(label))
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  authApi.getCurrentUser.mockResolvedValue(envelope(PROFILE))
  studentApi.getGrades.mockResolvedValue(envelope(GRADES))
  studentApi.getAllMyCourses.mockResolvedValue(MY_COURSES)
  studentApi.getVisualizationData.mockResolvedValue(envelope(VISUALIZATION))
  transcript.openTranscript.mockReturnValue(true)
})

describe('学业档案 · 学情报告', () => {
  it('聚合课程成绩、学分、学习投入与薄弱课程', async () => {
    const wrapper = mount(StudentReportPanel)
    await settle()

    expect(wrapper.text()).toContain('学情概览')
    expect(wrapper.text()).toContain('李昂')
    expect(wrapper.text()).toContain('江苏科技大学')
    expect(wrapper.text()).toContain('计算机网络')
    expect(wrapper.text()).toContain('CN202')
    expect(wrapper.text()).toContain('86.5')
    expect(wrapper.text()).toContain('5.5')
    expect(wrapper.text()).toContain('近 14 天学习')
    expect(wrapper.text()).toContain('70分钟')
    expect(wrapper.text()).toContain('薄弱课程')
    expect(wrapper.text()).toContain('62.5')

    wrapper.unmount()
  })

  it('打印成绩单并导出三张工作表', async () => {
    const wrapper = mount(StudentReportPanel)
    await settle()

    await findButton(wrapper, '打印成绩单')?.trigger('click')
    expect(transcript.openTranscript).toHaveBeenCalledTimes(1)
    const [profile, courses] = transcript.openTranscript.mock.calls[0]
    expect(profile.schoolName).toBe('江苏科技大学')
    expect(courses).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'CN202',
          name: '计算机网络',
          credits: 3.5,
          semester: '2026-2027-1',
          score: 86.5,
        }),
      ]),
    )

    await findButton(wrapper, '导出学情报告')?.trigger('click')
    await settle()
    expect(xlsxExport.downloadWorkbook).toHaveBeenCalledTimes(1)
    const [sheets, fileName] = xlsxExport.downloadWorkbook.mock.calls[0]
    expect(sheets.map((sheet: { name: string }) => sheet.name)).toEqual([
      '概览',
      '课程成绩',
      '薄弱课程',
    ])
    expect(fileName).toContain('学情报告-')

    wrapper.unmount()
  })
})
