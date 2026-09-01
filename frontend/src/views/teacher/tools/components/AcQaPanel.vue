<template>
  <div class="qa-panel">
    <div ref="msgBox" class="qa-messages">
      <div v-if="messages.length === 0" class="qa-empty">
        <el-icon :size="36"><ChatRound /></el-icon>
        <p>向 AI 提问，获取基于本课程的解答</p>
      </div>
      <div v-for="(m, i) in messages" :key="i" class="qa-msg" :class="m.role">
        <div class="qa-bubble">{{ m.content }}</div>
      </div>
    </div>
    <div class="qa-input">
      <el-input
        v-model="qaInput"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入问题，回车发送…"
        @keydown.enter.exact.prevent="sendQa"
      />
      <el-button type="primary" :loading="qaSending" @click="sendQa">
        <el-icon><Promotion /></el-icon>发送
      </el-button>
    </div>
    <div v-if="sessionId" class="qa-session">
      会话已建立 ·
      <el-link type="danger" :underline="false" @click="resetSession">重置会话</el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatRound, Promotion } from '@element-plus/icons-vue'
import aiApi from '@/api/ai'

const props = defineProps({
  courseId: { type: [Number, String], default: '' },
})

const messages = ref([])
const qaInput = ref('')
const qaSending = ref(false)
const sessionId = ref('')
const msgBox = ref(null)

async function sendQa() {
  const text = qaInput.value.trim()
  if (!text) return
  if (!props.courseId) return ElMessage.warning('请先选择课程')
  messages.value.push({ role: 'user', content: text })
  qaInput.value = ''
  qaSending.value = true
  await scrollMsg()
  try {
    let res
    if (sessionId.value) {
      res = await aiApi.qaContinue({ sessionId: sessionId.value, message: text })
    } else {
      res = await aiApi.qaAsk({ courseId: props.courseId, question: text })
    }
    const body = unwrap(res)
    const data = unwrap(body) || {}
    if (data.sessionId) sessionId.value = data.sessionId
    messages.value.push({ role: 'ai', content: data.answer || '（暂无回答）' })
  } catch (e) {
    logger.error('智能答疑失败', e)
    messages.value.push({ role: 'ai', content: '抱歉，回答失败，请稍后重试。' })
  } finally {
    qaSending.value = false
    await scrollMsg()
  }
}

function resetSession() {
  sessionId.value = ''
  messages.value = []
}

async function scrollMsg() {
  await nextTick()
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

// 切换课程时重置会话
watch(
  () => props.courseId,
  () => resetSession(),
)
</script>

<style scoped>
.qa-panel {
  display: flex;
  flex-direction: column;
  height: 560px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  background: var(--ac-bg-elevated);
  overflow: hidden;
}
.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--ac-surface-2);
}
.qa-empty {
  margin: auto;
  text-align: center;
  color: var(--ac-text-muted);
}
.qa-empty p {
  margin-top: 8px;
  font-size: 13px;
}
.qa-msg {
  display: flex;
}
.qa-msg.user {
  justify-content: flex-end;
}
.qa-msg.ai {
  justify-content: flex-start;
}
.qa-bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.qa-msg.user .qa-bubble {
  background: var(--ac-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.qa-msg.ai .qa-bubble {
  background: var(--ac-bg-elevated);
  color: var(--ac-text-primary);
  border: 1px solid var(--ac-border);
  border-bottom-left-radius: 4px;
}
.qa-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
}
.qa-input .el-input {
  flex: 1;
}
.qa-session {
  padding: 0 12px 10px;
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg-elevated);
}
</style>
