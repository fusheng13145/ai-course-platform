<template>
  <div ref="menu" class="pop-menu" :style="pos" role="menu">
    <div
      v-for="item in items"
      :key="item.label"
      class="menu-item"
      :class="{ danger: !!item.danger }"
      role="menuitem"
      tabindex="0"
      @click="pick(item)"
      @keydown.enter.prevent="pick(item)"
    >
      <!-- 图标取自仓内常量表（layouts/icons.ts），是受控输入；标签一律走文本插值，
           不再动态建 DOM 把整行拼成 HTML 片段 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span class="mi-ico" v-html="iconHtml(item.icon, 14)"></span>
      <span>{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { iconHtml } from '@/layouts/icons'

/**
 * 壳层用户菜单
 * <p>侧栏底部用户区与顶栏头像共用这一份：弹层跟随触发物位置，关闭由本组件负责。
 * 外观类（.pop-menu/.menu-item）取 design-system.css 的全局定义，这里不再抄一份。</p>
 */
const props = defineProps({
  items: { type: Array, required: true },
  anchorRect: { type: Object, required: true },
})
const emit = defineEmits(['closed'])

const ITEM_H = 34
/** 跟随触发物：在下半屏就向上开。改前是恒 top:52px ⇒ 点左下角头像、菜单出现在右上角 */
const pos = (() => {
  const r = props.anchorRect
  const openUp = r.top > window.innerHeight / 2
  const top = openUp
    ? Math.max(8, r.top - 8 - props.items.length * ITEM_H)
    : (r.bottom ?? r.top) + 8
  const left = Math.min(Math.max(8, r.left), window.innerWidth - 200)
  return { top: `${top}px`, left: `${left}px` }
})()

function pick(item) {
  emit('closed')
  item.action?.()
}

const menu = ref(null)

// 监听只随挂载存在、卸载即摘：否则实例没了之后任意一次点击还会 emit 一个没人接的 closed
function onDocMousedown(e) {
  if (!menu.value?.contains(e.target)) emit('closed')
}

function onDocKeydown(e) {
  if (e.key === 'Escape') emit('closed')
}

onMounted(() => {
  document.addEventListener('mousedown', onDocMousedown)
  document.addEventListener('keydown', onDocKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown)
  document.removeEventListener('keydown', onDocKeydown)
})
</script>
