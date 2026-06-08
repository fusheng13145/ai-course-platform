<template>
  <div>
    <div class="content-card" style="margin-bottom:var(--ac-spacing-md)">
      <div class="input-area">
        <el-input v-model="message" type="textarea" :rows="4" placeholder="输入您的问题..." />
      </div>
      <div class="action-buttons">
        <el-button type="primary" :loading="loading" @click="sendMessage">发送</el-button>
        <el-button @click="clearChat">清空对话</el-button>
      </div>
    </div>
    <div class="content-card">
      <div v-if="chatHistory.length === 0" class="empty-chat">
        开始与AI助手对话吧！
      </div>
      <div v-for="(msg, i) in chatHistory" :key="i" class="chat-message">
        <div class="message-author">{{ msg.role === 'user' ? '您' : 'AI助手' }}</div>
        <div class="message-content">{{ msg.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import aiApi from '@/api/ai'

const message = ref('')
const loading = ref(false)
const chatHistory = ref([])

async function sendMessage() {
  if (!message.value.trim()) return
  const userMsg = message.value.trim()
  chatHistory.value.push({ role: 'user', content: userMsg })
  message.value = ''
  loading.value = true

  try {
    const res = await aiApi.aiChat({ message: userMsg, context: '' })
    const reply = res.data?.response || res?.response || 'AI服务暂时不可用'
    chatHistory.value.push({ role: 'assistant', content: reply })
  } catch (e) {
    chatHistory.value.push({ role: 'assistant', content: 'AI服务暂时不可用，请稍后再试。' })
  } finally {
    loading.value = false
  }
}

function clearChat() { chatHistory.value = [] }
</script>

<style scoped>
.input-area {
  margin-bottom: var(--ac-spacing-md);
}

.action-buttons {
  display: flex;
  gap: var(--ac-spacing-sm);
}

.empty-chat {
  text-align: center;
  padding: 40px;
  color: var(--ac-text-secondary);
}

.chat-message {
  padding: 12px 0;
  border-bottom: 1px solid var(--ac-border);
}

.chat-message:last-child {
  border-bottom: none;
}

.message-author {
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--ac-text-primary);
}

.message-content {
  color: var(--ac-text-secondary);
  font-size: 14px;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>