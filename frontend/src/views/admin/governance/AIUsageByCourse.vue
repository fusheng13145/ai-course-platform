<template>
  <div>
    <PageHeader
      title="AI 使用统计"
      subtitle="跨课程 AI 调用留痕 · 按课程聚合（数据源 ai_call_logs）"
      icon="chart"
    >
      <!-- 时间窗切换刻意用原生控件而非 ui/TabGroup：后者是运行时声明 props 的 JS 组件，
           `style="seg"` 这种写法在 vue-tsc 下必然产生 TS2345（该错误在既有文件里已计入类型棘轮基线）。
           类型棘轮只许下降，故这里用等价的原生按钮组，视觉与 .tabs-seg 同款，不新增任何类型错误。 -->
      <div class="range-switch" role="group" aria-label="统计时间窗">
        <button
          v-for="t in rangeTabs"
          :key="t.key"
          type="button"
          class="range-btn"
          :class="{ active: t.key === activeRange }"
          @click="onRangeChange(t.key)"
        >
          {{ t.label }}
        </button>
      </div>
    </PageHeader>

    <!-- 采集口径声明：由后端下发，始终展示（不随 loading/失败消失），避免页面自行编造"历史趋势" -->
    <el-alert
      class="collect-note"
      type="info"
      :closable="false"
      show-icon
      :title="collectionNote"
    />

    <!-- 失败可见：加载失败时给出持久告警，而不是只显示"暂无数据"让用户误判 -->
    <el-alert
      v-if="loadError"
      class="load-error"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    />

    <div v-loading="loading">
      <div class="stat-grid animate-in d1">
        <StatCard label="调用次数" :value="fmtNum(stats.totalCalls)" icon="chart" />
        <StatCard label="覆盖课程" :value="fmtNum(stats.distinctCourses)" icon="book" />
        <StatCard
          label="未绑定课程"
          :value="fmtNum(stats.unboundCalls)"
          icon="warn"
          tone="var(--warn-soft)"
          tone-color="var(--warn)"
        />
        <StatCard
          label="成功率"
          :value="fmtRate(stats.successRate)"
          icon="checks"
          tone="var(--accent-soft)"
          tone-color="var(--accent)"
          :foot="`平均耗时 ${fmtMs(stats.avgDurationMs)} · Token ${fmtNum(stats.totalTokens)}`"
        />
      </div>

      <section class="l1-card animate-in d2">
        <div class="l1-body">
          <CollapsePanel title="调用量 Top 课程" icon="chart" :open="true">
            <BarChart v-if="topCourses.length" :data="topCourses" :w="560" :h="200" horizontal />
            <el-empty v-else description="暂无调用记录" :image-size="70" />
          </CollapsePanel>
          <CollapsePanel title="按课程明细" icon="list" :open="true">
            <div class="content-card">
              <div v-if="rows.length" class="tbl-wrap">
                <table class="tbl">
                  <thead>
                    <tr>
                      <th>课程</th>
                      <th>调用次数</th>
                      <th>Token 估算</th>
                      <th>平均耗时</th>
                      <th>成功率</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(r, i) in rows" :key="rowKey(r, i)">
                      <td class="cell-main">{{ r.courseName || '未绑定课程' }}</td>
                      <td class="num">{{ fmtNum(r.calls) }}</td>
                      <td class="num">{{ fmtNum(r.tokens) }}</td>
                      <td class="num">{{ fmtMs(r.avgDurationMs) }}</td>
                      <td class="num">{{ fmtRate(r.successRate) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <el-empty
                v-else
                description="当前时间窗内没有 AI 调用记录（统计自 2026-10-13 起采集）"
                :image-size="80"
              />
            </div>
          </CollapsePanel>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { useAIUsageByCourse, AI_USAGE_RANGES, type AiUsageCourseRow } from '@/composables/useAIUsageByCourse'

const { days, loading, loadError, stats, rows, collectionNote, topCourses, load } =
  useAIUsageByCourse()

// 显式标注为可变的基础类型数组：`AI_USAGE_RANGES` 是 as const 只读元组，
// 直接把推导出的字面量联合类型交给 TabGroup（运行时 props: { tabs: { type: Array } }）
// 会让 vue-tsc 在 props 匹配处报 TS2345（新增 1 条即撞类型棘轮）。
const rangeTabs: { key: string; label: string }[] = AI_USAGE_RANGES.map((r) => ({
  key: r.key,
  label: r.label,
}))
const activeRange = computed(() => String(days.value))

function onRangeChange(key: string) {
  const next = Number(key)
  if (Number.isFinite(next) && next > 0) load(next)
}

/** 「未绑定课程」行 courseId 为 null，需退回下标做 key（不能让两行撞 key） */
function rowKey(row: AiUsageCourseRow, index: number) {
  return row.courseId === null || row.courseId === undefined ? `unbound-${index}` : row.courseId
}

function fmtNum(v?: number) {
  return v === null || v === undefined ? '—' : v.toLocaleString('zh-CN')
}

function fmtMs(v?: number) {
  return v === null || v === undefined ? '—' : `${Math.round(v)} ms`
}

function fmtRate(v?: number | null) {
  return v === null || v === undefined ? '—' : `${Math.round(v * 100)}%`
}
</script>

<style scoped>
.collect-note,
.load-error {
  margin-bottom: 12px;
}

/* 时间窗切换：与 ui/TabGroup 的 .tabs-seg 同款观感（无渐变、无 hover 装饰） */
.range-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: var(--surface-3);
  border-radius: 8px;
}
.range-btn {
  padding: 4px 12px;
  font-size: 12px;
  line-height: 1.6;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--ink-3);
  cursor: pointer;
  white-space: nowrap;
}
.range-btn.active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
}
</style>
