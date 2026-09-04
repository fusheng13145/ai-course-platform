<template>
  <Modal narrow @close="emit('close')">
    <template #title>删除用户</template>
    <template #sub>删除后不可恢复，请谨慎操作</template>
    <div class="confirm-text">
      确认删除用户「{{ user.fullName || user.username }}」（账号
      {{ user.username }}）吗？其选课与关联数据将一并删除。
    </div>
    <template #footer>
      <button class="btn btn-plain" @click="emit('close')">取消</button>
      <button class="btn btn-primary" :disabled="deleting" @click="emit('confirm')">
        {{ deleting ? '删除中…' : '确认删除' }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import Modal from '@/components/ui/Modal.vue'

defineProps({
  user: { type: Object, default: null },
  deleting: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'confirm'])
</script>
