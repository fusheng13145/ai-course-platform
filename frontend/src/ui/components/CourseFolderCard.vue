<script setup lang="ts">
/**
 * 课程卡（玻璃口袋版）
 *
 * 视觉来自用户提供的 Glass Folder 参考实现，只取「材质 + 张口 + 页卡扇形」三层：
 * - 背板用课程色系（--tone-N）做斜向渐变 + SVG 噪点，代替参考实现里的 canvas 程序化艺术
 *   （那套 domain-warp fBm 每张要算 510×408，课程一多首屏就涩，视觉收益不成比例）。
 * - 前袋是 backdrop-filter 毛玻璃，hover 时轻微 rotateX 张口。
 * - 页卡是该课程的前 3 个章节（真实数据，GET /chapters/course/{id}），
 *   未 hover 时全部藏在前袋后面，hover 才抽出来扇形展开。
 *
 * 交互上刻意不抢点击：点前袋仍然进课程空间，和改动前一致；页卡是 aria-hidden 的纯展示层。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { CourseCard } from '../data/api'
import type { Role } from '../nav'
import { getChapters } from '@/api/chapter'
import { unwrap } from '@/utils/response'

const props = defineProps<{
  course: CourseCard
  role: Role
  dropping?: boolean
}>()

const emit = defineEmits<{
  open: [id: number]
  drop: [course: CourseCard]
}>()

interface Sheet {
  key: string
  no: string
  title: string
}

/** 同一个课程在列表内只拉一次章节；翻页/筛选来回切不重复打接口。 */
const sheetCache = new Map<number, Sheet[]>()
/** 并发上限：列表页一次可能十几张卡，不限流会同时打出十几个章节请求。 */
const CONCURRENCY = 3
let running = 0
const waiting: Array<() => void> = []

function takeSlot(): Promise<void> {
  if (running < CONCURRENCY) {
    running += 1
    return Promise.resolve()
  }
  return new Promise<void>((resolve) => waiting.push(resolve))
}

function releaseSlot() {
  running -= 1
  const next = waiting.shift()
  if (next) {
    running += 1
    next()
  }
}

function toSheets(courseId: number, rows: Record<string, unknown>[]): Sheet[] {
  const published = rows.filter((row) => row.isPublished !== false)
  const picked = published.slice(0, 3)
  if (!picked.length) {
    return [{ key: `${courseId}-empty`, no: '—', title: '本课程暂无章节' }]
  }
  return picked.map((row, index) => ({
    key: `${courseId}-${row.id ?? index}`,
    no: String(index + 1).padStart(2, '0'),
    title: String(row.title ?? row.name ?? '未命名章节'),
  }))
}

const sheets = ref<Sheet[]>([])
const hovered = ref(false)
const host = ref<HTMLElement | null>(null)

const metaText = computed(() => {
  const parts = [props.course.semester, props.course.teacher].filter(Boolean) as string[]
  if (props.role !== 'student' && props.course.students)
    parts.push(`${props.course.students} 名学生`)
  if (!parts.length) parts.push('课程信息')
  return parts.join(' · ')
})

let disposed = false

async function loadSheets() {
  const courseId = props.course.id
  const cached = sheetCache.get(courseId)
  if (cached) {
    sheets.value = cached
    return
  }
  await takeSlot()
  try {
    const payload = unwrap<unknown>(await getChapters(courseId))
    const rows = Array.isArray(payload) ? (payload as Record<string, unknown>[]) : []
    const list = toSheets(courseId, rows)
    sheetCache.set(courseId, list)
    if (disposed) return
    sheets.value = list
  } catch {
    // 章节拉不到不该让整张卡变错误态：退回一页占位，其余信息照常显示。
    if (disposed) return
    sheets.value = [{ key: `${courseId}-fail`, no: '—', title: '章节暂不可用' }]
  } finally {
    releaseSlot()
  }
}

let frame = 0
let px = 0
let py = 0

function writeTilt() {
  frame = 0
  const node = host.value
  if (!node) return
  node.style.setProperty('--cf-px', px.toFixed(3))
  node.style.setProperty('--cf-py', py.toFixed(3))
}

function onEnter() {
  hovered.value = true
}

