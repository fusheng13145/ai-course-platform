<template>
  <div>
    <!-- 工具栏 -->
    <div class="content-card filter-card">
      <div class="filter-row">
        <el-select
          :model-value="selectedCourseId"
          placeholder="选择课程"
          filterable
          class="w-260"
          @update:model-value="$emit('update:selectedCourseId', $event)"
          @change="$emit('course-change')"
        >
          <el-option
            v-for="c in courseList"
            :key="c.id"
            :label="c.name || c.title"
            :value="c.id"
          />
        </el-select>
        <el-button type="primary" :disabled="!selectedCourseId" @click="$emit('open-add')">
          <el-icon><Plus /></el-icon>新建文档
        </el-button>
        <el-button :disabled="!selectedCourseId" :loading="indexing" @click="$emit('build-index')">
          <el-icon><Refresh /></el-icon>重建索引
        </el-button>
        <el-button :disabled="!selectedCourseId" :loading="indexing" @click="$emit('clear-index')">
          <el-icon><Delete /></el-icon>清空索引
        </el-button>
        <div class="spacer"></div>
        <el-input
          :model-value="keyword"
          placeholder="搜索文档名称"
          clearable
          class="w-200"
          @update:model-value="$emit('update:keyword', $event)"
          @keyup.enter="$emit('search')"
        />
      </div>
    </div>

    <!-- 文档表格 -->
    <div class="content-card">
      <el-table
        v-loading="loading"
        :data="filteredDocs"
        style="width: 100%"
        empty-text="请选择课程后查看知识库文档"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="文档名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="doc-name">
              <el-icon class="doc-icon"><Document /></el-icon>
              {{ row.name || '未命名' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.type === 'DOCUMENT' ? 'primary' : 'info'"
              effect="plain"
            >
              {{ row.type === 'DOCUMENT' ? '文档' : '知识点' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ preview(row.description) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <div class="action-row">
              <el-button text type="primary" size="small" @click="$emit('open-edit', row)"
                >编辑</el-button
              >
              <el-button text type="danger" size="small" @click="$emit('remove-doc', row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="请选择课程后查看知识库文档">
            <template #image><div class="empty-deco">📚</div></template>
          </el-empty>
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Refresh, Delete, Document } from '@element-plus/icons-vue'

/**
 * 知识库文档管理区
 * <p>课程选择/新建/重建索引/清空索引/搜索工具栏 + 文档列表表格。</p>
 */
defineProps({
  /** 课程列表 */
  courseList: { type: Array, required: true },
  /** 当前选中课程（v-model） */
  selectedCourseId: { type: [String, Number], default: null },
  /** 关键词（v-model） */
  keyword: { type: String, default: '' },
  /** 过滤后的文档列表 */
  filteredDocs: { type: Array, required: true },
  /** 表格加载态 */
  loading: { type: Boolean, default: false },
  /** 索引操作加载态 */
  indexing: { type: Boolean, default: false },
  /** 内容预览截断函数 */
  preview: { type: Function, required: true },
})

defineEmits([
  'update:selectedCourseId',
  'update:keyword',
  'course-change',
  'search',
  'open-add',
  'build-index',
  'clear-index',
  'open-edit',
  'remove-doc',
])
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
.w-260 {
  width: 260px;
}
.w-200 {
  width: 200px;
}
.doc-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.doc-icon {
  color: var(--ac-text-muted);
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}
</style>
