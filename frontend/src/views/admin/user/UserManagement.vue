<template>
  <div>
    <PageHeader title="用户管理" subtitle="平台账号总览 · 按角色分组维护账号与权限" icon="users">
      <input v-model="search" class="input search-input" placeholder="搜索姓名 / 账号 / 邮箱" @input="onSearchInput" />
      <button class="btn btn-primary btn-sm" @click="showCreate = true"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg> 新增用户</button>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stat-grid animate-in d1">
      <StatCard label="平台总用户" :value="overview.totalUsers ?? 0" icon="users" />
      <StatCard label="教师账号" :value="overview.roleDistribution?.teacher ?? 0" icon="grad" tone="var(--info-soft)" tone-color="var(--info)" />
      <StatCard label="学生账号" :value="overview.roleDistribution?.student ?? 0" icon="user" tone="var(--ok-soft)" tone-color="var(--ok)" />
      <StatCard label="管理员账号" :value="overview.roleDistribution?.admin ?? 0" icon="shield" tone="var(--warn-soft)" tone-color="var(--warn)" />
    </div>

    <!-- 用户管理主卡片 -->
    <section class="l1-card animate-in d2">
      <div class="l1-body">
        <CollapsePanel v-for="g in groups" :key="g.key" :title="`${g.label}（${g.rows.length}）`" :desc="g.desc" icon="shield" :open="g.open">
          <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
          <div v-else-if="g.rows.length === 0" class="empty-state"><div class="e-title">暂无{{ g.label }}</div><div class="e-desc">可点击右上角「新增用户」创建</div></div>
          <div v-else class="tbl-wrap">
            <table class="tbl">
              <thead><tr><th>用户</th><th>账号</th><th>邮箱</th><th>所属单位</th><th>最近登录</th><th>状态</th><th></th></tr></thead>
              <tbody>
                <tr v-for="u in g.rows" :key="u.id" class="clickable" @click="openDetail(u)">
                  <td><div class="u-cell"><span class="avatar sm" :style="avStyle(u.fullName || u.username)">{{ (u.fullName || u.username).slice(-2) }}</span><div><div class="cell-main">{{ u.fullName || '—' }}</div><div class="cell-sub mono">#{{ u.id }}</div></div></div></td>
                  <td class="mono">{{ u.username }}</td>
                  <td class="small muted">{{ u.email || '—' }}</td>
                  <td>{{ u.department || '—' }}</td>
                  <td class="muted small">{{ fmtTime(u.lastLogin) }}</td>
                  <td><span class="tag" :class="statusOf(u).cls"><span class="t-dot"></span>{{ statusOf(u).text }}</span></td>
                  <td>
                    <div class="row-ops">
                      <span class="op-link" @click.stop="openDetail(u)">详情</span>
                      <span class="op-link" @click.stop="openReset(u)">重置密码</span>
                      <span v-if="u.role !== 'ADMIN'" class="op-link danger" @click.stop="openDelete(u)">删除</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </CollapsePanel>
      </div>
    </section>

    <!-- 用户详情弹窗（内嵌 Tab + 权限树） -->
    <Modal v-if="detailUser" wide @close="detailUser = null">
      <template #title>用户详情 · {{ detailUser.fullName || detailUser.username }}</template>
      <template #sub>#{{ detailUser.id }} · {{ roleLabel(detailUser.role) }}</template>
      <div>
        <TabGroup :tabs="detailTabs" :active="detailTab" style="pill" @change="detailTab = $event" />
        <div class="mt-16">
          <!-- 基本信息 -->
          <div v-if="detailTab === 'info'" class="desc-grid">
            <div v-for="(v, k) in infoItems" :key="k" class="desc-item">
              <span class="d-label">{{ k }}</span><span class="d-value">{{ v }}</span>
            </div>
          </div>
          <!-- 权限配置（角色权限，来自权限表） -->
          <div v-if="detailTab === 'perm'">
            <div class="l3-block">
              <div class="l3-title"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg> 角色「{{ roleLabel(detailUser.role) }}」权限配置</div>
              <div v-if="permLoading" class="empty-state"><div class="e-title">加载中…</div></div>
              <div v-else-if="permNodes.length === 0" class="empty-state"><div class="e-title">该角色暂无权限配置</div></div>
              <TreeView v-else :nodes="permNodes" @select="() => {}" />
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="detailUser = null">关闭</button>
      </template>
    </Modal>

    <!-- 重置密码弹窗 -->
    <Modal v-if="resetUser" narrow @close="resetUser = null">
      <template #title>重置密码 · {{ resetUser.username }}</template>
      <template #sub>重置后旧密码立即失效</template>
      <div class="form-grid">
        <div class="field span-2"><label>新密码<span class="req">*</span></label><input v-model="newPassword" class="input" type="password" placeholder="至少 6 位" @keyup.enter="doReset()" /></div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="resetUser = null">取消</button>
        <button class="btn btn-primary" :disabled="resetting" @click="doReset()">{{ resetting ? '提交中…' : '确认重置' }}</button>
      </template>
    </Modal>

    <!-- 删除确认弹窗 -->
    <Modal v-if="deleteUser" narrow @close="deleteUser = null">
      <template #title>删除用户</template>
      <template #sub>删除后不可恢复，请谨慎操作</template>
      <div class="confirm-text">确认删除用户「{{ deleteUser.fullName || deleteUser.username }}」（账号 {{ deleteUser.username }}）吗？其选课与关联数据将一并删除。</div>
      <template #footer>
        <button class="btn btn-plain" @click="deleteUser = null">取消</button>
        <button class="btn btn-primary" :disabled="deleting" @click="doDelete()">{{ deleting ? '删除中…' : '确认删除' }}</button>
      </template>
    </Modal>

    <!-- 新增用户弹窗 -->
    <Modal v-if="showCreate" wide @close="showCreate = false">
      <template #title>新增用户</template>
      <template #sub>创建后用户即可使用管理员设定的密码登录</template>
      <div class="form-grid">
        <div class="field"><label>登录账号<span class="req">*</span></label><input v-model="createForm.username" class="input" placeholder="工号 / 学号" /></div>
        <div class="field"><label>姓名<span class="req">*</span></label><input v-model="createForm.fullName" class="input" placeholder="真实姓名" /></div>
        <div class="field"><label>邮箱<span class="req">*</span></label><input v-model="createForm.email" class="input" placeholder="name@example.com" /></div>
        <div class="field"><label>初始密码<span class="req">*</span></label><input v-model="createForm.password" class="input" type="password" placeholder="至少 6 位" /></div>
        <div class="field"><label>角色<span class="req">*</span></label><select v-model="createForm.role" class="select"><option value="STUDENT">学生</option><option value="TEACHER">教师</option><option value="ADMIN">管理员</option></select></div>
        <div class="field"><label>所属单位</label><input v-model="createForm.department" class="input" placeholder="如：计算机学院" /></div>
        <div class="field span-2"><label>手机号</label><input v-model="createForm.phone" class="input" placeholder="选填" /></div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="showCreate = false">取消</button>
        <button class="btn btn-primary" :disabled="creating" @click="doCreate()">{{ creating ? '创建中…' : '创建用户' }}</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import TreeView from '@/components/ui/TreeView.vue'
