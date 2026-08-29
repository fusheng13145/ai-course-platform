import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import resourceApi from '@/api/resource'
import courseApi from '@/api/course'

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
      const res = await courseApi.getCourseList({ size: 500 })
      const raw = res.data?.records || res.data?.list || res.data || res
      courseList.value = Array.isArray(raw) ? raw : []
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
      const raw = res.data?.records || res.data?.list || res.data || res
      resources.value = Array.isArray(raw) ? raw : []
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
      await resourceApi.toggleVisibility(row.id, { visibleToAll: val })
      row.visibleToAll = val
      ElMessage.success(val ? '已设为公开' : '已设为私有')
    } catch (e) {
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
    ElMessageBox.confirm(`确定删除资源「${row.fileName || row.name}」？`, '删除确认', {
      type: 'warning',
    })
      .then(async () => {
        try {
          await resourceApi.deleteResource(row.id)
          ElMessage.success('删除成功')
          await loadResources()
        } catch (e) {
          ElMessage.error('删除失败')
        }
      })
      .catch(() => {})
  }

  async function doUpload(payload) {
    uploading.value = true
    try {
      const fd = new FormData()
      fd.append('file', payload.file)
      fd.append('courseId', selectedCourseId.value)
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
