import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import notificationApi from '@/api/notification'
import { confirmDanger } from '@/utils/dangerConfirm'

/**
 * 课程详情（教师）通知域
 * <p>承载课程通知的发送与删除；课程 id、通知数据与刷新动作由编排层注入。</p>
 */
export function useCourseDetailNotices(deps) {
  const { course, notifications, loadNotifications } = deps

  const notificationDialogVisible = ref(false)
  const notificationForm = reactive({ title: '', content: '' })
  const sendingNotification = ref(false)

  function showNotificationDialog() {
    notificationForm.title = ''
    notificationForm.content = ''
    notificationDialogVisible.value = true
  }
  async function sendNotification() {
    if (!notificationForm.title.trim() || !notificationForm.content.trim())
      return ElMessage.warning('请填写完整')
    sendingNotification.value = true
    try {
      await notificationApi.sendNotification({
        title: notificationForm.title,
        content: notificationForm.content,
        courseId: Number(course.id),
        category: 'COURSE',
      })
      ElMessage.success('发送成功')
      notificationDialogVisible.value = false
      await loadNotifications()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '发送失败')
    } finally {
      sendingNotification.value = false
    }
  }
  async function deleteNotification(row) {
    try {
      // D2-2：明确连带影响（学生不可见）与不可逆性
      const ok = await confirmDanger({
        verb: '删除',
        target: '通知',
        subject: row.title,
        impact: '删除后学生将无法再查看该通知',
      })
      if (!ok) return
      await notificationApi.deleteNotification(row.id)
      notifications.value = notifications.value.filter((n) => n.id !== row.id)
      ElMessage.success('已删除')
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }

  return {
    notificationDialogVisible,
    notificationForm,
    sendingNotification,
    showNotificationDialog,
    sendNotification,
    deleteNotification,
  }
}
