<template>
  <Teleport to="body">
    <Transition name="ob-fade">
      <div v-if="visible" class="ob-overlay" @click.self="skip">
        <!-- 定位到目标元素旁的气泡（target 存在时）；否则居中卡片 -->
        <div
          class="ob-card"
          :class="{ centered: !targetEl }"
          :style="cardStyle"
          role="dialog"
          aria-label="新用户引导"
        >
          <div class="ob-head">
            <span
              v-for="(_, i) in steps"
              :key="i"
              class="ob-step-dot"
              :class="{ active: i === index }"
            ></span>
            <span class="ob-count">{{ index + 1 }} / {{ steps.length }}</span>
          </div>

          <div class="ob-body">
            <!-- 内部图标常量（受控），无需净化 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div class="ob-icon" v-html="steps[index].icon"></div>
            <h3 class="ob-title">{{ steps[index].title }}</h3>
            <p class="ob-desc">{{ steps[index].desc }}</p>
          </div>

          <div class="ob-foot">
            <button class="ob-btn ghost" type="button" @click="skip">跳过</button>
            <div class="ob-foot-right">
              <button v-if="index > 0" class="ob-btn ghost" type="button" @click="prev">
                上一步
              </button>
              <button v-if="!isLast" class="ob-btn primary" type="button" @click="next">
                下一步
              </button>
              <button v-else class="ob-btn primary" type="button" @click="finish">开始使用</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 新用户引导教程（无第三方依赖实现）。
 *
 * 背景：原规划采用 driver.js 集成 3-5 步引导（见 docs/软件产品路线图.md D5），
 * 受离线依赖安装限制，本组件以原生 Vue + CSS 实现等价能力：
 * - 支持按 CSS 选择器高亮目标元素并定位气泡（fallback 居中卡片）；
 * - 首次进入自动触发，跳过/完成即写入 localStorage，不再自动重复打扰；
 * - 宿主可经组件 ref 调用暴露的 show() 重新唤起（AppShell 用户菜单「新手引导」）；
 * - 三端共用（挂载于 AppShell.vue，admin/teacher/student 均生效）。
 *
 * 「已看过」标记**按账号**记录（2026-10-13 由产品定案）：
 * 键名带当前用户 id，因此同一浏览器**换账号登录会重新看到引导**（每个账号首次登录都看得到），
 * 而同账号退出再登录不再打扰。标记只由本组件读写，登出（stores/auth.ts resetAuthState）
 * 不清除它——清除即等于"换账号"语义被破坏。
 *
 * 未来如需替换为 driver.js：仅需在本组件内换用 driver.js API，对外接口不变。
 */

/** done 标记键前缀；实际键为 `${前缀}:${userId}`（按账号隔离） */
const STORAGE_KEY_PREFIX = 'ai-course:onboarding:done:v1'

const auth = useAuthStore()
/** 当前账号的标记键；userId 缺失（异常态）时退化为 anon 段，保证不越权压其他账号 */
const storageKey = computed(() => `${STORAGE_KEY_PREFIX}:${auth.userId ?? 'anon'}`)

/** 写入「已看过」标记（隐私模式等场景静默降级） */
function markDone() {
  try {
    localStorage.setItem(storageKey.value, '1')
  } catch {
    /* 隐私模式等场景静默降级 */
  }
}

/** 是否已看过当前账号的引导 */
function hasSeen() {
  try {
    return localStorage.getItem(storageKey.value) === '1'
  } catch {
    // 无 localStorage 可用则每次展示
    return false
  }
}

const steps = [
  {
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3 3 7.5 12 12l9-4.5L12 3Z"/><path d="M3 12.5 12 17l9-4.5"/><path d="M3 17 12 21l9-4.5"/></svg>',
    title: '欢迎来到 AI 课程平台',
    desc: '一个融合知识图谱与大语言模型的智能助教系统，覆盖课程学习、智能问答、学情分析与教学管理全流程。',
  },
  {
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>',
    title: '左侧导航栏',
    desc: '所有功能入口都在这条侧边栏：课程、作业、考试、知识图谱、学情分析等，按角色自动适配。',
    target: '.sidebar',
  },
  {
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 6h16M4 12h16M4 18h10"/></svg>',
    title: '顶栏快捷操作',
    desc: '顶栏提供侧边栏折叠、通知中心、主题切换（浅色/深色）与个人菜单，方便快速切换。',
    target: '.topbar',
  },
  {
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/></svg>',
    title: 'AI 智能助手',
    desc: '基于知识图谱 + RAG 的智能问答、学习路径推荐、AI 出题与学情建议，让 AI 深度参与教与学。',
  },
  {
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 6 9 17l-5-5"/></svg>',
    title: '准备就绪',
    desc: '以上就是核心功能概览。随时可在左下角用户菜单里点「新手引导」重新查看本指引。祝学习愉快！',
  },
]

