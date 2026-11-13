import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/api/request', () => ({ default: mockRequest }))

describe('API 模块 - auth.js', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login 调用 POST /auth/login', async () => {
    const authApi = (await import('@/api/auth')).default
    const credentials = { username: 'test', password: 'pass' }
    mockRequest.post.mockResolvedValue({ data: { token: 'jwt' } })

    await authApi.login(credentials)
    expect(mockRequest.post).toHaveBeenCalledWith('/auth/login', credentials)
  })

  it('logout 调用 POST /auth/logout', async () => {
    const authApi = (await import('@/api/auth')).default
    mockRequest.post.mockResolvedValue({})

    await authApi.logout()
    expect(mockRequest.post).toHaveBeenCalledWith('/auth/logout')
  })

  it('getPermissions 调用 GET /auth/permissions', async () => {
    const authApi = (await import('@/api/auth')).default
    mockRequest.get.mockResolvedValue({ data: ['user:read', 'course:write'] })

    const result = await authApi.getPermissions()
    expect(mockRequest.get).toHaveBeenCalledWith('/auth/permissions')
    expect(result.data).toContain('user:read')
  })
})

describe('API 模块 - course.js', () => {
  it('getCourseList 调用 GET /courses', async () => {
    const courseApi = (await import('@/api/course')).default
    mockRequest.get.mockResolvedValue({ data: [{ id: 1, name: '计算机网络' }] })

    const result = await courseApi.getCourseList({ page: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/courses', { params: { page: 1 } })
    expect((result.data as unknown as Array<{ name: string }>)[0].name).toBe('计算机网络')
  })
})

describe('API 模块 - ai.js (接口)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('qaAsk 调用 POST /ai/qa/ask', async () => {
    const aiApi = await import('@/api/ai')
    mockRequest.post.mockResolvedValue({ data: { answer: 'RAG答案', sessionId: 's1' } })

    const result = await aiApi.qaAsk({ question: '什么是TCP', courseId: 1 })
    expect(mockRequest.post).toHaveBeenCalledWith('/ai/qa/ask', {
      question: '什么是TCP',
      courseId: 1,
    })
    expect(result.data.answer).toBe('RAG答案')
  })

  it('qaContinue 调用 POST /ai/qa/continue', async () => {
    const aiApi = await import('@/api/ai')
    mockRequest.post.mockResolvedValue({ data: { answer: '继续回答' } })

    await aiApi.qaContinue({ message: '继续', sessionId: 's1' })
    expect(mockRequest.post).toHaveBeenCalledWith('/ai/qa/continue', {
      message: '继续',
      sessionId: 's1',
    })
  })

  it('getSessions 调用 GET /ai/qa/sessions', async () => {
    const aiApi = await import('@/api/ai')
    mockRequest.get.mockResolvedValue({ data: [{ sessionId: 's1', title: '对话1' }] })

    const result = await aiApi.getSessions()
    expect(mockRequest.get).toHaveBeenCalledWith('/ai/qa/sessions')
    expect(result.data[0].title).toBe('对话1')
  })

  it('learningRecommendations 调用 GET /ai/recommendations/learning', async () => {
    const aiApi = await import('@/api/ai')
    mockRequest.get.mockResolvedValue({ data: { recommendation: '推荐内容' } })

    const result = await aiApi.learningRecommendations()
    expect(mockRequest.get).toHaveBeenCalledWith('/ai/recommendations/learning')
    expect(result.data.recommendation).toBe('推荐内容')
  })
})

describe('API 模块 - exam.js', () => {
  it('getExamPaperList 调用 GET /exam-papers', async () => {
    const examApi = (await import('@/api/exam')).default
    mockRequest.get.mockResolvedValue({ data: [] })

    await examApi.getExamPaperList({ courseId: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/exam-papers', { params: { courseId: 1 } })
  })
})

describe('API 模块 - submission.js', () => {
  it('getMySubmissions 调用 GET /submissions', async () => {
    const subApi = (await import('@/api/submission')).default
    mockRequest.get.mockResolvedValue({ data: [] })

    await subApi.getMySubmissions({ taskId: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/submissions', { params: { taskId: 1 } })
  })
})

describe('API 模块 - notification.js', () => {
  it('getNotificationList 调用 GET /notifications', async () => {
    const notifApi = (await import('@/api/notification')).default
    mockRequest.get.mockResolvedValue({ data: [] })

    await notifApi.getNotificationList({ page: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/notifications', { params: { page: 1 } })
  })
})

describe('API 模块 - user.js', () => {
  it('getUserList 调用 GET /users', async () => {
    const userApi = (await import('@/api/user')).default
    mockRequest.get.mockResolvedValue({ data: [{ id: 1, username: 'test' }] })

    const result = await userApi.getUserList({ page: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/users', { params: { page: 1 } })
    expect(result.data[0].username).toBe('test')
  })
})

describe('API 模块 - analytics.js', () => {
  it('getDashboardStats 调用 GET /analytics/dashboard', async () => {
    const analyticsApi = (await import('@/api/analytics')).default
    mockRequest.get.mockResolvedValue({ data: { totalCourses: 5 } })

    const result = await analyticsApi.getDashboardStats({ days: 30 })
    expect(mockRequest.get).toHaveBeenCalledWith('/analytics/dashboard', { params: { days: 30 } })
    expect(result.data.totalCourses).toBe(5)
  })
})
