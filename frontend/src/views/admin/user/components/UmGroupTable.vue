<template>
  <div>
    <CollapsePanel
      v-for="g in groups"
      :key="g.key"
      :title="`${g.label}（${g.rows.length}）`"
      :desc="g.desc"
      icon="shield"
      :open="g.open"
    >
      <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
      <div v-else-if="g.rows.length === 0" class="empty-state">
        <div class="e-title">暂无{{ g.label }}</div>
        <div class="e-desc">可点击右上角「新增用户」创建</div>
      </div>
      <div v-else class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr>
              <th>用户</th>
              <th>账号</th>
              <th>邮箱</th>
              <th>所属单位</th>
              <th>最近登录</th>
              <th>状态</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in g.rows" :key="u.id" class="clickable" @click="emit('open-detail', u)">
              <td>
                <div class="u-cell">
                  <span class="avatar sm" :style="avStyle(u.fullName || u.username)">{{
                    (u.fullName || u.username).slice(-2)
                  }}</span>
                  <div>
                    <div class="cell-main">{{ u.fullName || '—' }}</div>
                    <div class="cell-sub mono">#{{ u.id }}</div>
                  </div>
                </div>
              </td>
              <td class="mono">{{ u.username }}</td>
              <td class="small muted">{{ u.email || '—' }}</td>
              <td>{{ u.department || '—' }}</td>
              <td class="muted small">{{ fmtTime(u.lastLogin) }}</td>
              <td>
                <span class="tag" :class="statusOf(u).cls"
                  ><span class="t-dot"></span>{{ statusOf(u).text }}</span
                >
              </td>
              <td>
                <div class="row-ops">
                  <span class="op-link" @click.stop="emit('open-detail', u)">详情</span>
                  <span class="op-link" @click.stop="emit('open-reset', u)">重置密码</span>
                  <span
                    v-if="u.role !== 'ADMIN'"
                    class="op-link danger"
                    @click.stop="emit('open-delete', u)"
                    >删除</span
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </CollapsePanel>
  </div>
</template>

<script setup>
import CollapsePanel from '@/components/ui/CollapsePanel.vue'

defineProps({
  loading: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
})
const emit = defineEmits(['open-detail', 'open-reset', 'open-delete'])

const GRADIENTS = [
  'linear-gradient(135deg,#c13b30,#8f2a21)',
  'linear-gradient(135deg,#2563a8,#1a4a80)',
  'linear-gradient(135deg,#2e7d4f,#1f5c39)',
  'linear-gradient(135deg,#b57908,#8a5d06)',
  'linear-gradient(135deg,#6d4fa1,#4f3877)',
  'linear-gradient(135deg,#0f766e,#0a544f)',
]

function avStyle(name) {
  const i = [...(name || '')].reduce((s, c) => s + c.charCodeAt(0), 0) % GRADIENTS.length
  return { background: GRADIENTS[i] }
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}
function statusOf(u) {
  if (u.lockedUntil && new Date(u.lockedUntil) > new Date()) return { text: '锁定', cls: 'amber' }
  if (u.isActive === false) return { text: '停用', cls: 'red' }
  return { text: '正常', cls: 'green' }
}
</script>
