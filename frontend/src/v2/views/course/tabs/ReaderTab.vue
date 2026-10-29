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
        <button
          class="btn btn-primary"
          :class="{ 'is-done': activeDone }"
          :disabled="!activeChapter || marking || activeDone"
          @click="markDone"
        >
          {{ activeDone ? '已完成' : marking ? '提交中…' : '标记完成' }}
        </button>
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
import learningRecordApi from '@/api/learningRecord'
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
/** 已完成章节：由 POST /learning-records 的成功回执与挂载时的读数共同维护 */
const doneChapterIds = ref(new Set<number>())
const marking = ref(false)

const activeChapter = computed(
  () => chapters.value.find((item) => item.id === activeId.value) || chapters.value[0],
)

const activeIndex = computed(() =>
  chapters.value.findIndex((item) => item.id === activeChapter.value?.id),
)

const activeDone = computed(() => doneChapterIds.value.has(activeChapter.value?.id ?? -1))

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
  const chapter = activeChapter.value
  if (!chapter || marking.value || doneChapterIds.value.has(chapter.id)) return
  const chapterId = chapter.id
  marking.value = true
  // 与旧端 useStudentCourseDetail#markChapterComplete 同口径：章节级 COMPLETE 记录，
  // 字段仅 courseId / chapterId / actionType / duration，不臆造 progress
  learningRecordApi
    .createLearningRecord({
      courseId: props.courseId,
      chapterId,
      actionType: 'COMPLETE',
      duration: 0,
    })
    .then(() => {
      doneChapterIds.value = new Set(doneChapterIds.value).add(chapterId)
      ui.toast('本章已标记完成', 'ok')
    })
    .catch(() => {
      ui.toast('标记完成失败，请稍后重试', 'danger')
    })
    .finally(() => {
      marking.value = false
    })
}

/**
 * 已完成章节的读数：GET /learning-records 返回当前用户全部学习记录，
 * 章节完成态是本课程下 actionType=COMPLETE 的 chapterId 集合。
 * 读数失败只影响「已完成」标记，不影响阅读正文与提交，故静默降级。
 */
async function loadCompletedChapters() {
  try {
    const payload = unwrap(await learningRecordApi.getMyLearningRecords())
    const done = new Set(doneChapterIds.value)
    pickList<Record<string, unknown>>(payload).forEach((row) => {
      if (String(row.actionType ?? '').toUpperCase() !== 'COMPLETE') return
      if (Number(row.courseId) !== props.courseId) return
      const chapterId = Number(row.chapterId)
      if (Number.isFinite(chapterId) && chapterId > 0) done.add(chapterId)
    })
    doneChapterIds.value = done
  } catch {
    // 读数不可达时保持「未完成」态：按钮仍可提交，不冒充已完成
  }
}

onMounted(async () => {
  void loadCompletedChapters()
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
.reader-actions .btn:disabled {
  cursor: default;
  opacity: 0.7;
  transform: none;
}
.reader-actions .btn.is-done {
  background: var(--ok-soft);
  border-color: transparent;
  color: var(--ok);
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
