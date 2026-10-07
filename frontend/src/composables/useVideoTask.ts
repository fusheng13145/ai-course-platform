import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import studentApi from '@/api/student'
import taskApi from '@/api/task'

/**
 * 视频任务点交互组合式函数
 * <p>封装视频任务点的完整交互链路：续播定位、播放进度心跳上报（5s 节流）、
 * 90% 阈值自动完成、无资源时的兜底标记。由任务点抽屉组件（TaskDrawer）消费。</p>
 *
 * @param {Object} options
 * @param {string|number} options.courseId 课程 ID（视频状态按课程隔离）
 * @param {() => (Object|null)} options.getTask 获取当前活动任务点（须含 id）
 * @param {() => Promise<void>} options.onTaskCompleted 任务完成后回调（刷新学习中心进度）
 * @returns {{ videoEl: import('vue').Ref, videoSrc: import('vue').Ref, videoDuration: import('vue').Ref,
 *   localWatched: import('vue').Ref, videoState: import('vue').Reactive,
 *   loadVideo: Function, onVideoMeta: Function, onVideoTime: Function, onVideoEnded: Function,
 *   markVideoFallback: Function, resetVideo: Function }}
 */
export function useVideoTask({ courseId, getTask, onTaskCompleted }) {
  const videoEl = ref(null)
  const videoSrc = ref('')
  const videoDuration = ref(0)
  const localWatched = ref(0)
  const lastBeat = ref(0)
  const videoState = reactive({
    positionSeconds: 0,
    watchedDuration: 0,
    totalDuration: 0,
    completed: false,
  })

  /** 并发拉取续播状态与任务资源（attachmentUrl/videoUrl） */
  async function loadVideo(task) {
    const [stateRes, taskRes] = await Promise.all([
      studentApi.getVideoState(courseId, task.id),
      taskApi.getTaskById(task.id),
    ])
    const s = unwrap(stateRes) || {}
    Object.assign(videoState, {
      positionSeconds: s.positionSeconds || 0,
      watchedDuration: s.watchedDuration || 0,
      totalDuration: s.totalDuration || 0,
      completed: !!s.completed,
    })
    localWatched.value = s.watchedDuration || 0
    const t = unwrap(taskRes) || {}
    videoSrc.value = t.attachmentUrl || t.videoUrl || ''
    videoDuration.value = s.totalDuration || 0
  }

  /** 元数据就绪：按服务端续播位置定位（定位失败静默降级） */
  function onVideoMeta() {
    const v = videoEl.value
    if (!v) return
    videoDuration.value = v.duration || videoState.totalDuration || 0
    if (videoState.positionSeconds > 0 && videoState.positionSeconds < v.duration) {
      try {
        v.currentTime = videoState.positionSeconds
      } catch {
        if (import.meta.env.DEV) {
          logger.warn('视频定位失败（元数据可能尚未就绪）')
        }
      }
    }
  }

  /** 播放中：更新本地观看秒数；5s 节流心跳；达 90% 阈值触发自动完成 */
  function onVideoTime() {
    const v = videoEl.value
    if (!v || !v.duration) return
    localWatched.value = Math.max(localWatched.value, Math.floor(v.currentTime))
    const now = Date.now()
    if (now - lastBeat.value > 5000) {
      lastBeat.value = now
      sendHeartbeat()
    }
    if (!videoState.completed && localWatched.value / v.duration >= 0.9) {
      sendHeartbeat(true)
    }
  }

  function onVideoEnded() {
    if (!videoState.completed) sendHeartbeat(true)
  }

  /** 心跳上报；forceComplete=true 或服务端标记完成后刷新进度 */
  async function sendHeartbeat(forceComplete = false) {
    const v = videoEl.value
    const total = v?.duration ? Math.floor(v.duration) : videoState.totalDuration
    const pos = v?.currentTime ? Math.floor(v.currentTime) : videoState.positionSeconds
    try {
      const res = await studentApi.reportVideoHeartbeat(courseId, getTask()?.id, {
        position: pos,
        watchedDuration: Math.floor(localWatched.value),
        totalDuration: total,
      })
      const s = unwrap(res) || {}
      Object.assign(videoState, {
        positionSeconds: s.positionSeconds || 0,
        watchedDuration: s.watchedDuration || 0,
        totalDuration: s.totalDuration || 0,
        completed: !!s.completed,
      })
      if (forceComplete || videoState.completed) {
        ElMessage.success('视频观看完成')
        await onTaskCompleted()
      }
    } catch (e) {
      logger.warn('视频心跳上报失败:', e)
    }
  }

  /** 无视频资源时的兜底标记完成 */
  async function markVideoFallback() {
    try {
      await studentApi.markTaskComplete(courseId, getTask()?.id, 'VIDEO')
      ElMessage.success('已标记为完成')
      videoState.completed = true
      await onTaskCompleted()
    } catch {
      ElMessage.error('操作失败')
    }
  }

  /** 抽屉关闭时清理视频状态（释放视频源引用） */
  function resetVideo() {
    videoSrc.value = ''
    localWatched.value = 0
    lastBeat.value = 0
  }

  return {
    videoEl,
    videoSrc,
    videoDuration,
    localWatched,
    videoState,
    loadVideo,
    onVideoMeta,
    onVideoTime,
    onVideoEnded,
    markVideoFallback,
    resetVideo,
  }
}
