<template>
  <section class="card table-card">
    <header class="card-head">
      <div>
        <p class="card-kicker">{{ kicker }}</p>
        <h2>{{ table.title }}</h2>
      </div>
      <span class="chip chip-mute">{{ filtered.length }} 条记录</span>
    </header>
    <div class="table-toolbar">
      <label class="table-search">
        <Icon name="search" />
        <input v-model="query" placeholder="搜索当前列表…" aria-label="搜索当前列表" />
      </label>
      <div class="table-tools">
        <button class="btn btn-sm" @click="notReady('筛选')">筛选</button>
        <button class="btn btn-sm" @click="notReady('导出')">导出</button>
      </div>
    </div>
    <div class="table-wrap" @click="onTableClick">
      <table>
        <thead>
          <tr>
            <th v-for="column in table.columns" :key="column">{{ column }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, rowIndex) in filtered" :key="rowIndex">
            <td v-for="(cell, cellIndex) in row" :key="cellIndex">
              <span v-if="isChip(cell)" class="chip" :class="`chip-${cell.chip}`">{{
                cell.text
              }}</span>
              <!-- eslint-disable-next-line vue/no-v-html -- 仅渲染原型静态快照，走 utils/sanitize 的表格档位（无内联 style） -->
              <span v-else-if="isHtml(cell)" v-html="clean(cell)"></span>
              <template v-else>{{ cell }}</template>
            </td>
          </tr>
          <tr v-if="!filtered.length" class="table-empty">
            <td :colspan="table.columns.length">没有匹配记录，试试更短的关键词。</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from './Icon.vue'
import type { Table } from '../nav'
import { sanitizeTableCellHtml } from '@/utils/sanitize'
import { useUi } from '../composables/ui'

const props = withDefaults(defineProps<{ table: Table; kicker?: string }>(), {
  kicker: 'RECORD / LIST',
})
const ui = useUi()
const query = ref('')

type Cell = string | { text?: string; chip?: string } | null

const textOf = (cell: Cell): string => {
  if (cell == null) return ''
  if (typeof cell === 'string') return cell.replace(/<[^>]+>/g, '')
  return cell.text || ''
}

const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return props.table.rows
  return props.table.rows.filter((row) =>
    row.some((cell) => textOf(cell).toLowerCase().includes(keyword)),
  )
})

const isChip = (cell: Cell): cell is { text?: string; chip?: string } =>
  Boolean(cell && typeof cell === 'object' && 'chip' in cell)

const isHtml = (cell: Cell): cell is string =>
  typeof cell === 'string' && /<\/?(b|small|span|i|strong|br|time)\b/.test(cell)

/** 白名单档位本体在 utils/sanitize 的 TABLE 档（F-1：位点不再自带 DOMPurify 配置） */
function clean(html: string): string {
  return sanitizeTableCellHtml(html)
}

function notReady(action: string) {
  ui.toast(`演示数据：${action}尚未接入`, 'warn')
}

function onTableClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest('button')
  if (!button) return
  event.preventDefault()
  const message = button.dataset.msg || button.textContent || '该操作'
  ui.toast(`演示数据：${message}`, 'warn')
}
</script>
