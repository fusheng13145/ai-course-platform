<template>
  <div>
    <PageHeader
      title="AI 使用与治理"
      subtitle="跨课程 AI 调用留痕（数据源 ai_call_logs）· 教师出题权限治理"
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
            <EmptyState v-else title="暂无调用记录" icon="chart" />
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
              <EmptyState
                v-else
                title="当前时间窗内没有 AI 调用记录（统计自 2026-10-13 起采集）"
                icon="list"
              />
            </div>
          </CollapsePanel>
        </div>
      </section>

      <!-- 角色矩阵「限制教师出题权限」：平台级治理开关（管理员不受限，缺省=允许） -->
      <section class="l1-card animate-in d3">
        <div class="l1-body">
          <CollapsePanel title="AI 治理" icon="lock" :open="true">
            <div class="gov-row">
              <div>
                <div class="gov-title">教师 AI 出题权限</div>
                <div class="gov-desc">
                  关闭后，教师调用「AI 生成题目」与「批量生成」会被拒绝（403）；管理员不受限。
                  设置写入系统配置项
                  <code>ai.question-generation.teacher-enabled</code>，热生效、无需重启。
                </div>
              </div>
              <el-switch
                :model-value="questionGenEnabled"
                :loading="govLoading || govSaving"
                @change="onToggleGovernance"
              />
            </div>
            <el-alert
              v-if="govLoadError || govSaveError"
              class="gov-alert"
              type="error"
              :closable="false"
              show-icon
              :title="govLoadError || govSaveError"
            />

            <!-- 逐人名单（V10）：只限制被点名的教师，与上面的总闸并存 -->
            <div class="restrict-row">
              <el-select
                v-model="restrictTargets"
                class="restrict-select"
                multiple
                filterable
                placeholder="选择要限制出题的教师（可多选）"
              >
                <el-option
                  v-for="t in teachers"
                  :key="t.id"
                  :label="teacherLabel(t)"
                  :value="t.id"
                />
              </el-select>
              <el-input
                v-model="restrictReason"
                class="restrict-reason"
                maxlength="255"
                placeholder="限制原因（可选，会展示给被限制者）"
              />
              <el-button type="primary" :loading="restrictionBusy" @click="onRestrict">
                加入名单
              </el-button>
            </div>
            <el-alert
              v-if="restrictionActionError || restrictionLoadError"
              class="gov-alert"
              type="error"
              :closable="false"
              show-icon
              :title="restrictionActionError || restrictionLoadError"
            />

            <div v-loading="restrictionLoading" class="tbl-wrap restrict-tbl">
              <table v-if="restrictions.length" class="tbl">
                <thead>
                  <tr>
                    <th>被限制教师</th>
                    <th>原因</th>
                    <th>加入时间</th>
                    <th class="col-op">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in restrictions" :key="r.userId">
                    <td class="cell-main">{{ teacherName(r) }}</td>
                    <td class="muted small">{{ r.reason || '—' }}</td>
                    <td class="muted small">{{ fmtTime(r.createdAt) }}</td>
                    <td class="col-op">
                      <el-button
                        link
                        type="primary"
                        :disabled="restrictionBusy"
                        @click="onUnrestrict(r)"
                      >
                        解除
                      </el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <EmptyState v-else title="当前没有教师被限制出题" icon="users" />
            </div>
          </CollapsePanel>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { useAIUsageByCourse, AI_USAGE_RANGES, type AiUsageCourseRow } from '@/composables/useAIUsageByCourse'
import {
  useAiGovernance,
  type AiRestrictionRow,
  type TeacherOption,
} from '@/composables/useAiGovernance'

const { days, loading, loadError, stats, rows, collectionNote, topCourses, load } =
  useAIUsageByCourse()

const {
  questionGenEnabled,
  loading: govLoading,
  loadError: govLoadError,
  saving: govSaving,
  saveError: govSaveError,
  setQuestionGenEnabled,
  teachers,
  restrictions,
  restrictionLoading,
  restrictionLoadError,
  restrictionActionError,
  restrictionBusy,
  restrict,
  unrestrict,
} = useAiGovernance()

function onToggleGovernance(value: string | number | boolean) {
  setQuestionGenEnabled(Boolean(value))
}

/* ---- 逐人限制名单（V10） ---- */
const restrictTargets = ref<number[]>([])
const restrictReason = ref('')

function teacherLabel(t: TeacherOption) {
  if (t.fullName && t.username) return `${t.fullName}（${t.username}）`
  return t.fullName || t.username || `#${t.id}`
}
function teacherName(r: AiRestrictionRow) {
  return r.fullName || r.username || (r.userId != null ? `#${r.userId}` : '—')
}
function fmtTime(v?: string | null) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

async function onRestrict() {
  const ids = restrictTargets.value.filter((id) => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning('请选择要限制出题的教师')
    return
  }
  const reason = restrictReason.value.trim()
  for (const id of ids) {
    // 逐个加入；任一失败即停，避免"部分成功"却给出一致成功提示（名单是逐人记录，失败可重试）
    if (!(await restrict(id, reason))) return
  }
  ElMessage.success(`已限制 ${ids.length} 位教师的 AI 出题权限`)
  restrictTargets.value = []
  restrictReason.value = ''
}

async function onUnrestrict(row: AiRestrictionRow) {
  if (row.userId === undefined || row.userId === null) return
  try {
    await ElMessageBox.confirm(
      `确定解除「${teacherName(row)}」的 AI 出题限制吗？解除后该教师可立即恢复出题。`,
      '解除确认',
      { confirmButtonText: '确定解除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  if (await unrestrict(row.userId)) ElMessage.success('已解除限制')
}

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

/* 治理开关行：左说明右控件，文本与控件同排 */
.gov-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.gov-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink);
}
.gov-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
  max-width: 720px;
}
.gov-desc code {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 11.5px;
}
.gov-alert {
  margin-top: 10px;
}

/* 逐人限制名单：选择器 + 原因 + 按钮同排 */
.restrict-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.restrict-select {
  min-width: 320px;
  max-width: 460px;
}
.restrict-reason {
  max-width: 300px;
}
.restrict-tbl {
  margin-top: 10px;
}
</style>
