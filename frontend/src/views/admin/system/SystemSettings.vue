<template>
  <div class="fade-in system-settings">
    <PageHeader title="系统设置" subtitle="平台全局配置与参数管理" icon="settings" />

    <div class="settings-container card-base animate-in d1">
      <el-tabs v-model="activeTab" type="border-card" class="settings-tabs">
        <!-- 基础设置 -->
        <el-tab-pane label="基础设置" name="basic">
          <el-form
            ref="basicFormRef"
            :model="forms.basic"
            label-width="140px"
            :rules="basicRules"
            class="settings-form"
          >
            <el-divider content-position="left"><el-icon><Setting /></el-icon>站点信息</el-divider>

            <el-form-item label="站点名称" prop="siteName">
              <el-input v-model="forms.basic.siteName" placeholder="请输入站点名称" />
            </el-form-item>

            <el-form-item label="站点描述" prop="siteDescription">
              <el-input v-model="forms.basic.siteDescription" type="textarea" :rows="3" placeholder="站点描述信息" />
            </el-form-item>

            <el-form-item label="站点Logo URL" prop="logoUrl">
              <el-input v-model="forms.basic.logoUrl" placeholder="Logo图片地址" />
            </el-form-item>

            <el-form-item label="备案号" prop="icpNumber">
              <el-input v-model="forms.basic.icpNumber" placeholder="ICP备案号（可选）" />
            </el-form-item>

            <el-divider content-position="left"><el-icon><User /></el-icon>用户相关</el-divider>

            <el-form-item label="允许注册" prop="allowRegister">
              <el-switch v-model="forms.basic.allowRegister" active-text="开启" inactive-text="关闭" />
              <span class="form-tip">关闭后新用户无法自行注册</span>
            </el-form-item>

            <el-form-item label="默认角色" prop="defaultRole">
              <el-select v-model="forms.basic.defaultRole" class="w-200">
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师" value="TEACHER" />
              </el-select>
            </el-form-item>

            <el-form-item label="每页显示条数" prop="pageSize">
              <el-input-number v-model="forms.basic.pageSize" :min="5" :max="100" :step="5" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving.basic" @click="saveSettings('basic')">
                保存基础设置
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 安全设置 -->
        <el-tab-pane label="安全设置" name="security">
          <el-form ref="securityFormRef" :model="forms.security" label-width="160px" class="settings-form">
            <el-divider content-position="left"><el-icon><Lock /></el-icon>密码策略</el-divider>

            <el-form-item label="最小密码长度" prop="minPasswordLength">
              <el-input-number v-model="forms.security.minPasswordLength" :min="6" :max="32" />
            </el-form-item>

            <el-form-item label="密码复杂度要求" prop="passwordComplexity">
              <el-checkbox-group v-model="forms.security.passwordComplexity">
                <el-checkbox value="uppercase">必须包含大写字母</el-checkbox>
                <el-checkbox value="lowercase">必须包含小写字母</el-checkbox>
                <el-checkbox value="number">必须包含数字</el-checkbox>
                <el-checkbox value="special">必须包含特殊字符</el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="密码有效期(天)" prop="passwordExpiryDays">
              <el-input-number v-model="forms.security.passwordExpiryDays" :min="0" :max="365" />
              <span class="form-tip">0表示永不过期</span>
            </el-form-item>

            <el-divider content-position="left"><el-icon><Monitor /></el-icon>登录限制</el-divider>

            <el-form-item label="最大登录尝试次数" prop="maxLoginAttempts">
              <el-input-number v-model="forms.security.maxLoginAttempts" :min="3" :max="20" />
            </el-form-item>

            <el-form-item label="锁定时长(分钟)" prop="lockoutDuration">
              <el-input-number v-model="forms.security.lockoutDuration" :min="5" :max="1440" />
            </el-form-item>

            <el-form-item label="会话超时时间(分钟)" prop="sessionTimeout">
              <el-input-number v-model="forms.security.sessionTimeout" :min="15" :max="1440" :step="15" />
            </el-form-item>

            <el-form-item label="启用双因素认证" prop="enable2FA">
              <el-switch v-model="forms.security.enable2FA" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving.security" @click="saveSettings('security')">
                保存安全设置
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 邮件配置 -->
        <el-tab-pane label="邮件配置" name="email">
          <el-form ref="emailFormRef" :model="forms.email" label-width="150px" class="settings-form">
            <el-alert title="配置完成后可使用「发送测试邮件」验证连通性" type="info" show-icon :closable="false" class="alert-mb" />

            <el-form-item label="SMTP服务器地址" prop="smtpHost">
              <el-input v-model="forms.email.smtpHost" placeholder="例如: smtp.example.com" />
            </el-form-item>

            <el-form-item label="SMTP端口" prop="smtpPort">
              <el-input-number v-model="forms.email.smtpPort" :min="1" :max="65535" class="w-200" />
              <span class="form-tip ml-8">常用: 25, 465, 587</span>
            </el-form-item>

            <el-form-item label="发件人邮箱" prop="senderEmail">
              <el-input v-model="forms.email.senderEmail" placeholder="noreply@example.com" />
            </el-form-item>

            <el-form-item label="发件人名称" prop="senderName">
              <el-input v-model="forms.email.senderName" placeholder="AI课程平台" />
            </el-form-item>

            <el-form-item label="用户名/授权码" prop="username">
              <el-input v-model="forms.email.username" placeholder="SMTP登录凭据" type="password" show-password />
            </el-form-item>

            <el-form-item label="启用SSL/TLS" prop="useSSL">
              <el-switch v-model="forms.email.useSSL" active-text="SSL" inactive-text="TLS/无加密" />
            </el-form-item>

            <el-form-item>
              <el-space>
                <el-button type="primary" :loading="saving.email" @click="saveSettings('email')">保存邮件配置</el-button>
                <el-button @click="sendTestEmail">发送测试邮件</el-button>
              </el-space>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 上传配置 -->
        <el-tab-pane label="上传配置" name="upload">
          <el-form ref="uploadFormRef" :model="forms.upload" label-width="160px" class="settings-form">
            <el-divider content-position="left"><el-icon><Upload /></el-icon>文件大小限制</el-divider>

            <el-form-item label="单文件最大(MB)" prop="maxFileSize">
              <el-slider v-model="forms.upload.maxFileSize" :min="1" :max="100" :step="1" show-input input-size="small" class="w-400" />
            </el-form-item>

            <el-form-item label="总存储配额(GB)" prop="totalQuota">
              <el-input-number v-model="forms.upload.totalQuota" :min="1" :max="1000" />
            </el-form-item>

            <el-divider content-position="left"><el-icon><Document /></el-icon>文件格式限制</el-divider>

            <el-form-item label="允许的图片格式" prop="imageFormats">
              <el-tag
                v-for="fmt in forms.upload.imageFormats"
                :key="fmt"
                closable
                class="format-tag"
                @close="removeFormat('image', fmt)"
              >{{ fmt }}</el-tag>
              <el-input
                v-if="inputVisible.image"
                v-model="inputValue.image"
                size="small"
                class="w-100"
                @keyup.enter="confirmFormat('image')"
                @blur="confirmFormat('image')"
              />
              <el-button
                v-else
                size="small"
                @click="showInput('image')"
              ><el-icon><Plus /></el-icon></el-button>
            </el-form-item>

            <el-form-item label="允许的文档格式" prop="docFormats">
              <el-tag
                v-for="fmt in forms.upload.docFormats"
                :key="fmt"
                closable
                class="format-tag"
                @close="removeFormat('doc', fmt)"
              >{{ fmt }}</el-tag>
              <el-input
                v-if="inputVisible.doc"
                v-model="inputValue.doc"
                size="small"
                class="w-100"
                @keyup.enter="confirmFormat('doc')"
                @blur="confirmFormat('doc')"
              />
              <el-button
                v-else
                size="small"
                @click="showInput('doc')"
              ><el-icon><Plus /></el-icon></el-button>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving.upload" @click="saveSettings('upload')">
                保存上传配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 通知设置 -->
        <el-tab-pane label="通知设置" name="notification">
          <el-form ref="notifFormRef" :model="forms.notification" label-width="160px" class="settings-form">
            <el-divider content-position="left"><el-icon><Bell /></el-icon>默认通知方式</el-divider>

            <el-form-item label="站内通知" prop="inAppNotification">
              <el-switch v-model="forms.notification.inAppNotification" />
            </el-form-item>

            <el-form-item label="邮件通知" prop="emailNotification">
              <el-switch v-model="forms.notification.emailNotification" />
              <span class="form-tip">需先配置邮件服务</span>
            </el-form-item>

            <el-form-item label="短信通知" prop="smsNotification">
              <el-switch v-model="forms.notification.smsNotification" disabled />
              <span class="form-tip">暂未开放</span>
            </el-form-item>

            <el-divider content-position="left"><el-icon><Clock /></el-icon>自动提醒</el-divider>

            <el-form-item label="考试前提醒(小时)" prop="examReminderHours">
              <el-input-number v-model="forms.notification.examReminderHours" :min="0" :max="168" :step="1" />
            </el-form-item>

            <el-form-item label="作业截止前提醒" prop="deadlineReminder">
              <el-switch v-model="forms.notification.deadlineReminder" />
            </el-form-item>

            <el-form-item label="成绩发布通知" prop="gradePublishNotify">
              <el-switch v-model="forms.notification.gradePublishNotify" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving.notification" @click="saveSettings('notification')">
                保存通知设置
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  Setting,
  User,
  Lock,
  Monitor,
  Upload,
  Document,
  Bell,
  Clock,
  Plus
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import systemApi from '@/api/system'
import { systemConfigApi } from '@/api/systemConfig'

