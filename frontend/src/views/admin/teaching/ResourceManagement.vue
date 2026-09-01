<template>
  <div class="fade-in resource-management">
    <PageHeader
      title="校本资源库"
      subtitle="按课程管理教学资源，支持上传、检索、授权与下载统计"
      icon="doc"
      icon-bg="accent"
    >
      <el-button type="primary" :disabled="!selectedCourseId" @click="showUpload = true">
        <el-icon><Upload /></el-icon>上传资源
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="资源总数"
        :value="resStats.total"
        icon="grid"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="公开资源"
        :value="resStats.publicCount"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="文档类"
        :value="resStats.docCount"
        icon="doc"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="音视频"
        :value="resStats.mediaCount"
        icon="play"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
    </div>

    <!-- 资源列表 -->
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
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">资源列表</div>
          <div class="l1-sub">按课程检索、授权与下载统计</div>
        </div>
      </div>
      <div class="l1-body">
        <RmFilterBar
          v-model:selected-course-id="selectedCourseId"
          v-model:keyword="keyword"
          v-model:type-filter="typeFilter"
          :course-list="courseList"
          @search="onSearch"
          @upload="showUpload = true"
        />
        <RmResourceTable
          :loading="loading"
          :filtered-resources="filteredResources"
          :selected-course-id="selectedCourseId"
          @toggle-visibility="toggleVisibility"
          @download="downloadRes"
          @remove="removeRes"
        />
      </div>
    </section>

    <!-- 上传弹窗 -->
    <RmUploadDialog
      v-if="showUpload"
      :current-course-name="currentCourseName"
      :uploading="uploading"
      @close="showUpload = false"
      @upload="doUpload"
    />
  </div>
</template>

<script setup lang="ts">
import { Upload } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import RmFilterBar from './components/RmFilterBar.vue'
import RmResourceTable from './components/RmResourceTable.vue'
import RmUploadDialog from './components/RmUploadDialog.vue'
import { useResourceManagement } from '@/composables/useResourceManagement'

const {
  loading,
  courseList,
  selectedCourseId,
  keyword,
  typeFilter,
  showUpload,
  uploading,
  currentCourseName,
  resStats,
  filteredResources,
  onSearch,
  toggleVisibility,
  downloadRes,
  removeRes,
  doUpload,
} = useResourceManagement()
</script>

<style scoped>
.resource-management {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
