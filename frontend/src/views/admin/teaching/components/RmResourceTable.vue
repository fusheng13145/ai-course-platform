<template>
  <div class="content-card">
    <el-table v-loading="loading" :data="filteredResources" style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="fileName" label="资源名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="res-name">
            <el-icon class="res-icon"><component :is="fileIcon(row)" /></el-icon>
            {{ row.fileName || row.name || '未命名' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        prop="fileType"
        label="类型"
        width="120"
        align="center"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ typeTag(row.fileType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column label="可见性" width="130" align="center" class-name="tag-col">
        <template #default="{ row }">
          <el-switch
            :model-value="row.visibleToAll !== false"
            active-text="公开"
            inactive-text="私有"
            inline-prompt
            @change="(val) => emit('toggle-visibility', row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="uploadTime" label="上传时间" width="170">
        <template #default="{ row }">{{
          formatTime(row.uploadTime || row.createdAt, '—')
        }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <div class="action-row">
            <button class="act-btn act-btn--edit" @click="emit('download', row)">下载</button>
            <button class="act-btn--del" @click="emit('remove', row)">删除</button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="selectedCourseId ? '该课程暂无资源' : '请先选择课程'">
          <template #image><div class="empty-deco">📁</div></template>
        </el-empty>
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { formatTime } from '@/utils/datetime'
import { Document, Picture, VideoPlay, Files } from '@element-plus/icons-vue'

defineProps({
  loading: { type: Boolean, default: false },
  filteredResources: { type: Array, default: () => [] },
  selectedCourseId: { type: [Number, String], default: null },
})
const emit = defineEmits(['toggle-visibility', 'download', 'remove'])

function classifyType(fileType) {
  const t = (fileType || '').toLowerCase()
  if (/pdf|word|doc|excel|xls|ppt|txt|application/.test(t)) return 'doc'
  if (/image|jpg|jpeg|png|gif|bmp/.test(t)) return 'image'
  if (/video|audio|mp4|mp3|mpeg|avi/.test(t)) return 'media'
  return 'other'
}
function typeTag(fileType) {
  return { doc: '文档', image: '图片', media: '音视频', other: '其他' }[classifyType(fileType)]
}
function fileIcon(row) {
  const t = classifyType(row.fileType)
  return t === 'image' ? Picture : t === 'media' ? VideoPlay : t === 'doc' ? Document : Files
}
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}

.res-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.res-icon {
  color: var(--ac-text-muted);
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.act-btn,
.act-btn--edit,
.act-btn--del {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
  font-weight: 500;
  background: transparent;
  white-space: nowrap;
}
.act-btn:hover,
.act-btn--edit:hover,
.act-btn--del:hover {
  transform: translateY(-1px);
}
.act-btn--edit {
  color: var(--ac-primary, #3b82f6);
}
.act-btn--edit:hover {
  background: rgba(59, 130, 246, 0.08);
}
.act-btn--del {
  color: #b05045;
}
.act-btn--del:hover {
  background: rgba(176, 80, 69, 0.08);
}

:deep(.tag-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
