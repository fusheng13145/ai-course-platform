<template>
  <div class="fade-in system-settings">
    <PageHeader title="系统设置" subtitle="平台全局配置与参数管理" icon="settings" />

    <div class="settings-container card-base animate-in d1">
      <el-tabs v-model="activeTab" type="border-card" class="settings-tabs">
        <!-- 基础设置 -->
        <el-tab-pane label="基础设置" name="basic">
          <SsBasicForm :form="forms.basic" :rules="basicRules" :saving="saving.basic" @save="saveSettings('basic')" />
        </el-tab-pane>

        <!-- 安全设置 -->
        <el-tab-pane label="安全设置" name="security">
          <SsSecurityForm :form="forms.security" :saving="saving.security" @save="saveSettings('security')" />
        </el-tab-pane>

        <!-- 邮件配置 -->
        <el-tab-pane label="邮件配置" name="email">
          <SsEmailForm :form="forms.email" :saving="saving.email" @save="saveSettings('email')" @test-email="sendTestEmail" />
        </el-tab-pane>

        <!-- 上传配置 -->
        <el-tab-pane label="上传配置" name="upload">
          <SsUploadForm
            :form="forms.upload"
            :input-visible="inputVisible"
            :input-value="inputValue"
            :show-input="showInput"
            :confirm-format="confirmFormat"
            :remove-format="removeFormat"
            :saving="saving.upload"
            @save="saveSettings('upload')"
          />
        </el-tab-pane>

        <!-- 通知设置 -->
        <el-tab-pane label="通知设置" name="notification">
          <SsNotificationForm
            :form="forms.notification"
            :saving="saving.notification"
            @save="saveSettings('notification')"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
import { useSystemSettings } from '@/composables/useSystemSettings'
import SsBasicForm from './components/SsBasicForm.vue'
import SsSecurityForm from './components/SsSecurityForm.vue'
import SsEmailForm from './components/SsEmailForm.vue'
import SsUploadForm from './components/SsUploadForm.vue'
import SsNotificationForm from './components/SsNotificationForm.vue'

/**
 * 系统设置页（页面壳）
 * <p>五组配置 Tab 编排（基础/安全/邮件/上传/通知），业务逻辑委托 useSystemSettings。</p>
 */
const {
  activeTab,
  saving,
  forms,
  basicRules,
  inputVisible,
  inputValue,
  saveSettings,
  sendTestEmail,
  showInput,
  confirmFormat,
  removeFormat,
} = useSystemSettings()
</script>

<style scoped>
.system-settings {
  max-width: 1000px;
  margin: 0 auto;
}

/* 设置容器 */
.settings-container {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  overflow: hidden;
}

/* Tabs 样式覆盖 */
.settings-tabs {
  --el-border-color: var(--ac-border);
}

.settings-tabs :deep(.el-tabs__header) {
  background: var(--ac-bg);
  margin: 0;
  border-bottom: 1px solid var(--ac-border);
}

.settings-tabs :deep(.el-tabs__item) {
  padding: 12px 24px;
  font-size: 14px;
  color: var(--ac-text-secondary);
  transition: all var(--ac-transition-normal);
}

.settings-tabs :deep(.el-tabs__item.is-active) {
  color: var(--ac-primary);
  font-weight: 600;
}

.settings-tabs :deep(.el-tabs__content) {
  padding: var(--ac-spacing-xl);
}

.settings-tabs :deep(.el-tab-pane) {
  max-width: 700px;
}

@media (max-width: 768px) {
  .settings-container {
    overflow-x: auto;
  }

  .settings-tabs :deep(.el-tabs__content) {
    padding: var(--ac-spacing-md);
  }

  .settings-form .el-form-item__label {
    width: 130px !important;
  }
}
</style>
