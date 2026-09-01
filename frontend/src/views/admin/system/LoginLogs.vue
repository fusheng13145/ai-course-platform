<template>
  <div>
    <PageHeader title="登录日志" subtitle="全平台登录行为审计 · 状态筛选" icon="log">
      <TabGroup :tabs="filterTabs" :active="activeTab" style="seg" @change="onTabChange" />
    </PageHeader>

    <div v-loading="loading">
      <div class="stat-grid animate-in d1">
        <StatCard label="今日登录" :value="stats.todayCount ?? 0" icon="monitor" />
        <StatCard
          label="累计登录"
          :value="stats.totalCount ?? 0"
          icon="users"
          tone="var(--info-soft)"
          tone-color="var(--info)"
        />
        <StatCard
          label="失败登录"
          :value="stats.failedCount ?? 0"
          icon="warn"
          tone="var(--warn-soft)"
          tone-color="var(--warn)"
        />
        <StatCard
          label="登录成功率"
          :value="successRate"
          icon="shield"
          tone="var(--accent-soft)"
          tone-color="var(--accent)"
          :foot="`近 7 天 ${stats.weekCount ?? 0} 次`"
        />
      </div>

      <section class="l1-card animate-in d2">
        <div class="l1-body">
          <CollapsePanel title="登录趋势（近 30 天）" icon="chart" :open="true">
            <BarChart :data="trendData" :w="540" :h="180" />
          </CollapsePanel>
          <CollapsePanel title="登录记录" icon="list" :open="true">
            <div class="content-card">
              <div v-if="logs.length" class="tbl-wrap">
                <table class="tbl">
                  <thead>
                    <tr>
                      <th>用户</th>
                      <th>登录 IP</th>
                      <th>设备</th>
                      <th>结果</th>
                      <th>时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="l in logs" :key="l.id">
                      <td class="cell-main">{{ l.username || '—' }}</td>
                      <td class="mono">{{ l.ipAddress || '—' }}</td>
                      <td class="muted small">
                        {{ l.userAgent?.slice(0, 40) || l.deviceName || '—' }}
                      </td>
                      <td>
                        <span class="tag" :class="statusCls(l.loginStatus)">{{
                          statusLabel(l.loginStatus)
                        }}</span>
                      </td>
                      <td class="muted small">{{ fmtTime(l.createdAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <el-empty v-else description="暂无登录记录" :image-size="80" />
            </div>
          </CollapsePanel>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import TabGroup from '@/components/ui/TabGroup.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { getMonoSeries } from '@/utils/chartTheme'
import loginLogApi from '@/api/loginLog'

const activeTab = ref('all')

const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'fail', label: '失败' },
  { key: 'locked', label: '锁定' },
]

const STATUS_LABELS = { SUCCESS: '成功', FAILED: '失败', LOCKED: '锁定', EXPIRED: '过期' }
const STATUS_CLS = { SUCCESS: 'green', FAILED: 'amber', LOCKED: 'red', EXPIRED: 'red' }

const stats = ref({})
const logs = ref([])
const trendData = ref([])
const loading = ref(false)

const successRate = computed(() => {
  const v = stats.value.successRate
  return v != null ? v + '%' : '—'
})

function statusLabel(s) {
  return STATUS_LABELS[s] || s || '—'
}
function statusCls(s) {
  return STATUS_CLS[s] || ''
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

// 修复：模板多语句表达式与 prettier(semi:false) 冲突会产生 Vue 编译器无法解析的换行格式，
// 提取为方法（模板保持声明式，符合 Vue 最佳实践）
function onTabChange(v) {
  activeTab.value = v
  load()
}

async function load() {
  loading.value = true
  try {
    const statusParam =
      activeTab.value === 'fail' ? 'FAILED' : activeTab.value === 'locked' ? 'LOCKED' : undefined
    const [s, l, t] = await Promise.all([
      loginLogApi.getStats(),
      loginLogApi.getList({ page: 1, size: 50, status: statusParam }),
      loginLogApi.getTrends({ days: 30 }),
    ])
    stats.value = s.data?.data || s.data || {}
    const page = l.data?.data || l.data || {}
    logs.value = page.list || page.records || []
    const td = t.data?.data || t.data || []
    trendData.value = td.map((d) => ({
      label: String(d.date).slice(5),
      value: d.totalCount ?? 0,
      color: getMonoSeries()[1],
    }))
  } catch (e) {
    // 保持空列表与空统计，不注入假数据
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.content-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-l2);
  overflow: hidden;
}
</style>
