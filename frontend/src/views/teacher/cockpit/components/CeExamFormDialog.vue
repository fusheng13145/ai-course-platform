<template>
  <el-dialog
    ref="dialogRef"
    :model-value="modelValue"
    :title="editing ? '编辑试卷' : '新建试卷'"
    width="520px"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <el-form :model="form" label-width="84px">
      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="请输入试卷标题" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="请输入试卷描述"
        />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number
          v-model="form.duration"
          :min="1"
          :max="600"
          controls-position="right"
          class="w-100"
        />
      </el-form-item>
      <el-form-item label="总分">
        <el-input-number
          v-model="form.totalScore"
          :min="1"
          :max="1000"
          controls-position="right"
          class="w-100"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
/**
 * 课程试卷 — 新建/编辑对话框。
 * <p>拆分子组件：表单双向绑定由页面壳传入的 form 对象承接（共享引用），
 * 保存动作 emit('save') 交还页面壳。</p>
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editing: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  /** 表单对象（页面壳持有共享引用，表单双向绑定直接生效） */
  form: { type: Object, required: true },
})
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, computed(() => props.modelValue))


const emit = defineEmits(['update:modelValue', 'save', 'closed'])
</script>

<style scoped>
.w-100 {
  width: 100%;
}
</style>
