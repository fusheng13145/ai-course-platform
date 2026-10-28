<template>
  <div class="ai-layout">
    <section class="card chat-card">
      <header class="chat-head">
        <div class="chat-id">
          <span class="ai-mini"><V2Icon name="sparkle" /></span>
          <div>
            <b>云枢 AI 助教</b>
            <small><i class="live-dot"></i> RAG 已连接 · {{ roleLabel }}</small>
          </div>
        </div>
        <span class="chip chip-warn">示例回答</span>
      </header>
      <div class="chat-context">
        <span>CONTEXT</span>
        <b>课程上下文</b>
        <b>RAG top-k 5</b>
        <b>教师确认后生效</b>
      </div>
      <div ref="logRef" class="chat-log">
        <div v-for="(message, index) in messages" :key="index" class="msg" :class="message.from">
          <span class="msg-avatar">
            <V2Icon v-if="message.from === 'ai'" name="sparkle" />
            <template v-else>{{ avatar }}</template>
          </span>
          <div class="msg-bubble">
            <span v-if="message.pending" class="typing" aria-label="AI 正在输入">
              <i></i><i></i><i></i>
            </span>
            <span v-else class="msg-text">{{ message.text }}</span>
            <div v-if="message.cites?.length" class="msg-cite">
              <span v-for="cite in message.cites" :key="cite">{{ cite }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="chat-suggest">
        <button v-for="prompt in prompts" :key="prompt" @click="send(prompt)">{{ prompt }}</button>
      </div>
      <form class="chat-form" @submit.prevent="send(input)">
        <input
          v-model="input"
          autocomplete="off"
          placeholder="问课程问题，或让 AI 制定复习计划…"
          aria-label="向 AI 提问"
        />
        <button class="btn btn-primary" type="submit" aria-label="发送">
          <V2Icon name="arrow" />
        </button>
      </form>
      <p class="chat-caveat">
        当前为示例回答：真实 AI 接口已存在，本页尚未接线；引擎不可达时旧版会明确返回 503。
      </p>
    </section>
    <aside class="card side-card ai-side">
      <p class="card-kicker">SOURCES / 引用来源</p>
      <h2>本轮引用</h2>
      <ul class="source-list">
        <li v-for="source in sources" :key="source.title">
          <span class="source-ico"><V2Icon name="file" /></span>
          <span
            ><b>{{ source.title }}</b
            ><small>{{ source.meta }}</small></span
          >
        </li>
      </ul>
      <div class="ai-mode"><span>模式</span><b>RAG + LLM</b><span>失败语义</span><b>503</b></div>
      <p class="caveat">关键结论请回看原文；AI 不替代教师。</p>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import V2Icon from '../../../components/V2Icon.vue'
import { useV2Ui } from '../../../composables/ui'
import { AI_INITIAL, AI_PROMPTS, AI_SOURCES, aiReply } from '../../../data/demo'
import type { V2Role } from '../../../nav'

interface Message {
  from: 'ai' | 'user'
  text: string
  cites?: string[]
  pending?: boolean
}

const props = defineProps<{ role: V2Role }>()
const { avatar, roleLabel } = useV2Ui()
const role = computed(() => props.role)
const initial = AI_INITIAL[role.value]
const prompts = AI_PROMPTS[role.value]
const sources = AI_SOURCES[role.value]
const input = ref('')
const logRef = ref<HTMLElement | null>(null)
const messages = ref<Message[]>([
  { from: 'ai', text: initial.text, cites: initial.cites },
  { from: 'ai', text: initial.followup },
])

async function scrollToEnd() {
  await nextTick()
  if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
}

function send(text: string) {
  const value = String(text || '').trim()
  if (!value) return
  if (messages.value.some((message) => message.pending)) return
  messages.value.push({ from: 'user', text: value })
  messages.value.push({ from: 'ai', text: '', pending: true })
  input.value = ''
  scrollToEnd()
  window.setTimeout(() => {
    const pending = messages.value.find((message) => message.pending)
    if (!pending) return
    pending.pending = false
    pending.text = aiReply(role.value, value)
    pending.cites = sources.slice(0, 2).map((source) => source.title)
    scrollToEnd()
  }, 650)
}
</script>

<style scoped>
.msg-text {
  white-space: pre-line;
}
.typing {
  display: inline-flex;
  gap: 4px;
}
.typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-3);
  animation: v2-typing 1s var(--ease) infinite;
}
.typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.typing i:nth-child(3) {
  animation-delay: 0.3s;
}
</style>
