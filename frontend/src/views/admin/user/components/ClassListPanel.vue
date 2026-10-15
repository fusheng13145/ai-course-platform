<template>
  <div class="list-panel">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="emit('create')"
          ><el-icon><Plus /></el-icon>新建班级</el-button
        >
        <span v-if="activeOrgLabel" class="active-filter">
          当前筛选：<b>{{ activeOrgLabel }}</b>
        </span>
      </div>
      <div class="toolbar-right">
        <el-input
          :model-value="keyword"
          placeholder="搜索班级名称 / 编码"
          clearable
          style="width: 220px"
          @update:model-value="emit('update:keyword', $event)"
          @keyup.enter="emit('search')"
        >
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-button @click="emit('search')">查询</el-button>
      </div>
    </div>

    <div class="content-card">
      <el-table v-loading="loading" :data="pagedList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column prop="name" label="班级名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="班级编码" min-width="110" show-overflow-tooltip />
        <el-table-column prop="major" label="专业" min-width="120" show-overflow-tooltip />
        <el-table-column prop="gradeLevel" label="年级" width="90" align="center" />
        <el-table-column label="人数" width="110" align="center">
          <template #default="{ row }">
            <span class="count-badge"
              >{{ row.studentCount || 0 }} / {{ row.maxStudents || '—' }}</span
            >
          </template>
        </el-table-column>
        <el-table-column
          prop="isActive"
          label="状态"
          width="90"
          align="center"
          class-name="tag-col"
        >
          <template #default="{ row }">
            <span
              class="status-dot"
              :class="row.isActive !== false ? 'status-dot--active' : 'status-dot--inactive'"
            >
              <i></i>{{ row.isActive !== false ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <div class="action-row">
              <button class="act-btn act-btn--edit" @click="emit('members', row)">成员</button>
              <button class="act-btn act-btn--edit" @click="emit('edit', row)">编辑</button>
              <button class="act-btn--del" @click="emit('remove', row)">删除</button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState title="暂无班级数据" icon="users" />
        </template>
      </el-table>

      <div v-if="filteredList.length > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="filteredList.length"
          :page-size="pageSize"
          :current-page="page"
          @current-change="emit('page-change', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Search } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 班级列表（表格 + 分页）
 * <p>展示班级信息（人数/状态徽标），行操作（成员/编辑/删除）经事件上抛父组件；
 * 分页变化经 page-change 事件同步页码。</p>
 */
defineProps({
  /** 分页后的班级行数据 */
  pagedList: { type: Array, default: () => [] },
  /** 过滤后的全量列表（用于分页 total 与是否显示分页器） */
  filteredList: { type: Array, default: () => [] },
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 关键字（双向同步父组件 useClassrooms.keyword） */
  keyword: { type: String, default: '' },
  /** 当前页（双向同步） */
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  /** 当前组织筛选标签 */
  activeOrgLabel: { type: String, default: '' },
})

const emit = defineEmits([
  'create',
  'search',
  'members',
  'edit',
  'remove',
  'page-change',
  'update:keyword',
])
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.list-panel {
  min-width: 0;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.active-filter {
  font-size: 13px;
  color: var(--ac-text-muted);
}
.active-filter b {
  color: var(--ac-primary, var(--info));
}
.count-badge {
  font-size: 13px;
  color: var(--ac-text-regular);
  font-weight: 500;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 状态徽标 */
:deep(.tag-col .cell) {
  display: flex;
  justify-content: center;
  align-items: center;
}
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 20px;
  line-height: 20px;
}
.status-dot i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot--active {
  color: var(--ok);
  background: rgba(22, 163, 74, 0.08);
}
.status-dot--active i {
  background: var(--ok);
}
.status-dot--inactive {
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
}
.status-dot--inactive i {
  background: var(--ink-3);
}

/* 操作按钮 */
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
  color: var(--ac-primary, var(--info));
}
.act-btn--edit:hover {
  background: rgba(59, 130, 246, 0.08);
}
.act-btn--del {
  color: var(--accent-strong);
}
.act-btn--del:hover {
  background: rgba(176, 80, 69, 0.08);
}
</style>
