<template>
  <el-dialog
    :model-value="modelValue"
    title="进行中的考试"
    width="800px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-table v-loading="loadingSessions" :data="activeSessions" max-height="400">
      <el-table-column prop="paperTitle" label="试卷名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="courseName" label="课程" width="140" />
      <el-table-column prop="studentCount" label="参考人数" width="90" align="center" />
      <el-table-column prop="startTime" label="开始时间" width="160" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button text type="danger" size="small" @click="$emit('force-submit', row)"
            >强制交卷</el-button
          >
          <el-button text type="primary" size="small" @click="$emit('view-cheating', row)"
            >作弊检测</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup>
/**
 * 进行中考试对话框
 * <p>当前活跃考试会话列表，支持强制交卷与作弊检测入口。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 活跃会话列表 */
  activeSessions: { type: Array, required: true },
  /** 加载态 */
  loadingSessions: { type: Boolean, default: false },
})

defineEmits(['update:modelValue', 'force-submit', 'view-cheating'])
</script>
