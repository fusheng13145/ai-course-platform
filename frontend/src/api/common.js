import request from './request'
export const sendVerificationCode = (email, type) => request.post('/common/verification-code', { email, type })
export const uploadFile = (file, onProgress) => {
  const formData = new FormData()
  formData.append("file", file)
  return request.post("/common/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: onProgress
  })
}
export const uploadBatch = (files, onProgress) => {
  const formData = new FormData()
  files.forEach(f => formData.append("files", f))
  return request.post("/common/upload/batch", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    onUploadProgress: onProgress
  })
}
export default { sendVerificationCode, uploadFile, uploadBatch }