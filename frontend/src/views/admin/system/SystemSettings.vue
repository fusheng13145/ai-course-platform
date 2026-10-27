<template>
  <div class="fade-in system-settings">
    <PageHeader title="系统设置" subtitle="平台全局配置与参数管理" icon="settings" />

    <div class="settings-container card-base animate-in d1">
      <!-- P4-MIG-07：el-tabs(border-card)→TabGroup（seg 为最近盒型）；五组 pane 内容
           v-show（等价 el-tabs 全挂载隐藏语义），原 :deep EP 内部类样式平移到 .tab-i/.settings-pane -->
      <TabGroup
        :tabs="SETTINGS_TABS"
        :active="activeTab"
        variant="seg"
        class="settings-tabs"
        @change="activeTab = $event"
      />
      <div v-show="activeTab === 'basic'" class="settings-pane">
        <!-- 基础设置 -->
        <SsBasicForm
          :form="forms.basic"
          :rules="basicRules"
          :saving="saving.basic"
          @save="saveSettings('basic')"
        />
      </div>
      <div v-show="activeTab === 'security'" class="settings-pane">
        <!-- 安全设置 -->
        <SsSecurityForm
          :form="forms.security"
          :saving="saving.security"
          @save="saveSettings('security')"
        />
      </div>
      <div v-show="activeTab === 'email'" class="settings-pane">
        <!-- 邮件配置 -->
        <SsEmailForm
          :form="forms.email"
          :saving="saving.email"
          @save="saveSettings('email')"
          @test-email="sendTestEmail"
        />
      </div>
      <div v-show="activeTab === 'upload'" class="settings-pane">
        <!-- 上传配置 -->
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
      </div>
      <div v-show="activeTab === 'notification'" class="settings-pane">
        <!-- 通知设置 -->
        <SsNotificationForm
          :form="forms.notification"
          :saving="saving.notification"
          @save="saveSettings('notification')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import { useSystemSettings } from '@/composables/useSystemSettings'
import SsBasicForm from './components/SsBasicForm.vue'
import SsSecurityForm from './components/SsSecurityForm.vue'
import SsEmailForm from './components/SsEmailForm.vue'
import SsUploadForm from './components/SsUploadForm.vue'
import SsNotificationForm from './components/SsNotificationForm.vue'
import TabGroup from '@/components/ui/TabGroup.vue'

const SETTINGS_TABS = [
  { key: 'basic', label: '基础设置' },
  { key: 'security', label: '安全设置' },
  { key: 'email', label: '邮件配置' },
  { key: 'upload', label: '上传配置' },
  { key: 'notification', label: '通知设置' },
]

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

/* Tabs 样式覆盖（P4-MIG-07：原 :deep(.el-tabs__*) 定制平移到 TabGroup 结构） */
.settings-tabs {
  width: 100%;
  border-radius: 0;
  border-bottom: 1px solid var(--ac-border);
}

.settings-tabs :deep(.tab-i) {
  padding: 12px 24px;
  font-size: 14px;
  color: var(--ac-text-secondary);
  transition: all var(--ac-transition-normal);
}

.settings-tabs :deep(.tab-i.active) {
  color: var(--ac-primary);
  font-weight: 600;
}

.settings-pane {
  padding: var(--ac-spacing-xl);
  max-width: 700px;
}

@media (max-width: 768px) {
  .settings-container {
    overflow-x: auto;
  }

  .settings-pane {
    padding: var(--ac-spacing-md);
  }

  .settings-form .el-form-item__label {
    width: 130px !important;
  }
}
</style>
