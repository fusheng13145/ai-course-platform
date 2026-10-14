import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import resourceApi from '@/api/resource'
import courseApi from '@/api/course'
import { pickList, unwrap } from '@/utils/response'
import { confirmDanger } from '@/utils/dangerConfirm'

export function useResourceManagement() {
  const loading = ref(false)
  const courseList = ref([])
  const selectedCourseId = ref(null)
  const resources = ref([])
  const keyword = ref('')
  const typeFilter = ref('')

  const showUpload = ref(false)
  const uploading = ref(false)

  function classifyType(fileType) {
    const t = (fileType || '').toLowerCase()
    if (/pdf|word|doc|excel|xls|ppt|txt|application/.test(t)) return 'doc'
    if (/image|jpg|jpeg|png|gif|bmp/.test(t)) return 'image'
    if (/video|audio|mp4|mp3|mpeg|avi/.test(t)) return 'media'
    return 'other'
  }

  const currentCourseName = computed(() => {
    const c = courseList.value.find((x) => x.id === selectedCourseId.value)
    return c ? c.name || c.title : ''
  })

  const resStats = computed(() => {
    const list = resources.value
    return {
      total: list.length,
      publicCount: list.filter((r) => r.visibleToAll !== false).length,
      docCount: list.filter((r) => classifyType(r.fileType) === 'doc').length,
      mediaCount: list.filter((r) => classifyType(r.fileType) === 'media').length,
    }
  })

  const filteredResources = computed(() => {
    let list = resources.value
    if (typeFilter.value) list = list.filter((r) => classifyType(r.fileType) === typeFilter.value)
    const q = keyword.value.trim().toLowerCase()
    if (q)
      list = list.filter(
        (r) =>
          (r.fileName || r.name || '').toLowerCase().includes(q) ||
          (r.description || '').toLowerCase().includes(q),
      )
    return list
  })

  /* ---- 数据加载 ---- */

  async function loadCourses() {
    try {
      courseList.value = await courseApi.getAllCourses()
      if (courseList.value.length && !selectedCourseId.value) {
        selectedCourseId.value = courseList.value[0].id
        await loadResources()
      }
    } catch (e) {
      logger.error('加载课程失败:', e)
      courseList.value = []
    }
  }

  async function loadResources() {
    if (!selectedCourseId.value) return
    loading.value = true
    try {
      const res = await resourceApi.getResourceList(selectedCourseId.value)
      resources.value = pickList(unwrap(res))
    } catch (e) {
      logger.error('加载资源失败:', e)
      resources.value = []
      ElMessage.error('加载资源失败')
    } finally {
      loading.value = false
    }
  }

  function onSearch() {
    /* 前端过滤即可，computed 已处理 */
  }

  async function toggleVisibility(row, val) {
    try {
      // 缺陷修复（2026-10-04）：原实现传 `{ visibleToAll: val }`，而 api 层签名是
      // toggleVisibility(id, visible) —— 对象被再次塞进 DTO 字段，上报成
      // `{"visibleToAll":{"visibleToAll":true}}`，后端 Boolean 反序列化失败必 400。
      await resourceApi.toggleVisibility(row.id, val)
      row.visibleToAll = val
      ElMessage.success(val ? '已设为公开' : '已设为私有')
    } catch {
      ElMessage.error('设置失败')
    }
  }

  async function downloadRes(row) {
    try {
      const res = await resourceApi.downloadResource(row.id)
      const blob = new Blob([res.data || res])
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.fileName || row.name || `resource-${row.id}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch (e) {
      logger.error(e)
      ElMessage.error('下载失败')
    }
  }

  function removeRes(row) {
    confirmDanger({
      verb: '删除',
      target: '资源',
      subject: row.fileName || row.name,
      impact: '将一并移除该资源文件，课程中学生将无法再下载',
    })
      .then(async (ok) => {
        if (!ok) return
        try {
          await resourceApi.deleteResource(row.id)
          ElMessage.success('删除成功')
          await loadResources()
        } catch {
          ElMessage.error('删除失败')
        }
      })
      .catch(() => {})
  }

  async function doUpload(payload) {
    // 缺陷修复（P0-5）：原实现 `fd.append('courseId', selectedCourseId.value)` ——
    // 未选课程时 append 的是 null，FormData 会把它序列化成字符串 "null" 上报，
    // 后端按 Long 解析必然 400，而界面只显示一句无信息量的「上传失败」。
    // 现在空课程直接本地拒发并明确提示。
    const courseId = selectedCourseId.value
    if (courseId === null || courseId === undefined || courseId === '') {
      ElMessage.warning('请先选择所属课程后再上传')
      return
    }
    // 缺陷修复（2026-10-04）：未选择文件时 `fd.append('file', undefined)` 会把
    // undefined 序列化成字符串 "undefined" 上报，后端 MultipartFile 解析到一个 9 字节的
    // 文本分片，最终报「上传失败」而掩盖真实原因（用户忘了选文件）。
    if (!payload?.file) {
      ElMessage.warning('请先选择要上传的文件')
      return
    }
    uploading.value = true
    try {
      const fd = new FormData()
      fd.append('file', payload.file)
      fd.append('courseId', String(courseId))
      fd.append('description', payload.description || '')
      await resourceApi.uploadResource(fd)
      ElMessage.success('上传成功')
      showUpload.value = false
      await loadResources()
    } catch (e) {
      logger.error(e)
      ElMessage.error('上传失败：' + (e.response?.data?.message || e.message || '未知错误'))
    } finally {
      uploading.value = false
    }
  }

  onMounted(loadCourses)

  return {
    loading,
    courseList,
    selectedCourseId,
    resources,
    keyword,
    typeFilter,
    showUpload,
    uploading,
    currentCourseName,
    resStats,
    filteredResources,
    onSearch,
    toggleVisibility,
    downloadRes,
    removeRes,
    doUpload,
  }
}
