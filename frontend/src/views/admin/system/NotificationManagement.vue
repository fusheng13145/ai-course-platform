<template>
  <div>
    <PageHeader title="通知公告" subtitle="按已读状态分组 · 管理员可向指定角色批量发送" icon="bell">
      <button class="btn btn-primary" @click="drawerOpen = true"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg> 发布通知</button>
    </PageHeader>

    <section class="l1-card animate-in d1">
      <div class="l1-body">
        <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
        <CollapsePanel v-for="g in noticeGroups" :key="g.key" :title="`${g.label}（${g.rows.length}）`" :open="g.open" :icon="g.icon">
          <div v-if="g.rows.length === 0" class="empty-state"><div class="e-title">暂无内容</div></div>
          <div v-for="n in g.rows" :key="n.id" class="row-card" @click="openNotice(n)">
            <span class="rc-ico" :style="{ background: g.icoBg, color: g.icoColor }">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="g.icoPath"/></svg>
            </span>
            <div class="rc-main">
              <div class="rc-title">{{ n.title }} <span class="tag" :class="catCls(n.category)">{{ catLabel(n.category) }}</span> <span v-if="n.priority === 'HIGH'" class="tag red">高优先级</span></div>
              <div class="rc-sub">
                <span class="s-item"><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2"/></svg> {{ fmtTime(n.createdAt) }}</span>
                <span class="s-item"><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/></svg> {{ typeLabel(n.type) }}</span>
              </div>
            </div>
            <div class="rc-side">
              <span class="tag" :class="n.isRead ? 'green' : 'amber'">{{ n.isRead ? '已读' : '未读' }}</span>
              <button v-if="!n.isRead" class="btn btn-sm btn-soft" @click.stop="doRead(n)">标记已读</button>
              <button class="t-icon-btn" @click.stop="openMenu($event, n)">
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"/></svg>
              </button>
            </div>
          </div>
        </CollapsePanel>
      </div>
    </section>

    <!-- 通知详情弹窗 -->
    <Modal v-if="detailNotice" wide @close="detailNotice = null">
      <template #title>通知详情</template>
      <template #sub>#{{ detailNotice.id }}</template>
      <div>
        <div class="l3-block">
          <div class="strong notice-title">{{ detailNotice.title }}</div>
          <div class="small muted mb-12">{{ catLabel(detailNotice.category) }} · {{ priorityLabel(detailNotice.priority) }} · {{ fmtTime(detailNotice.createdAt) }}</div>
          <div class="notice-content">{{ detailNotice.content }}</div>
        </div>
      </div>
    </Modal>

    <!-- 发布通知抽屉 -->
    <Drawer v-if="drawerOpen" wide @close="drawerOpen = false">
      <template #title>发布通知</template>
      <template #sub>选择接收对象后由系统批量写入通知</template>
      <div class="form-grid">
        <div class="field span-2"><label>通知标题<span class="req">*</span></label><input v-model="sendForm.title" class="input" placeholder="请输入标题" /></div>
        <div class="field span-2"><label>接收对象</label>
          <div class="radio-cards">
            <div v-for="s in scopes" :key="s.key" class="radio-card" :class="{ checked: sendForm.scope === s.key }" @click="sendForm.scope = s.key">
              <div class="rc-title">{{ s.label }}</div>
              <div class="rc-desc">{{ s.desc }}</div>
            </div>
          </div>
        </div>
        <div v-if="sendForm.scope === 'custom'" class="field span-2">
          <label>用户 ID 列表（逗号分隔）</label>
          <input v-model="sendForm.customIds" class="input" placeholder="如：1, 2, 3" />
        </div>
        <div class="field"><label>分类</label><select v-model="sendForm.category" class="select"><option value="SYSTEM">系统公告</option><option value="ASSIGNMENT">作业提醒</option><option value="EXAM">考试通知</option></select></div>
        <div class="field"><label>优先级</label><select v-model="sendForm.priority" class="select"><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></div>
        <div class="field span-2"><label>正文内容<span class="req">*</span></label><textarea v-model="sendForm.content" class="textarea send-textarea" placeholder="请输入通知正文…"></textarea></div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="drawerOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="sending" @click="doSend()">{{ sending ? '发送中…' : '确认发布' }}</button>
      </template>
    </Drawer>

    <!-- 更多菜单 -->
    <DropdownMenu v-if="ctx.visible" :items="ctx.items" :x="ctx.x" :y="ctx.y" @close="ctx.visible = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import Modal from '@/components/ui/Modal.vue'
import Drawer from '@/components/ui/Drawer.vue'
import DropdownMenu from '@/components/ui/DropdownMenu.vue'
import { toast } from '@/composables/useToast'
import notificationApi from '@/api/notification'
import adminApi from '@/api/admin'

const CAT_LABELS = { SYSTEM: '系统公告', ASSIGNMENT: '作业提醒', EXAM: '考试通知' }
const CAT_CLS = { SYSTEM: 'red', ASSIGNMENT: 'blue', EXAM: 'violet' }
const PRIORITY_LABELS = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }
const TYPE_LABELS = { COURSE: '课程通知', TASK: '作业通知', EXAM: '考试通知', SYSTEM: '系统消息' }

