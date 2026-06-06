<template>
  <el-tooltip :content="themeTooltip" placement="bottom" :show-after="300">
    <div class="theme-switcher" @click="cycleTheme">
      <svg v-if="theme === 'light'" class="theme-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/>
        <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
        <line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/>
        <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
      </svg>
      <svg v-else-if="theme === 'dark'" class="theme-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
      </svg>
      <svg v-else class="theme-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
      </svg>
    </div>
  </el-tooltip>
</template>
<script setup>
import { computed } from 'vue'
import { useTheme } from '@/composables/useTheme'
const { theme, cycleTheme } = useTheme()
const themeTooltip = computed(() => {
  const map = { light: '切换暗色', dark: '跟随系统', system: '切换亮色' }
  return `${theme.value === 'light' ? '亮色' : theme.value === 'dark' ? '暗色' : '跟随系统'}模式（点击${map[theme.value]}）`
})
</script>
<style scoped>
.theme-switcher { display:flex; align-items:center; justify-content:center; width:36px; height:36px; border-radius:10px; cursor:pointer; transition:all .25s; color:var(--el-text-color-secondary); }
.theme-switcher:hover { background:var(--el-color-primary-light-9); color:var(--el-color-primary); transform:rotate(15deg); }
.theme-icon { width:18px; height:18px; }
</style>