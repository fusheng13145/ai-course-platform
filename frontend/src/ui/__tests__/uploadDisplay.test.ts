import { describe, expect, it } from 'vitest'
import {
  formatUploadSize,
  pickUploadProgress,
  pickUploadSessions,
  uploadStatusLabel,
  uploadStatusTone,
  uploadToneToTagType,
} from '../../utils/uploadDisplay'

/**
 * 上传会话的展示语义只有 uploadDisplay 一份实现，旧端与 各自做薄映射。
 * 这张判定表一旦被改错，两棵树同时错，所以逐状态钉住。
 */
describe('上传状态语义', () => {
  it('六种已知状态都有中文译名与色调，未知状态原样透出且不静默', () => {
    expect(uploadStatusLabel('UPLOADING')).toBe('上传中')
    expect(uploadStatusLabel('MERGED')).toBe('已合并')
    expect(uploadStatusLabel('COMPLETED')).toBe('已完成')
    expect(uploadStatusLabel('CANCELLED')).toBe('已取消')
    expect(uploadStatusLabel('FAILED')).toBe('失败')
    expect(uploadStatusLabel('PENDING')).toBe('待上传')
    expect(uploadStatusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(uploadStatusLabel(undefined)).toBe('未知状态')
  })

  it('色调映射：完成=ok、进行中=warn、取消/失败=danger、其余=info', () => {
    expect(uploadStatusTone('MERGED')).toBe('ok')
    expect(uploadStatusTone('COMPLETED')).toBe('ok')
    expect(uploadStatusTone('UPLOADING')).toBe('warn')
    expect(uploadStatusTone('CANCELLED')).toBe('danger')
    expect(uploadStatusTone('FAILED')).toBe('danger')
    expect(uploadStatusTone('PENDING')).toBe('info')
  })

  it('旧端 el-tag 的 type 名与本模块色调名不同名，必须逐档换算（warn→warning）', () => {
    expect(uploadToneToTagType(uploadStatusTone('MERGED'))).toBe('success')
    expect(uploadToneToTagType(uploadStatusTone('UPLOADING'))).toBe('warning')
    expect(uploadToneToTagType(uploadStatusTone('FAILED'))).toBe('danger')
    expect(uploadToneToTagType(uploadStatusTone('PENDING'))).toBe('info')
  })

  it('字节格式化：负数与非有限值给可读占位，不出现 NaN', () => {
    expect(formatUploadSize(512)).toBe('512B')
    expect(formatUploadSize(2048)).toBe('2.0KB')
    expect(formatUploadSize(5 * 1024 * 1024)).toBe('5.0MB')
    expect(formatUploadSize(-1)).toBe('--')
    expect(formatUploadSize(Number.NaN)).toBe('--')
    expect(formatUploadSize(null)).toBe('--')
    expect(formatUploadSize(undefined)).toBe('--')
  })
})

describe('会话列表与进度响应归一', () => {
  it('裸数组、records、list 三种形状都取出列表（信封键不能只认一个）', () => {
    expect(pickUploadSessions({ data: [{ id: 1 }] })).toEqual([{ id: 1 }])
    expect(pickUploadSessions({ data: { records: [{ id: 2 }] } })).toEqual([{ id: 2 }])
    expect(pickUploadSessions({ data: { list: [{ id: 3 }] } })).toEqual([{ id: 3 }])
  })

  it('空数组是真·空列表，与「形状不可信」必须区分开', () => {
    expect(pickUploadSessions({ data: [] })).toEqual([])
    expect(pickUploadSessions({ data: { records: [], total: 0 } })).toEqual([])
    // 裸实体对象／null 不是列表：返回 null 让调用方进失败态，而不是渲染出空表骗人
    expect(pickUploadSessions({ data: { id: 1, fileName: 'a.bin' } })).toBeNull()
    expect(pickUploadSessions(null)).toBeNull()
  })

  it('records 有数据而 list 为空时不被前面的空字段吞掉', () => {
    expect(pickUploadSessions({ data: { list: [], records: [{ id: 9 }] } })).toEqual([{ id: 9 }])
  })

  it('进度取服务端磁盘真值：缺失分片序号过滤非法值，百分比由 uploaded/total 推导', () => {
    const view = pickUploadProgress({
      data: { uploaded: 3, total: 8, status: 'UPLOADING', missingIndexes: [3, 4, -1, 1.5] },
    })
    expect(view.percent).toBe(38)
    expect(view.missing).toEqual([3, 4])
    expect(view.status).toBe('UPLOADING')

    // 尚未合并但已齐片：仍按磁盘真值给出 100%
    const ready = pickUploadProgress({ data: { uploaded: 8, total: 8, status: 'UPLOADING' } })
    expect(ready.percent).toBe(100)
    expect(ready.missing).toEqual([])
  })

  it('终态会话不照抄磁盘真值：合并后 .part 已回收，照抄会把 100% 说成 0% 并谎报缺片', () => {
    // 2026-10-29 实测的 COMPLETED 响应原文（GET /upload-sessions/390272/progress）
    const completed = pickUploadProgress({
      data: { uploaded: 0, total: 1, percentage: '0.0%', status: 'COMPLETED', missingIndexes: [0] },
    })
    expect(completed).toEqual({
      status: 'COMPLETED',
      uploaded: null,
      total: null,
      missing: [],
      percent: null,
    })

    // 后端 merge/cancel 都会删分片目录，两种终态同等不可信
    expect(pickUploadProgress({ data: { uploaded: 0, total: 1, status: 'CANCELLED' } })).toEqual({
      status: 'CANCELLED',
      uploaded: null,
      total: null,
      missing: [],
      percent: null,
    })

    // 进行中的会话仍按磁盘真值上报（阴性对照：终态豁免没有吞掉正常路径）
    const uploading = pickUploadProgress({
      data: { uploaded: 0, total: 1, status: 'UPLOADING', missingIndexes: [0] },
    })
    expect(uploading.percent).toBe(0)
    expect(uploading.missing).toEqual([0])
  })

  it('进度缺字段时给 null 而不是 0，调用方据此保留上一次读数', () => {
    const partial = pickUploadProgress({ data: { status: 'UPLOADING' } })
    expect(partial.percent).toBeNull()
    expect(partial.uploaded).toBeNull()
    expect(partial.total).toBeNull()
    expect(partial.missing).toEqual([])
    expect(pickUploadProgress(null).status).toBe('')
  })
})
