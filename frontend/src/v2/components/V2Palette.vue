<template>
  <div v-if="paletteOpen" class="palette-layer" @click.self="close">
    <div class="palette" role="dialog" aria-modal="true" aria-label="全局搜索">
      <div class="palette-input">
        <V2Icon name="search" />
        <input
          ref="inputRef"
          v-model="query"
          autocomplete="off"
          placeholder="搜索模块、课程、题目、知识点…"
          aria-label="搜索"
          @keydown.down.prevent="move(1)"
          @keydown.up.prevent="move(-1)"
          @keydown.enter.prevent="choose(results[activeIndex])"
          @keydown.esc.prevent="close"
        />
        <kbd>ESC</kbd>
        <button class="icon-btn" aria-label="关闭搜索" @click="close">
          <V2Icon name="x" />
        </button>
      </div>
      <div class="palette-results">
        <!-- 结果区限高 330px：状态条排在分组之前并吸顶，24 条命中也不会把失败提示挤到折叠线以下 -->
        <p v-if="searchState === 'loading' && !results.length" class="palette-status">
          正在检索全站…
        </p>
        <p v-else-if="searchState === 'error'" class="palette-status is-error" role="alert">
          <span>检索服务暂不可用</span>
          <button type="button" class="palette-retry" @click="retrySearch">重试</button>
        </p>
        <p v-else-if="hasKeyword && !results.length" class="palette-status">
          没有匹配结果，试试更短的关键词。
        </p>
        <template v-for="section in sections" :key="section.id">
          <p class="palette-group">{{ section.heading }}</p>
          <template v-for="item in section.items" :key="item.key">
            <button
              v-if="item.path"
              class="palette-item"
              :class="{ 'is-active': item.index === activeIndex }"
              @click="choose(item)"
              @mouseenter="activeIndex = item.index"
            >
              <V2Icon :name="item.icon" />
              <span>
                <b>{{ item.label }}</b>
                <small>{{ item.meta }}</small>
              </span>
              <V2Icon name="arrow" />
            </button>
            <div
              v-else
              class="palette-item is-disabled"
              :class="{ 'is-active': item.index === activeIndex }"
              aria-disabled="true"
              @mouseenter="activeIndex = item.index"
            >
              <V2Icon :name="item.icon" />
              <span>
                <b>{{ item.label }}</b>
                <small>{{ item.meta }}</small>
              </span>
            </div>
          </template>
        </template>
      </div>
      <div class="palette-foot">
        <span>↑↓ 选择 · ↵ 打开</span>
        <span>{{ roleLabel }} / {{ results.length }} 条入口</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import V2Icon from './V2Icon.vue'
import { useV2Ui } from '../composables/ui'
import { courseTabs, navGroups, type V2Role } from '../nav'
import { getCourses } from '../data/api'
import { searchAll } from '@/api/search'

/** 关键词停顿 250ms 才打后端：Ctrl+K 面板里连打是常态，逐字请求只会浪费配额 */
const SEARCH_DEBOUNCE_MS = 250
const RESULT_LIMIT = 24
const SNIPPET_LIMIT = 48

type SearchState = 'idle' | 'loading' | 'done' | 'error'
type ItemGroup = 'local' | 'server'

interface Item {
  key: string
  label: string
  meta: string
  icon: string
  /** 空表示当前角色没有承接页，只能看不能点 */
  path: string | null
  group: ItemGroup
}

interface ServerHit {
  type?: string
  id?: number | string | null
  title?: string
  snippet?: string
  score?: number
}

const TYPE_META: Record<string, { label: string; icon: string }> = {
  course: { label: '课程', icon: 'book' },
  question: { label: '题目', icon: 'file' },
  knowledge: { label: '知识点', icon: 'path' },
  note: { label: '笔记', icon: 'message' },
}

const { paletteOpen, paletteQuery, role, roleLabel } = useV2Ui()
const route = useRoute()
const router = useRouter()
const query = paletteQuery
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const courses = ref<Array<{ id: number; name: string; code: string }>>([])

const serverItems = ref<Item[]>([])
const searchState = ref<SearchState>('idle')
/**
 * 单调递增的请求序号：作废与发起都 +1，响应回来时序号对不上就整包丢弃。
 * 只靠 debounce 不够——先发的慢请求仍可能后到并覆盖新关键词的命中。
 */
