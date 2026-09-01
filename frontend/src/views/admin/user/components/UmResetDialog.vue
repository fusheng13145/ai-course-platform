<template>
  <Modal narrow @close="emit('close')">
    <template #title>重置密码 · {{ user.username }}</template>
    <template #sub>重置后旧密码立即失效</template>
    <div class="form-grid">
      <div class="field span-2">
        <label>新密码<span class="req">*</span></label
        ><input
          v-model="newPassword"
          class="input"
          type="password"
          placeholder="至少 6 位"
          @keyup.enter="submit()"
        />
      </div>
    </div>
    <template #footer>
      <button class="btn btn-plain" @click="emit('close')">取消</button>
      <button class="btn btn-primary" :disabled="submitting" @click="submit()">
        {{ submitting ? '提交中…' : '确认重置' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { toast } from '@/composables/useToast'
import Modal from '@/components/ui/Modal.vue'

defineProps({
  user: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'submit'])

const newPassword = ref('')

function submit() {
  if (!newPassword.value || newPassword.value.length < 6) {
    toast.show('新密码至少 6 位', 'warning')
    return
  }
  emit('submit', newPassword.value)
}
</script>
