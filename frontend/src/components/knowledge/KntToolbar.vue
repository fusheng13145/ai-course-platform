<template>
  <!-- 工具栏 -->
  <div class="tab-toolbar">
    <div style="display: flex; gap: 8px; align-items: center">
      <el-button type="primary" plain size="small" @click="$emit('create')"
        ><Plus :size="14" /> 新建知识点</el-button
      >
      <el-button size="small" @click="$emit('ai')"><MagicStick :size="12" /> AI提取</el-button>
      <el-button size="small" :disabled="exportDisabled" @click="$emit('export')"
        ><Download :size="12" /> 导出</el-button
      >
    </div>
    <el-input
      :model-value="searchText"
      placeholder="搜索知识点..."
      clearable
      style="width: 200px"
      :prefix-icon="Search"
      @update:model-value="$emit('update:searchText', $event)"
    />
  </div>
</template>

<script setup>
import { Search, Plus, Download, MagicStick } from '@element-plus/icons-vue'

defineProps({
  searchText: { type: String, default: '' },
  exportDisabled: { type: Boolean, default: false },
})
defineEmits(['update:searchText', 'create', 'ai', 'export'])
</script>

<style scoped>
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