function onLeave() {
  hovered.value = false
  px = 0
  py = 0
  if (!frame) frame = requestAnimationFrame(writeTilt)
}

function onMove(event: PointerEvent) {
  // 触屏没有 hover 语义，视差只会让手指拖动时画面抖，直接跳过。
  if (event.pointerType === 'touch') return
  const node = host.value
  if (!node) return
  const bounds = node.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  px = ((event.clientX - bounds.left) / bounds.width) * 2 - 1
  py = ((event.clientY - bounds.top) / bounds.height) * 2 - 1
  if (!frame) frame = requestAnimationFrame(writeTilt)
}

onMounted(() => {
  void loadSheets()
})

onUnmounted(() => {
  disposed = true
  if (frame) cancelAnimationFrame(frame)
})
</script>

<template>
  <article
    ref="host"
    class="cf"
    :data-tone="course.tone"
    :class="{ 'cf--open': hovered }"
    @pointerenter="onEnter"
    @pointermove="onMove"
    @pointerleave="onLeave"
  >
    <div class="cf-back" aria-hidden="true">
      <span class="cf-noise"></span>
      <span class="cf-glow"></span>
    </div>

    <div class="cf-sheets" aria-hidden="true">
      <div
        v-for="(sheet, index) in sheets"
        :key="sheet.key"
        class="cf-sheet"
        :data-empty="sheet.no === '—' ? '1' : '0'"
        :style="{ '--i': index }"
      >
        <span class="cf-sheet-no">{{ sheet.no }}</span>
        <span class="cf-sheet-title">{{ sheet.title }}</span>
      </div>
    </div>

    <div class="cf-pad" aria-hidden="true">
      <span class="cf-short">{{ course.short }}</span>
    </div>

    <div class="cf-front">
      <span class="cf-tint" aria-hidden="true"></span>
      <span class="cf-spec" aria-hidden="true"></span>

      <h3 class="cf-title">{{ course.name }}</h3>
      <p class="cf-meta">{{ metaText }}</p>

      <div class="cf-bar"><i :style="{ '--p': `${course.progress}%` }"></i></div>

      <div class="cf-foot">
        <span>{{ course.progress >= 70 ? '进展良好' : '需要关注' }}</span>
        <div class="cf-actions">
          <button
            v-if="role === 'student'"
            class="btn btn-sm btn-ghost"
            :disabled="dropping"
            @click="emit('drop', course)"
          >
            退选
          </button>
          <button class="btn btn-sm" @click="emit('open', course.id)">
            {{ role === 'admin' ? '查看治理' : '进入课程' }}
          </button>
        </div>
      </div>

      <button
        class="cf-hit"
        type="button"
        :aria-label="`打开课程 ${course.name}`"
        @click="emit('open', course.id)"
      ></button>
    </div>
  </article>
</template>

<style scoped>
.cf {
  --cf-px: 0;
  --cf-py: 0;
  --cf-ease: cubic-bezier(0.2, 0.8, 0.2, 1);
  --cf-spring: cubic-bezier(0.34, 1.36, 0.5, 1);
  --cf-pad-h: 60px;
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: calc(var(--cf-pad-h) + 132px);
  border-radius: var(--r-card);
  isolation: isolate;
  /* 页卡要能探出卡片顶部，这里不能裁 */
  overflow: visible;
}

/* hover 时把整卡抬到相邻卡之上，页卡探出边界才不会被同行的姐妹卡盖住 */
.cf:hover,
.cf:focus-within {
  z-index: 5;
}

