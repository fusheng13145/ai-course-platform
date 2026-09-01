<template>
  <div class="course-question-bank fade-in">
    <PageHeader
      title="课程题库"
      subtitle="维护本课程题目，支持 AI 智能出题与一键审核"
      icon="layers"
      icon-bg="info"
    >
      <el-button :icon="RefreshLeft" @click="resetFilter">重置</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建题目</el-button>
      <el-button :icon="MagicStick" @click="openAIDialog">AI 生成</el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="题目总数"
        :value="total"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="待审核"
        :value="countPending"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="已审核"
        :value="countReviewed"
        icon="checks"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="题型种类"
        :value="TYPE_OPTS.length"
        icon="tag"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--info-soft); color: var(--info)">
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
              d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">题目列表</div>
          <div class="l1-sub">按题型、难度与关键词快速检索</div>
        </div>
      </div>
      <div class="l1-body">
        <CqbQuestionTable
          :loading="loading"
          :list="list"
          :filters="filters"
          :type-opts="TYPE_OPTS"
          :diff-opts="DIFF_OPTS"
          :total="total"
          :page="page"
          :size="size"
          @update:type="filters.type = $event"
          @update:difficulty="filters.difficulty = $event"
          @update:keyword="filters.keyword = $event"
          @update:page="page = $event"
          @update:size="size = $event"
          @query="onFilter"
          @edit="openEdit"
          @review="review"
          @copy="copy"
          @remove="remove"
        />
      </div>
    </section>

    <!-- 新建 / 编辑 对话框 -->
    <CqbEditDialog
      v-model="dialog.visible"
      :editing="dialog.editing"
      :saving="dialog.saving"
      :form="form"
      :type-opts="TYPE_OPTS"
      :diff-opts="DIFF_OPTS"
      @closed="resetForm"
      @save="save"
    />

    <!-- AI 生成 对话框 -->
    <CqbAiDialog
      v-model="ai.visible"
      v-model:kp-text="kpText"
      :loading="ai.loading"
      :form="aiForm"
      :type-opts="TYPE_OPTS"
      :diff-opts="DIFF_OPTS"
      @closed="resetAIForm"
      @generate="aiGenerate"
    />
  </div>
</template>

<script setup lang="ts">
import { toRef } from 'vue'
import { useRoute } from 'vue-router'
import { RefreshLeft, Plus, MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CqbQuestionTable from './components/CqbQuestionTable.vue'
import CqbEditDialog from './components/CqbEditDialog.vue'
import CqbAiDialog from './components/CqbAiDialog.vue'
import { useCourseQuestionBank } from '@/composables/useCourseQuestionBank'

const route = useRoute()
const courseIdRef = toRef(route.params, 'id')

const {
  loading,
  list,
  total,
  page,
  size,
  TYPE_OPTS,
  DIFF_OPTS,
  filters,
  countPending,
  countReviewed,
  dialog,
  form,
  ai,
  aiForm,
  kpText,
  onFilter,
  resetFilter,
  openCreate,
  openEdit,
  save,
  review,
  copy,
  remove,
  resetAIForm,
  openAIDialog,
  aiGenerate,
} = useCourseQuestionBank(courseIdRef)
</script>

<style scoped>
.course-question-bank {
  padding: 4px;
}
</style>
