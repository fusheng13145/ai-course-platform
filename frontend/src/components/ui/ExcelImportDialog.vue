<template>
  <Modal wide @close="emit('close')">
    <template #title>{{ title }}</template>
    <template #sub>{{ sub }}</template>

    <!-- 文件选择区 -->
    <div
      class="drop"
      :class="{ 'drop-on': dragging, 'drop-filled': !!file }"
      @click="fileInput?.click()"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <input ref="fileInput" type="file" accept=".xlsx" class="hidden-input" @change="onPick" />
      <svg
        viewBox="0 0 24 24"
        width="26"
        height="26"
        fill="none"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
      </svg>
      <div v-if="!file" class="drop-main">点击选择，或拖拽 .xlsx 文件到此处</div>
      <template v-else>
        <div class="drop-main">{{ file.name }}</div>
        <div class="drop-sub">{{ sizeText }} · 点击可重新选择</div>
      </template>
    </div>

    <!-- 表头说明 -->
    <div v-if="columnList.length" class="tip">
      <div class="tip-head">表头（第 1 行，共 {{ columnList.length }} 列，顺序固定）</div>
      <div class="cols">
        <code v-for="c in columnList" :key="c.name" :class="{ req: c.required }">{{ c.name }}</code>
      </div>
      <ul v-if="notes.length" class="notes">
        <li v-for="(n, i) in notes" :key="i">{{ n }}</li>
      </ul>
    </div>

    <!-- 导入结果 -->
    <div v-if="stat" class="result">
      <div class="result-row" :style="{ gridTemplateColumns: `repeat(${cells.length}, 1fr)` }">
        <div v-for="c in cells" :key="c.label" class="result-cell" :class="c.tone">
          <div class="result-num">{{ c.value }}</div>
          <div class="result-lab">{{ c.label }}</div>
        </div>
      </div>

      <!-- 特有区块（如用户导入的随机密码清单） -->
      <slot name="extra" />

      <div v-if="failRows.length" class="fail">
        <div class="fail-head">未导入的行（{{ failRows.length }}）</div>
        <ul class="fail-list">
          <li v-for="(msg, i) in failRows" :key="i">{{ msg }}</li>
        </ul>
      </div>
    </div>

    <template #footer>
      <button class="btn btn-plain" @click="emit('close')">{{ stat ? '完成' : '取消' }}</button>
      <button class="btn btn-primary" :disabled="!file || importing || !!stat" @click="submit">
        {{ importing ? '导入中…' : stat ? '已导入' : '开始导入' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { toast } from '@/composables/useToast'
import Modal from '@/components/ui/Modal.vue'

/** 导入结果的公共形态：兼容各端点略有差异的字段命名 */
interface ImportResult {
  // 用户导入
  successCount?: number
  failCount?: number
  // 题目 / 章节 / 知识点导入
  importedCount?: number
  skippedCount?: number
  failedCount?: number
  total?: number
  errors?: string[]
  [key: string]: unknown
}

/** 表头列定义 */
interface ColDef {
  name: string
  required?: boolean
}

const props = defineProps({
  title: { type: String, default: '批量导入' },
  sub: { type: String, default: '' },
  /** 表头列说明；required 为 true 的列会高亮 */
  columns: { type: Array, default: () => [] },
  notes: { type: Array, default: () => [] },
  importing: { type: Boolean, default: false },
  result: { type: Object, default: null },
})
const emit = defineEmits(['close', 'submit'])

const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const dragging = ref(false)

/** 运行时声明的 Array props 会被推断为 unknown[]，此处收窄为列定义类型供模板使用 */
const columnList = computed<ColDef[]>(() => (props.columns as unknown as ColDef[]) ?? [])

const resultData = computed<ImportResult | null>(
  () => (props.result as unknown as ImportResult | null) ?? null,
)

const sizeText = computed(() => {
  const n = file.value?.size ?? 0
  return n < 1024
    ? `${n} B`
    : n < 1024 * 1024
      ? `${(n / 1024).toFixed(1)} KB`
      : `${(n / 1024 / 1024).toFixed(2)} MB`
})

/** 归一化统计：不同端点用 successCount/failCount 或 importedCount/failedCount */
const stat = computed(() => {
  const r = resultData.value
  if (!r) return null
  return {
    ok: r.successCount ?? r.importedCount ?? 0,
    skip: r.skippedCount ?? 0,
    fail: r.failedCount ?? r.failCount ?? 0,
    total: r.total ?? 0,
  }
})

/** 统计卡：跳过数仅在出现时展示，避免空占位 */
const cells = computed(() => {
  const s = stat.value
  if (!s) return []
  const list = [{ label: '成功', value: s.ok, tone: 'ok' }]
  if (s.skip > 0) list.push({ label: '跳过（已存在）', value: s.skip, tone: 'skip' })
  list.push({ label: '失败', value: s.fail, tone: s.fail > 0 ? 'bad' : '' })
  list.push({ label: '文件总行数', value: s.total, tone: '' })
  return list
})

const failRows = computed(() => resultData.value?.errors ?? [])

function checkAndSet(f: File) {
  if (!/\.xlsx$/i.test(f.name)) {
    toast.show('仅支持 .xlsx 格式的 Excel 文件', 'warning')
    return
  }
  file.value = f
}

function onPick(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) checkAndSet(f)
}

function onDrop(e: DragEvent) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) checkAndSet(f)
}

function submit() {
  if (!file.value) return
  emit('submit', file.value)
}
</script>

<style scoped>
.hidden-input {
  display: none;
}
.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 22px 16px;
  border: 1.5px dashed var(--ac-border-strong);
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg-subtle);
  color: var(--ac-text-muted);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
}
.drop:hover,
.drop-on {
  border-color: var(--ac-primary);
  background: var(--ac-primary-bg);
}
.drop-filled {
  border-style: solid;
  border-color: var(--ac-success);
  background: var(--ac-success-bg);
}
.drop-main {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.drop-sub {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.tip {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg-subtle);
  border: 1px solid var(--ac-border);
}
.tip-head {
  font-size: 12px;
  font-weight: 700;
  color: var(--ac-text-secondary);
  margin-bottom: 8px;
}
.cols {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cols code {
  font-size: 11.5px;
  padding: 2px 7px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  color: var(--ac-text-secondary);
}
.cols code.req {
  color: var(--ac-primary);
  border-color: var(--ac-primary);
  font-weight: 700;
}
.notes {
  margin: 9px 0 0;
  padding-left: 16px;
  font-size: 12px;
  line-height: 1.75;
  color: var(--ac-text-muted);
}
.result {
  margin-top: 14px;
}
.result-row {
  display: grid;
  gap: 10px;
}
.result-cell {
  padding: 11px 8px;
  text-align: center;
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg-subtle);
  border: 1px solid var(--ac-border);
}
.result-cell.ok .result-num {
  color: var(--ac-success);
}
.result-cell.bad .result-num {
  color: var(--ac-danger);
}
.result-cell.skip .result-num {
  color: var(--ac-warning);
}
.result-num {
  font-size: 21px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--ac-text-primary);
}
.result-lab {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--ac-text-muted);
}
.fail {
  margin-top: 12px;
  border: 1px solid var(--ac-danger);
  background: var(--ac-danger-bg);
  border-radius: var(--ac-radius-md);
  padding: 10px 12px;
}
.fail-head {
  font-size: 12px;
  font-weight: 700;
  color: var(--ac-danger);
  margin-bottom: 6px;
}
.fail-list {
  margin: 0;
  padding-left: 17px;
  max-height: 130px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.75;
  color: var(--ac-text-secondary);
}
</style>