// 当前激活的Tab
const activeTab = ref('basic')

// 各表单引用
const basicFormRef = ref(null)
const securityFormRef = ref(null)
const emailFormRef = ref(null)
const uploadFormRef = ref(null)
const notifFormRef = ref(null)

// 保存状态
const saving = reactive({
  basic: false,
  security: false,
  email: false,
  upload: false,
  notification: false
})

// 表单数据
const forms = reactive({
  basic: {
    siteName: 'AI智能课程平台',
    siteDescription: '基于AI技术的智能学习平台，提供个性化学习体验',
    logoUrl: '',
    icpNumber: '',
    allowRegister: true,
    defaultRole: 'STUDENT',
    pageSize: 20
  },
  security: {
    minPasswordLength: 8,
    passwordComplexity: ['lowercase', 'number'],
    passwordExpiryDays: 90,
    maxLoginAttempts: 5,
    lockoutDuration: 30,
    sessionTimeout: 120,
    enable2FA: false
  },
  email: {
    smtpHost: '',
    smtpPort: 587,
    senderEmail: 'noreply@aiedu.com',
    senderName: 'AI课程平台',
    username: '',
    useSSL: true
  },
  upload: {
    maxFileSize: 10,
    totalQuota: 50,
    imageFormats: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'],
    docFormats: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt']
  },
  notification: {
    inAppNotification: true,
    emailNotification: false,
    smsNotification: false,
    examReminderHours: 24,
    deadlineReminder: true,
    gradePublishNotify: true
  }
})

