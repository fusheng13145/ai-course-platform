<template>
  <ProfileCard :icon="Setting" title="偏好设置">
    <div class="setting-row">
      <span class="setting-label">主题模式</span>
      <el-radio-group :model-value="theme" size="small" @change="emit('set-theme', $event)">
        <el-radio-button value="light">浅色</el-radio-button>
        <el-radio-button value="dark">深色</el-radio-button>
        <el-radio-button value="system">跟随系统</el-radio-button>
      </el-radio-group>
    </div>
    <div class="setting-row">
      <span class="setting-label">学习提醒</span>
      <el-switch :model-value="notifyOn" @update:model-value="emit('update:notifyOn', $event)" />
    </div>
    <div class="setting-row">
      <span class="setting-label">邮件通知</span>
      <el-switch :model-value="emailOn" @update:model-value="emit('update:emailOn', $event)" />
    </div>
  </ProfileCard>
</template>

<script setup lang="ts">
import { Setting } from '@element-plus/icons-vue'
import ProfileCard from './ProfileCard.vue'

/**
 * 个人中心「偏好设置」区块
 * <p>主题模式（经 set-theme 事件交由 useTheme 处理）与学习提醒/邮件通知开关（v-model 双向）。</p>
 */
defineProps({
  /** 当前主题（light/dark/system） */
  theme: { type: String, default: 'system' },
  /** 学习提醒开关 */
  notifyOn: { type: Boolean, default: true },
  /** 邮件通知开关 */
  emailOn: { type: Boolean, default: false },
})

const emit = defineEmits(['set-theme', 'update:notifyOn', 'update:emailOn'])
</script>

<style scoped>
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--ac-border);
}
.setting-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.setting-label {
  font-size: 13.5px;
  color: var(--ac-text-secondary);
}
</style>