const visible = ref(false)
const index = ref(0)
const targetEl = ref(null)
const pos = ref({ top: 0, left: 0, width: 0, height: 0 })
const cardWidth = 320
const isLast = computed(() => index.value >= steps.length - 1)

const cardStyle = computed(() => {
  if (!targetEl.value) return {}
  const { top, left, width, height } = pos.value
  // 气泡优先放目标下方；视口底部空间不足时放上方
  const gap = 12
  let cardTop = top + height + gap
  if (cardTop + 240 > window.innerHeight && top > 240) {
    cardTop = top - gap - 240
  }
  let cardLeft = left + width / 2 - cardWidth / 2
  cardLeft = Math.max(12, Math.min(cardLeft, window.innerWidth - cardWidth - 12))
  return { top: `${cardTop}px`, left: `${cardLeft}px` }
})

function locate() {
  const step = steps[index.value]
  targetEl.value = null
  if (step?.target) {
    const el = document.querySelector(step.target)
    if (el) {
      targetEl.value = el
      const rect = el.getBoundingClientRect()
      pos.value = { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
      return
    }
  }
  // 无目标或目标不存在：居中展示
  pos.value = { top: 0, left: 0, width: 0, height: 0 }
}

function show() {
  visible.value = true
  index.value = 0
  nextTick(locate)
}

function hide() {
  visible.value = false
}

/** 供外部主动唤起（AppShell 用户菜单「新手引导」）：总是回到第 1 步 */
defineExpose({ show })

function skip() {
  markDone()
  hide()
}

function prev() {
  if (index.value > 0) {
    index.value -= 1
    nextTick(locate)
  }
}

function next() {
  if (index.value < steps.length - 1) {
    index.value += 1
    nextTick(locate)
  } else {
    finish()
  }
}

function finish() {
  markDone()
  hide()
}

function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') skip()
  if (e.key === 'ArrowRight') next()
  if (e.key === 'ArrowLeft') prev()
}

onMounted(() => {
  // 监听器无条件注册：已跳过的用户经菜单重开时同样需要 Esc/←/→ 与 resize 定位
  window.addEventListener('resize', locate)
  window.addEventListener('keydown', onKeydown)
  // 首次进入触发；当前账号已完成后不再自动打扰（换账号登录会重新看到）
  if (hasSeen()) return
  // 等待首屏渲染稳定后再弹出（避免定位到未布局完成的目标）
  window.setTimeout(show, 600)
})

onUnmounted(() => {
  window.removeEventListener('resize', locate)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.ob-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(10, 14, 24, 0.45);
  backdrop-filter: blur(1px);
}
.ob-card {
  position: fixed;
  width: 320px;
  background: var(--ac-bg-elevated, var(--surface));
  color: var(--ac-text-primary, var(--ink));
  border: 1px solid var(--ac-border, rgba(0, 0, 0, 0.08));
  border-radius: var(--ac-radius-xl, 16px);
  box-shadow: var(--ac-shadow-xl, 0 24px 48px rgba(15, 23, 42, 0.18));
  padding: 18px 18px 14px;
}
.ob-card.centered {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
.ob-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.ob-step-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ac-border-strong, rgba(0, 0, 0, 0.18));
  transition: all 0.22s var(--ease-out, ease);
}
.ob-step-dot.active {
  width: 18px;
  border-radius: 4px;
  background: var(--accent, var(--info));
}
.ob-count {
  font-size: 12px;
  color: var(--ac-text-tertiary, var(--ink-3));
}
.ob-body {
  text-align: center;
  padding: 6px 4px 14px;
}
.ob-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--accent-soft, rgba(37, 99, 168, 0.12));
  color: var(--accent, var(--info));
  margin-bottom: 10px;
}
.ob-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
}
.ob-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ac-text-secondary, var(--ink-2));
}
.ob-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ob-foot-right {
  display: flex;
  gap: 8px;
}
.ob-btn {
  border: none;
  cursor: pointer;
  border-radius: var(--ac-radius-md, 8px);
  padding: 6px 14px;
  font-size: 13px;
  transition: all 0.18s var(--ease-out, ease);
}
.ob-btn.ghost {
  background: transparent;
  color: var(--ac-text-secondary, var(--ink-2));
}
.ob-btn.ghost:hover {
  background: var(--ac-bg-hover, rgba(0, 0, 0, 0.05));
}
.ob-btn.primary {
  background: var(--accent, var(--info));
  color: var(--ac-on-primary, var(--surface));
}
.ob-btn.primary:hover {
  filter: brightness(1.08);
}
.ob-fade-enter-active,
.ob-fade-leave-active {
  transition: opacity 0.25s var(--ease-out, ease);
}
.ob-fade-enter-from,
.ob-fade-leave-to {
  opacity: 0;
}
</style>
