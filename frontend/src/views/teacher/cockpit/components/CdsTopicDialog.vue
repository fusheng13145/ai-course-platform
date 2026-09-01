<template>
  <el-dialog v-model="visible" title="发起话题" width="480px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="话题标题" maxlength="60" />
      </el-form-item>
      <el-form-item label="内容" required>
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

defineProps({
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'submit'])

const visible = ref(true)
const form = reactive({ title: '', content: '', chapterId: '' })

function submit() {
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
