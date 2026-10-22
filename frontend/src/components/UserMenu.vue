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
 * 外观类（.pop-menu/.menu-item）取 design-system.css 的全局定义，这里不再抄一份；
 * 沿用只管外观不管层级——层级由下面那一条 scoped 例外给出。</p>
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

<style scoped>
/* 层级例外，不是第二份外观抄本：全局 .pop-menu 的 z-index 是 --z-menu(130)，
   会被 toast(160) 与 EP 弹层(2000 起)压在下面——本菜单由壳层常驻渲染，落点正是那一档。
   这里只补这一条属性，外观规则继续沿用全局那份。 */
.pop-menu {
  z-index: var(--z-shell-menu);
}

/* 图标盒：SVG 由 iconHtml 按 14 出图，行内盒负责居中与占位（缺了它图标会撑不满行高、
   与文字基线错位），尺寸与旧实现逐字同值 */
.mi-ico {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
}
</style>
