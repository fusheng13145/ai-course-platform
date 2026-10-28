<template>
  <div class="reader-layout">
    <aside class="card chapter-rail">
      <header class="card-head">
        <div>
          <p class="card-kicker">COURSE {{ courseId }}</p>
          <h2>章节</h2>
        </div>
        <span class="chip chip-info">{{ chapters.length }} 节</span>
      </header>
      <div class="chapter-list">
        <button
          v-for="chapter in chapters"
          :key="chapter.id"
          class="chapter-item"
          :class="{ 'is-active': chapter.id === activeId, 'is-child': chapter.depth > 0 }"
          @click="selectChapter(chapter.id)"
        >
          <span>
            <b>{{ chapter.title }}</b>
            <small>{{ chapter.meta }}</small>
          </span>
          <span class="chapter-state chip" :class="chapter.published ? 'chip-ok' : 'chip-mute'">
            {{ chapter.published ? '已发布' : '草稿' }}
          </span>
        </button>
        <p v-if="!chapters.length" class="empty-note">该课程还没有章节。</p>
      </div>
      <p class="hint">{{ sourceNote }}</p>
    </aside>

    <article class="card reader-panel">
      <nav class="breadcrumb-line">课程 / {{ activeChapter?.title || '章节' }}</nav>
      <h2>{{ activeChapter?.title || '章节内容' }}</h2>
      <div class="reader-meta">
        <span>{{ activeChapter?.meta || '课程讲义' }}</span>
        <span>{{ chapters.length }} 个章节</span>
        <span>{{ activeChapter?.published ? '已发布' : '草稿' }}</span>
      </div>
      <!-- eslint-disable-next-line vue/no-v-html -- 章节正文已过 DOMPurify -->
      <div v-if="activeChapter?.html" class="reader-content" v-html="activeChapter.html"></div>
      <template v-else>
        <p v-if="activeChapter?.loading">章节正文加载中…</p>
        <template v-else>
          <p>
            章节正文尚未发布或内容为空。授课教师在课程空间中维护章节与任务点；学生端只展示已发布内容。
          </p>
          <p>
            旧版学习中心（/student/courses/{id}/learning-center）仍可继续使用，本页为新版阅读列。
          </p>
        </template>
      </template>
      <div class="reader-actions">
        <button class="btn btn-ghost" @click="step(-1)">上一节</button>
        <button class="btn btn-primary" @click="markDone">标记完成</button>
      </div>
    </article>

    <aside class="card toc-panel">
      <p class="card-kicker">ON THIS PAGE</p>
      <h2>本节目录</h2>
      <a
        v-for="chapter in chapters.slice(0, 8)"
        :key="chapter.id"
        href="javascript:void(0)"
        @click="selectChapter(chapter.id)"
      >
        {{ chapter.title }}
      </a>
      <div class="toc-note">{{ chapters.length }} 个章节 · 真实数据</div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import chapterApi from '@/api/chapter'
import { pickList, unwrap } from '@/utils/response'
import { useV2Ui } from '../../../composables/ui'

interface ChapterRow {
  id: number
  title: string
  meta: string
  published: boolean
  html: string
  loading: boolean
  depth: number
}

const props = defineProps<{ courseId: number }>()
const ui = useV2Ui()
const chapters = ref<ChapterRow[]>([])
const activeId = ref<number | null>(null)
const sourceNote = ref('接口数据：章节来自 /chapters/course/{id}，正文按需读取 /chapters/{id}。')

const activeChapter = computed(
  () => chapters.value.find((item) => item.id === activeId.value) || chapters.value[0],
)

const activeIndex = computed(() =>
  chapters.value.findIndex((item) => item.id === activeChapter.value?.id),
)

function clean(html: string): string {
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}

function flatten(rows: Array<Record<string, unknown>>, depth = 0): ChapterRow[] {
  const result: ChapterRow[] = []
  rows.forEach((row, index) => {
    const taskCount = Number(row.taskCount ?? 0)
    result.push({
      id: Number(row.id ?? `${depth}-${index}`),
      title: String(row.title ?? `第 ${index + 1} 章`),
      meta:
        depth > 0
          ? '子章节'
          : taskCount
            ? `${taskCount} 个任务点`
            : row.completionRate != null
              ? `完成度 ${row.completionRate}%`
              : '章节',
      published: row.isPublished !== false,
      html: row.content ? clean(String(row.content)) : '',
      loading: false,
      depth,
    })
    const children = row.children
    if (Array.isArray(children) && children.length) {
      result.push(...flatten(children as Array<Record<string, unknown>>, depth + 1))
    }
  })
  return result
}

async function ensureContent(chapter?: ChapterRow) {
  if (!chapter || chapter.html || chapter.loading) return
  chapter.loading = true
  try {
    const payload = unwrap(await chapterApi.getChapterById(chapter.id)) as Record<string, unknown>
    const content = payload?.content
    if (content) chapter.html = clean(String(content))
  } catch {
    ui.toast('章节正文加载失败，请稍后重试', 'danger')
  } finally {
    chapter.loading = false
  }
}

function selectChapter(id: number) {
  activeId.value = id
  ensureContent(chapters.value.find((item) => item.id === id))
}

function step(delta: number) {
  const next = chapters.value[activeIndex.value + delta]
  if (next) selectChapter(next.id)
}

function markDone() {
  ui.toast('演示数据：标记完成尚未接入学习记录接口', 'warn')
}

onMounted(async () => {
  try {
    const payload = unwrap(await chapterApi.getChapters(props.courseId))
    chapters.value = flatten(pickList(payload) as Array<Record<string, unknown>>)
    activeId.value = chapters.value[0]?.id ?? null
    await ensureContent(chapters.value[0])
  } catch {
    chapters.value = []
    sourceNote.value = '章节接口不可达；旧版学习中心仍可使用。'
  }
})
</script>

<style scoped>
.empty-note {
  margin: 12px 0;
  font-size: 10px;
  color: var(--ink-3);
}
.reader-content {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--ink-2);
}
.chapter-item.is-child {
  padding-left: 20px;
}
body[data-app='v2'] .chapter-item {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
body[data-app='v2'] .chapter-item > span:first-child {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
body[data-app='v2'] .chapter-item .chapter-state {
  flex: none;
}
</style>