// 基础表单验证规则
const basicRules = {
  siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }]
}

// 上传格式输入状态
const inputVisible = reactive({ image: false, doc: false })
const inputValue = reactive({ image: '', doc: '' })

/**
 * 加载指定分组的配置
 */
async function loadConfigGroup(group) {
  try {
    // 优先使用 systemConfigApi 按分组获取
    const res = await systemConfigApi.getByGroup(group)
    const data = res.data?.data || res.data || res

    if (data && typeof data === 'object') {
      // 合并到对应表单
      if (forms[group]) {
        Object.assign(forms[group], data)
      }
    }
  } catch (error) {
    // 回退到 systemApi
    try {
      const res = await systemApi.getSystemConfig(group)
      const fallbackData = res.data || res
      if (fallbackData && typeof fallbackData === 'object' && forms[group]) {
        Object.assign(forms[group], fallbackData)
      }
    } catch (e) {
      console.warn(`加载${group}配置失败:`, e)
    }
  }
}

/**
 * 保存设置
 */
async function saveSettings(group) {
  let formRef = null
  switch (group) {
    case 'basic': formRef = basicFormRef; break
    case 'security': formRef = securityFormRef; break
    case 'email': formRef = emailFormRef; break
    case 'upload': formRef = uploadFormRef; break
    case 'notification': formRef = notifFormRef; break
  }

  if (formRef?.value) {
    await formRef.value.validate(async (valid) => {
      if (!valid) return

      saving[group] = true
      try {
        // 优先使用 systemConfigApi 批量更新接口
        await systemConfigApi.batchUpdate({ group, configs: forms[group] })
        ElMessage.success(`${getGroupName(group)}保存成功`)
      } catch (error) {
        console.error('保存配置失败:', error)

        // 回退：逐个保存
        const configData = forms[group]
        for (const [key, value] of Object.entries(configData)) {
          try {
            await systemApi.updateSystemConfig(key, value)
          } catch (e) {
            console.warn(`保存 ${key} 失败:`, e)
          }
        }

        ElMessage.success(`${getGroupName(group)}已保存`)
      } finally {
        saving[group] = false
      }
    })
  } else {
    saving[group] = true
    try {
      const configData = forms[group]
      for (const [key, value] of Object.entries(configData)) {
        await systemApi.updateSystemConfig(key, value)
      }
      ElMessage.success(`${getGroupName(group)}已保存`)
    } catch (error) {
      ElMessage.error('保存失败')
    } finally {
      saving[group] = false
    }
  }
}

