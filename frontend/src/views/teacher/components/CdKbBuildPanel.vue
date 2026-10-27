<template>
  <div class="kb-build-panel">
    <div class="kb-head">
      <div class="kb-title-group">
        <span class="kb-title">课程知识库（RAG 语料）</span>
        <span class="kb-sub"
          >把课程简介、章节正文与审核通过的资源一键灌入知识库，已存在的文档会自动跳过</span
        >
      </div>
      <el-button type="primary" plain :disabled="!courseId" :loading="building" @click="build">
        <el-icon><MagicStick /></el-icon>从课程资料构建
      </el-button>
    </div>

    <!-- 失败/降级常驻可见：不静默吞错，也不把「未真正写入」显示成「已入库」 -->
    <el-alert v-if="buildError" :title="buildError" type="error" :closable="false" show-icon />
    <el-alert
      v-else-if="buildResult"
      :title="buildResult.note || '课程知识库构建已完成'"
      :type="buildResult.mode === 'LOCAL' ? 'warning' : 'success'"
      :closable="false"
      show-icon
    />
    <div v-if="buildResult && !buildError" class="kb-summary">
      本次识别课程资料 {{ buildResult.total ?? 0 }} 条 · 已写入 {{ buildResult.added ?? 0 }} 条 ·
      跳过 {{ buildResult.skipped ?? 0 }} 条（知识库中已存在）
    </div>
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { useCourseKbBuild } from '@/composables/useCourseKbBuild'

/**
 * 课程详情「知识管理」页签内的课程知识库构建入口（教师侧）。
 * <p>逻辑复用 useCourseKbBuild，与管理员知识库管理页保持同一行为口径（含失败可见状态）。</p>
 */
const props = defineProps({ courseId: { type: [Number, String], required: true } })
const courseIdRef = toRef(props, 'courseId')

const { building, buildError, buildResult, build } = useCourseKbBuild(courseIdRef)
</script>

<style scoped>
.kb-build-panel {
  margin-bottom: 14px;
  padding: 14px 16px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg-elevated);
}
.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.kb-title-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 260px;
}
.kb-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.kb-sub {
  font-size: 12px;
  color: var(--ac-text-secondary);
}
.kb-summary {
  margin-top: 10px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}
</style>
