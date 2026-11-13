<template>
  <section class="assistant-body">
    <section class="card chat-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">STUDENT ASSISTANT / AI</p>
          <h2>全局 AI 助手</h2>
        </div>
      </header>

      <div ref="logRef" class="chat-log" aria-live="polite">
        <div v-if="!messages.length" class="empty-state compact">
          <b>问我任何学习问题</b>
          <p>
            课程内的提问会基于该课程知识库精准回答；本页是跨课程的全局助手，超纲或不确定时会如实说明。
          </p>
        </div>
        <div
          v-for="(msg, index) in messages"
          :key="String(index)"
          class="chat-row"
          :class="msg.role"
        >
          <span class="chat-who">{{ msg.role === 'user' ? '我' : '助手' }}</span>
          <p class="chat-bubble">{{ msg.text }}</p>
        </div>
        <p v-if="pending" class="hint-inline">助手思考中…</p>
      </div>

      <form class="chat-input-row" @submit.prevent="send">
        <input
          v-model="draft"
          class="input chat-input"
          data-testid="assistant-input"
          placeholder="输入你的问题…"
          aria-label="输入你的问题"
          :disabled="pending"
        />
        <button class="btn btn-primary" type="submit" :disabled="pending || !draft.trim()">
          {{ pending ? '思考中…' : '发送' }}
        </button>
      </form>
      <p v-if="chatError" class="form-error" role="alert">{{ chatError }}</p>
      <p class="hint">AI 回答有不确定性，重要结论请以课程资料为准；响应 503 时为模型通道不可用。</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { qaAsk } from '@/api/ai'
import { unwrap } from '@/utils/response'

interface ChatMsg {
  role: 'user' | 'assistant'
  text: string
}

const messages = ref<ChatMsg[]>([])
const draft = ref('')
const pending = ref(false)
const chatError = ref('')
const logRef = ref<HTMLElement | null>(null)

async function scrollLog() {
  await nextTick()
  logRef.value?.scrollTo({ top: logRef.value.scrollHeight })
}

function answerOf(payload: unknown): string {
  const data = payload as Record<string, unknown> | null
  const raw = (data?.answer ?? data?.data ?? data?.content ?? payload) as unknown
  const text = typeof raw === 'string' ? raw : JSON.stringify(raw ?? '')
  return text || '（模型返回为空）'
}

async function send() {
  const text = draft.value.trim()
  if (!text || pending.value) return
  messages.value.push({ role: 'user', text })
  draft.value = ''
  pending.value = true
  chatError.value = ''
  void scrollLog()
  try {
    // 全局助手不带 courseId：不接课程知识库，模型直接作答（课程内的精准问答走课程空间 AI 页签）
    const payload = unwrap(await qaAsk({ question: text }))
    messages.value.push({ role: 'assistant', text: answerOf(payload) })
  } catch (error) {
    const err = error as {
      code?: number
      message?: string
      response?: { data?: { message?: string } }
    }
    chatError.value = err?.message || err?.response?.data?.message || '请求失败，请稍后重试'
    messages.value.push({ role: 'assistant', text: '（本次回答失败，请重试或换个问法）' })
  } finally {
    pending.value = false
    void scrollLog()
  }
}
</script>

<style scoped>
.assistant-body {
  display: grid;
  gap: 14px;
}
.chat-card {
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  min-height: 480px;
}
.chat-log {
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 14px 16px;
  overflow-y: auto;
  max-height: 420px;
}
.chat-row {
  display: grid;
  gap: 4px;
  max-width: 78%;
}
.chat-row.user {
  justify-self: end;
  text-align: right;
}
.chat-who {
  font-size: 11px;
  opacity: 0.65;
}
.chat-bubble {
  margin: 0;
  padding: 8px 12px;
  border-radius: var(--r-md);
  background: var(--mute-soft);
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
}
.chat-input-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
}
.chat-input {
  flex: 1;
}
</style>
