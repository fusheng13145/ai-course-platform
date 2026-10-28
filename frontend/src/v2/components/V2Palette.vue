<template>
  <div v-if="paletteOpen" class="palette-layer" @click.self="close">
    <div class="palette" role="dialog" aria-modal="true" aria-label="全局搜索">
      <div class="palette-input">
        <V2Icon name="search" />
        <input
          ref="inputRef"
          v-model="query"
          autocomplete="off"
          placeholder="搜索模块、课程、页面…"
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
        <button
          v-for="(item, index) in results"
          :key="item.path + item.label"
          class="palette-item"
          :class="{ 'is-active': index === activeIndex }"
          @click="choose(item)"
          @mouseenter="activeIndex = index"
        >
          <V2Icon :name="item.icon" />
          <span>
            <b>{{ item.label }}</b>
            <small>{{ item.meta }}</small>
          </span>
          <V2Icon name="arrow" />
        </button>
        <p v-if="!results.length" class="palette-empty">没有匹配结果，试试更短的关键词。</p>
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

interface Item {
  label: string
  meta: string
  icon: string
  path: string
}

const { paletteOpen, paletteQuery, role, roleLabel } = useV2Ui()
const route = useRoute()
const router = useRouter()
const query = paletteQuery
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const courses = ref<Array<{ id: number; name: string; code: string }>>([])

const items = computed<Item[]>(() => {
  const list: Item[] = []
  for (const group of navGroups(role.value)) {
    for (const item of group.items) {
      list.push({ label: item.label, meta: group.label, icon: item.icon, path: item.path })
    }
  }
  const courseId = Number(route.params.id)
  const tabs = courseTabs(role.value)
  for (const tab of tabs) {
    list.push({
      label: tab.label,
      meta: courseId ? '课程页签' : '课程页签（进入课程后可用）',
      icon: tab.icon || 'dashboard',
      path: courseId
        ? `/v2/${role.value}/courses/${courseId}/${tab.id}`
        : `/v2/${role.value}/courses`,
    })
  }
  for (const course of courses.value) {
    list.push({
      label: course.name,
      meta: `${course.code || '课程'} · 进入课程`,
      icon: 'book',
      path: `/v2/${role.value}/courses/${course.id}/overview`,
    })
  }
  return list
})

const results = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return items.value.slice(0, 24)
  return items.value
    .filter((item) => `${item.label}${item.meta}`.toLowerCase().includes(keyword))
    .slice(0, 24)
})

watch(results, () => {
  activeIndex.value = 0
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
  if (!item) return
  close()
  router.push(item.path)
}

function close() {
  paletteOpen.value = false
  query.value = ''
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
onBeforeUnmount(() => window.removeEventListener('keydown', onHotkey))
</script>
