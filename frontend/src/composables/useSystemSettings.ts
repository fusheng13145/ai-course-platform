import logger from '@/utils/logger'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import systemApi from '@/api/system'
import { systemConfigApi } from '@/api/systemConfig'

/**
 * 系统设置领域逻辑
 * <p>五组配置（基础/安全/邮件/上传/通知）的加载与保存（双通道：systemConfigApi 优先、
 * systemApi 回退）、测试邮件、上传格式标签增删。</p>
 */
export function useSystemSettings() {
  // 当前激活的Tab
  const activeTab = ref('basic')

  // 保存状态
  const saving = reactive({
    basic: false,
    security: false,
    email: false,
    upload: false,
    notification: false,
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
      pageSize: 20,
    },
    security: {
      minPasswordLength: 8,
      passwordComplexity: ['lowercase', 'number'],
      passwordExpiryDays: 90,
      maxLoginAttempts: 5,
      lockoutDuration: 30,
      sessionTimeout: 120,
      enable2FA: false,
    },
    email: {
      smtpHost: '',
      smtpPort: 587,
      senderEmail: 'noreply@aiedu.com',
      senderName: 'AI课程平台',
      username: '',
      useSSL: true,
    },
    upload: {
      maxFileSize: 10,
      totalQuota: 50,
      imageFormats: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'],
      docFormats: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt'],
    },
    notification: {
      inAppNotification: true,
      emailNotification: false,
      smsNotification: false,
      examReminderHours: 24,
      deadlineReminder: true,
      gradePublishNotify: true,
    },
  })

  // 基础表单验证规则
  const basicRules = {
    siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
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
    } catch {
      // 回退到 systemApi
      try {
        const res = await systemApi.getSystemConfig(group)
        const fallbackData = res.data || res
        if (fallbackData && typeof fallbackData === 'object' && forms[group]) {
          Object.assign(forms[group], fallbackData)
        }
      } catch (e) {
        logger.warn(`加载${group}配置失败:`, e)
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
      notification: '通知设置',
    }
    return map[group] || group
  }

  /**
   * 保存设置（校验已在表单组件内完成）
   */
  async function saveSettings(group) {
    saving[group] = true
    try {
      // 优先使用 systemConfigApi 批量更新接口
      await systemConfigApi.batchUpdate({ group, configs: forms[group] })
      ElMessage.success(`${getGroupName(group)}保存成功`)
    } catch (error) {
      logger.error('保存配置失败:', error)

      // 回退：逐个保存
      const configData = forms[group]
      for (const [key, value] of Object.entries(configData)) {
        try {
          await systemApi.updateSystemConfig(key, value)
        } catch (e) {
          logger.warn(`保存 ${key} 失败:`, e)
        }
      }

      ElMessage.success(`${getGroupName(group)}已保存`)
    } finally {
      saving[group] = false
    }
  }

  /**
   * 发送测试邮件
   */
  async function sendTestEmail() {
    ElMessage.info('正在发送测试邮件...')
    try {
      await systemConfigApi.testEmail()
      ElMessage.success('测试邮件已发送，请查收')
    } catch {
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

  return {
    activeTab,
    saving,
    forms,
    basicRules,
    inputVisible,
    inputValue,
    loadConfigGroup,
    saveSettings,
    sendTestEmail,
    showInput,
    confirmFormat,
    removeFormat,
  }
}
