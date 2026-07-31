<template>
  <div>
    <PageHeader icon="bell" title="通知中心" subtitle="按月份折叠分组 · 未读高亮 · 点击查看详情">
      <button class="btn btn-plain btn-sm" @click="markAllRead">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5"/></svg> 全部已读
      </button>
    </PageHeader>

    <section class="l1-card animate-in">
      <div v-loading="loading" class="l1-body">
        <template v-if="loadError">
          <div class="empty-state">
            <div class="e-ico"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg></div>
            <div class="e-title">通知加载失败</div>
            <div class="e-desc">{{ loadError }}</div>
            <button class="btn btn-primary btn-sm" @click="loadNotices">重新加载</button>
          </div>
        </template>
        <template v-else>
          <CollapsePanel v-for="(g, gi) in groupedNotices" :key="g.group" :title="g.group" :desc="`${g.items.length} 条通知 · ${g.items.filter(i => i.unread).length} 条未读`" :open="gi === 0" icon="calendar">
            <div v-for="n in g.items" :key="n.id" class="notice-card" :class="{ unread: n.unread }" @click="openNotice(n)">
              <span class="n-ico" :style="{ background: n.type === '考试' ? 'var(--warn-soft)' : n.type === '作业' ? 'var(--accent-soft)' : 'var(--info-soft)', color: n.type === '考试' ? 'var(--warn)' : n.type === '作业' ? 'var(--accent)' : 'var(--info)' }">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="n.type === '考试' ? 'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2' : n.type === '作业' ? 'M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z' : 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0'"/></svg>
              </span>
              <div class="n-main">
                <div class="n-title">{{ n.title }} <span class="tag outline">{{ n.type }}</span><span v-if="n.unread" class="tag red">未读</span></div>
                <div class="n-preview">{{ n.content }}</div>
              </div>
              <span class="n-time">{{ n.createdAt || n.time }}</span>
            </div>
          </CollapsePanel>
          <div v-if="!groupedNotices.length" class="empty-state"><div class="e-ico"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg></div><div class="e-title">暂无通知</div></div>
        </template>
      </div>
    </section>

    <!-- 通知详情弹窗 -->
    <Modal v-if="detail" @close="detail = null">
      <template #title>{{ detail.title }}</template>
      <template #sub>{{ detail.type }} · {{ detail.time }}</template>
      <div class="l3-block"><div style="line-height:2;color:var(--ink-2);font-size:13.5px">{{ detail.content }}</div></div>
      <template #footer><button class="btn btn-primary" @click="detail.unread = false; detail = null">已读</button></template>
    </Modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import notificationApi from '@/api/notification'
import { unwrap } from '@/utils/response'
import PageHeader from '@/components/PageHeader.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import Modal from '@/components/ui/Modal.vue'

const detail = ref(null)
const notices = ref([])
const loading = ref(false)
const loadError = ref('')

const groupedNotices = computed(() => {
  const map = new Map()
  notices.value.forEach(n => { const g = n.group || '其他'; if (!map.has(g)) map.set(g, []); map.get(g).push(n) })
  return [...map.entries()].map(([group, items]) => ({ group, items }))
})

function openNotice(n) { n.unread = false; detail.value = n }

async function markAllRead() {
  try {
    await notificationApi.markAllAsRead()
    notices.value.forEach(n => n.unread = false)
    ElMessage.success('已全部标记为已读')
  } catch {
    ElMessage.error('标记失败，请稍后重试')
  }
}

async function loadNotices() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await notificationApi.getNotificationList({ page: 1, size: 20 })
    const data = unwrap(res)
    const list = data?.records || data?.list || []
    notices.value = list.map(n => ({
      ...n,
      group: groupByMonth(n.createdAt || n.time),
      unread: !n.isRead,
      type: n.type || '通知',
      time: n.createdAt || n.time || '',
    }))
  } catch (e) {
    notices.value = []
    loadError.value = e?.message || '加载失败，请检查网络'
  } finally {
    loading.value = false
  }
}

onMounted(loadNotices)
function groupByMonth(t) { if (!t) return '其他'; const d = new Date(t); return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月` }
</script>
