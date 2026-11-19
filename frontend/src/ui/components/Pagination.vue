<template>
  <div class="pager" role="group" aria-label="分页">
    <button
      class="btn btn-sm"
      type="button"
      aria-label="上一页"
      :disabled="page <= 1 || loading"
      @click="emit('turn', -1)"
    >
      上一页
    </button>
    <button
      class="btn btn-sm"
      type="button"
      aria-label="下一页"
      :disabled="page >= pageCount || nextDisabled || loading"
      @click="emit('turn', 1)"
    >
      下一页
    </button>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    page: number
    pageCount: number
    loading?: boolean
    nextDisabled?: boolean
  }>(),
  { loading: false, nextDisabled: false },
)

const emit = defineEmits<{
  turn: [delta: -1 | 1]
}>()
</script>
