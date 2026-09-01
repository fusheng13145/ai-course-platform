<template>
  <!-- 发起签到 -->
  <el-dialog
    :model-value="modelValue"
    title="发起签到"
    width="460px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="签到标题" required>
        <el-input
          v-model="form.title"
          placeholder="如：第3章课堂签到"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="时长(秒)">
        <el-input-number v-model="form.duration" :min="30" :max="3600" :step="30" />
      </el-form-item>
      <el-form-item label="签到类型">
        <el-select v-model="form.type" style="width: 100%">
          <el-option label="普通签到" value="NORMAL" />
          <el-option label="二维码签到" value="QR" />
          <el-option label="定位签到" value="LOCATION" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')"
        >发起并发布</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps({
  modelValue: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  form: { type: Object, default: () => ({}) },
})
defineEmits(['update:modelValue', 'submit'])
</script>
