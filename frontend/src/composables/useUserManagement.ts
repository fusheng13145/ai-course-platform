import { ref, onMounted } from 'vue'
import { toast } from '@/composables/useToast'
import adminApi from '@/api/admin'
import permissionApi from '@/api/permission'
import { pickList, readTotal, unwrap } from '@/utils/response'

const ROLE_LABELS = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }

export function useUserManagement() {
  const overview = ref(
    {} as {
      totalUsers?: number
      roleDistribution?: { teacher?: number; student?: number; admin?: number }
      [key: string]: unknown
    },
  )
  const search = ref('')
  const loading = ref(true)
  const groups = ref([
    {
      key: 'admin',
      label: '管理员',
      role: 'ADMIN',
      desc: '系统管理员与教务管理员',
      open: true,
      rows: [],
      page: 1,
      size: 50,
      total: 0,
    },
    {
      key: 'teacher',
      label: '教师',
      role: 'TEACHER',
      desc: '开课教师账号',
      open: true,
      rows: [],
      page: 1,
      size: 50,
      total: 0,
    },
    {
      key: 'student',
      label: '学生',
      role: 'STUDENT',
      desc: '在读学生账号',
      open: false,
      rows: [],
      page: 1,
      size: 50,
      total: 0,
    },
  ])

  const detailUser = ref(null)
  const detailTab = ref('info')
  const permLoading = ref(false)
  const permNodes = ref([])

  const showCreate = ref(false)
  const creating = ref(false)
  const resetUser = ref(null)
  const resetting = ref(false)
  const deleteUser = ref(null)
  const deleting = ref(false)
  /** 批量导入（Excel）相关状态 */
  const showImport = ref(false)
  const importing = ref(false)
  const importResult = ref(null)

  function roleLabel(r) {
    return ROLE_LABELS[r] || r || '—'
  }

  async function load() {
    loading.value = true
    try {
      const [ovRes, ...roleRes] = await Promise.all([
        adminApi.getOverview(),
        ...groups.value.map((g) =>
          adminApi.getUsers({
            page: g.page,
            size: g.size,
            role: g.role,
            keyword: search.value.trim() || undefined,
          }),
        ),
      ])
      overview.value = unwrap(ovRes) || {}
      roleRes.forEach((res, i) => {
        const group = groups.value[i]
        const payload = unwrap(res)
        group.rows = pickList(payload)
        group.total = readTotal(payload, group.rows.length)
      })
    } catch {
      toast.show('用户数据加载失败', 'error')
    } finally {
      loading.value = false
    }
  }

  async function changeGroupPage(groupKey, page) {
    const group = groups.value.find((item) => item.key === groupKey)
    if (!group || page === group.page) return
    group.page = page
    loading.value = true
    try {
      const response = await adminApi.getUsers({
        page: group.page,
        size: group.size,
        role: group.role,
        keyword: search.value.trim() || undefined,
      })
      const payload = unwrap(response)
      group.rows = pickList(payload)
      group.total = readTotal(payload, group.rows.length)
    } catch {
      toast.show(`${group.label}列表加载失败`, 'error')
    } finally {
      loading.value = false
    }
  }

  let searchTimer = null
  function onSearchInput() {
    groups.value.forEach((group) => (group.page = 1))
    clearTimeout(searchTimer)
    searchTimer = setTimeout(load, 350)
  }

  async function openDetail(u) {
    detailUser.value = u
    detailTab.value = 'info'
    permLoading.value = true
    permNodes.value = []
    try {
      const res = await permissionApi.getRolePermissions(u.role)
      const perms = res?.data?.data || res?.data || []
      const byId = {}
      perms.forEach((p) => {
        byId[p.id] = { ...p, children: [] }
      })
      const roots = []
      Object.values(byId).forEach(
        (p: {
          id: string
          parentId?: string
          permissionName: string
          resourcePath?: string
          children: unknown[]
        }) => {
          if (p.parentId && byId[p.parentId]) byId[p.parentId].children.push(p)
          else roots.push(p)
        },
      )
      permNodes.value = roots.map(toNode)
    } catch {
      permNodes.value = []
    } finally {
      permLoading.value = false
    }
  }

  function toNode(p) {
    return {
      key: p.id,
      label: p.permissionName + (p.resourcePath ? ` · ${p.resourcePath}` : ''),
      icon: 'file',
      children: p.children.map(toNode),
    }
  }

  async function handleCreate(form) {
    creating.value = true
    try {
      const res = await adminApi.createUser({ ...form })
      if (res?.data?.code === 200 || res?.data?.data) {
        toast.show('用户已创建', 'ok')
        showCreate.value = false
        load()
      } else {
        toast.show(res?.data?.message || '创建失败', 'error')
      }
    } catch {
      toast.show('创建失败', 'error')
    } finally {
      creating.value = false
    }
  }

  function openReset(u) {
    resetUser.value = u
  }

  async function handleReset(newPassword) {
    resetting.value = true
    try {
      const res = await adminApi.resetUserPassword(resetUser.value.id, { newPassword })
      if (res?.data?.code === 200 || res?.data?.data) {
        toast.show('密码已重置', 'ok')
        resetUser.value = null
      } else {
        toast.show(res?.data?.message || '重置失败', 'error')
      }
    } catch {
      toast.show('重置失败', 'error')
    } finally {
      resetting.value = false
    }
  }

  function openDelete(u) {
    deleteUser.value = u
  }

  async function handleDelete() {
    deleting.value = true
    try {
      const res = await adminApi.deleteUser(deleteUser.value.id)
      if (res?.data?.code === 200 || res?.data?.data) {
        toast.show('用户已删除', 'ok')
        deleteUser.value = null
        load()
      } else {
        toast.show(res?.data?.message || '删除失败', 'error')
      }
    } catch {
      toast.show('删除失败', 'error')
    } finally {
      deleting.value = false
    }
  }

  /* ---------------- 批量导入（Excel） ---------------- */

  function openImport() {
    importResult.value = null
    showImport.value = true
  }

  /**
   * 上传 Excel 批量创建账号。
   * 后端逐行容错：单行失败不影响其他行，结果含成功/失败计数与随机密码映射。
   */
  async function handleImport(file) {
    if (!file) return
    importing.value = true
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await adminApi.importUsersExcel(formData)
      const payload = res?.data?.data || res?.data || {}
      importResult.value = payload
      const okCount = payload.successCount ?? 0
      if (okCount > 0) {
        toast.show(`成功导入 ${okCount} 个账号`, 'ok')
        load()
      } else {
        toast.show('没有账号被导入，请检查文件内容', 'warning')
      }
    } catch {
      toast.show('导入失败，请检查文件格式（.xlsx，10 列表头）', 'error')
    } finally {
      importing.value = false
    }
  }

  onMounted(load)

  return {
    overview,
    search,
    loading,
    groups,
    detailUser,
    detailTab,
    permLoading,
    permNodes,
    showCreate,
    creating,
    resetUser,
    resetting,
    deleteUser,
    deleting,
    showImport,
    importing,
    importResult,
    roleLabel,
    onSearchInput,
    changeGroupPage,
    openDetail,
    handleCreate,
    openReset,
    handleReset,
    openDelete,
    handleDelete,
    openImport,
    handleImport,
  }
}
