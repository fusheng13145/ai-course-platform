<template>
  <V2Modal
    :open="state.open"
    :title="state.title"
    kicker="CONFIRM / ACTION"
    width="480px"
    :close-on-backdrop="false"
    @update:open="onToggle"
  >
    <p class="confirm-message">{{ state.message }}</p>
    <template #footer>
      <button class="btn btn-ghost" @click="settleV2Confirm(false)">{{ state.cancelText }}</button>
      <button
        class="btn"
        :class="state.danger ? 'btn-danger' : 'btn-primary'"
        @click="settleV2Confirm(true)"
      >
        {{ state.confirmText }}
      </button>
    </template>
  </V2Modal>
</template>

<script setup lang="ts">
import V2Modal from './V2Modal.vue'
import { settleV2Confirm, useV2Confirm } from '../composables/v2Confirm'

const state = useV2Confirm()

function onToggle(value: boolean) {
  if (!value) settleV2Confirm(false)
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
