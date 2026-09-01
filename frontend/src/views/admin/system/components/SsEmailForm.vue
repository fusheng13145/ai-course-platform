<template>
  <el-form ref="formRef" :model="form" label-width="150px" class="settings-form">
    <el-alert
      title="配置完成后可使用「发送测试邮件」验证连通性"
      type="info"
      show-icon
      :closable="false"
      class="alert-mb"
    />

    <el-form-item label="SMTP服务器地址" prop="smtpHost">
      <el-input v-model="form.smtpHost" placeholder="例如: smtp.example.com" />
    </el-form-item>

    <el-form-item label="SMTP端口" prop="smtpPort">
      <el-input-number v-model="form.smtpPort" :min="1" :max="65535" class="w-200" />
      <span class="form-tip ml-8">常用: 25, 465, 587</span>
    </el-form-item>

    <el-form-item label="发件人邮箱" prop="senderEmail">
      <el-input v-model="form.senderEmail" placeholder="noreply@example.com" />
    </el-form-item>

    <el-form-item label="发件人名称" prop="senderName">
      <el-input v-model="form.senderName" placeholder="AI课程平台" />
    </el-form-item>

    <el-form-item label="用户名/授权码" prop="username">
      <el-input
        v-model="form.username"
        placeholder="SMTP登录凭据"
        type="password"
        show-password
      />
    </el-form-item>

    <el-form-item label="启用SSL/TLS" prop="useSSL">
      <el-switch
        v-model="form.useSSL"
        active-text="SSL"
        inactive-text="TLS/无加密"
      />
    </el-form-item>

    <el-form-item>
      <el-space>
        <el-button type="primary" :loading="saving" @click="handleSave"
          >保存邮件配置</el-button
        >
        <el-button @click="$emit('test-email')">发送测试邮件</el-button>
      </el-space>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * 系统设置 — 邮件配置表单
 * <p>SMTP 服务器/端口/凭据/SSL 配置 + 测试邮件入口。</p>
 */
defineProps({
  /** 邮件表单对象（共享引用） */
  form: { type: Object, required: true },
  /** 保存加载态 */
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['save', 'test-email'])

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

.alert-mb {
  margin-bottom: 20px;
}

.w-200 {
  width: 200px;
}

.ml-8 {
  margin-left: 8px;
}
</style>