/* ---------- 背板：课程色系斜向渐变 + 噪点 ---------- */
.cf-back {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: var(--r-card);
  overflow: hidden;
  background: linear-gradient(
    148deg,
    color-mix(in srgb, var(--tone, var(--tone-1)) 62%, var(--tone-ink, var(--tone-1-ink)) 38%) 0%,
    color-mix(in srgb, var(--tone, var(--tone-1)) 88%, var(--tone-ink, var(--tone-1-ink)) 12%) 58%,
    var(--tone, var(--tone-1)) 100%
  );
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--tone-ink, var(--tone-1-ink)) 22%, transparent);
  transition:
    filter 0.7s var(--cf-ease),
    transform 1s var(--cf-ease);
}
.cf[data-tone='1'] {
  --tone: var(--tone-1);
  --tone-ink: var(--tone-1-ink);
}
.cf[data-tone='2'] {
  --tone: var(--tone-2);
  --tone-ink: var(--tone-2-ink);
}
.cf[data-tone='3'] {
  --tone: var(--tone-3);
  --tone-ink: var(--tone-3-ink);
}
.cf[data-tone='4'] {
  --tone: var(--tone-4);
  --tone-ink: var(--tone-4-ink);
}
.cf[data-tone='5'] {
  --tone: var(--tone-5);
  --tone-ink: var(--tone-5-ink);
}

.cf-noise {
  position: absolute;
  inset: 0;
  opacity: 0.055;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23n)'/%3E%3C/svg%3E");
  mix-blend-mode: overlay;
}

.cf-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    120% 90% at calc(78% + var(--cf-px) * 12%) 6%,
    rgba(255, 255, 255, 0.55),
    rgba(255, 255, 255, 0) 62%
  );
  transition: background 0.6s var(--cf-ease);
}

/* ---------- 露出区：只留课程缩写 ---------- */
.cf-pad {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: var(--cf-pad-h);
  z-index: 2;
  display: flex;
  align-items: flex-start;
  padding: 11px 14px 0;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--tone-ink, var(--tone-1-ink));
  opacity: 0.9;
  transition: opacity 0.4s var(--cf-ease);
}
.cf--open .cf-pad {
  opacity: 0.35;
}

/* ---------- 页卡：章节 ---------- */
.cf-sheets {
  position: absolute;
  left: 50%;
  top: 10px;
  z-index: 1;
  width: 216px;
  margin-left: -108px;
  height: 0;
  pointer-events: none;
}

