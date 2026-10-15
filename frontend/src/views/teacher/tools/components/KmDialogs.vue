<template>
  <!-- 新建/编辑知识点对话框 -->
  <el-dialog
    :model-value="formDialogVisible"
    :title="isEdit ? '编辑知识点' : '新建知识点'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:formVisible', $event)"
  >
    <el-form ref="formRef" :model="kpForm" :rules="formRules" label-width="100px">
      <el-form-item label="知识点名称" prop="name">
        <el-input
          v-model="kpForm.name"
          placeholder="输入知识点名称"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="所属课程" prop="courseId">
        <el-select v-model="kpForm.courseId" placeholder="选择课程" class="w-100" filterable>
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="父级知识点">
        <el-tree-select
          v-model="kpForm.parentId"
          :data="flatTreeOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          check-strictly
          clearable
          placeholder="选择父级（可选）"
          class="w-100"
          filterable
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="难度" prop="difficulty">
            <el-select v-model="kpForm.difficulty" class="w-100">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重要度" prop="importance">
            <el-rate v-model="kpForm.importance" :max="3" show-text :texts="['低', '中', '高']" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述">
        <el-input
          v-model="kpForm.description"
          type="textarea"
          :rows="3"
          placeholder="知识点描述（可选）"
          maxlength="300"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:formVisible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('submit')">{{
        isEdit ? '保存修改' : '创建'
      }}</el-button>
    </template>
  </el-dialog>

  <!-- 关联关系对话框 -->
  <el-dialog
    :model-value="relationVisible"
    title="管理关联关系"
    width="520px"
    @update:model-value="emit('update:relationVisible', $event)"
  >
    <div v-if="currentKpForRelation">
      <p class="rel-desc">
        当前知识点：<strong>{{ currentKpForRelation.name }}</strong>
      </p>

      <!-- 添加前置依赖 -->
      <div class="rel-section">
        <h4 class="rel-heading">添加前置依赖</h4>
        <div class="rel-row">
          <el-select
            :model-value="newPrereqId"
            placeholder="选择前置知识点"
            clearable
            filterable
            class="rel-select"
            @update:model-value="emit('update:newPrereqId', $event)"
          >
            <el-option v-for="k in availablePrereqs" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
          <el-button
            type="primary"
            size="small"
            :disabled="!newPrereqId"
            @click="emit('add-prereq')"
            >添加</el-button
          >
        </div>
      </div>

      <!-- 已有前置依赖列表 -->
      <div>
        <h4 class="rel-heading">已有前置依赖</h4>
        <div v-if="prerequisitesList.length > 0" class="relation-list">
          <div v-for="p in prerequisitesList" :key="p.id" class="relation-item">
            <span class="relation-arrow">←</span>
            <span class="relation-name">{{ p.name }}</span>
            <el-button text type="danger" size="small" @click="emit('remove-prereq', p.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <EmptyState v-else title="暂无前置依赖" icon="list" size="compact" />
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 知识管理对话框（新建/编辑 + 关联关系）
 * <p>表单对象/校验/课程选项经 props 注入；保存与关联操作经事件上抛父组件
 * （useKnowledgeManagement）。表单校验能力经 defineExpose 暴露。</p>
 */
defineProps({
  formDialogVisible: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  kpForm: { type: Object, required: true },
  formRules: { type: Object, default: () => ({}) },
  courseList: { type: Array, default: () => [] },
  flatTreeOptions: { type: Array, default: () => [] },
  relationVisible: { type: Boolean, default: false },
  currentKpForRelation: { type: Object, default: null },
  newPrereqId: { type: [Number, String], default: null },
  availablePrereqs: { type: Array, default: () => [] },
  prerequisitesList: { type: Array, default: () => [] },
})

const emit = defineEmits([
  'update:formVisible',
  'update:relationVisible',
  'update:newPrereqId',
  'submit',
  'add-prereq',
  'remove-prereq',
])

// 暴露表单校验（父组件 submitForm 调用）
const formRef = ref(null)
defineExpose({
  validate: (cb) => formRef.value?.validate(cb),
})
</script>

<style scoped>
.w-100 {
  width: 100%;
}
.rel-desc {
  margin-bottom: 16px;
  color: var(--ac-text-secondary);
  font-size: 13px;
}
.rel-desc strong {
  color: var(--ac-primary);
}
.rel-section {
  margin-bottom: 16px;
}
.rel-heading {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--ac-text-primary);
}
.rel-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.rel-select {
  flex: 1;
}
.relation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.relation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  transition: all var(--ac-transition-fast);
}
.relation-item:hover {
  background: var(--ac-bg-hover);
}
.relation-arrow {
  color: var(--ac-secondary);
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}
.relation-name {
  font-size: 13px;
  color: var(--ac-text-secondary);
  flex: 1;
}
</style>
