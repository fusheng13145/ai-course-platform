<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    :title="dialogMode === 'add' ? '新建知识库文档' : '编辑文档内容'"
    width="560px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="所属课程">
        <el-input :model-value="currentCourseName" disabled />
      </el-form-item>
      <el-form-item v-if="dialogMode === 'add'" label="文档名称">
        <el-input v-model="form.name" placeholder="如：第3章 神经网络基础" />
      </el-form-item>
      <el-form-item label="文档内容">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="10"
          placeholder="粘贴或输入作为知识库语料的文本，将用于语义召回与问答生成"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
/**
 * 知识库文档新增/编辑弹窗
 * <p>所属课程只读展示 + 名称（仅新增）+ 内容文本域；保存逻辑委托 composable。</p>
 */
const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add=新建 / edit=编辑 */
  dialogMode: { type: String, default: 'add' },
  /** 当前课程名（只读展示） */
  currentCourseName: { type: String, default: '' },
  /** 表单对象（共享引用：id/name/content） */
  form: { type: Object, required: true },
  /** 保存加载态 */
  saving: { type: Boolean, default: false },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))


defineEmits(['update:modelValue', 'save'])
</script>
