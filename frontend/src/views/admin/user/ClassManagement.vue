<template>
  <div class="fade-in class-management">
    <PageHeader
      title="组织与班级管理"
      subtitle="按院系 / 专业 / 年级维度组织班级，管理班级成员与班主任"
      icon="building"
      icon-bg="accent"
    />

    <!-- 概览统计：班级总数 / 专业数 / 在册学生 / 平均班额 -->
    <div class="stat-grid">
      <StatCard
        label="班级总数"
        :value="classStats[0].value"
        icon="grid"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="专业数"
        :value="classStats[1].value"
        icon="layers"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="在册学生"
        :value="classStats[2].value"
        icon="users"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="平均班额"
        :value="classStats[3].value"
        icon="chart"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18zM2 22h20M9 6h1M14 6h1M9 10h1M14 10h1M9 14h1M14 14h1M10 22v-3h4v3"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">班级列表</div>
          <div class="l1-sub">选择组织维度筛选，新建或维护班级</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="layout-split">
          <!-- 左侧：组织树 -->
          <ClassOrgTree
            ref="orgTreeRef"
            :org-tree="orgTree"
            @node-click="onOrgNodeClick"
            @reset="resetOrgFilter"
          />

          <!-- 右侧：班级列表 -->
          <ClassListPanel
            :paged-list="pagedList"
            :filtered-list="filteredList"
            :loading="loading"
            :keyword="keyword"
            :page="page"
            :page-size="pageSize"
            :active-org-label="activeOrgLabel"
            @update:keyword="keyword = $event"
            @search="loadClassrooms"
            @page-change="page = $event"
            @create="openCreate"
            @members="manageStudents"
            @edit="openEdit"
            @remove="removeClassroom"
          />
        </div>
      </div>
    </section>

    <!-- 新建/编辑班级 -->
    <ClassEditDialog
      ref="formRef"
      v-model="showEdit"
      :editing="editing"
      :form="form"
      :rules="rules"
      :saving="saving"
      @save="saveClassroom"
    />

    <!-- 班级成员管理 + 添加学生 -->
    <ClassMemberManagerDialog
      v-model="showStudents"
      v-model:add-visible="showAddStudent"
      v-model:student-keyword="studentKeyword"
      v-model:candidate-keyword="candidateKeyword"
      :current-class="currentClass"
      :student-list="studentList"
      :student-loading="studentLoading"
      :filtered-candidates="filteredCandidates"
      :candidate-loading="candidateLoading"
      @reload="loadStudents"
      @remove="removeStudentFromClass"
      @add-student="openAddStudent"
      @add="addStudentToClass"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useClassrooms } from '@/composables/useClassrooms'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import ClassOrgTree from './components/ClassOrgTree.vue'
import ClassListPanel from './components/ClassListPanel.vue'
import ClassEditDialog from './components/ClassEditDialog.vue'
import ClassMemberManagerDialog from './components/ClassMemberManagerDialog.vue'

/**
 * 组织与班级管理页面（管理员视角）
 * <p>页面壳：页头 + 四项概览统计 + 班级列表区（左侧组织树 / 右侧列表）
 * + 新建编辑/成员管理/添加学生三个对话框；全部数据与业务逻辑由 useClassrooms 承载。</p>
 */
const {
  loading,
  saving,
  keyword,
  page,
  pageSize,
  orgTreeRef,
  orgTree,
  filteredList,
  pagedList,
  activeOrgLabel,
  classStats,
  onOrgNodeClick,
  resetOrgFilter,
  loadClassrooms,
  showEdit,
  editing,
  formRef,
  form,
  rules,
  openCreate,
  openEdit,
  saveClassroom,
  removeClassroom,
  showStudents,
  currentClass,
  studentList,
  studentLoading,
  studentKeyword,
  manageStudents,
  loadStudents,
  removeStudentFromClass,
  showAddStudent,
  candidateLoading,
  candidateKeyword,
  filteredCandidates,
  openAddStudent,
  addStudentToClass,
} = useClassrooms()

onMounted(loadClassrooms)
</script>

<style scoped>
.class-management {
  max-width: 1400px;
  margin: 0 auto;
}

.layout-split {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--ac-spacing-lg);
  align-items: start;
}

@media (max-width: 900px) {
  .layout-split {
    grid-template-columns: 1fr;
  }
}
</style>