import TabGroup from '@/components/ui/TabGroup.vue'
import Modal from '@/components/ui/Modal.vue'
import { toast } from '@/composables/useToast'
import adminApi from '@/api/admin'
import permissionApi from '@/api/permission'

const ROLE_LABELS = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生' }
const GRADIENTS = [
  'linear-gradient(135deg,#c13b30,#8f2a21)', 'linear-gradient(135deg,#2563a8,#1a4a80)',
  'linear-gradient(135deg,#2e7d4f,#1f5c39)', 'linear-gradient(135deg,#b57908,#8a5d06)',
  'linear-gradient(135deg,#6d4fa1,#4f3877)', 'linear-gradient(135deg,#0f766e,#0a544f)',
]

const overview = ref({})
const search = ref('')
const loading = ref(true)
const groups = ref([
  { key: 'admin', label: '管理员', role: 'ADMIN', desc: '系统管理员与教务管理员', open: true, rows: [] },
  { key: 'teacher', label: '教师', role: 'TEACHER', desc: '开课教师账号', open: true, rows: [] },
  { key: 'student', label: '学生', role: 'STUDENT', desc: '在读学生账号', open: false, rows: [] },
])

const detailUser = ref(null)
const detailTab = ref('info')
const permLoading = ref(false)
const permNodes = ref([])
const detailTabs = [{ key: 'info', label: '基本信息' }, { key: 'perm', label: '权限配置' }]

