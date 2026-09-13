<template>
  <div class="learning-mode">
    <div class="learning-header">
      <el-button text @click="$emit('exit')">
        <el-icon><ArrowLeft /></el-icon> 返回章节列表
      </el-button>
      <h3 class="learning-chapter-title">{{ currentChapter.title }}</h3>
      <div class="learning-actions">
        <el-button type="success" :loading="completing" @click="$emit('complete')">
          <el-icon><Select /></el-icon> 标记完成
        </el-button>
      </div>
    </div>

    <div class="learning-body">
      <!-- 视频播放区域：播放当前章节关联的视频资源（fileType 以 video/ 开头） -->
      <div class="video-player-area">
        <video
          v-if="currentChapterVideo"
          :key="currentChapterVideo.id"
          class="chapter-video"
          :src="videoSrc(currentChapterVideo)"
          controls
          preload="metadata"
        ></video>
        <div v-else class="video-empty">
          <el-icon :size="48" color="var(--ac-text-muted)"><VideoPlay /></el-icon>
          <p>本章暂无视频资源</p>
          <p class="video-hint">可阅读下方章节内容学习</p>
        </div>
      </div>

      <!-- 章节内容区域 -->
      <div class="chapter-content-area">
        <h4 class="content-subtitle">章节内容</h4>
        <div class="content-text">
          {{
            currentChapter.content ||
            currentChapter.description ||
            '本章节暂无详细内容，请观看视频学习。'
          }}
        </div>
      </div>
    </div>

    <!-- 学习记录自动上报提示 -->
    <div class="learning-footer">
      <el-alert title="学习记录将自动保存" type="info" :closable="false" show-icon>
        <template #default>
          已学习 <strong>{{ learningTimeDisplay }}</strong> · 学习进度已同步记录
        </template>
      </el-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, Select, VideoPlay } from '@element-plus/icons-vue'

/**
 * 章节学习 Tab — 学习模式视图
 * <p>返回/章节标题/标记完成头栏 + 视频播放区与章节内容 + 学习记录自动保存提示。</p>
 */
defineProps({
  /** 当前学习章节 */
  currentChapter: { type: Object, required: true },
  /** 当前章节视频资源（可能为空） */
  currentChapterVideo: { type: Object, default: null },
  /** 已学习时长（mm:ss 展示） */
  learningTimeDisplay: { type: String, default: '00:00' },
  /** 标记完成加载态 */
  completing: { type: Boolean, default: false },
  /** 视频 URL 解析函数 */
  videoSrc: { type: Function, required: true },
})

defineEmits(['exit', 'complete'])
</script>

<style scoped>
.learning-mode {
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  overflow: hidden;
}

.learning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  background: var(--ac-gradient-card);
  border-bottom: 1px solid var(--ac-border);
}

.learning-chapter-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.learning-body {
  display: grid;
  grid-template-columns: 1fr 380px;
  min-height: 400px;
}

.video-player-area {
  background: var(--surface-invert);
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--ac-border);
  min-height: 300px;
}

.chapter-video {
  width: 100%;
  height: 100%;
  max-height: 480px;
  object-fit: contain;
  background: var(--surface-invert);
}

.video-empty {
  text-align: center;
  color: var(--ac-text-muted);
  padding: 40px 20px;
}

.video-empty p {
  margin-top: var(--ac-spacing-sm);
  font-size: 14px;
}

.video-hint {
  font-size: 12px !important;
  color: var(--ac-text-muted) !important;
}

.chapter-content-area {
  padding: var(--ac-spacing-lg);
  overflow-y: auto;
}

.content-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-md);
  padding-bottom: var(--ac-spacing-sm);
  border-bottom: 1px solid var(--ac-border);
}

.content-text {
  line-height: 1.9;
  color: var(--ac-text-secondary);
  font-size: 14px;
  white-space: pre-wrap;
}

.learning-footer {
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-top: 1px solid var(--ac-border);
  background: var(--ac-bg);
}

@media (max-width: 900px) {
  .learning-body {
    grid-template-columns: 1fr;
  }
  .video-player-area {
    min-height: 220px;
    border-right: none;
    border-bottom: 1px solid var(--ac-border);
  }
}
</style>
