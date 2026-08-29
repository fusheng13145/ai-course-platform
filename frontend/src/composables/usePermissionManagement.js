import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import permissionApi from '@/api/permission'

/**
 * 权限管理页数据逻辑（拆分自 admin/user/PermissionManagement.vue，2026-08-29）
 */
export function usePermissionManagement() {
  // 权限列表原始数据
  const permissionList = ref([])
  // 加载状态
  const loading = ref(false)
  // 提交中状态
  const submitting = ref(false)

  // 筛选条件
  const roleFilter = ref('')
  const searchKeyword = ref('')

  // 对话框状态
  const dialogVisible = ref(false)
  const isEdit = ref(false)

  // 表单数据
  const form = reactive({
    id: null,
    role: '',
    menuKey: '',
    buttonKey: '',
    description: '',
    isActive: true,
  })

  // 表单校验规则
  const rules = {
    role: [{ required: true, message: '请选择角色', trigger: 'change' }],
    menuKey: [{ required: true, message: '请输入菜单权限 Key', trigger: 'blur' }],
  }

  // 统计卡片数据
  const permissionStats = computed(() => {
    const list = permissionList.value
    return [
      { label: '总权限数', value: list.length, class: '' },
      { label: '已启用', value: list.filter((p) => p.isActive).length, class: 'active' },
      { label: '已停用', value: list.filter((p) => !p.isActive).length, class: 'inactive' },
      { label: '管理员专用', value: list.filter((p) => p.role === 'ADMIN').length, class: 'admin' },
    ]
  })

  // 经过筛选的列表数据
  const filteredList = computed(() => {
    let list = permissionList.value
    // 角色筛选
    if (roleFilter.value) {
      list = list.filter((p) => p.role === roleFilter.value)
    }
    // 关键词搜索（匹配 menuKey 或 buttonKey）
    if (searchKeyword.value) {
      const q = searchKeyword.value.toLowerCase()
      list = list.filter(
        (p) =>
          (p.menuKey && p.menuKey.toLowerCase().includes(q)) ||
          (p.buttonKey && p.buttonKey.toLowerCase().includes(q)),
      )
    }
    return list
  })

  /** 角色值 → 中文标签 */
  function roleLabel(role) {
    const map = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
    return map[role] || role
  }

  /** 角色 → tag 类型 */
  function roleTagType(role) {
    const map = { ADMIN: 'danger', TEACHER: 'success', STUDENT: 'warning' }
    return map[role] || 'info'
  }

  /** 从后端获取全部权限列表 */
  async function fetchPermissionList() {
    loading.value = true
    try {
      const res = await permissionApi.getAllPermissions()
      const rawData = res.data?.records || res.data?.list || res.data || res
      permissionList.value = Array.isArray(rawData) ? rawData : []
    } catch (e) {
      console.error('获取权限列表失败:', e)
      ElMessage.error('获取权限列表失败')
      permissionList.value = []
    } finally {
      loading.value = false
    }
  }

  /** 打开新增对话框 */
  function openAddDialog() {
    isEdit.value = false
    resetForm()
    dialogVisible.value = true
  }

  /** 打开编辑对话框 */
  function openEditDialog(row) {
    isEdit.value = true
    Object.assign(form, {
      id: row.id,
      role: row.role,
      menuKey: row.menuKey,
      buttonKey: row.buttonKey || '',
      description: row.description || '',
      isActive: row.isActive ?? true,
    })
    dialogVisible.value = true
  }

  /** 重置表单 */
  function resetForm() {
    form.id = null
    form.role = ''
    form.menuKey = ''
    form.buttonKey = ''
    form.description = ''
    form.isActive = true
  }

  /** 提交表单（新增或编辑）——由对话框校验通过后调用 */
  async function handleSubmit() {
    submitting.value = true
    try {
      const payload = {
        role: form.role,
        menuKey: form.menuKey,
        buttonKey: form.buttonKey,
        description: form.description,
        isActive: form.isActive,
      }
      if (isEdit.value) {
        // 编辑模式 — 调用更新接口
        await permissionApi.updatePermission(form.id, payload)
        // 更新本地列表中的对应项
        const idx = permissionList.value.findIndex((p) => p.id === form.id)
        if (idx !== -1) {
          Object.assign(permissionList.value[idx], payload)
        }
        ElMessage.success('权限更新成功')
      } else {
        // 新增模式 — 调用创建接口
        const res = await permissionApi.createPermission(payload)
        const newItem = res.data || res
        permissionList.value.push(newItem)
        ElMessage.success('权限创建成功')
      }
      dialogVisible.value = false
    } catch (e) {
      console.error('保存失败:', e)
      ElMessage.error(isEdit.value ? '更新失败，请重试' : '创建失败，请重试')
    } finally {
      submitting.value = false
    }
  }

  /** 切换启用 / 停用状态 */
  function handleToggle(row) {
    const action = row.isActive ? '停用' : '启用'
    ElMessageBox.confirm(`确定${action}该权限项吗？`, '操作确认', { type: 'warning' })
      .then(async () => {
        try {
          await permissionApi.togglePermission(row.id)
          row.isActive = !row.isActive
          ElMessage.success(`${action}成功`)
        } catch (e) {
          console.error('切换状态失败:', e)
          ElMessage.error(`${action}失败`)
        }
      })
      .catch(() => {})
  }

  /** 删除权限 */
  function handleDelete(row) {
    ElMessageBox.confirm(`确定删除该权限项吗？删除后不可恢复。`, '删除确认', { type: 'warning' })
      .then(async () => {
        try {
          await permissionApi.deletePermission(row.id)
          permissionList.value = permissionList.value.filter((p) => p.id !== row.id)
          ElMessage.success('删除成功')
        } catch (e) {
          console.error('删除失败:', e)
          ElMessage.error('删除失败，请重试')
        }
      })
      .catch(() => {})
  }

  onMounted(fetchPermissionList)

  return {
    permissionList,
    loading,
    submitting,
    roleFilter,
    searchKeyword,
    dialogVisible,
    isEdit,
    form,
    rules,
    permissionStats,
    filteredList,
    roleLabel,
    roleTagType,
    fetchPermissionList,
    openAddDialog,
    openEditDialog,
    resetForm,
    handleSubmit,
    handleToggle,
    handleDelete,
  }
}