const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ username: '', fullName: '', email: '', password: '', role: 'STUDENT', department: '', phone: '' })
const resetUser = ref(null)
const resetting = ref(false)
const newPassword = ref('')
const deleteUser = ref(null)
const deleting = ref(false)

const infoItems = computed(() => {
  const u = detailUser.value || {}
  const st = statusOf(u)
  return {
    '姓名': u.fullName || '—',
    '登录账号': u.username || '—',
    '用户编号': u.id != null ? '#' + u.id : '—',
    '角色': roleLabel(u.role),
    '所属单位': u.department || '—',
    '邮箱': u.email || '—',
    '手机号': u.phone || '—',
    '账号状态': st.text,
    '最近登录': fmtTime(u.lastLogin),
    '注册时间': fmtTime(u.createdAt),
  }
})

function roleLabel(r) { return ROLE_LABELS[r] || r || '—' }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }
function statusOf(u) {
  if (u.lockedUntil && new Date(u.lockedUntil) > new Date()) return { text: '锁定', cls: 'amber' }
  if (u.isActive === false) return { text: '停用', cls: 'red' }
  return { text: '正常', cls: 'green' }
}

async function load() {
  loading.value = true
  try {
    const [ovRes, ...roleRes] = await Promise.all([
      adminApi.getOverview(),
      ...groups.value.map(g => adminApi.getUsers({ page: 1, size: 100, role: g.role, keyword: search.value.trim() || undefined })),
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
    perms.forEach(p => { byId[p.id] = { ...p, children: [] } })
    const roots = []
    Object.values(byId).forEach(p => {
      if (p.parentId && byId[p.parentId]) byId[p.parentId].children.push(p)
      else roots.push(p)
    })
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

async function doCreate() {
  if (!createForm.username.trim() || !createForm.fullName.trim() || !createForm.email.trim() || !createForm.password) {
    toast.show('请填写账号、姓名、邮箱与初始密码', 'warning')
    return
  }
  creating.value = true
  try {
    const res = await adminApi.createUser({ ...createForm })
    if (res?.data?.code === 200 || res?.data?.data) {
      toast.show('用户已创建', 'ok')
      showCreate.value = false
      Object.assign(createForm, { username: '', fullName: '', email: '', password: '', role: 'STUDENT', department: '', phone: '' })
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

function openReset(u) { resetUser.value = u; newPassword.value = '' }
async function doReset() {
  if (!newPassword.value || newPassword.value.length < 6) { toast.show('新密码至少 6 位', 'warning'); return }
  resetting.value = true
  try {
    const res = await adminApi.resetUserPassword(resetUser.value.id, { newPassword: newPassword.value })
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

function openDelete(u) { deleteUser.value = u }
async function doDelete() {
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

function avStyle(name) {
  const i = [...(name || '')].reduce((s, c) => s + c.charCodeAt(0), 0) % GRADIENTS.length
  return { background: GRADIENTS[i] }
}

onMounted(load)
</script>

<style scoped>
.search-input {
  width: 220px;
  height: 32px;
}

.confirm-text {
  line-height: 1.8;
  color: var(--ink-2);
}
</style>