.cf-sheet {
  position: absolute;
  left: 0;
  top: 0;
  width: 216px;
  height: 62px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 12px 0 10px;
  border-radius: var(--r-md);
  background: linear-gradient(180deg, #ffffff 0%, #f5f5f3 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    0 0 0 0.5px rgba(0, 0, 0, 0.09),
    0 10px 22px -10px rgba(10, 12, 20, 0.34);
  color: #18181b;
  transform-origin: 50% 100%;
  /* 默认整页藏在前袋后面，只留一条边 */
  transform: translate(calc((var(--i) - 1) * 6px), calc(44px + var(--i) * 3px)) rotate(0deg)
    scale(0.95);
  opacity: 0;
  transition:
    transform 0.62s var(--cf-spring) calc(var(--i) * 45ms),
    opacity 0.4s var(--cf-ease) calc(var(--i) * 30ms);
  will-change: transform;
}

/* 页卡左侧色条：说明这一页属于当前课程，不是一个通用占位 */
.cf-sheet::before {
  content: '';
  flex: none;
  width: 3px;
  height: 26px;
  border-radius: var(--r-chip);
  background: var(--tone-ink, var(--tone-1-ink));
  opacity: 0.6;
}

.cf--open .cf-sheet {
  opacity: 1;
  transform: translate(
      calc((var(--i) - 1) * 50px + var(--cf-px) * 4px),
      calc(-19px + var(--i) * -6px)
    )
    rotate(calc((var(--i) - 1) * 2.8deg + var(--cf-py) * 0.8deg)) scale(1);
}

.cf-sheet-no {
  flex: none;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
  color: rgba(24, 24, 27, 0.38);
}
.cf-sheet-title {
  min-width: 0;
  font-size: 11px;
  line-height: 1.3;
  font-weight: 500;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}
.cf-sheet[data-empty='1'] {
  background: linear-gradient(180deg, #fbfbfa 0%, #f1f1ef 100%);
  border: 1px dashed rgba(24, 24, 27, 0.16);
  box-shadow: 0 6px 16px -10px rgba(10, 12, 20, 0.24);
}
.cf-sheet[data-empty='1'] .cf-sheet-title {
  color: rgba(24, 24, 27, 0.45);
  font-weight: 400;
}

/* 暗色下页卡改成深纸：否则三张纯白纸从暗口袋里抽出来会过曝 */
body[data-theme='dark'] .cf-sheet {
  background: linear-gradient(180deg, #2c2c31 0%, #1e1e22 100%);
  color: #f2f2f4;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.14),
    0 0 0 0.5px rgba(255, 255, 255, 0.1),
    0 12px 26px -10px rgba(0, 0, 0, 0.7);
}
body[data-theme='dark'] .cf-sheet-no {
  color: rgba(242, 242, 244, 0.38);
}
body[data-theme='dark'] .cf-sheet[data-empty='1'] {
  background: linear-gradient(180deg, #232327 0%, #1a1a1e 100%);
  border-color: rgba(255, 255, 255, 0.14);
}
body[data-theme='dark'] .cf-sheet[data-empty='1'] .cf-sheet-title {
  color: rgba(242, 242, 244, 0.45);
}

/* ---------- 前袋：毛玻璃 ---------- */
.cf-front {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  top: var(--cf-pad-h);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px 13px 13px;
  border-radius: var(--r-card);
  overflow: hidden;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--surface) 38%, transparent) 0%,
    color-mix(in srgb, var(--surface) 82%, transparent) 42%,
    var(--surface) 100%
  );
  -webkit-backdrop-filter: blur(18px) saturate(1.35);
  backdrop-filter: blur(18px) saturate(1.35);
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, #fff 70%, transparent),
    0 -1px 0 color-mix(in srgb, var(--tone-ink, var(--tone-1-ink)) 12%, transparent),
    0 8px 22px -14px rgba(10, 12, 20, 0.3);
  transform-origin: 50% 100%;
  transform: perspective(900px) rotateX(0deg) translateY(0);
  transition:
    transform 0.72s var(--cf-spring),
    box-shadow 0.5s var(--cf-ease);
}

.cf--open .cf-front {
  transform: perspective(900px) rotateX(-5.5deg) translateY(2px);
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, #fff 80%, transparent),
    0 -1px 0 color-mix(in srgb, var(--tone-ink, var(--tone-1-ink)) 16%, transparent),
    0 14px 30px -16px rgba(10, 12, 20, 0.38);
}

.cf-tint {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    120% 70% at calc(30% + var(--cf-px) * 22%) calc(8% + var(--cf-py) * 16%),
    color-mix(in srgb, #fff 60%, transparent),
    transparent 62%
  );
  opacity: 0.7;
}
.cf-spec {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(
    105deg,
    transparent 38%,
    color-mix(in srgb, #fff 45%, transparent) 50%,
    transparent 62%
  );
  transform: translateX(calc(-30% + var(--cf-px) * 26%));
  opacity: 0.55;
  transition: transform 0.7s var(--cf-ease);
}

.cf-title {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.35;
  font-weight: 500;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cf-meta {
  margin: 0;
  font-size: 8px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cf-bar {
  height: 3px;
  margin: 2px 0 1px;
  border-radius: var(--r-chip);
  background: color-mix(in srgb, var(--tone-ink, var(--tone-1-ink)) 14%, transparent);
  overflow: hidden;
}
.cf-bar i {
  display: block;
  height: 100%;
  width: var(--p);
  border-radius: var(--r-card);
  background: var(--tone-ink, var(--tone-1-ink));
  transition: width 0.7s var(--cf-ease);
}
.cf-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: auto;
  font-size: 8.5px;
  color: var(--ink-3);
}
.cf-actions {
  display: flex;
  gap: 5px;
}

/* 整块前袋可点，但按钮要压在上面且不被覆盖 */
.cf-hit {
  all: unset;
  position: absolute;
  inset: 0;
  z-index: 0;
  cursor: pointer;
}
.cf-hit:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
  border-radius: var(--r-card);
}
.cf-title,
.cf-meta,
.cf-bar,
.cf-foot {
  position: relative;
  z-index: 1;
  pointer-events: none;
}
.cf-foot .btn {
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .cf-back,
  .cf-sheet,
  .cf-front,
  .cf-spec,
  .cf-pad {
    transition-duration: 0s !important;
    transition-delay: 0s !important;
  }
  .cf--open .cf-front {
    transform: none;
  }
}
</style>
