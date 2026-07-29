import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

// Mock vue-router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' }, query: {} }),
  useRouter: () => ({ push: mockPush }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// Mock course API
const mockGetCourseById = vi.fn()
vi.mock('@/api/course', () => ({
  default: { getCourseById: mockGetCourseById },
}))

// Mock chapter API
const mockGetChapters = vi.fn()
vi.mock('@/api/chapter', () => ({
  default: { getChapters: mockGetChapters },
}))

// Mock enrollment API
const mockGetMyEnrollments = vi.fn()
const mockEnrollCourse = vi.fn()
vi.mock('@/api/enrollment', () => ({
  default: {
    getMyEnrollments: mockGetMyEnrollments,
    enrollCourse: mockEnrollCourse,
  },
}))

// Mock learning record API
const mockCreateLearningRecord = vi.fn()
vi.mock('@/api/learningRecord', () => ({
  default: { createLearningRecord: mockCreateLearningRecord },
}))

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

// Mock element-plus icons
vi.mock('@element-plus/icons-vue', () => ({
  Plus: { name: 'Plus' },
  ArrowRight: { name: 'ArrowRight' },
  Select: { name: 'Select' },
  Timer: { name: 'Timer' },
  ArrowLeft: { name: 'ArrowLeft' },
  VideoPlay: { name: 'VideoPlay' },
}))

let CourseDetail
describe('CourseDetail 课程详情页', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    // 默认 API mock 数据
    mockGetCourseById.mockResolvedValue({
      data: {
        id: 1,
        name: '测试课程',
        description: '这是一个测试课程的描述',
        category: '计算机科学',
        teacherName: '张老师',
        credits: 3,
        difficulty: '中等',
      },
    })
    mockGetChapters.mockResolvedValue({
      data: [
        { id: 1, title: '第一章', description: '基础入门', duration: 30, status: 'COMPLETED' },
        { id: 2, title: '第二章', description: '进阶内容', duration: 45, status: 'IN_PROGRESS' },
      ],
    })
    mockGetMyEnrollments.mockResolvedValue({
      data: {
        list: [{ id: 1, courseId: 1, isActive: true, progress: 0.5 }],
      },
    })

    const mod = await import('../CourseDetail.vue')
    CourseDetail = mod.default
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('基础渲染', () => {
    let wrapper
    beforeEach(async () => {
      wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': { template: '<div class="el-page-header"><slot name="content" /></div>' },
            'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['size', 'type'] },
            'el-progress': { template: '<div class="el-progress"></div>', props: ['percentage', 'strokeWidth', 'color', 'style', 'showText'] },
            'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'], emits: ['update:modelValue'] },
            'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
            'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
            'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
            'el-button': { template: '<button :disabled="$attrs.loading || $attrs.disabled" @click="$emit(\'click\')"><slot /></button>', props: ['type', 'size', 'text', 'loading', 'disabled'], emits: ['click'] },
            'el-icon': { template: '<i><slot /></i>', props: ['size', 'color'] },
            'el-empty': { template: '<div class="el-empty"><slot /></div>', props: ['description', 'imageSize'] },
            'el-alert': { template: '<div class="el-alert"><slot name="default" /></div>', props: ['title', 'type', 'closable', 'showIcon'] },
            RouterLink: true,
            Transition: true,
          },
          directives: {
            loading: {},
          },
        },
      })
      await nextTick()
      // 等待异步数据加载
      await new Promise(resolve => setTimeout(resolve, 50))
    })

    it('组件正常渲染', () => {
      expect(wrapper.exists()).toBe(true)
    })

    it('渲染课程标题', () => {
      const html = wrapper.html()
      expect(html).toContain('测试课程')
    })

    it('渲染课程描述', () => {
      const html = wrapper.html()
      expect(html).toContain('这是一个测试课程的描述')
    })

    it('渲染进度概览', () => {
      expect(wrapper.find('.progress-overview').exists()).toBe(true)
    })

    it('渲染课程头部卡片', () => {
      expect(wrapper.find('.course-header-card').exists()).toBe(true)
    })

    it('渲染主 tabs 区域', () => {
      expect(wrapper.find('.main-tabs').exists()).toBe(true)
    })
  })

  describe('数据逻辑', () => {
    it('onMounted 时加载课程数据', async () => {
      mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      // 等待异步加载
      await new Promise(resolve => setTimeout(resolve, 100))

      expect(mockGetCourseById).toHaveBeenCalledWith('1')
      expect(mockGetChapters).toHaveBeenCalledWith('1')
      expect(mockGetMyEnrollments).toHaveBeenCalled()
    })

    it('getProgressColor 返回正确颜色', () => {
      const wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      expect(wrapper.vm.getProgressColor(90)).toBe('var(--ac-success)')
      expect(wrapper.vm.getProgressColor(60)).toBe('var(--ac-primary)')
      expect(wrapper.vm.getProgressColor(40)).toBe('var(--ac-warning)')
      expect(wrapper.vm.getProgressColor(10)).toBe('var(--ac-danger)')
    })

    it('getDifficultyType 返回正确类型', () => {
      const wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      expect(wrapper.vm.getDifficultyType('简单')).toBe('success')
      expect(wrapper.vm.getDifficultyType('中等')).toBe('warning')
      expect(wrapper.vm.getDifficultyType('困难')).toBe('danger')
      expect(wrapper.vm.getDifficultyType('未知')).toBe('info')
    })

    it('estimatedDuration 计算总预计时长', async () => {
      const wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      await new Promise(resolve => setTimeout(resolve, 100))

      // 章节有 30 + 45 = 75 分钟
      expect(wrapper.vm.estimatedDuration).toBe(75)
    })

    it('章节数据正确映射', async () => {
      const wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      await new Promise(resolve => setTimeout(resolve, 100))

      expect(wrapper.vm.chapters).toHaveLength(2)
      expect(wrapper.vm.chapters[0].completed).toBe(true)
      expect(wrapper.vm.chapters[1].current).toBe(true)
    })

    it('课程信息正确填充', async () => {
      const wrapper = mount(CourseDetail, {
        global: {
          stubs: {
            'el-page-header': true,
            'el-tag': true,
            'el-progress': true,
            'el-tabs': true,
            'el-tab-pane': true,
            'el-descriptions': true,
            'el-descriptions-item': true,
            'el-button': true,
            'el-icon': true,
            'el-empty': true,
            'el-alert': true,
            RouterLink: true,
            Transition: true,
          },
          directives: { loading: {} },
        },
      })

      await new Promise(resolve => setTimeout(resolve, 100))

      expect(wrapper.vm.course.name).toBe('测试课程')
      expect(wrapper.vm.course.category).toBe('计算机科学')
      expect(wrapper.vm.course.teacherName).toBe('张老师')
      expect(wrapper.vm.course.credits).toBe(3)
    })
  })
})
