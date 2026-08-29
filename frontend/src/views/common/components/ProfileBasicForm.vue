<template>
  <ProfileCard :icon="Postcard" title="基本资料">
    <el-form :model="form" label-position="top" class="pc-form">
      <el-form-item label="用户名">
        <el-input v-model="form.username" disabled />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="form.fullName" placeholder="请输入真实姓名" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" placeholder="name@example.com" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone" placeholder="请输入手机号" />
      </el-form-item>
      <el-button type="primary" :loading="saving" class="pc-submit" @click="emit('save')">
        保存修改
      </el-button>
    </el-form>
  </ProfileCard>
</template>

<script setup>
import { Postcard } from '@element-plus/icons-vue'
import ProfileCard from './ProfileCard.vue'

/**
 * 个人中心「基本资料」区块
 * <p>用户名只读，姓名/邮箱/手机号可编辑，保存动作经 save 事件上抛父组件。</p>
 */
defineProps({
  /** 资料表单对象（username/fullName/email/phone） */
  form: { type: Object, required: true },
  /** 保存中状态 */
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['save'])
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
