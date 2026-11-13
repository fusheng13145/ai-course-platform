<template>
  <Modal
    :open="state.open"
    :title="state.title"
    kicker="CONFIRM / ACTION"
    width="480px"
    :close-on-backdrop="false"
    @update:open="onToggle"
  >
    <p class="confirm-message">{{ state.message }}</p>
    <template #footer>
      <button class="btn btn-ghost" @click="settleConfirm(false)">{{ state.cancelText }}</button>
      <button
        class="btn"
        :class="state.danger ? 'btn-danger' : 'btn-primary'"
        @click="settleConfirm(true)"
      >
        {{ state.confirmText }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import Modal from './Modal.vue'
import { settleConfirm, useConfirm } from '../composables/confirm'

const state = useConfirm()

function onToggle(value: boolean) {
  if (!value) settleConfirm(false)
}
</script>

<style scoped>
.confirm-message {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-line;
}
</style>
