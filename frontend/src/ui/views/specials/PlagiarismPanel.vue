<template>
  <section class="plagiarism-panel">
    <div class="stat-grid kpi-grid">
      <Stat v-for="metric in metrics" :key="metric.label" :metric="metric" role="admin" />
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            查重任务 <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <input
            v-model="keyword"
            class="input search-input"
            type="search"
            placeholder="搜索提交 / 学生 / 摘要"
            aria-label="搜索查重任务"
          />
          <select v-model="status" class="input filter-select" aria-label="按状态筛选">
            <option value="">全部状态</option>
            <option value="PENDING">待检测</option>
            <option value="CHECKING">检测中</option>
            <option value="COMPLETED">已完成</option>
            <option value="FAILED">失败</option>
          </select>
          <button class="btn btn-sm" :disabled="loading" @click="load">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="listError" class="callout danger" role="alert">
        <span>查重任务</span>
        <p>{{ listError }}</p>
        <button class="btn btn-sm" :disabled="loading" @click="load">重试</button>
      </div>
      <div v-else-if="loading && !rows.length" class="empty-state compact">
        <b>正在加载查重任务…</b>
      </div>
      <div v-else-if="!rows.length" class="empty-state compact">
        <b>暂无查重任务</b>
        <p>教师在批阅页发起查重后，任务会出现在这里。</p>
      </div>
      <div v-else-if="!filtered.length" class="empty-state compact">
        <b>没有匹配的查重任务</b>
        <p>换个关键词或状态再试。</p>
      </div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-80">任务</th>
              <th>提交 / 学生</th>
              <th class="w-96">类型</th>
              <th class="w-96">状态</th>
              <th class="w-120">相似度</th>
              <th class="w-120">高相似片段</th>
              <th class="w-180">完成时间</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paged" :key="String(row.id)">
              <td>#{{ row.id }}</td>
              <td>
                <b>提交 #{{ row.submissionId ?? '—' }}</b>
                <small class="cell-sub">学生 #{{ row.studentId ?? '—' }}</small>
              </td>
              <td>{{ row.checkType || 'TEXT' }}</td>
              <td>
                <span class="chip" :class="statusClass(row.status)">{{
                  statusLabel(row.status)
                }}</span>
              </td>
              <td>{{ percent(row.similarityScore) }}</td>
              <td>{{ row.highSimilarityCount ?? '—' }}</td>
              <td>{{ formatDateTime(row.checkedAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="canExecute(row)"
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="execute(row)"
                  >
                    {{ acting === String(row.id) ? '执行中…' : '执行' }}
                  </button>
                  <button class="text-btn" @click="openDetail(row)">详情</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer v-if="showPager" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page >= Math.ceil(total / pageSize)"
            @click="onPageChange(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal v-model:open="detail.open" title="查重详情" width="760px">
      <div v-if="detail.loading" class="modal-hint">正在加载查重详情…</div>
      <div v-else-if="detail.error" class="form-error" role="alert">{{ detail.error }}</div>
      <template v-else>
        <p class="modal-text">
          状态 {{ statusLabel(detail.status) }} · 相似度 {{ percent(detail.score) }} · 高相似片段
          {{ detail.highCount ?? 0 }}
        </p>
        <p v-if="detail.summary" class="modal-text">{{ detail.summary }}</p>
        <div v-if="detail.matches.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th class="w-120">相似度</th>
                <th>来源</th>
                <th>原文片段</th>
                <th>匹配片段</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(match, index) in detail.matches" :key="`m-${match.id ?? index}`">
                <td>{{ percent(match.similarity) }}</td>
                <td>{{ match.matchedSource || `提交 #${match.matchedSubmissionId ?? '—'}` }}</td>
                <td>{{ brief(match.originalText) }}</td>
                <td>{{ brief(match.matchedText) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="modal-hint">服务端未返回匹配明细。</p>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="detail.open = false">关闭</button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, watch } from 'vue'
import Stat from '../../components/Stat.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useClientPagination } from '@/composables/useClientPagination'
import plagiarismApi from '@/api/plagiarismCheck'
import { pickList, unwrap } from '@/utils/response'
import type { Metric } from '../../nav'

interface CheckRow {
  id?: number | string
  taskId?: number | string | null
  submissionId?: number | string | null
  studentId?: number | string | null
  checkType?: string
  status?: string
  similarityScore?: number | string | null
  highSimilarityCount?: number | null
  resultSummary?: string | null
  checkedAt?: string | null
}

interface MatchRow {
  id?: number | string
  matchedSubmissionId?: number | string | null
  matchedSource?: string
  similarity?: number | string | null
  originalText?: string
  matchedText?: string
}

const { toast } = useUi()
const rows = ref<CheckRow[]>([])
const loading = ref(false)
const listError = ref('')
const keyword = ref('')
const status = ref('')
const acting = ref('')

const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (status.value && row.status !== status.value) return false
    if (!word) return true
    return [row.id, row.submissionId, row.studentId, row.resultSummary]
      .map((value) => String(value ?? '').toLowerCase())
      .some((value) => value.includes(word))
  })
})