let requestSeq = 0
let debounceTimer: ReturnType<typeof setTimeout> | undefined
/** 关键词变化后的下一轮 results 直接回到第一条；其余结果变化按 key 保留选中 */
let resetActiveOnResults = true

const items = computed<Item[]>(() => {
  const list: Array<Omit<Item, 'key'>> = []
  for (const group of navGroups(role.value)) {
    for (const item of group.items) {
      list.push({
        label: item.label,
        meta: group.label,
        icon: item.icon,
        path: item.path,
        group: 'local',
      })
    }
  }
  const courseId = Number(route.params.id)
  const tabs = courseTabs(role.value)
  for (const tab of tabs) {
    list.push({
      label: tab.label,
      meta: courseId ? '课程页签' : '课程页签（进入课程后可用）',
      icon: tab.icon || 'dashboard',
      path: courseId ? `/${role.value}/courses/${courseId}/${tab.id}` : `/${role.value}/courses`,
      group: 'local',
    })
  }
  for (const course of courses.value) {
    list.push({
      label: course.name,
      meta: `${course.code || '课程'} · 进入课程`,
      icon: 'book',
      path: `/${role.value}/courses/${course.id}/overview`,
      group: 'local',
    })
  }
  return list.map((item, index) => ({ ...item, key: `local:${index}:${item.path}` }))
})

const hasKeyword = computed(() => query.value.trim().length > 0)

const localResults = computed<Item[]>(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return items.value.slice(0, RESULT_LIMIT)
  return items.value
    .filter((item) => `${item.label}${item.meta}`.toLowerCase().includes(keyword))
    .slice(0, RESULT_LIMIT)
})

/** 键盘选择只认这一个扁平数组；分组标题只影响渲染，不改变 activeIndex 的语义 */
const results = computed<Item[]>(() => [...localResults.value, ...serverItems.value])

const sections = computed(() => {
  const indexed = results.value.map((item, index) => ({ ...item, index }))
  return [
    {
      id: 'local' as const,
      heading: '导航与课程',
      items: indexed.filter((item) => item.group === 'local'),
    },
    {
      id: 'server' as const,
      heading: '全站检索',
      items: indexed.filter((item) => item.group === 'server'),
    },
  ].filter((section) => section.items.length > 0)
})

/** 服务端命中落到 v2 页面：没有承接页的角色一律返回 null（展示但不可点） */
function serverRoute(type: string, id?: number | string | null): string | null {
  const current = role.value
  if (type === 'course') {
    // 后端可能给 null / 空串：不拦住会拼出 /courses/null/overview
    if (id === undefined || id === null) return null
    const courseId = String(id).trim()
    if (!courseId || courseId === 'null' || courseId === 'undefined') return null
    return `/${current}/courses/${courseId}/overview`
  }
  if (type === 'question') {
    if (current === 'teacher') return '/teacher/bank'
    if (current === 'admin') return '/admin/questions'
    return null
  }
  if (type === 'knowledge') {
    if (current === 'teacher') return '/teacher/knowledge-management'
    // 学生没有独立知识点页，落到自己的课程列表
    if (current === 'student') return '/student/courses'
    return null
  }
  if (type === 'note') {
    // 管理员侧没有消息中心，笔记命中只展示不可点
    return current === 'admin' ? null : `/${current}/messages`
  }
  return null
}

function toServerItem(hit: ServerHit, index: number): Item {
  const type = String(hit?.type || '').toLowerCase()
  const known = TYPE_META[type]
  const path = serverRoute(type, hit?.id)
  const title = String(hit?.title || '').trim()
  const snippet = String(hit?.snippet || '').trim()
  const label = title || snippet || '未命名结果'
  const metaParts = [`${known ? known.label : '结果'} · 全站检索`]
  if (snippet && snippet !== label) {
    metaParts.push(snippet.length > SNIPPET_LIMIT ? `${snippet.slice(0, SNIPPET_LIMIT)}…` : snippet)
  }
  if (!path) metaParts.push('暂无可打开入口')
  return {
    // 同一 type 可能有多条缺 id 的命中，必须带下标才能保证 Vue key 唯一
    key: `server:${index}:${type}:${String(hit?.id ?? '')}`,
    label,
    meta: metaParts.join(' · '),
    icon: known ? known.icon : 'search',
    path,
    group: 'server',
  }
}

