<template>
  <el-form
    ref="formRef"
    :model="form"
    label-width="140px"
    :rules="rules"
    class="settings-form"
  >
    <el-divider content-position="left"><el-icon><Setting /></el-icon>站点信息</el-divider>

    <el-form-item label="站点名称" prop="siteName">
      <el-input v-model="form.siteName" placeholder="请输入站点名称" />
    </el-form-item>

    <el-form-item label="站点描述" prop="siteDescription">
      <el-input
        v-model="form.siteDescription"
        type="textarea"
        :rows="3"
        placeholder="站点描述信息"
      />
    </el-form-item>

    <el-form-item label="站点Logo URL" prop="logoUrl">
      <el-input v-model="form.logoUrl" placeholder="Logo图片地址" />
    </el-form-item>

    <el-form-item label="备案号" prop="icpNumber">
      <el-input v-model="form.icpNumber" placeholder="ICP备案号（可选）" />
    </el-form-item>

    <el-divider content-position="left"><el-icon><User /></el-icon>用户相关</el-divider>

    <el-form-item label="允许注册" prop="allowRegister">
      <el-switch
        v-model="form.allowRegister"
        active-text="开启"
        inactive-text="关闭"
      />
      <span class="form-tip">关闭后新用户无法自行注册</span>
    </el-form-item>

    <el-form-item label="默认角色" prop="defaultRole">
      <el-select v-model="form.defaultRole" class="w-200">
        <el-option label="学生" value="STUDENT" />
        <el-option label="教师" value="TEACHER" />
      </el-select>
    </el-form-item>

    <el-form-item label="每页显示条数" prop="pageSize">
      <el-input-number v-model="form.pageSize" :min="5" :max="100" :step="5" />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存基础设置
      </el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Setting, User } from '@element-plus/icons-vue'

/**
 * 系统设置 — 基础设置表单
 * <p>站点信息 + 用户相关配置；内部校验通过后 emit save。</p>
 */
defineProps({
  /** 基础表单对象（共享引用） */
  form: { type: Object, required: true },
  /** 校验规则 */
  rules: { type: Object, required: true },
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

.form-tip {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: 10px;
}

.w-200 {
  width: 200px;
}
</style>
