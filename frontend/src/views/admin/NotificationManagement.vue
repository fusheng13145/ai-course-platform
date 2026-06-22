﻿﻿﻿﻿﻿<template>
  <div class="fade-in notification-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">通知管理</h2>
        <p class="page-desc">系统公告与消息推送中心</p>
      </div>
      <div class="header-right">
        <el-button @click="scrollToSendForm" type="primary">
          <el-icon><Plus /></el-icon>发送通知
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card-mini">
        <span class="label">总通知数</span>
        <span class="value">{{ stats.total || 0 }}</span>
      </div>
      <div class="stat-card-mini unread">
        <span class="label">未读数</span>
        <span class="value">{{ stats.unread || 0 }}</span>
      </div>
      <div class="stat-card-mini today">
        <span class="label">今日发送</span>
        <span class="value">{{ stats.todaySent || 0 }}</span>
      </div>
      <div class="stat-card-mini read-rate">
        <span class="label">阅读率</span>
        <span class="value">{{ stats.readRate ? (stats.readRate * 100).toFixed(1) + '%' : '--' }}</span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-layout">
      <!-- 左侧：发送通知表单 -->
      <div class="send-section card-base" ref="sendSectionRef">
        <h3 class="section-title">快速发送</h3>

        <el-form ref="sendFormRef" :model="sendForm" :rules="sendRules" label-position="top">
          <el-form-item label="通知标题" prop="title">
            <el-input v-model="sendForm.title" placeholder="请输入通知标题" maxlength="100" show-word-limit />
          </el-form-item>

          <el-form-item label="通知内容" prop="content">
            <el-input
              v-model="sendForm.content"
              type="textarea"
              placeholder="请输入通知内容"
              :rows="5"
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="目标用户">
            <el-radio-group v-model="sendForm.targetType">
              <el-radio value="ALL">全员推送</el-radio>
              <el-radio value="STUDENT">仅学生</el-radio>
              <el-radio value="TEACHER">仅教师</el-radio>
              <el-radio value="SPECIFIC">指定用户</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="sendForm.targetType === 'SPECIFIC'" label="选择用户">
            <el-select
              v-model="sendForm.targetUsers"
              multiple
              filterable
              remote
              reserve-keyword
              placeholder="搜索并选择用户"
              :remote-method="searchUsers"
              style="width:100%"
            >
              <el-option
                v-for="user in userSearchResults"
                :key="user.id"
                :label="`${user.fullName} (${user.username})`"
                :value="user.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="通知类型" prop="type">
            <el-select v-model="sendForm.type" style="width:100%">
              <el-option label="系统公告" value="SYSTEM" />
              <el-option label="课程通知" value="COURSE" />
              <el-option label="考试提醒" value="EXAM" />
              <el-option label="活动通知" value="ACTIVITY" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>

          <el-form-item label="优先级" prop="priority">
            <el-radio-group v-model="sendForm.priority">
              <el-radio value="LOW">
                <el-tag size="small" effect="plain" type="info">低</el-tag>
              </el-radio>
              <el-radio value="NORMAL">
                <el-tag size="small" effect="plain">普通</el-tag>
              </el-radio>
              <el-radio value="HIGH">
                <el-tag size="small" effect="plain" type="warning">高</el-tag>
              </el-radio>
              <el-radio value="URGENT">
                <el-tag size="small" effect="plain" type="danger">紧急</el-tag>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="定时发送">
            <el-switch v-model="sendForm.scheduled" active-text="是" inactive-text="否" />
          </el-form-item>

          <el-form-item v-if="sendForm.scheduled" label="发送时间">
            <el-date-picker
              v-model="sendForm.scheduleTime"
              type="datetime"
              placeholder="选择发送时间"
              style="width:100%"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="sending" @click="handleSendNotification" style="width:100%">
              <el-icon><Promotion /></el-icon>立即发送
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 右侧：通知历史列表 -->
      <div class="history-section">
        <div class="card-base history-header">
          <h3 class="section-title">通知历史</h3>
          <div class="header-actions">
            <el-button text type="primary" size="small" @click="markAllRead">全部标为已读</el-button>
          </div>
        </div>

        <div class="card-base notification-list" v-loading="loading">
          <!-- 筛选栏 -->
          <div class="list-filter">
            <el-select v-model="filters.type" placeholder="类型筛选" clearable size="small" style="width:120px" @change="fetchNotifications">
              <el-option label="全部" value="" />
              <el-option label="系统公告" value="SYSTEM" />
              <el-option label="课程通知" value="COURSE" />
              <el-option label="考试提醒" value="EXAM" />
              <el-option label="活动通知" value="ACTIVITY" />
            </el-select>
            <el-select v-model="filters.status" placeholder="状态筛选" clearable size="small" style="width:110px" @change="fetchNotifications">
              <el-option label="全部" value="" />
              <el-option label="未读" value="UNREAD" />
              <el-option label="已读" value="READ" />
            </el-select>
          </div>

          <!-- 通知列表 -->
          <div v-if="notificationList.length > 0" class="notification-items">
            <div
              v-for="item in notificationList"
              :key="item.id"
              class="notification-item"
              :class="{ unread: !item.isRead }"
            >
              <div class="notif-header">
                <el-tag :type="getTypeColor(item.type)" size="small" effect="light">{{ getTypeText(item.type) }}</el-tag>
                <el-tag
                  v-if="item.priority === 'HIGH' || item.priority === 'URGENT'"
                  :type="item.priority === 'URGENT' ? 'danger' : 'warning'"
                  size="small"
                  effect="dark"
                >{{ item.priority === 'URGENT' ? '紧急' : '重要' }}</el-tag>
                <span class="notif-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <h4 class="notif-title">{{ item.title }}</h4>
              <p class="notif-content">{{ item.content }}</p>
              <div class="notif-footer">
                <span class="target-info">
                  <el-icon><User /></el-icon>
                  {{ getTargetText(item.targetType) }}
                </span>
                <span class="read-count">
                  <el-icon><View /></el-icon>
                  {{ item.readCount || 0 }} 人已读
                </span>
                <el-button text type="danger" size="small" @click="deleteNotification(item)">删除</el-button>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂无通知记录" :image-size="80" />

          <!-- 分页 -->
          <div v-if="pagination.total > 0" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              :total="pagination.total"
              :page-sizes="[10, 20, 30]"
              layout="total, prev, pager, next"
              small
              background
              @size-change="fetchNotifications"
              @current-change="fetchNotifications"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 发送确认对话框（复用） -->
    <el-dialog v-model="showConfirmDialog" title="确认发送" width="450px" append-to-body destroy-on-close>
      <div class="confirm-content">
        <p>即将发送以下通知：</p>
        <div class="confirm-preview">
          <p><strong>标题：</strong>{{ sendForm.title }}</p>
          <p><strong>目标：</strong>{{ getTargetText(sendForm.targetType) }}</p>
          <p><strong>类型：</strong>{{ getTypeText(sendForm.type) }}</p>
          <p><strong>优先级：</strong>{{ getPriorityText(sendForm.priority) }}</p>
        </div>
        <p class="confirm-warning" v-if="sendForm.targetType === 'ALL'">
          ⚠️ 此操作将向所有注册用户推送该通知，请确认内容无误。
        </p>
      </div>
      <template #footer>
        <el-button @click="showConfirmDialog = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="confirmSend">确认发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  Plus,
  Promotion,
  User,
  View,
  Bell,
  Message
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import notificationApi from '@/api/notification'
import userApi from '@/api/user'
import { notificationEnhancedApi } from '@/api/notificationEnhanced'