const loading = ref(true)
const unread = ref([])
const read = ref([])

const detailNotice = ref(null)
const drawerOpen = ref(false)
const sending = ref(false)
const sendForm = ref({ title: '', content: '', scope: 'teachers', customIds: '', category: 'SYSTEM', priority: 'MEDIUM' })
const scopes = [
  { key: 'teachers', label: '全体教师', desc: '向所有教师账号发送' },
  { key: 'students', label: '全体学生', desc: '向所有学生账号发送' },
  { key: 'admins', label: '全体管理员', desc: '向所有管理员账号发送' },
  { key: 'custom', label: '指定用户', desc: '输入用户 ID 列表' },
]
const ctx = ref({ visible: false, x: 0, y: 0, items: [], notice: null })

const noticeGroups = computed(() => [
  { key: 'unread', label: '未读', icon: 'clock', open: true, icoBg: 'var(--warn-soft)', icoColor: 'var(--warn)', icoPath: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2', rows: unread.value },
  { key: 'read', label: '已读', icon: 'checks', open: false, icoBg: 'var(--ok-soft)', icoColor: 'var(--ok)', icoPath: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5', rows: read.value },
])

function catLabel(c) { return CAT_LABELS[c] || c || '其他' }
function catCls(c) { return CAT_CLS[c] || '' }
function priorityLabel(p) { return PRIORITY_LABELS[p] || p || '—' }
function typeLabel(t) { return TYPE_LABELS[t] || t || '—' }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

async function load() {
  loading.value = true
  try {
    const [unreadRes, readRes] = await Promise.all([
      notificationApi.getNotificationList({ page: 1, size: 50, isRead: false }),
      notificationApi.getNotificationList({ page: 1, size: 50, isRead: true }),
    ])
    unread.value = unreadRes?.data?.data?.list || unreadRes?.data?.list || []
    read.value = readRes?.data?.data?.list || readRes?.data?.list || []
  } catch (e) {
    toast.show('通知加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function openNotice(n) { detailNotice.value = n }

async function doRead(n) {
  try {
    await notificationApi.markAsRead(n.id)
    n.isRead = true
    unread.value = unread.value.filter(x => x.id !== n.id)
    read.value.unshift(n)
  } catch (e) {
    toast.show('操作失败', 'error')
  }
}

async function doDelete(n) {
  try {
    await notificationApi.deleteNotification(n.id)
    const src = n.isRead ? read.value : unread.value
    const i = src.findIndex(x => x.id === n.id)
    if (i > -1) src.splice(i, 1)
    toast.show('通知已删除', 'ok')
  } catch (e) {
    toast.show('删除失败', 'error')
  }
}

async function doSend() {
  const f = sendForm.value
  if (!f.title.trim() || !f.content.trim()) { toast.show('请填写标题与正文', 'warning'); return }
  sending.value = true
  try {
    let targetUserIds = []
    if (f.scope === 'custom') {
      targetUserIds = f.customIds.split(/[,，\s]+/).map(s => Number(s.trim())).filter(n => Number.isFinite(n) && n > 0)
      if (!targetUserIds.length) { toast.show('请输入有效的用户 ID', 'warning'); sending.value = false; return }
    } else {
      const roleMap = { teachers: 'TEACHER', students: 'STUDENT', admins: 'ADMIN' }
      const res = await adminApi.getUsers({ page: 1, size: 500, role: roleMap[f.scope] })
      const p = res?.data?.data || res?.data || {}
      targetUserIds = (p.list || []).map(u => u.id).filter(Boolean)
      if (!targetUserIds.length) { toast.show('该角色暂无用户', 'warning'); sending.value = false; return }
    }
    const sendRes = await notificationApi.sendNotification({
      title: f.title.trim(),
      content: f.content.trim(),
      category: f.category,
      priority: f.priority,
      targetUserIds,
    })
    if (sendRes?.data?.code === 200 || sendRes?.data?.data) {
      toast.show(`已向 ${targetUserIds.length} 个用户发送`, 'ok')
      drawerOpen.value = false
      sendForm.value = { title: '', content: '', scope: 'teachers', customIds: '', category: 'SYSTEM', priority: 'MEDIUM' }
    } else {
      toast.show(sendRes?.data?.message || '发送失败', 'error')
    }
  } catch (e) {
    toast.show('发送失败', 'error')
  } finally {
    sending.value = false
  }
}

function openMenu(e, n) {
  const r = e.currentTarget.getBoundingClientRect()
  const ops = [{ text: '查看详情', icon: 'eye', onClick: () => openNotice(n) }]
  if (!n.isRead) ops.push({ text: '标记已读', icon: 'check', onClick: () => doRead(n) })
  ops.push('-', { text: '删除', icon: 'trash', danger: true, onClick: () => doDelete(n) })
  ctx.value = { visible: true, x: r.left, y: r.bottom + 4, items: ops }
}

onMounted(load)
</script>

<style scoped>
.notice-title {
  font-size: 15px;
  margin-bottom: 6px;
}

.notice-content {
  line-height: 1.9;
  color: var(--ink-2);
  font-size: 13.5px;
  white-space: pre-wrap;
}

.send-textarea {
  min-height: 140px;
}
</style>
