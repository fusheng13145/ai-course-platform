import type { ApiResponse, PagedResponse, PageParams } from '@/types'

declare module '@/api/auth' {
  export interface LoginParams {
    username: string
    password: string
    rememberMe?: boolean
  }
  export interface RegisterParams {
    username: string
    fullName: string
    phone: string
    role: string
    password: string
    confirmPassword: string
  }
  export interface LoginResult {
    token: string
    refreshToken: string
    user: import('@/types').User
  }
}

declare module '@/api/course' {
  export interface CourseQuery extends PageParams {
    keyword?: string
    category?: string
    status?: string
    publishedOnly?: boolean
  }
}

declare module '@/api/notification' {
  export interface NotificationQuery extends PageParams {
    isRead?: boolean
    category?: string
  }
}

declare module '@/api/exam' {
  export interface ExamQuery extends PageParams {
    courseId?: number
    status?: string
  }
  export interface SubmitExamParams {
    examSessionId: number
    answers: Record<number, string | string[]>
  }
}

declare module '@/api/submission' {
  export interface SubmissionQuery extends PageParams {
    taskId?: number
    studentId?: number
    status?: string
  }
}