// 加载状态
const loading = ref(false)
const sending = ref(false)

// 统计数据
const stats = reactive({
  total: 0,
  unread: 0,
  todaySent: 0,
  readRate: 0
})

// 筛选条件
const filters = reactive({
  type: '',
  status: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 数据列表
const notificationList = ref([])

// 发送表单
const sendFormRef = ref(null)
const sendSectionRef = ref(null)
const showSendDialog = ref(true) // 默认显示（作为页面的一部分）

// 滚动到发送表单
function scrollToSendForm() {
  sendSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
const showConfirmDialog = ref(false)

const sendForm = reactive({
  title: '',
  content: '',
  targetType: 'ALL',
  targetUsers: [],
  type: 'SYSTEM',
  priority: 'NORMAL',
  scheduled: false,
  scheduleTime: null
})

// 表单验证规则
const sendRules = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
  type: [{ required: true, message: '请选择通知类型', trigger: 'change' }]
}

// 用户搜索结果
const userSearchResults = ref([])

/**
 * 获取通知列表
 */
async function fetchNotifications() {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    }

    if (filters.type) params.type = filters.type
    if (filters.status) params.status = filters.status

    const res = await notificationApi.getNotificationList(params)
    const rawData = res.data?.records || res.data?.list || res.data || res

    if (Array.isArray(rawData)) {
      notificationList.value = rawData
      pagination.total = rawData.length
    } else if (rawData?.records || rawData?.items) {
      notificationList.value = rawData.records || rawData.items
      pagination.total = rawData.total || notificationList.value.length
    }

    // 更新统计
    updateStats()
  } catch (error) {
    console.error('获取通知列表失败:', error)
    ElMessage.error('加载通知列表失败')
    notificationList.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 更新统计数据
 */
async function updateStats() {
  try {
    // 使用增强 API 获取未读数量
    const unreadRes = await notificationEnhancedApi.getUnreadCount()
    stats.unread = unreadRes.data?.count ?? unreadRes.data ?? 0
  } catch (e) {
    // 回退到本地计算
    stats.unread = notificationList.value.filter(n => !n.isRead).length
  }

  stats.total = notificationList.value.length
  stats.todaySent = notificationList.value.filter(isToday).length

  const readCount = notificationList.value.filter(n => n.isRead).length
  stats.readRate = stats.total > 0 ? readCount / stats.total : 0
}

/**
 * 判断是否为今天
 */
function isToday(item) {
  if (!item.createdAt) return false
  const date = new Date(item.createdAt)
  const today = new Date()
  return (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  )
}

/**
 * 搜索用户
 */
async function searchUsers(query) {
  if (!query || query.length < 2) {
    userSearchResults.value = []
    return
  }

  try {
    const res = await userApi.getUserList({ keyword: query, size: 10 })
    const data = res.data?.list || res.data || res || []
    userSearchResults.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.warn('搜索用户失败:', error)
    userSearchResults.value = []
  }
}

/**
 * 处理发送通知
 */
async function handleSendNotification() {
  if (!sendFormRef.value) return

  await sendFormRef.value.validate(async (valid) => {
    if (!valid) return

    // 如果是指定用户但未选择任何用户
    if (sendForm.targetType === 'SPECIFIC' && sendForm.targetUsers.length === 0) {
      ElMessage.warning('请至少选择一个目标用户')
      return
    }

    showConfirmDialog.value = true
  })
}

/**
 * 确认发送
 */
async function confirmSend() {
  sending.value = true
  try {
    const payload = {
      title: sendForm.title,
      content: sendForm.content,
      type: sendForm.type,
      priority: sendForm.priority,
      targetType: sendForm.targetType,
      targetUserIds: sendForm.targetType === 'SPECIFIC' ? sendForm.targetUsers : undefined,
      scheduledAt: sendForm.scheduled ? sendForm.scheduleTime?.toISOString() : undefined
    }

    await notificationEnhancedApi.sendNotification(payload)
    ElMessage.success('通知发送成功')

    // 重置表单
    resetSendForm()
    showConfirmDialog.value = false

    // 刷新列表
    fetchNotifications()
  } catch (error) {
    console.error('发送通知失败:', error)
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

/**
 * 重置发送表单
 */
function resetSendForm() {
  Object.assign(sendForm, {
    title: '',
    content: '',
    targetType: 'ALL',
    targetUsers: [],
    type: 'SYSTEM',
    priority: 'NORMAL',
    scheduled: false,
    scheduleTime: null
  })

  if (sendFormRef.value) {
    sendFormRef.value.resetFields()
  }
}

/**
 * 全部标为已读
 */
async function markAllRead() {
  try {
    await notificationApi.markAllAsRead()
    ElMessage.success('已将所有通知标记为已读')
    fetchNotifications()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

/**
 * 删除通知
 */
async function deleteNotification(item) {
  try {
    await ElMessageBox.confirm(`确定删除通知「${item.title}」？`, '确认删除', { type: 'warning' })

    await notificationApi.deleteNotification(item.id)
    ElMessage.success('删除成功')
    fetchNotifications()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

/**
 * 获取类型对应的标签颜色
 */
function getTypeColor(type) {
  const map = {
    SYSTEM: '',
    COURSE: 'success',
    EXAM: 'warning',
    ACTIVITY: 'danger',
    OTHER: 'info'
  }
  return map[type] || ''
}

/**
 * 获取类型文本
 */
function getTypeText(type) {
  const map = {
    SYSTEM: '系统公告',
    COURSE: '课程通知',
    EXAM: '考试提醒',
    ACTIVITY: '活动通知',
    OTHER: '其他'
  }
  return map[type] || type
}

/**
 * 获取目标文本
 */
function getTargetText(targetType) {
  const map = {
    ALL: '全员',
    STUDENT: '学生',
    TEACHER: '教师',
    SPECIFIC: `指定用户 (${sendForm.targetUsers.length}人)`
  }
  return map[targetType] || targetType
}

/**
 * 获取优先级文本
 */
function getPriorityText(priority) {
  const map = {
    LOW: '低',
    NORMAL: '普通',
    HIGH: '高',
    URGENT: '紧急'
  }
  return map[priority] || priority
}

/**
 * 格式化时间
 */
function formatTime(time) {
  if (!time) return '--'
  const date = new Date(time)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 初始化
onMounted(() => {
  fetchNotifications()
})
</script>

<style scoped>
.notification-management {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--ac-spacing-lg);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
}

/* 统计行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-card-mini {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}

.stat-card-mini:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}

.stat-card-mini .label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.stat-card-mini .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stat-card-mini.unread .value { color: var(--ac-primary); }
.stat-card-mini.today .value { color: var(--ac-success); }
.stat-card-mini.read-rate .value { color: var(--ac-secondary); }

/* 主布局 */
.main-layout {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: var(--ac-spacing-md);
  align-items: start;
}

/* 卡片基础样式 */
.card-base {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

/* 发送区域 */
.send-section {
  position: sticky;
  top: 0;
}

/* 历史区域 */
.history-section {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px var(--ac-spacing-lg);
}

.history-header .section-title {
  margin-bottom: 0;
}

/* 通知列表 */
.notification-list {
  display: flex;
  flex-direction: column;
  min-height: 500px;
}

.list-filter {
  display: flex;
  gap: var(--ac-spacing-sm);
  margin-bottom: var(--ac-spacing-md);
  padding-bottom: var(--ac-spacing-md);
  border-bottom: 1px solid var(--ac-border);
}

.notification-items {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}

.notification-item {
  padding: 18px 20px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg);
  border: 1px solid transparent;
  transition: all var(--ac-transition-normal);
  cursor: default;
}

.notification-item:hover {
  border-color: var(--ac-border);
  box-shadow: var(--ac-shadow-sm);
}

.notification-item.unread {
  border-left: 3px solid var(--ac-primary);
}

.notif-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.notif-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.notif-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 6px;
  line-height: 1.4;
}

.notif-content {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}

.notif-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.target-info,
.read-count {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 分页 */
.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: center;
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}

/* 确认对话框 */
.confirm-content p {
  margin-bottom: 12px;
  color: var(--ac-text-secondary);
}

.confirm-preview {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 14px 18px;
  margin: 12px 0;
}

.confirm-preview p {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--ac-text-primary);
}

.confirm-preview p:last-child {
  margin-bottom: 0;
}

.confirm-warning {
  color: var(--ac-danger) !important;
  font-weight: 500;
  background: rgba(196, 128, 128, 0.08);
  padding: 10px 14px;
  border-radius: var(--ac-radius-sm);
  margin-top: 12px !important;
}

/* 响应式 */
@media (max-width: 1024px) {
  .main-layout {
    grid-template-columns: 1fr;
  }

  .send-section {
    position: static;
  }
}
</style>
