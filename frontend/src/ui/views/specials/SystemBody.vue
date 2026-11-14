<template>
  <section class="system-body">
    <!-- 口径先摆在前头：本页是「读配置 + 改配置值」的运维面，
         不含备份、恢复、日志文件下载等高风险动作（那些端点存在但本页不提供入口）。 -->
    <div class="card caveat-card">
      <p class="card-kicker">运维面</p>
      <p class="scope-line">
        当前共 <b>{{ configs.length }}</b> 条配置，分
        <b>{{ groupCount }}</b> 组；修改某一条只影响该条配置。
      </p>
      <p class="scope-note">
        密钥类配置以掩码显示，编辑时必须输入新值才会提交；本页<b>不提供</b>备份、恢复与日志下载入口。
      </p>
    </div>

    <div v-if="listError" class="callout" role="alert">
      <span>系统配置</span>
      <p>{{ listError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="loadConfigs()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <!-- 健康状态与配置列表分路：健康探针挂了不该让整页读不出配置 -->
    <section class="card health-card">
      <header class="card-head">
        <div>
          <h2>
            服务状态
            <span class="chip" :class="healthChip">{{ healthText }}</span>
          </h2>
        </div>
        <button class="btn btn-sm btn-ghost" :disabled="healthLoading" @click="loadHealth()">
          {{ healthLoading ? '刷新中…' : '刷新' }}
        </button>
      </header>

      <div v-if="healthError" class="callout" role="alert">
        <span>服务状态</span>
        <p>{{ healthError }}</p>
      </div>
      <div v-else-if="!health" class="empty-state compact">
        <b>尚未取到服务状态</b>
      </div>
      <div v-else class="health-grid">
        <div class="health-item">
          <b>数据库</b>
          <span class="chip" :class="health.database === 'CONNECTED' ? 'chip-ok' : 'chip-danger'">
            {{ health.database || '—' }}
          </span>
        </div>
        <div class="health-item">
          <b>缓存</b>
          <span class="chip" :class="health.redis === 'CONNECTED' ? 'chip-ok' : 'chip-danger'">
            {{ health.redis || '—' }}
          </span>
        </div>
        <div v-for="service in serviceRows" :key="service.name" class="health-item">
          <b>{{ service.name }}</b>
          <span class="chip" :class="service.chip">{{ service.status }}</span>
          <small class="cell-sub">{{ service.baseUrl }}</small>
        </div>
      </div>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            配置项 <span class="chip chip-mute">{{ filtered.length }} 条</span>
          </h2>
        </div>
        <div class="filter-row">
          <form class="search-form" @submit.prevent>
            <input
              v-model="keyword"
              class="input search-input"
              type="search"
              placeholder="搜索配置键或说明"
              aria-label="搜索配置项"
            />
          </form>
          <select v-model="groupFilter" class="input" aria-label="按分组筛选配置">
            <option value="">全部分组</option>
            <option v-for="group in groups" :key="group" :value="group">{{ group }}</option>
          </select>
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="loadConfigs()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>

      <div v-if="pagedRows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="w-200">配置键</th>
              <th>说明</th>
              <th class="w-80">类型</th>
              <th>当前值</th>
              <th class="w-80 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedRows" :key="row.key">
              <td>
                <b>{{ row.key }}</b>
                <small class="cell-sub">{{ row.group }}</small>
              </td>
              <td>{{ row.description || '—' }}</td>
              <td>
                <span class="chip chip-mute">{{ row.type }}</span>
              </td>
              <td>
                <code class="config-value">{{ displayValue(row) }}</code>
              </td>
              <td>
                <button class="text-btn" @click="startEdit(row)">修改</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="loading" class="empty-state compact">
        <b>正在加载系统配置…</b>
      </div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><Icon name="settings" /></span>
        <b>{{ emptyDescription() }}</b>
        <p>{{ listError ? '请查看上方提示后重试。' : '换个关键词或分组再试。' }}</p>
      </div>

      <footer v-if="!listError && filtered.length > pageSize" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ filtered.length }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1" @click="onPageChange(page - 1)">
            上一页
          </button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="onPageChange(page + 1)">
            下一页
          </button>
        </div>
      </footer>
    </section>

    <Modal v-model:open="editOpen" title="修改配置" width="520px">
      <p class="modal-text">
        配置键 <b>{{ editing?.key }}</b
        >：{{ editing?.description || '（无说明）' }}
      </p>
      <label class="edit-field">
        <span>新值</span>
        <select v-if="editing?.type === 'BOOLEAN'" v-model="draft" class="input">
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
        <textarea
          v-else-if="editing?.type === 'JSON'"
          v-model="draft"
          class="input edit-textarea"
          rows="4"
        ></textarea>
        <input
          v-else
          v-model="draft"
          class="input"
          :type="editing?.type === 'NUMBER' ? 'number' : 'text'"
        />
      </label>
      <p class="modal-hint">
        {{
          editing && isSensitive(editing.key)
            ? '该项是密钥类配置：留空表示不修改（不会提交空值）。'
            : '保存只影响这一个配置键。'
        }}
      </p>
      <p v-if="actionError" class="modal-error" role="alert">{{ actionError }}</p>
      <template #footer>
        <button class="btn btn-ghost" :disabled="actionBusy" @click="closeEdit">取消</button>
        <button class="btn btn-primary" :disabled="actionBusy" @click="saveEdit">
          {{ actionBusy ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Icon from '../../components/Icon.vue'
import Modal from '../../components/Modal.vue'
import { useUi } from '../../composables/ui'
import { useListFeedback } from '@/composables/useListFeedback'
import { useClientPagination } from '@/composables/useClientPagination'
import { getConfigs, getHealth, updateConfig } from '@/api/admin'
import { unwrap } from '@/utils/response'

interface ConfigRow {
  key: string
  value?: string | number | boolean | null
  description?: string | null
  type?: string | null
  group?: string | null
}

/**
 * GET /admin/health 的真实形状（2026-11-04 真栈实测）：
 * `{ status, database, redis, timestamp, pythonServices }` —— 依赖状态是**平铺**的，
 * 没有再套一层 `health`；第一版按嵌套写会在渲染时抛 undefined 读属性。
 */
interface HealthPayload {
  status?: string
  database?: string
  redis?: string
  timestamp?: string
  pythonServices?: Record<string, { enabled?: boolean; status?: string; baseUrl?: string }>
}

const { toast } = useUi()

const configs = ref<ConfigRow[]>([])
const health = ref<HealthPayload | null>(null)
const healthError = ref('')
const healthLoading = ref(false)
const keyword = ref('')
const groupFilter = ref('')

const editOpen = ref(false)
const editing = ref<ConfigRow | null>(null)
const draft = ref('')
const actionBusy = ref(false)
const actionError = ref('')

const pageSize = 20

const { loading, listError, run, emptyDescription } = useListFeedback({
  failMessage: '系统配置加载失败，请稍后重试。',
  toastMessage: '系统配置加载失败',
  emptyText: '没有匹配的配置项',
  errorEmptyText: '无法确认系统配置，请查看上方提示后重试',
  notify: (message) => toast(message, 'danger'),
})

const groups = computed(() =>
  Array.from(new Set(configs.value.map((row) => String(row.group || '未分组')))).sort(),
)
const groupCount = computed(() => groups.value.length)

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return configs.value.filter((row) => {
    if (groupFilter.value && String(row.group || '未分组') !== groupFilter.value) return false
    if (!q) return true
    return [row.key, row.description]
      .map((item) => String(item || '').toLowerCase())
      .some((item) => item.includes(q))
  })
})

const {
  page,
  paged: pagedRows,
  onPageChange,
  reset,
} = useClientPagination(() => filtered.value, { pageSize })

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))

