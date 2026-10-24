<template>
  <section class="l1-card animate-in d1">
    <div class="l1-body">
      <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
      <CollapsePanel
        v-for="g in groups"
        :key="g.key"
        :title="`${g.label}（${g.rows.length}）`"
        :open="g.open"
        :icon="g.icon"
      >
        <div v-if="g.rows.length === 0" class="empty-state">
          <div class="e-title">暂无内容</div>
        </div>
        <div v-for="n in g.rows" :key="n.id" class="row-card" @click="emit('open', n)">
          <span class="rc-ico" :style="{ background: g.icoBg, color: g.icoColor }">
            <svg
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path :d="g.icoPath" />
            </svg>
          </span>
          <div class="rc-main">
            <div class="rc-title">
              {{ n.title }}
              <span class="tag" :class="catCls(n.category)">{{ catLabel(n.category) }}</span>
              <span v-if="n.priority === 'HIGH'" class="tag red">高优先级</span>
            </div>
            <div class="rc-sub">
              <span class="s-item"
                ><svg
                  viewBox="0 0 24 24"
                  width="12"
                  height="12"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2" />
                </svg>
                {{ fmtTime(n.createdAt) }}</span
              >
              <span class="s-item"
                ><svg
                  viewBox="0 0 24 24"
                  width="12"
                  height="12"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
                  />
                </svg>
                {{ typeLabel(n.type) }}</span
              >
            </div>
          </div>
          <div class="rc-side">
            <span class="tag" :class="n.isRead ? 'green' : 'amber'">{{
              n.isRead ? '已读' : '未读'
            }}</span>
            <button v-if="!n.isRead" class="btn btn-sm btn-soft" @click.stop="emit('mark-read', n)">
              标记已读
            </button>
            <button class="t-icon-btn" @click.stop="emit('menu', $event, n)">
              <svg
                viewBox="0 0 24 24"
                width="15"
                height="15"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"
                />
              </svg>
            </button>
          </div>
        </div>
      </CollapsePanel>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'

/** 列表行（页面壳 unread/read 数组的元素，字段以本列表消费为准） */
type NoticeRow = {
  id: number | string
  title?: string
  category?: string
  priority?: string
  createdAt?: string
  type?: string
  isRead?: boolean
}
/** 分组（未读/已读两组：组头元信息 + 行引用，由页面壳构造） */
type NoticeGroup = {
  key: string
  label: string
  icon: string
  open: boolean
  icoBg: string
  icoColor: string
  icoPath: string
  rows: NoticeRow[]
}

/**
 * 通知公告管理 — 通知列表（未读/已读分组 + 行操作）。
 * <p>拆分子组件：纯展示 + 文案映射；行操作（打开/标记已读/菜单）通过 emit 交还页面壳。</p>
 */
defineProps({
  /** 分组数组（key/label/icon/open/icoBg/icoColor/icoPath/rows，由页面壳按未读/已读构造） */
  groups: { type: Array as PropType<NoticeGroup[]>, default: () => [] },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['open', 'mark-read', 'menu'])

const CAT_LABELS = { SYSTEM: '系统公告', ASSIGNMENT: '作业提醒', EXAM: '考试通知' }
const CAT_CLS = { SYSTEM: 'red', ASSIGNMENT: 'blue', EXAM: 'violet' }
const TYPE_LABELS = { COURSE: '课程通知', TASK: '作业通知', EXAM: '考试通知', SYSTEM: '系统消息' }

function catLabel(c) {
  return CAT_LABELS[c] || c || '其他'
}
function catCls(c) {
  return CAT_CLS[c] || ''
}
function typeLabel(t) {
  return TYPE_LABELS[t] || t || '—'
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}
</script>
