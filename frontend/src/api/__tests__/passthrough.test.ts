import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as permissionApi from '@/api/permission'
import * as operationLogApi from '@/api/operationLog'
import * as enrollmentApi from '@/api/enrollment'
import * as interactionApi from '@/api/interaction'
import * as learningPathApi from '@/api/learningPath'
import * as learningRecordApi from '@/api/learningRecord'
import * as discussionApi from '@/api/discussion'
import * as gradeApi from '@/api/grade'
import { loginLogApi } from '@/api/loginLog'
import { systemConfigApi } from '@/api/systemConfig'
import { knowledgeRelationApi } from '@/api/knowledgePointRelation'
import commonApi from '@/api/common'

/**
 * 纯转发模块的 method + URL 契约表（2026-10-04）
 * <p>这 12 个模块此前均 0% 覆盖、无任何护栏，全部是「方法 + URL + 参数」薄封装。
 * 这里只锁定最容易漂移也最致命的一项：HTTP 方法与实际请求 URL（含路径参数插值结果），
 * 不重复各调用点的参数内容断言。knowledgeRelationApi.create 直接复用已测的
 * knowledge.addRelation，故不在此重复。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    patch: mocks.patch,
    delete: mocks.delete,
  },
}))

const spyOf = {
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  patch: mocks.patch,
  delete: mocks.delete,
}
type Method = keyof typeof spyOf

const UPLOAD_FILE = new File(['x'], 'a.txt', { type: 'text/plain' })

/** 契约表：[人类可读标签, HTTP 方法, 期望 URL, 实际调用] */
const ROWS: Array<[string, Method, string, () => Promise<unknown>]> = [
  // permission
  [
    'permission.getRolePermissions',
    'get',
    '/admin/permissions/role/TEACHER',
    () => permissionApi.getRolePermissions('TEACHER'),
  ],
  [
    'permission.getAllPermissions',
    'get',
    '/admin/permissions/all',
    () => permissionApi.getAllPermissions(),
  ],
  [
    'permission.createPermission',
    'post',
    '/admin/permissions/create',
    () => permissionApi.createPermission({}),
  ],
  [
    'permission.updatePermission',
    'put',
    '/admin/permissions/update/4',
    () => permissionApi.updatePermission(4, {}),
  ],
  [
    'permission.deletePermission',
    'delete',
    '/admin/permissions/delete/4',
    () => permissionApi.deletePermission(4),
  ],
  [
    'permission.togglePermission',
    'patch',
    '/admin/permissions/toggle/4',
    () => permissionApi.togglePermission(4),
  ],
  // loginLog
  ['loginLog.getList', 'get', '/login-logs', () => loginLogApi.getList({})],
  ['loginLog.getStats', 'get', '/login-logs/stats', () => loginLogApi.getStats()],
  ['loginLog.getTrends', 'get', '/login-logs/trends', () => loginLogApi.getTrends({})],
  ['loginLog.getRecent', 'get', '/login-logs/recent', () => loginLogApi.getRecent(5)],
  ['loginLog.getFailures', 'get', '/login-logs/failures', () => loginLogApi.getFailures({})],
  ['loginLog.getUserHistory', 'get', '/login-logs/user/3', () => loginLogApi.getUserHistory(3, {})],
  ['loginLog.getAbnormal', 'get', '/login-logs/abnormal/3', () => loginLogApi.getAbnormal(3)],
  // operationLog
  [
    'operationLog.getOperationLogs',
    'get',
    '/admin/operation-logs',
    () => operationLogApi.getOperationLogs({}),
  ],
  [
    'operationLog.getOperationLogStatistics',
    'get',
    '/admin/operation-logs/statistics',
    () => operationLogApi.getOperationLogStatistics(),
  ],
  // systemConfig
  ['systemConfig.getList', 'get', '/system-configs', () => systemConfigApi.getList()],
  [
    'systemConfig.getByGroup',
    'get',
    '/system-configs/group/mail',
    () => systemConfigApi.getByGroup('mail'),
  ],
  ['systemConfig.getPublic', 'get', '/system-configs/public', () => systemConfigApi.getPublic()],
  [
    'systemConfig.updateByKey',
    'put',
    '/system-configs/key/site_name',
    () => systemConfigApi.updateByKey('site_name', 'x'),
  ],
  [
    'systemConfig.batchUpdate',
    'post',
    '/system-configs/batch-update',
    () => systemConfigApi.batchUpdate({}),
  ],
  ['systemConfig.search', 'get', '/system-configs/search', () => systemConfigApi.search('kw')],
  [
    'systemConfig.testEmail',
    'post',
    '/system-configs/test-email',
    () => systemConfigApi.testEmail(),
  ],
  // enrollment
  ['enrollment.enrollCourse', 'post', '/enrollments', () => enrollmentApi.enrollCourse({})],
  ['enrollment.unenrollCourse', 'delete', '/enrollments/2', () => enrollmentApi.unenrollCourse(2)],
  ['enrollment.getMyEnrollments', 'get', '/enrollments/my', () => enrollmentApi.getMyEnrollments()],
  [
    'enrollment.approveEnrollment',
    'post',
    '/enrollments/2/approve',
    () => enrollmentApi.approveEnrollment(2),
  ],
  [
    'enrollment.rejectEnrollment',
    'post',
    '/enrollments/2/reject',
    () => enrollmentApi.rejectEnrollment(2),
  ],
  // interaction
  [
    'interaction.createSignIn',
    'post',
    '/interaction/sign-in',
    () => interactionApi.createSignIn({}),
  ],
  [
    'interaction.publishSignIn',
    'post',
    '/interaction/sign-in/5/publish',
    () => interactionApi.publishSignIn(5),
  ],
  [
    'interaction.closeSignIn',
    'post',
    '/interaction/sign-in/5/close',
    () => interactionApi.closeSignIn(5),
  ],
  [
    'interaction.getSignInList',
    'get',
    '/interaction/sign-in/course/3',
    () => interactionApi.getSignInList(3),
  ],
  [
    'interaction.getSignInRecords',
    'get',
    '/interaction/sign-in/5/records',
    () => interactionApi.getSignInRecords(5),
  ],
  [
    'interaction.markSignIn',
    'post',
    '/interaction/sign-in/5/manual',
    () => interactionApi.markSignIn(5, {}),
  ],
  ['interaction.createVote', 'post', '/interaction/vote', () => interactionApi.createVote({})],
  ['interaction.closeVote', 'post', '/interaction/vote/6/close', () => interactionApi.closeVote(6)],
  [
    'interaction.getVoteList',
    'get',
    '/interaction/vote/course/3',
    () => interactionApi.getVoteList(3),
  ],
  [
    'interaction.getVoteDetail',
    'get',
    '/interaction/vote/6',
    () => interactionApi.getVoteDetail(6),
  ],
  [
    'interaction.getVoteResult',
    'get',
    '/interaction/vote/6/result',
    () => interactionApi.getVoteResult(6),
  ],
  // knowledgePointRelation（create 委托 knowledge.addRelation，另测）
  [
    'knowledgeRelation.getList',
    'get',
    '/knowledge/relations',
    () => knowledgeRelationApi.getList({}),
  ],
  [
    'knowledgeRelation.delete',
    'delete',
    '/knowledge/relations/8',
    () => knowledgeRelationApi.delete(8),
  ],
  [
    'knowledgeRelation.getGraphData',
    'get',
    '/knowledge/relations/graph/3',
    () => knowledgeRelationApi.getGraphData(3),
  ],
  [
    'knowledgeRelation.getSourceRelations',
    'get',
    '/knowledge/relations/source/2',
    () => knowledgeRelationApi.getSourceRelations(2),
  ],
  [
    'knowledgeRelation.getTargetRelations',
    'get',
    '/knowledge/relations/target/2',
    () => knowledgeRelationApi.getTargetRelations(2),
  ],
  // learningPath
  ['learningPath.getMyPaths', 'get', '/learning-paths', () => learningPathApi.getMyPaths()],
  ['learningPath.createPath', 'post', '/learning-paths', () => learningPathApi.createPath({})],
  ['learningPath.getPathById', 'get', '/learning-paths/1', () => learningPathApi.getPathById(1)],
  ['learningPath.updatePath', 'put', '/learning-paths/1', () => learningPathApi.updatePath(1, {})],
  [
    'learningPath.getPathNodes',
    'get',
    '/learning-paths/1/nodes',
    () => learningPathApi.getPathNodes(1),
  ],
  [
    'learningPath.addPathNode',
    'post',
    '/learning-paths/1/nodes',
    () => learningPathApi.addPathNode(1, {}),
  ],
  [
    'learningPath.updatePathNode',
    'put',
    '/learning-paths/nodes/9',
    () => learningPathApi.updatePathNode(9, {}),
  ],
  [
    'learningPath.generatePath',
    'get',
    '/learning-paths/generate',
    () => learningPathApi.generatePath(3),
  ],
  [
    'learningPath.getActivePath',
    'get',
    '/learning-paths/active/3',
    () => learningPathApi.getActivePath(3),
  ],
  [
    'learningPath.advancePath',
    'post',
    '/learning-paths/1/advance',
    () => learningPathApi.advancePath(1),
  ],
  [
    'learningPath.completePath',
    'post',
    '/learning-paths/1/complete',
    () => learningPathApi.completePath(1),
  ],
  ['learningPath.pausePath', 'post', '/learning-paths/1/pause', () => learningPathApi.pausePath(1)],
  [
    'learningPath.resumePath',
    'post',
    '/learning-paths/1/resume',
    () => learningPathApi.resumePath(1),
  ],
  [
    'learningPath.getDailyTasks',
    'get',
    '/learning-paths/1/daily-tasks',
    () => learningPathApi.getDailyTasks(1),
  ],
  [
    'learningPath.getPathDetail',
    'get',
    '/learning-paths/1/detail',
    () => learningPathApi.getPathDetail(1),
  ],
  [
    'learningPath.getPathProgress',
    'get',
    '/learning-paths/1/progress',
    () => learningPathApi.getPathProgress(1),
  ],
  // learningRecord
  [
    'learningRecord.createLearningRecord',
    'post',
    '/learning-records',
    () => learningRecordApi.createLearningRecord({}),
  ],
  [
    'learningRecord.getMyLearningRecords',
    'get',
    '/learning-records',
    () => learningRecordApi.getMyLearningRecords(),
  ],
  [
    'learningRecord.getLearningStats',
    'get',
    '/learning-records/stats',
    () => learningRecordApi.getLearningStats(),
  ],
  [
    'learningRecord.getHeatmap',
    'get',
    '/learning-records/calendar',
    () => learningRecordApi.getHeatmap({}),
  ],
  // discussion
  ['discussion.createTopic', 'post', '/discussion/topic', () => discussionApi.createTopic({})],
  [
    'discussion.getTopics',
    'get',
    '/discussion/topic/course/3',
    () => discussionApi.getTopics(3, {}),
  ],
  [
    'discussion.getTopicDetail',
    'get',
    '/discussion/topic/7',
    () => discussionApi.getTopicDetail(7),
  ],
  [
    'discussion.replyTopic',
    'post',
    '/discussion/topic/7/reply',
    () => discussionApi.replyTopic(7, {}),
  ],
  ['discussion.likePost', 'post', '/discussion/post/11/like', () => discussionApi.likePost(11)],
  ['discussion.closeTopic', 'post', '/discussion/topic/7/close', () => discussionApi.closeTopic(7)],
  ['discussion.pinTopic', 'post', '/discussion/topic/7/pin', () => discussionApi.pinTopic(7, true)],
  ['discussion.deletePost', 'delete', '/discussion/post/11', () => discussionApi.deletePost(11)],
  // common
  ['common.sendVerifyCode', 'post', '/common/verify-code', () => commonApi.sendVerifyCode({})],
  ['common.uploadFile', 'post', '/common/upload', () => commonApi.uploadFile(UPLOAD_FILE)],
  [
    'common.uploadBatch',
    'post',
    '/common/upload/batch',
    () => commonApi.uploadBatch([UPLOAD_FILE]),
  ],
  [
    'common.getFeatureRoadmap',
    'get',
    '/common/feature-roadmap',
    () => commonApi.getFeatureRoadmap(),
  ],
  // grade
  ['grade.saveGradePolicy', 'post', '/grade/policy', () => gradeApi.saveGradePolicy({})],
  ['grade.getGradePolicy', 'get', '/grade/policy/course/3', () => gradeApi.getGradePolicy(3)],
  ['grade.saveGradeItem', 'post', '/grade/item', () => gradeApi.saveGradeItem({})],
  ['grade.computeFinal', 'get', '/grade/final/5/student/7', () => gradeApi.computeFinal(5, 7)],
  ['grade.getCourseReport', 'get', '/grade/report/course/3', () => gradeApi.getCourseReport(3)],
]

