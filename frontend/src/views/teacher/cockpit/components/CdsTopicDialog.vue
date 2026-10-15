<template>
  <el-dialog v-model="visible" title="发起话题" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" scroll-to-error>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="话题标题" maxlength="60" />
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="4" placeholder="话题内容" />
      </el-form-item>
      <el-form-item label="章节ID">
        <el-input v-model="form.chapterId" placeholder="可选，关联章节" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { validateFormFields, type ElFormLike } from '@/composables/useFormSubmit'

defineProps({
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'submit'])

const visible = ref(true)
const formRef = ref<ElFormLike | null>(null)
const form = reactive({ title: '', content: '', chapterId: '' })

// D2-4：必填规则声明（原为手写 if 校验），blur 内联错误 + 提交时首错滚动定位
const rules = {
  title: [{ required: true, message: '请填写话题标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写话题内容', trigger: 'blur' }],
}

async function submit() {
  // validate 先行（真实环境内联错误 + scroll-to-error 定位首错）；
  // 表单未挂载/测试桩环境下 validateFormFields 返回 true，由下方快检兜底
  if (!(await validateFormFields(formRef, ['title', 'content']))) return
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  emit('submit', {
    title: form.title.trim(),
    content: form.content.trim(),
    chapterId: form.chapterId.trim(),
  })
}
</script>
