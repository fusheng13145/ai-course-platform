<template>
  <div class="fade-in knowledge-base">
    <PageHeader
      title="知识库管理"
      subtitle="按课程维护 RAG 知识库：导入文档语料、召回测试、智能问答联调，支撑「基于知识库的 AI 助教」"
      icon="layers"
      icon-bg="accent"
    />

    <!-- 模型状态横幅 -->
    <KbModelBanner
      :banner-class="bannerClass"
      :banner-icon="bannerIcon"
      :banner-text="bannerText"
    />

    <div class="stat-grid animate-in d1">
      <StatCard
        label="知识库文档数"
        :value="docs.length"
        icon="doc"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="当前课程"
        :value="currentCourseName || '未选择'"
        icon="building"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="可管理课程"
        :value="courseList.length"
        icon="list"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
      <StatCard
        label="生成模型"
        :value="modelLabel"
        icon="info"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <!-- 知识库文档 -->
    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico ico-accent">
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
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">知识库文档</div>
          <div class="l1-sub">按课程检索与维护文档语料</div>
        </div>
      </div>
      <div class="l1-body">
        <KbDocSection
          v-model:selected-course-id="selectedCourseId"
          v-model:keyword="keyword"
          :course-list="courseList"
          :filtered-docs="filteredDocs"
          :loading="loading"
          :indexing="indexing"
          :preview="preview"
          @course-change="onCourseChange"
          @search="loadDocs"
          @open-add="openAdd"
          @build-index="buildIndex"
          @clear-index="clearIndex"
          @open-edit="openEdit"
          @remove-doc="removeDoc"
        />
      </div>
    </section>

    <!-- RAG 联调面板 -->
    <section class="l1-card animate-in d3">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M12 2a3 3 0 0 0-3 3v1a3 3 0 0 0-3 3 3 3 0 0 0 0 6 3 3 0 0 0 3 3v1a3 3 0 0 0 3 3 3 3 0 0 0 3-3v-1a3 3 0 0 0 3-3 3 3 0 0 0 0-6 3 3 0 0 0-3-3V5a3 3 0 0 0-3-3z"
            /></svg
        ></span>
        <div><h3 class="l1-title">RAG 联调测试</h3></div>
      </div>
      <div class="l1-body">
        <KbRagPlayground
          :selected-course-id="selectedCourseId"
          :ask-form="askForm"
          :ask-result="askResult"
          :asking="asking"
          :retrieve-form="retrieveForm"
          :retrieve-result="retrieveResult"
          :retrieving="retrieving"
          :preview="preview"
          @ask="doAsk"
          @retrieve="doRetrieve"
        />
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <KbDocDialog
      v-model="showDialog"
      :dialog-mode="dialogMode"
      :current-course-name="currentCourseName"
      :form="form"
      :saving="saving"
      @save="saveDoc"
    />
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useKnowledgeBase } from '@/composables/useKnowledgeBase'
import KbModelBanner from './components/KbModelBanner.vue'
import KbDocSection from './components/KbDocSection.vue'
import KbRagPlayground from './components/KbRagPlayground.vue'
import KbDocDialog from './components/KbDocDialog.vue'

/**
 * 知识库管理页（页面壳）
 * <p>模型横幅 + 统计卡 + 文档管理/联调面板双区块编排 + 弹窗装配，业务逻辑委托 useKnowledgeBase。</p>
 */
const {
  loading,
  indexing,
  asking,
  retrieving,
  saving,
  courseList,
  selectedCourseId,
  docs,
  keyword,
  currentCourseName,
  modelLabel,
  bannerClass,
  bannerIcon,
  bannerText,
  filteredDocs,
  preview,
  showDialog,
  dialogMode,
  form,
  askForm,
  askResult,
  retrieveForm,
  retrieveResult,
  loadDocs,
  onCourseChange,
  openAdd,
  openEdit,
  saveDoc,
  removeDoc,
  buildIndex,
  clearIndex,
  doAsk,
  doRetrieve,
} = useKnowledgeBase()
</script>

<style scoped>
.knowledge-base {
  max-width: 1400px;
  margin: 0 auto;
}

.ico-accent {
  background: var(--accent-soft);
  color: var(--accent);
}
</style>