const { page, pageSize, total, paged, showPager, onPageChange, reset } =
  useClientPagination<CheckRow>(() => filtered.value, { pageSize: 15 })
watch([keyword, status], () => reset())

const metrics = computed<Metric[]>(() => {
  const pending = rows.value.filter((row) =>
    ['PENDING', 'CHECKING'].includes(String(row.status)),
  ).length
  const completed = rows.value.filter((row) => row.status === 'COMPLETED').length
  const high = rows.value.reduce((sum, row) => sum + Number(row.highSimilarityCount || 0), 0)
  const failed = rows.value.filter((row) => row.status === 'FAILED').length
  return [
    {
      label: '查重任务',
      value: rows.value.length,
      unit: '条',
      note: '最近 500 条',
      icon: 'layers',
    },
    {
      label: '待检测',
      value: pending,
      unit: '条',
      note: '待执行 / 检测中',
      icon: 'clock',
      tone: 'warn',
    },
    {
      label: '已完成',
      value: completed,
      unit: '条',
      note: '可查看匹配',
      icon: 'check',
      tone: 'ok',
    },
    { label: '高相似片段', value: high, unit: '段', note: `失败 ${failed} 条`, icon: 'shield' },
  ]
})

const detail = reactive({
  open: false,
  loading: false,
  error: '',
  status: '',
  score: null as number | string | null,
  highCount: null as number | null,
  summary: '',
  matches: [] as MatchRow[],
})

function canExecute(row: CheckRow): boolean {
  return ['PENDING', 'FAILED'].includes(String(row.status))
}

function statusLabel(value: unknown): string {
  return (
    (
      {
        PENDING: '待检测',
        CHECKING: '检测中',
        COMPLETED: '已完成',
        FAILED: '失败',
      } as Record<string, string>
    )[String(value ?? '')] || String(value ?? '—')
  )
}

function statusClass(value: unknown): string {
  if (value === 'COMPLETED') return 'chip-ok'
  if (value === 'FAILED') return 'chip-danger'
  if (value === 'CHECKING') return 'chip-info'
  return 'chip-warn'
}

function percent(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `${(num <= 1 ? num * 100 : num).toFixed(2)}%`
}

function formatDateTime(value: unknown): string {
  return (
    String(value ?? '')
      .replace('T', ' ')
      .slice(0, 16) || '—'
  )
}

function brief(text: unknown): string {
  const raw = String(text ?? '')
  return raw.length > 80 ? raw.slice(0, 80) + '…' : raw || '—'
}

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

async function load() {
  loading.value = true
  listError.value = ''
  try {
    const payload = unwrap<unknown>(await plagiarismApi.getCheckHistory())
    if (!Array.isArray(payload)) throw new Error('响应格式无效')
    rows.value = pickList<CheckRow>(payload)
  } catch (error) {
    listError.value = errorText(error, '查重任务加载失败，请稍后重试')
    toast(listError.value, 'danger')
  } finally {
    loading.value = false
  }
}

async function execute(row: CheckRow) {
  if (row.id == null) return
  acting.value = String(row.id)
  try {
    await plagiarismApi.executeCheck(row.id)
    toast('查重执行完成', 'ok')
    await load()
  } catch (error) {
    toast(errorText(error, '查重执行失败'), 'danger')
  } finally {
    acting.value = ''
  }
}

async function openDetail(row: CheckRow) {
  if (row.id == null) return
  detail.open = true
  detail.loading = true
  detail.error = ''
  detail.matches = []
  try {
    const payload = unwrap<Record<string, unknown>>(await plagiarismApi.getCheckDetail(row.id))
    detail.status = String(payload?.status ?? row.status ?? '')
    detail.score =
      (payload?.similarityScore as number | string | null) ?? row.similarityScore ?? null
    detail.highCount = Number(payload?.highSimilarityCount ?? row.highSimilarityCount ?? 0)
    detail.summary = String(payload?.resultSummary ?? row.resultSummary ?? '')
    detail.matches = pickList<MatchRow>(payload?.matches)
  } catch (error) {
    detail.error = errorText(error, '查重详情加载失败')
  } finally {
    detail.loading = false
  }
}

onMounted(() => void load())
</script>

<style scoped>
.plagiarism-panel {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.search-input {
  width: 220px;
  height: 30px;
}
.filter-select {
  width: 120px;
  height: 30px;
}
</style>