function cancelPending() {
  if (debounceTimer !== undefined) {
    clearTimeout(debounceTimer)
    debounceTimer = undefined
  }
  requestSeq += 1
}

async function runSearch(keyword: string) {
  cancelPending()
  const seq = requestSeq
  searchState.value = 'loading'
  try {
    const hits = await searchAll(keyword)
    if (seq !== requestSeq) return
    const rows: ServerHit[] = Array.isArray(hits) ? hits : []
    serverItems.value = rows.map((hit, index) => toServerItem(hit, index))
    searchState.value = 'done'
  } catch {
    if (seq !== requestSeq) return
    serverItems.value = []
    searchState.value = 'error'
  }
}

function retrySearch() {
  const keyword = query.value.trim()
  if (!keyword) return
  void runSearch(keyword)
}

// 关键词变化：作废在途响应、清掉上一轮命中，再按停顿发请求；空关键词零请求
watch(
  () => query.value.trim(),
  (keyword) => {
    cancelPending()
    serverItems.value = []
    activeIndex.value = 0
    resetActiveOnResults = true
    if (!keyword) {
      searchState.value = 'idle'
      return
    }
    searchState.value = 'loading'
    debounceTimer = setTimeout(() => {
      debounceTimer = undefined
      void runSearch(keyword)
    }, SEARCH_DEBOUNCE_MS)
  },
)

/**
 * 服务端命中是异步追加的：不能像关键词变化那样把选中项打回 0，
 * 否则用户已用 ↓ 选好的条目会在响应落地时被换掉，紧接着的回车会打开错误入口。
 * 按 key 追住当前条目；条目真的消失时才夹回有效范围。
 */
watch(results, (next, previous) => {
  if (resetActiveOnResults) {
    resetActiveOnResults = false
    activeIndex.value = 0
    return
  }
  const current = previous[activeIndex.value]
  const kept = current ? next.findIndex((item) => item.key === current.key) : -1
  if (kept !== -1) {
    activeIndex.value = kept
    return
  }
  if (activeIndex.value >= next.length) {
    activeIndex.value = Math.max(next.length - 1, 0)
  }
})

watch(paletteOpen, async (open) => {
  if (!open) return
  activeIndex.value = 0
  await nextTick()
  inputRef.value?.focus()
  if (!courses.value.length) {
    const rows = await getCourses(role.value as V2Role)
    courses.value = rows.map((row) => ({ id: row.id, name: row.name, code: row.code }))
  }
})

function move(delta: number) {
  if (!results.value.length) return
  activeIndex.value = (activeIndex.value + delta + results.value.length) % results.value.length
}

function choose(item?: Item) {
  // 无承接页的命中（未知类型 / 管理员侧的笔记）只展示，回车不跳转
  if (!item?.path) return
  close()
  router.push(item.path)
}

function close() {
  cancelPending()
  paletteOpen.value = false
  query.value = ''
  serverItems.value = []
  searchState.value = 'idle'
}

function onHotkey(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    paletteOpen.value = !paletteOpen.value
    if (!paletteOpen.value) query.value = ''
    return
  }
  if (event.key === 'Escape' && paletteOpen.value) close()
}

onMounted(() => window.addEventListener('keydown', onHotkey))
onBeforeUnmount(() => {
  cancelPending()
  window.removeEventListener('keydown', onHotkey)
})
</script>

<style scoped>
.palette-group {
  margin: 8px 6px 2px;
  padding: 0 7px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  color: var(--ink-4);
}

.palette-results > .palette-group:first-child {
  margin-top: 2px;
}

.palette-item.is-disabled {
  cursor: default;
  opacity: 0.55;
}

.palette-item.is-disabled:hover {
  background: none;
  color: inherit;
}

.palette-status {
  margin: 6px;
  padding: 14px 12px;
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
}

/* 失败与空态必须一眼可分：失败走警示色与左边框，空态维持中性 */
.palette-status.is-error {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-left: 3px solid var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
  text-align: left;
}

.palette-retry {
  flex: none;
  padding: 4px 10px;
  border: 1px solid currentcolor;
  border-radius: 6px;
  background: none;
  color: inherit;
  font-size: 10px;
  cursor: pointer;
}
</style>
