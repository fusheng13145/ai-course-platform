/**
 * 公共通用服务 API
 * 对应后端 CommonController: /common
 */
import request from './request'
import { type ApiData } from '@/utils/response'

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  /** POST /common/verify-code -- 发送验证码 */
  sendVerifyCode(data: ApiData) {
    return request.post('/common/verify-code', data)
  },

  /** POST /common/upload -- 单文件上传 */
  uploadFile(file: File | Blob) {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/common/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** POST /common/upload/batch -- 批量文件上传 */
  uploadBatch(files: (File | Blob)[]) {
    const fd = new FormData()
    files.forEach((f) => fd.append('files', f))
    return request.post('/common/upload/batch', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** GET /common/feature-roadmap -- 获取功能路线图 */
  getFeatureRoadmap() {
    return request.get('/common/feature-roadmap')
  },
}
