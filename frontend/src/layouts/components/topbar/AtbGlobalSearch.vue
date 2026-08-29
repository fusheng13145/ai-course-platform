<template>
  <!-- 全局搜索（知识点/题目/课程/笔记） -->
  <div class="global-search">
    <svg
      class="gs-ico"
      viewBox="0 0 24 24"
      width="14"
      height="14"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m21 21-4.35-4.35" />
    </svg>
    <input
      v-model="searchKeyword"
      class="gs-input"
      type="text"
      placeholder="搜索知识点 / 题目 / 课程…"
      autocomplete="off"
      @input="onSearchInput"
      @focus="onSearchFocus"
      @keydown.enter.prevent="goFirstResult"
      @keydown.esc="closeSearch"
      @blur="onSearchBlur"
    />
    <div v-if="searchPanelOpen" class="search-panel">
      <template v-if="searchLoading"><div class="sp-empty">搜索中…</div></template>
      <template v-else-if="searchResults.length">
        <div
          v-for="(r, ri) in searchResults"
          :key="`${r.type}-${r.id}-${ri}`"
          class="sp-item"
          @mousedown.prevent="goResult(r)"
        >
          <span class="sp-type" :class="`t-${r.type}`">{{ typeLabel(r.type) }}</span>
          <div class="sp-main">
            <div class="sp-title">{{ r.title }}</div>
            <div v-if="r.snippet" class="sp-snippet">{{ r.snippet }}</div>
          </div>
          <span class="sp-arrow">›</span>
        </div>
      </template>
      <template v-else><div class="sp-empty">未找到相关内容</div></template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchAll } from '@/api/search'

const props = defineProps({
  role: { type: String, required: true },
})

const router = useRouter()

const searchKeyword = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
const searchPanelOpen = ref(false)
let searchTimer = null
let searchBlurTimer = null

function typeLabel(type) {
  return { knowledge: '知识', question: '题目', course: '课程', note: '笔记' }[type] || type
}

function onSearchInput() {
  window.clearTimeout(searchTimer)
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    searchPanelOpen.value = false
    return
  }
  searchTimer = window.setTimeout(async () => {
    searchLoading.value = true
    searchPanelOpen.value = true
    try {
      searchResults.value = await searchAll(kw)
    } catch {
      searchResults.value = []
    } finally {
      searchLoading.value = false
    }
  }, 300)
}

function onSearchFocus() {
  window.clearTimeout(searchBlurTimer)
  if (searchKeyword.value.trim()) searchPanelOpen.value = true
}

function onSearchBlur() {
  searchBlurTimer = window.setTimeout(() => {
    searchPanelOpen.value = false
  }, 160)
}

function closeSearch() {
  searchPanelOpen.value = false
  searchKeyword.value = ''
  searchResults.value = []
}

function routeFor(r) {
  const role = props.role
  const id = r.id
  switch (r.type) {
    case 'course':
      if (role === 'TEACHER') return `/teacher/courses/${id}`
      if (role === 'ADMIN') return '/admin/courses'
      return `/student/courses/${id}/overview`
    case 'knowledge':
      return '/student/knowledge-graph'
    case 'question':
      if (role === 'ADMIN') return '/admin/questions'
      if (role === 'TEACHER') return '/teacher/courses'
      return '/student/exams'
    case 'note':
    default:
      return '/student/dashboard'
  }
}

function goResult(r) {
  router.push(routeFor(r))
  closeSearch()
}

function goFirstResult() {
  if (searchResults.value.length) goResult(searchResults.value[0])
}
</script>

<style scoped>
.global-search {
  position: relative;
  display: flex;
  align-items: center;
  width: 240px;
  margin-right: 8px;
}
.gs-ico {
  position: absolute;
  left: 10px;
  color: var(--ac-text-tertiary, #98a2b3);
  pointer-events: none;
}
.gs-input {
  width: 100%;
  height: 32px;
  padding: 0 12px 0 30px;
  font-size: 13px;
  color: var(--ac-text-primary, #1f2430);
  background: var(--ac-bg-subtle, rgba(0, 0, 0, 0.04));
  border: 1px solid transparent;
  border-radius: var(--ac-radius-md, 8px);
  outline: none;
  transition: all 0.2s var(--ease-out, ease);
}
.gs-input::placeholder {
  color: var(--ac-text-tertiary, #98a2b3);
}
.gs-input:focus {
  border-color: var(--accent, #2563a8);
  background: var(--ac-bg-elevated, #fff);
  box-shadow: 0 0 0 3px var(--accent-soft, rgba(37, 99, 168, 0.14));
}
.search-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 360px;
  max-height: 380px;
  overflow-y: auto;
  background: var(--ac-bg-elevated, #fff);
  border: 1px solid var(--ac-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--ac-radius-xl, 14px);
  box-shadow: var(--ac-shadow-xl, 0 24px 48px rgba(15, 23, 42, 0.18));
  padding: 6px;
  z-index: 2000;
}
.sp-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--ac-radius-md, 8px);
  cursor: pointer;
}
.sp-item:hover {
  background: var(--ac-bg-hover, rgba(0, 0, 0, 0.05));
}
.sp-type {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 6px;
  background: var(--ac-bg-subtle, rgba(0, 0, 0, 0.05));
  color: var(--ac-text-secondary, #4b5565);
}
.sp-type.t-knowledge {
  background: var(--accent-soft, rgba(37, 99, 168, 0.12));
  color: var(--accent, #2563a8);
}
.sp-type.t-course {
  background: var(--ok-soft, rgba(46, 125, 79, 0.12));
  color: var(--ok, #2e7d4f);
}
.sp-type.t-question {
  background: var(--warn-soft, rgba(255, 165, 0, 0.14));
  color: var(--warn, #b26a00);
}
.sp-type.t-note {
  background: var(--info-soft, rgba(64, 118, 200, 0.12));
  color: var(--info, #4076c8);
}
.sp-main {
  flex: 1;
  min-width: 0;
}
.sp-title {
  font-size: 13px;
  color: var(--ac-text-primary, #1f2430);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sp-snippet {
  font-size: 12px;
  color: var(--ac-text-tertiary, #98a2b3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sp-arrow {
  color: var(--ac-text-tertiary, #98a2b3);
}
.sp-empty {
  padding: 18px 10px;
  text-align: center;
  font-size: 13px;
  color: var(--ac-text-tertiary, #98a2b3);
}

@media (max-width: 768px) {
  .global-search {
    width: 132px;
    margin-right: 4px;
  }
  .search-panel {
    width: min(360px, calc(100vw - 24px));
    right: -8px;
  }
}
</style>
