import { ref, onMounted } from 'vue'
import { toast } from '@/composables/useToast'
import adminApi from '@/api/admin'
import permissionApi from '@/api/permission'

const ROLE_LABELS = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }

export function useUserManagement() {
  const overview = ref({} as Record<string, unknown>)
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
    },
    { key: 'teacher', label: '教师', role: 'TEACHER', desc: '开课教师账号', open: true, rows: [] },
    { key: 'student', label: '学生', role: 'STUDENT', desc: '在读学生账号', open: false, rows: [] },
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
            page: 1,
            size: 100,
            role: g.role,
            keyword: search.value.trim() || undefined,
          }),
        ),
      ])
      overview.value = ovRes?.data?.data || ovRes?.data || {}
      roleRes.forEach((res, i) => {
        const p = res?.data?.data || res?.data || {}
        groups.value[i].rows = p.list || []
      })
    } catch (e) {
      toast.show('用户数据加载失败', 'error')
    } finally {
      loading.value = false
    }
  }

  let searchTimer = null
  function onSearchInput() {
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
    } catch (e) {
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
    } catch (e) {
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
    } catch (e) {
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
    } catch (e) {
      toast.show('删除失败', 'error')
    } finally {
      deleting.value = false
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
    roleLabel,
    onSearchInput,
    openDetail,
    handleCreate,
    openReset,
    handleReset,
    openDelete,
    handleDelete,
  }
}