// 筛选条件变化后回到第 1 页，否则可能停在越界页码上（分页原语的收敛只兜数据收缩）
watch([keyword, groupFilter], () => reset())

const healthText = computed(() => {
  if (!health.value) return '—'
  return health.value.status || '—'
})

const healthChip = computed(() => {
  const status = String(health.value?.status || '')
  return status === 'UP' ? 'chip-ok' : status ? 'chip-danger' : 'chip-mute'
})

const serviceRows = computed(() => {
  const services = health.value?.pythonServices || {}
  const names: Record<string, string> = {
    'knowledge-graph': '知识图谱服务',
    rag: 'RAG 检索服务',
    'fine-tune': '微调服务',
  }
  return Object.entries(services).map(([key, value]) => ({
    name: names[key] || key,
    status: String(value?.status || (value?.enabled ? 'ENABLED' : '—')),
    baseUrl: value?.baseUrl || '',
    chip: value?.enabled ? 'chip-ok' : 'chip-mute',
  }))
})

/** 密钥类配置：列表里掩码，编辑时必须输入新值才提交 */
function isSensitive(key: string): boolean {
  return /(key|secret|password|token)/i.test(key)
}

function displayValue(row: ConfigRow): string {
  const raw = row.value === null || row.value === undefined ? '' : String(row.value)
  if (!raw) return '（空）'
  if (isSensitive(row.key)) return '••••••'
  return raw.length > 48 ? `${raw.slice(0, 48)}…` : raw
}

