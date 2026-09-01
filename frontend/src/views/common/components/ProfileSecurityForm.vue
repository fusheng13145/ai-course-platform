<template>
  <ProfileCard :icon="Lock" title="账号安全">
    <el-form :model="passwordForm" label-position="top" class="pc-form">
      <el-form-item label="当前密码">
        <el-input
          v-model="passwordForm.oldPassword"
          type="password"
          show-password
          placeholder="请输入当前密码"
        />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input
          v-model="passwordForm.newPassword"
          type="password"
          show-password
          placeholder="至少 6 位"
        />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input
          v-model="passwordForm.confirmPassword"
          type="password"
          show-password
          placeholder="再次输入新密码"
        />
      </el-form-item>
      <el-button
        type="primary"
        :loading="changing"
        class="pc-submit"
        @click="emit('change')"
      >
        修改密码
      </el-button>
    </el-form>
  </ProfileCard>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import ProfileCard from './ProfileCard.vue'

/**
 * 个人中心「账号安全」区块
 * <p>修改密码表单（当前密码 / 新密码 / 确认新密码）；
 * 长度与一致性校验在 useProfile.changePassword 内完成。</p>
 */
defineProps({
  /** 密码表单对象（oldPassword/newPassword/confirmPassword） */
  passwordForm: { type: Object, required: true },
  /** 提交中状态 */
  changing: { type: Boolean, default: false },
})

const emit = defineEmits(['change'])
</script>

<style scoped>
.pc-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.pc-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.pc-submit {
  width: 100%;
  margin-top: 4px;
}
</style>
