<template>
  <div>
    <PageHeader title="管理总览" subtitle="平台核心运营指标与常用管理入口" icon="dashboard" />
    <div v-loading="loading">
      <!-- 统计卡片 -->
      <div class="stat-grid animate-in d1">
        <StatCard
          label="平台总用户"
          icon="users"
          tone="var(--accent-soft)"
          tone-color="var(--accent)"
          :foot="`教师 ${fmt(overview.roleDistribution?.teacher)} · 学生 ${fmt(overview.roleDistribution?.student)}`"
          ><template #value><CountUp :value="Number(overview.totalUsers ?? 0)" /></template
        ></StatCard>
        <StatCard
          label="课程总数"
          icon="book"
          tone="var(--info-soft)"
          tone-color="var(--info)"
          :foot="`${overview.activeCourses ?? 0} 门活跃课程`"
          ><template #value><CountUp :value="Number(overview.totalCourses ?? 0)" /></template
        ></StatCard>
        <StatCard label="选课总人次" icon="send" tone="var(--ok-soft)" tone-color="var(--ok)"
          ><template #value><CountUp :value="Number(overview.totalEnrollments ?? 0)" /></template
        ></StatCard>
        <StatCard
          label="作业提交率"
          :value="submitRate"
          icon="chart"
          tone="var(--warn-soft)"
          tone-color="var(--warn)"
          :foot="`成绩优良率 ${excellentRate}%`"
        />
      </div>

      <!-- 系统管理快捷入口 -->
      <section class="l1-card animate-in d1">
        <div class="l1-head">
          <span class="l1-ico"
            ><svg
              viewBox="0 0 24 24"
              width="17"
              height="17"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg
          ></span>
          <div>
            <div class="l1-title">系统管理</div>
            <div class="l1-sub">用户权限 / 角色管理 / 系统设置</div>
          </div>
          <div class="l1-extra">
            <router-link to="/admin/users" class="btn btn-primary btn-sm">进入用户管理</router-link>
          </div>
        </div>
        <div class="l1-body">
          <div class="grid-3">
            <div class="l3-block hoverable" @click="$router.push('/admin/users')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"
                  />
                </svg>
                用户权限
              </div>
              <div class="entry-desc">平台全部用户账号管理<br />按角色分组折叠 · 权限树配置</div>
              <div class="flex gap-8 mt-8">
                <span class="tag violet">{{ fmt(overview.totalUsers) }} 用户</span
                ><span class="tag green">{{ fmt(overview.roleDistribution?.teacher) }} 教师</span
                ><span class="tag blue">{{ fmt(overview.roleDistribution?.student) }} 学生</span>
              </div>
            </div>
            <div class="l3-block hoverable" @click="$router.push('/admin/permissions')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4"
                  />
                </svg>
                角色管理
              </div>
              <div class="entry-desc">角色权限矩阵配置<br />内置角色 + 自定义角色</div>
              <div class="flex gap-8 mt-8">
                <span class="tag violet">管理员</span><span class="tag blue">教师</span
                ><span class="tag">学生</span><span class="tag outline">自定义</span>
              </div>
            </div>
            <div class="l3-block hoverable" @click="$router.push('/admin/system')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6" />
                </svg>
                系统设置
              </div>
              <div class="entry-desc">基础设置 / 安全策略<br />存储配额 / 在线预览配置</div>
              <div class="flex gap-8 mt-8"><span class="tag green">运行中</span></div>
            </div>
          </div>
        </div>
      </section>

      <!-- 教学管理快捷入口 -->
      <section class="l1-card">
        <div class="l1-head">
          <span class="l1-ico ico-info"
            ><svg
              viewBox="0 0 24 24"
              width="17"
              height="17"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
              /></svg
          ></span>
          <div>
            <div class="l1-title">教学管理</div>
            <div class="l1-sub">课程库 / 专业班级 / 排课分配</div>
          </div>
          <div class="l1-extra">
            <router-link to="/admin/courses" class="btn btn-primary btn-sm">课程库总览</router-link>
          </div>
        </div>
        <div class="l1-body">
          <div class="grid-3">
            <div class="l3-block hoverable" @click="$router.push('/admin/courses')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                </svg>
                课程库总览
              </div>
              <div class="entry-text">
                <b>{{ fmt(overview.totalCourses) }}</b> 门 · 活跃
                <b>{{ fmt(overview.activeCourses) }}</b>
              </div>
            </div>
            <div class="l3-block hoverable" @click="$router.push('/admin/classes')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2z" />
                </svg>
                专业班级管理
              </div>
              <div class="entry-text">院系 → 专业 → 班级 三级树</div>
            </div>
            <div class="l3-block hoverable" @click="$router.push('/admin/supervision')">
              <div class="l3-title">
                <svg
                  viewBox="0 0 24 24"
                  width="14"
                  height="14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.9"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path
                    d="M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
                  />
                </svg>
                排课与课程分配
              </div>
              <div class="entry-text">教师 → 课程 → 班级级联</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 数据看板 + 图表区 -->
      <div class="grid-32 animate-in d2">
        <section class="l1-card">
          <div class="l1-head">
            <span class="l1-ico ico-ok"
              ><svg
                viewBox="0 0 24 24"
                width="17"
                height="17"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M3 3v18h18M18 17V9M13 17V5M8 17v-3" /></svg
            ></span>
            <div><div class="l1-title">近 30 天用户增长趋势</div></div>
          </div>
          <div class="l1-body">
            <div class="flex gap-12 wrap mb-12">
              <span class="tag blue">新增注册</span><span class="tag outline">按天统计</span>
            </div>
            <BarChart :data="trendData" :w="540" :h="190" />
          </div>
        </section>
        <section class="l1-card">
          <div class="l1-head">
            <span class="l1-ico ico-info"
              ><svg
                viewBox="0 0 24 24"
                width="17"
                height="17"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M21.21 15.89A10 10 0 1 1 8 2.83M22 12A10 10 0 0 0 12 2v10z" /></svg
            ></span>
            <div><div class="l1-title">全校成绩分布</div></div>
          </div>
          <div class="l1-body">
            <DonutChart :data="gradeDist" center-label="人次" :size="160" />
          </div>
        </section>
      </div>

      <!-- 院系对比 + 进度表 -->
      <section class="l1-card">
        <div class="l1-head">
          <span class="l1-ico ico-violet"
            ><svg
              viewBox="0 0 24 24"
              width="17"
              height="17"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18z" /></svg
          ></span>
          <div><div class="l1-title">课程运营数据</div></div>
          <div class="l1-extra">
            <router-link to="/admin/analytics" class="btn btn-plain btn-sm">查看详情</router-link>
          </div>
        </div>
        <div class="l1-body">
          <CollapsePanel title="课程分类分布" icon="chart" :open="true">
            <BarChart :data="deptData" :horizontal="true" :w="540" :h="190" />
          </CollapsePanel>
          <CollapsePanel title="热门课程 TOP5（按选课人数）" icon="list" :open="false" flush>
            <div v-if="topCourses.length" class="tbl-wrap">
              <table class="tbl">
                <thead>
                  <tr>
                    <th>课程</th>
                    <th>课程号</th>
                    <th>分类</th>
                    <th>选课人数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(p, i) in topCourses" :key="p.courseId || i">
                    <td class="cell-main">{{ p.courseName }}</td>
                    <td class="mono">{{ p.courseCode || '—' }}</td>
                    <td>
                      <span class="tag outline">{{ p.category || '未分类' }}</span>
                    </td>
                    <td class="num">{{ p.enrollmentCount ?? 0 }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <el-empty v-else description="暂无热门课程数据" :image-size="80" />
          </CollapsePanel>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CountUp from '@/components/CountUp.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import BarChart from '@/components/charts/BarChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { getMonoSeries } from '@/utils/chartTheme'
import adminApi from '@/api/admin'
import analyticsApi from '@/api/analytics'
import { toast } from '@/composables/useToast'

const overview = ref({})
const dash = ref({})
const trendData = ref([])
const gradeDist = ref([])
const deptData = ref([])
const topCourses = ref([])
const loading = ref(false)

function fmt(n) {
  return (n ?? 0).toLocaleString('zh-CN')
}

const submitRate = computed(() => {
  const v = dash.value.completionRate
  return v != null ? v + '%' : '—'
})
const excellentRate = computed(() => {
  const g = dash.value.gradeDistribution
  if (!Array.isArray(g) || g.length < 5) return '—'
  const total = g.reduce((s, x) => s + (Number(x) || 0), 0)
  if (!total) return '0'
  return Math.round((((Number(g[0]) || 0) + (Number(g[1]) || 0)) * 100) / total)
})

function fmtDate(d) {
  const [y, m, day] = String(d).split('-')
  return y ? `${m}-${day}` : d
}

async function load() {
  loading.value = true
  try {
    const [ovRes, dashRes, growthRes, courseRes] = await Promise.all([
      adminApi.getOverview(),
      analyticsApi.getDashboardStats(),
      adminApi.getUserGrowth({ days: 30 }),
      adminApi.getCourseStats(),
    ])
    const ov = ovRes?.data?.data || ovRes?.data || {}
    const dc = dashRes?.data?.data || dashRes?.data || {}
    const growth = growthRes?.data?.data || growthRes?.data || []
    const cs = courseRes?.data?.data || courseRes?.data || {}

    overview.value = ov
    dash.value = dc

    const mono = getMonoSeries()
    trendData.value = growth.map((item) => ({
      label: fmtDate(item.date),
      value: item.newUsers ?? 0,
      color: mono[1],
    }))

    const gLabels = ['优秀(90+)', '良好(80-89)', '中等(70-79)', '及格(60-69)', '不及格']
    gradeDist.value = (dc.gradeDistribution || []).slice(0, 5).map((v, i) => ({
      label: gLabels[i] || '分段' + (i + 1),
      value: Number(v) || 0,
      color: mono[i % mono.length],
    }))

    const catMap = cs.categoryDistribution || {}
    deptData.value = Object.entries(catMap).map(([label, value], i) => ({
      label,
      value: Number(value) || 0,
      color: mono[i % mono.length],
    }))

    topCourses.value = cs.topCourses || []
  } catch (e) {
    toast.show('看板数据加载失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.entry-desc {
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.6;
}
.entry-text {
  font-size: 12px;
  color: var(--ink-3);
}
.ico-info {
  background: var(--info-soft);
  color: var(--info);
}
.ico-ok {
  background: var(--ok-soft);
  color: var(--ok);
}
.ico-violet {
  background: var(--violet-soft);
  color: var(--violet);
}
</style>
