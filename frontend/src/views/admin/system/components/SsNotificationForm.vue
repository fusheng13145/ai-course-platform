<template>
  <el-form ref="formRef" :model="form" label-width="160px" class="settings-form">
    <el-divider content-position="left"><el-icon><Bell /></el-icon>默认通知方式</el-divider>

    <el-form-item label="站内通知" prop="inAppNotification">
      <el-switch v-model="form.inAppNotification" />
    </el-form-item>

    <el-form-item label="邮件通知" prop="emailNotification">
      <el-switch v-model="form.emailNotification" />
      <span class="form-tip">需先配置邮件服务</span>
    </el-form-item>

    <el-form-item label="短信通知" prop="smsNotification">
      <el-switch v-model="form.smsNotification" disabled />
      <span class="form-tip">暂未开放</span>
    </el-form-item>

    <el-divider content-position="left"><el-icon><Clock /></el-icon>自动提醒</el-divider>

    <el-form-item label="考试前提醒(小时)" prop="examReminderHours">
      <el-input-number
        v-model="form.examReminderHours"
        :min="0"
        :max="168"
        :step="1"
      />
    </el-form-item>

    <el-form-item label="作业截止前提醒" prop="deadlineReminder">
      <el-switch v-model="form.deadlineReminder" />
    </el-form-item>

    <el-form-item label="成绩发布通知" prop="gradePublishNotify">
      <el-switch v-model="form.gradePublishNotify" />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存通知设置
      </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Bell, Clock } from '@element-plus/icons-vue'

/**
 * 系统设置 — 通知设置表单
 * <p>默认通知方式（站内/邮件/短信）+ 自动提醒配置。</p>
 */
defineProps({
  /** 通知表单对象（共享引用） */
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