/**
 * 获取分组中文名称
 */
function getGroupName(group) {
  const map = {
    basic: '基础设置',
    security: '安全设置',
    email: '邮件配置',
    upload: '上传配置',
    notification: '通知设置'
  }
  return map[group] || group
}

/**
 * 发送测试邮件
 */
async function sendTestEmail() {
  ElMessage.info('正在发送测试邮件...')
  try {
    await systemConfigApi.testEmail()
    ElMessage.success('测试邮件已发送，请查收')
  } catch (error) {
    ElMessage.error('发送失败，请检查邮件配置')
  }
}

/**
 * 显示格式输入框
 */
function showInput(type) {
  inputVisible[type] = true
  inputValue[type] = ''
}

/**
 * 确认添加格式
 */
function confirmFormat(type) {
  const val = inputValue[type]?.trim().toLowerCase()
  if (val && !forms.upload[`${type}Formats`].includes(val)) {
    forms.upload[`${type}Formats`].push(val)
  }
  inputVisible[type] = false
  inputValue[type] = ''
}

/**
 * 移除格式
 */
function removeFormat(type, format) {
  const idx = forms.upload[`${type}Formats`].indexOf(format)
  if (idx > -1) {
    forms.upload[`${type}Formats`].splice(idx, 1)
  }
}

// 初始化加载所有配置
onMounted(() => {
  loadConfigGroup('basic')
  loadConfigGroup('security')
  loadConfigGroup('email')
  loadConfigGroup('upload')
  loadConfigGroup('notification')
})
</script>

<style scoped>
.system-settings {
  max-width: 1000px;
  margin: 0 auto;
}

/* 宽度工具类（内联样式提取） */
.w-200 { width: 200px; }
.w-400 { width: 400px; }
.w-100 { width: 100px; }
.alert-mb { margin-bottom: 20px; }

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

/* 设置表单 */
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

.ml-8 {
  margin-left: 8px;
}

/* 格式标签 */
.format-tag {
  margin-right: 6px;
  margin-top: 4px;
}

/* 响应式 */
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
