<template>
  <!--
    写回只留 `update:modelValue` 这一条腿（Task 10C 收掉的双转发）。
    改前这里是 `@update:model-value="emit('update:modelValue', $event)"` 加
    `@close="emit('update:modelValue', false)"` 两条并存 —— 同一件"关"的事被写回两次。
    为什么留这条：本页是**受控**调用点（`NotificationManagement.vue` 用 `v-model="drawerOpen"`，
    下面再把值透传给 `:model-value`），v-model 的语义通道就是 `update:modelValue`；
    而 Task 10C 把 Drawer 的 close() 收到 Modal 形状之后，`close` 与 `update:modelValue(false)`
    是**成对发出**的，`@close` 上再挂一次写回等于每次关闭多发一条相同事件。
    `close` 那条腿的受众是非受控调用点（`RagDocs.vue` 的 `v-if` ＋ `@close` 那一型），
    本组件不属这一型，故这里不听它——留着它不接是安全的：Drawer 面板消失由父级把
    `drawerOpen` 落下来驱动，而那条已经由 `update:modelValue` 送达。
  -->
  <Drawer :model-value="modelValue" wide @update:model-value="emit('update:modelValue', $event)">
    <template #title>发布通知</template>
    <template #sub>选择接收对象后由系统批量写入通知</template>
    <div class="form-grid">
      <div class="field span-2">
        <label>通知标题<span class="req">*</span></label
        ><input v-model="sendForm.title" class="input" placeholder="请输入标题" />
      </div>
      <div class="field span-2">
        <label>接收对象</label>
        <div class="radio-cards">
          <div
            v-for="s in scopes"
            :key="s.key"
            class="radio-card"
            :class="{ checked: sendForm.scope === s.key }"
            @click="sendForm.scope = s.key"
          >
            <div class="rc-title">{{ s.label }}</div>
            <div class="rc-desc">{{ s.desc }}</div>
          </div>
        </div>
      </div>
      <div v-if="sendForm.scope === 'custom'" class="field span-2">
        <label>用户 ID 列表（逗号分隔）</label>
        <input v-model="sendForm.customIds" class="input" placeholder="如：1, 2, 3" />
      </div>
      <div class="field">
        <label>分类</label
        ><select v-model="sendForm.category" class="select">
          <option value="SYSTEM">系统公告</option>
          <option value="ASSIGNMENT">作业提醒</option>
          <option value="EXAM">考试通知</option>
        </select>
      </div>
      <div class="field">
        <label>优先级</label
        ><select v-model="sendForm.priority" class="select">
          <option value="LOW">低</option>
          <option value="MEDIUM">中</option>
          <option value="HIGH">高</option>
        </select>
      </div>
      <div class="field span-2">
        <label>正文内容<span class="req">*</span></label
        ><textarea
          v-model="sendForm.content"
          class="textarea send-textarea"
          placeholder="请输入通知正文…"
        ></textarea>
      </div>
    </div>
    <template #footer>
      <button class="btn btn-plain" @click="emit('update:modelValue', false)">取消</button>
      <button class="btn btn-primary" :disabled="sending" @click="doSend()">
        {{ sending ? '发送中…' : '确认发布' }}
      </button>
    </template>
  </Drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Drawer from '@/components/ui/Drawer.vue'
import { toast } from '@/composables/useToast'
import notificationApi from '@/api/notification'
import adminApi from '@/api/admin'

/**
 * 通知公告管理 — 发布通知抽屉。
 * <p>拆分子组件：自包含发送表单与批量发送逻辑（按角色取用户 / 自定义 ID 列表），
 * 发送成功后 emit('published') 通知页面刷新。</p>
 */
defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'published'])

const sending = ref(false)
const sendForm = ref({
  title: '',
  content: '',
  scope: 'teachers',
  customIds: '',
  category: 'SYSTEM',
  priority: 'MEDIUM',
})
const scopes = [
  { key: 'teachers', label: '全体教师', desc: '向所有教师账号发送' },
  { key: 'students', label: '全体学生', desc: '向所有学生账号发送' },
  { key: 'admins', label: '全体管理员', desc: '向所有管理员账号发送' },
  { key: 'custom', label: '指定用户', desc: '输入用户 ID 列表' },
]

async function doSend() {
  const f = sendForm.value
  if (!f.title.trim() || !f.content.trim()) {
    toast.show('请填写标题与正文', 'warning')
    return
  }
  sending.value = true
  try {
    let targetUserIds = []
    if (f.scope === 'custom') {
      targetUserIds = f.customIds
        .split(/[,，\s]+/)
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isFinite(n) && n > 0)
      if (!targetUserIds.length) {
        toast.show('请输入有效的用户 ID', 'warning')
        sending.value = false
        return
      }
    } else {
      const roleMap = { teachers: 'TEACHER', students: 'STUDENT', admins: 'ADMIN' }
      // getAllUsers 已由 fetchAllPages 解包为数组；显式给出 id 字段避免 unknown 流入下游。
      const users = (await adminApi.getAllUsers({ role: roleMap[f.scope] })) as Array<{
        id?: number
      }>
      targetUserIds = users.map((u) => u.id).filter(Boolean)
      if (!targetUserIds.length) {
        toast.show('该角色暂无用户', 'warning')
        sending.value = false
        return
      }
    }
    const sendRes = await notificationApi.sendNotification({
      title: f.title.trim(),
      content: f.content.trim(),
      category: f.category,
      priority: f.priority,
      targetUserIds,
    })
    if (sendRes?.data?.code === 200 || sendRes?.data?.data) {
      toast.show(`已向 ${targetUserIds.length} 个用户发送`, 'ok')
      emit('update:modelValue', false)
      sendForm.value = {
        title: '',
        content: '',
        scope: 'teachers',
        customIds: '',
        category: 'SYSTEM',
        priority: 'MEDIUM',
      }
      emit('published')
    } else {
      toast.show(sendRes?.data?.message || '发送失败', 'error')
    }
  } catch (e) {
    toast.show(e?.response?.data?.message || '发送失败', 'error')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.send-textarea {
  min-height: 140px;
}
</style>
