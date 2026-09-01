<template>
  <el-form ref="formRef" :model="form" label-width="160px" class="settings-form">
    <el-divider content-position="left"><el-icon><Upload /></el-icon>文件大小限制</el-divider>

    <el-form-item label="单文件最大(MB)" prop="maxFileSize">
      <el-slider
        v-model="form.maxFileSize"
        :min="1"
        :max="100"
        :step="1"
        show-input
        input-size="small"
        class="w-400"
      />
    </el-form-item>

    <el-form-item label="总存储配额(GB)" prop="totalQuota">
      <el-input-number v-model="form.totalQuota" :min="1" :max="1000" />
    </el-form-item>

    <el-divider content-position="left"><el-icon><Document /></el-icon>文件格式限制</el-divider>

    <el-form-item label="允许的图片格式" prop="imageFormats">
      <el-tag
        v-for="fmt in form.imageFormats"
        :key="fmt"
        closable
        class="format-tag"
        @close="removeFormat('image', fmt)"
        >{{ fmt }}</el-tag
      >
      <el-input
        v-if="inputVisible.image"
        v-model="inputValue.image"
        size="small"
        class="w-100"
        @keyup.enter="confirmFormat('image')"
        @blur="confirmFormat('image')"
      />
      <el-button v-else size="small" @click="showInput('image')"
        ><el-icon><Plus /></el-icon
      ></el-button>
    </el-form-item>

    <el-form-item label="允许的文档格式" prop="docFormats">
      <el-tag
        v-for="fmt in form.docFormats"
        :key="fmt"
        closable
        class="format-tag"
        @close="removeFormat('doc', fmt)"
        >{{ fmt }}</el-tag
      >
      <el-input
        v-if="inputVisible.doc"
        v-model="inputValue.doc"
        size="small"
        class="w-100"
        @keyup.enter="confirmFormat('doc')"
        @blur="confirmFormat('doc')"
      />
      <el-button v-else size="small" @click="showInput('doc')"
        ><el-icon><Plus /></el-icon
      ></el-button>
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存上传配置
      </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Upload, Document, Plus } from '@element-plus/icons-vue'

/**
 * 系统设置 — 上传配置表单
 * <p>文件大小/配额 + 图片/文档格式标签增删。</p>
 */
defineProps({
  /** 上传表单对象（共享引用） */
  form: { type: Object, required: true },
  /** 格式输入显隐状态（image/doc） */
  inputVisible: { type: Object, required: true },
  /** 格式输入值（image/doc） */
  inputValue: { type: Object, required: true },
  /** 显示输入框函数 */
  showInput: { type: Function, required: true },
  /** 确认添加格式函数 */
  confirmFormat: { type: Function, required: true },
  /** 移除格式函数 */
  removeFormat: { type: Function, required: true },
  /** 保存加载态 */
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['save'])

const formRef = ref(null)

async function handleSave() {
  const valid = formRef.value ? await formRef.value.validate().catch(() => false) : true
  if (!valid) return
  emit('save')
}
</script>

<style scoped>
.settings-form {
  max-width: 650px;
}

.settings-form .el-divider {
  margin: 24px 0 16px;
  border-color: var(--ac-border);
}

.settings-form .el-divider__text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  background: transparent;
  padding: 0;
}

.w-400 {
  width: 400px;
}
.w-100 {
  width: 100px;
}

.format-tag {
  margin-right: 6px;
  margin-top: 4px;
}
</style>
