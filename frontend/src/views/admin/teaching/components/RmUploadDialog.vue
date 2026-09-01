<template>
  <el-dialog v-model="visible" title="上传资源" width="520px" append-to-body>
    <el-form label-width="80px">
      <el-form-item label="所属课程">
        <el-input :model-value="currentCourseName" disabled />
      </el-form-item>
      <el-form-item label="资源描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="选填，简要描述资源内容"
        />
      </el-form-item>
      <el-form-item label="选择文件">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="onFileChange"
          :on-exceed="onExceed"
          drag
          style="width: 100%"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="upload-tip">支持 PDF/Word/Excel/图片/音视频，单文件 ≤ 50MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="submit">上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

defineProps({
  currentCourseName: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'upload'])

const visible = ref(true)
const uploadRef = ref(null)
const form = reactive({ description: '' })
const selectedFile = ref(null)

function onFileChange(file) {
  selectedFile.value = file.raw
}
function onExceed(files) {
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(files[0])
  selectedFile.value = files[0].raw || files[0]
}

function submit() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  if (selectedFile.value.size > 50 * 1024 * 1024) {
    ElMessage.error('文件超过 50MB 限制')
    return
  }
  emit('upload', { file: selectedFile.value, description: form.description || '' })
}
</script>

<style scoped>
.upload-icon {
  font-size: 40px;
  color: var(--ac-text-muted);
  margin-bottom: 8px;
}
.upload-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 6px;
}
</style>