async function loadConfigs() {
  await run(async () => {
    const data = unwrap(await getConfigs())
    if (!Array.isArray(data)) throw new Error('系统配置响应格式无效')
    configs.value = data as ConfigRow[]
  })
}

async function loadHealth() {
  healthLoading.value = true
  healthError.value = ''
  try {
    const data = unwrap<HealthPayload>(await getHealth())
    health.value = data && typeof data === 'object' ? data : null
  } catch {
    // 健康探针失败不影响配置读数：分路归因，不清空另一块
    health.value = null
    healthError.value = '服务状态探针暂不可用；配置列表不受影响。'
  } finally {
    healthLoading.value = false
  }
}

function startEdit(row: ConfigRow) {
  editing.value = row
  // 密钥类配置不回显真实值：留空表示不修改
  draft.value = isSensitive(row.key)
    ? ''
    : row.value === null || row.value === undefined
      ? ''
      : String(row.value)
  actionError.value = ''
  editOpen.value = true
}

function closeEdit() {
  if (actionBusy.value) return
  editOpen.value = false
  editing.value = null
  actionError.value = ''
}

async function saveEdit() {
  const row = editing.value
  if (!row) return
  const next = draft.value.trim()
  if (isSensitive(row.key) && !next) {
    actionError.value = '密钥类配置必须输入新值才会提交；留空表示不修改。'
    return
  }
  actionBusy.value = true
  actionError.value = ''
  try {
    await updateConfig(row.key, { value: next, group: row.group || '' })
    toast(`已更新配置 ${row.key}`, 'ok')
    editOpen.value = false
    editing.value = null
    await loadConfigs()
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } } | null
    const message = err?.response?.data?.message || '配置更新失败，请稍后重试'
    toast(message, 'danger')
    actionError.value = message
  } finally {
    actionBusy.value = false
  }
}

onMounted(() => {
  void loadConfigs()
  void loadHealth()
})
</script>

<style scoped>
.system-body {
  display: grid;
  gap: 14px;
}
.caveat-card {
  display: grid;
  gap: 6px;
}
.scope-line {
  margin: 0;
  color: var(--text-2);
}
.scope-note {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.health-item {
  display: grid;
  gap: 4px;
  justify-items: start;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface-2);
}
.config-value {
  font-family: var(--font-mono);
  font-size: 12px;
  word-break: break-all;
}
.edit-field {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}
.edit-field > span {
  font-size: 12px;
  color: var(--text-3);
}
.edit-textarea {
  width: 100%;
  resize: vertical;
  font-family: var(--font-mono);
}
.modal-error {
  margin: 8px 0 0;
  color: var(--danger);
  font-size: 13px;
}
</style>