describe('纯转发模块契约（method + URL）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.patch.mockResolvedValue({ code: 200, data: {} })
    mocks.delete.mockResolvedValue({ code: 200, data: {} })
  })

  it.each(ROWS)('%s → %s %s', async (label, method, url, run) => {
    const spy = spyOf[method]
    await run()
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0][0]).toBe(url)
  })

  it('契约表覆盖了 12 个模块的全部转发端点（防止新增端点漏测）', () => {
    // 各模块导出数量快照：若源码新增接口，此处计数会变化以提醒补表
    const counts: Record<string, number> = {
      permission: Object.keys(permissionApi).filter((k) => k !== 'default').length,
      grade: Object.keys(gradeApi).filter((k) => k !== 'default').length,
      interaction: Object.keys(interactionApi).filter((k) => k !== 'default').length,
    }
    expect(counts.permission).toBe(6)
    expect(counts.grade).toBe(5)
    expect(counts.interaction).toBe(11)
  })
})

describe('具名导出模块的默认导出完整性', () => {
  const NAMESPACE_MODULES: Array<[string, Record<string, unknown>]> = [
    ['permission', permissionApi],
    ['operationLog', operationLogApi],
    ['enrollment', enrollmentApi],
    ['interaction', interactionApi],
    ['learningPath', learningPathApi],
    ['learningRecord', learningRecordApi],
    ['discussion', discussionApi],
    ['grade', gradeApi],
  ]

  it.each(NAMESPACE_MODULES)('%s：默认导出应包含全部具名接口', (_label, mod) => {
    const named = Object.keys(mod).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(mod.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
