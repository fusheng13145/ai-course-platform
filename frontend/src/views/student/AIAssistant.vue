<template>
  <div>
    <div class="content-card" style="margin-bottom:20px">
      <div style="display:flex;gap:16px;margin-bottom:16px">
        <el-input v-model="message" type="textarea" :rows="4" placeholder="输入您的问题..." />
      </div>
      <div style="display:flex;gap:8px;margin-bottom:16px">
        <el-button type="primary" :loading="loading" @click="sendMessage">发送</el-button>
        <el-button @click="clearChat">清空对话</el-button>
      </div>
    </div>
    <div class="content-card">
      <div v-if="chatHistory.length === 0" style="text-align:center;padding:40px;color:#909399">开始与AI助手对话吧！</div>
      <div v-for="(msg, i) in chatHistory" :key="i" style="padding:12px 0;border-bottom:1px solid #f0f0f0">
        <div style="font-weight:600;margin-bottom:4px;color:#303133">{{ msg.role === 'user' ? '您' : 'AI助手' }}</div>
        <div style="color:#606266;font-size:14px;white-space:pre-wrap">{{ msg.content }}</div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const message = ref('')
const loading = ref(false)
const chatHistory = ref([])

const mockResponses = {
  "你好": "你好！我是AI学习助手，有什么可以帮助你的吗？",
  "python": "Python是一种广泛使用的编程语言，特别适合数据科学、AI开发和Web开发。",
  "机器学习": "机器学习是AI的一个分支，让计算机从数据中学习模式并做出决策。"
}

function sendMessage() {
  if (!message.value.trim()) return
  const userMsg = message.value.trim()
  chatHistory.value.push({ role: 'user', content: userMsg })
  message.value = ''
  loading.value = true

  setTimeout(() => {
    let reply = "抱歉，我不太理解这个问题。请尝试问一些关于编程、数据结构、算法或AI的问题。"
    for (const [key, resp] of Object.entries(mockResponses)) {
      if (userMsg.includes(key)) { reply = resp; break }
    }
    chatHistory.value.push({ role: 'assistant', content: reply })
    loading.value = false
  }, 800)
}

function clearChat() { chatHistory.value = [] }
</script>