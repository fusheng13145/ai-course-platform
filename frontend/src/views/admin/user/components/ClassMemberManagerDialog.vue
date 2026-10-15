<template>
  <!-- 成员管理主对话框 -->
  <el-dialog
    :model-value="modelValue"
    :title="`成员管理 · ${currentClass?.name || ''}`"
    width="720px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="student-toolbar">
      <el-input
        :model-value="studentKeyword"
        placeholder="搜索学生姓名 / 学号"
        clearable
        style="width: 240px"
        @update:model-value="emit('update:studentKeyword', $event)"
        @keyup.enter="emit('reload')"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-button @click="emit('reload')">查询</el-button>
      <el-button type="primary" @click="emit('add-student')"
        ><el-icon><Plus /></el-icon>添加学生</el-button
      >
    </div>
    <el-table v-loading="studentLoading" :data="studentList" max-height="380" style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="学号" min-width="120" show-overflow-tooltip />
      <el-table-column prop="fullName" label="姓名" min-width="100" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <button class="act-btn--del" @click="emit('remove', row)">移除</button>
        </template>
      </el-table-column>
      <template #empty
        ><EmptyState title="该班级暂无学生" icon="users"
      /></template>
    </el-table>
  </el-dialog>

  <!-- 添加学生对话框 -->
  <el-dialog
    :model-value="addVisible"
    title="添加学生到班级"
    width="620px"
    append-to-body
    @update:model-value="emit('update:addVisible', $event)"
  >
    <div class="student-toolbar">
      <el-input
        :model-value="candidateKeyword"
        placeholder="搜索学生姓名 / 学号"
        clearable
        style="width: 280px"
        @update:model-value="emit('update:candidateKeyword', $event)"
      />
    </div>
    <el-table
      v-loading="candidateLoading"
      :data="filteredCandidates"
      max-height="380"
      style="width: 100%"
    >
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="学号" min-width="120" show-overflow-tooltip />
      <el-table-column prop="fullName" label="姓名" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <button class="act-btn act-btn--approve" @click="emit('add', row)">添加</button>
        </template>
      </el-table-column>
      <template #empty
        ><EmptyState title="无可添加学生" icon="users"
      /></template>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { Plus, Search } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 班级成员管理（成员列表 + 添加学生双对话框）
 * <p>成员表展示当前班级学生，支持搜索/移除；添加学生对话框展示全量候选学生
 * （排除已入班、关键字过滤），添加后刷新成员列表。全部状态与操作由父组件
 * （useClassrooms）承载，本组件仅负责展示与事件上抛。</p>
 */
defineProps({
  /** 成员管理对话框可见性（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前班级对象 */
  currentClass: { type: Object, default: null },
  /** 成员列表 */
  studentList: { type: Array, default: () => [] },
  studentLoading: { type: Boolean, default: false },
  /** 成员搜索关键字（双向） */
  studentKeyword: { type: String, default: '' },
  /** 添加学生对话框可见性（v-model） */
  addVisible: { type: Boolean, default: false },
  /** 候选学生列表 */
  filteredCandidates: { type: Array, default: () => [] },
  candidateLoading: { type: Boolean, default: false },
  /** 候选搜索关键字（双向） */
  candidateKeyword: { type: String, default: '' },
})

const emit = defineEmits([
  'update:modelValue',
  'update:addVisible',
  'update:studentKeyword',
  'update:candidateKeyword',
  'reload',
  'remove',
  'add-student',
  'add',
])
</script>

<style scoped>
.student-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.act-btn,
.act-btn--approve,
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
.act-btn--approve:hover,
.act-btn--del:hover {
  transform: translateY(-1px);
}
.act-btn--approve {
  color: var(--ok);
}
.act-btn--approve:hover {
  background: rgba(21, 128, 61, 0.08);
}
.act-btn--del {
  color: var(--accent-strong);
}
.act-btn--del:hover {
  background: rgba(176, 80, 69, 0.08);
}
</style>
