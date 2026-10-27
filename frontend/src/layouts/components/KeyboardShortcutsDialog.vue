<template>
  <Modal
    :model-value="modelValue"
    title="键盘快捷键"
    width="480px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="ks-list">
      <div v-for="item in SHORTCUTS" :key="item.desc" class="ks-row">
        <span class="ks-keys">
          <kbd v-for="k in item.keyList" :key="k">{{ k }}</kbd>
        </span>
        <span class="ks-desc">{{ item.desc }}</span>
      </div>
    </div>
    <p class="ks-tip">
      单键快捷键（<kbd>/</kbd> 与 <kbd>?</kbd>）在输入框获得焦点时不触发，不会打断输入。
    </p>
  </Modal>
</template>

<script setup lang="ts">
import Modal from '@/components/ui/Modal.vue'

/**
 * 键盘快捷键说明弹层（D2-7 第八批）
 *
 * <p>快捷键体系的「自说明」入口：按 `?` 打开本弹层（再按一次或 Esc 关闭）。
 * 内容与 useKeyboardShortcuts 的实际绑定保持同源口径——新增快捷键时须同步本表。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
})

// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

/** 快捷键清单（keyList 逐枚渲染为 kbd 键帽） */
const SHORTCUTS = [
  { keyList: ['Ctrl / ⌘', 'K'], desc: '聚焦顶栏全局搜索' },
  { keyList: ['/'], desc: '聚焦顶栏全局搜索（输入框外）' },
  { keyList: ['?'], desc: '打开 / 关闭本说明' },
  { keyList: ['Esc'], desc: '关闭当前弹层 / 收起搜索面板' },
  { keyList: ['Tab', 'Shift', 'Tab'], desc: '在可聚焦控件间前后移动' },
  { keyList: ['Enter / Space'], desc: '激活按钮、选择表格行' },
]

defineEmits(['update:modelValue'])
</script>

<style scoped>
.ks-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ks-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ks-keys {
  display: inline-flex;
  gap: 4px;
  flex: 0 0 168px;
  justify-content: flex-end;
}
kbd {
  display: inline-block;
  padding: 2px 7px;
  font-size: 12px;
  font-family: inherit;
  color: var(--ac-text-secondary, #4b5563);
  background: var(--ac-bg-soft, #f3f4f6);
  border: 1px solid var(--ac-border, #e5e7eb);
  border-bottom-width: 2px;
  border-radius: 5px;
  white-space: nowrap;
}
.ks-desc {
  font-size: 13px;
  color: var(--ac-text-primary);
}
.ks-tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
