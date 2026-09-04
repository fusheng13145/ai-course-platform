<template>
  <el-form ref="formRef" :model="form" label-width="160px" class="settings-form">
    <el-divider content-position="left"
      ><el-icon><Lock /></el-icon>密码策略</el-divider
    >

    <el-form-item label="最小密码长度" prop="minPasswordLength">
      <el-input-number v-model="form.minPasswordLength" :min="6" :max="32" />
    </el-form-item>

    <el-form-item label="密码复杂度要求" prop="passwordComplexity">
      <el-checkbox-group v-model="form.passwordComplexity">
        <el-checkbox value="uppercase">必须包含大写字母</el-checkbox>
        <el-checkbox value="lowercase">必须包含小写字母</el-checkbox>
        <el-checkbox value="number">必须包含数字</el-checkbox>
        <el-checkbox value="special">必须包含特殊字符</el-checkbox>
      </el-checkbox-group>
    </el-form-item>

    <el-form-item label="密码有效期(天)" prop="passwordExpiryDays">
      <el-input-number v-model="form.passwordExpiryDays" :min="0" :max="365" />
      <span class="form-tip">0表示永不过期</span>
    </el-form-item>

    <el-divider content-position="left"
      ><el-icon><Monitor /></el-icon>登录限制</el-divider
    >

    <el-form-item label="最大登录尝试次数" prop="maxLoginAttempts">
      <el-input-number v-model="form.maxLoginAttempts" :min="3" :max="20" />
    </el-form-item>

    <el-form-item label="锁定时长(分钟)" prop="lockoutDuration">
      <el-input-number v-model="form.lockoutDuration" :min="5" :max="1440" />
    </el-form-item>

    <el-form-item label="会话超时时间(分钟)" prop="sessionTimeout">
      <el-input-number v-model="form.sessionTimeout" :min="15" :max="1440" :step="15" />
    </el-form-item>

    <el-form-item label="启用双因素认证" prop="enable2FA">
      <el-switch v-model="form.enable2FA" />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="saving" @click="handleSave"> 保存安全设置 </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Lock, Monitor } from '@element-plus/icons-vue'

/**
 * 系统设置 — 安全设置表单
 * <p>密码策略 + 登录限制配置；内部校验通过后 emit save。</p>
 */
defineProps({
  /** 安全表单对象（共享引用） */
  form: { type: Object, required: true },
  /** 保存加载态 */
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['save'])

const formRef = ref(null)

async function handleSave() {
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : true
  if (!valid) return
  emit('save')
}
</script>

<style scoped>
.settings-form {
  max-width: 650px;
}

.settings-form .el-divider {
  margin: 24px 0 16px;
  border-color: var(--ac-border);
}

.settings-form .el-divider__text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  background: transparent;
  padding: 0;
}

.form-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: 10px;
}
</style>
