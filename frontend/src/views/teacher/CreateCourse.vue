<template>
  <div class="content-card" style="max-width:800px;margin:0 auto">
    <h2 style="margin-bottom:24px">创建课程</h2>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="课程名称" prop="name"><el-input v-model="form.name" placeholder="输入课程名称" /></el-form-item>
      <el-form-item label="课程分类" prop="category"><el-select v-model="form.category" style="width:100%"><el-option label="编程开发" value="编程开发" /><el-option label="人工智能" value="人工智能" /><el-option label="数据分析" value="数据分析" /><el-option label="设计创意" value="设计创意" /><el-option label="其他" value="其他" /></el-select></el-form-item>
      <el-form-item label="课程简介" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" placeholder="输入课程简介" /></el-form-item>
      <el-form-item label="封面图片"><el-upload class="avatar-uploader" action="#"><el-icon :size="24"><Plus /></el-icon></el-upload></el-form-item>
      <el-form-item><el-button type="primary" @click="handleSubmit">提交审核</el-button><el-button @click="$router.back()">取消</el-button></el-form-item>
    </el-form>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import courseApi from '@/api/course'
const formRef = ref(null)
const form = reactive({ name: '', category: '', description: '' })
const rules = { name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }], category: [{ required: true, message: '请选择分类', trigger: 'change' }] }
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try { await courseApi.createCourse(form); ElMessage.success('提交成功，等待审核'); formRef.value?.resetFields() }
    catch (e) { ElMessage.error('提交失败') }
  })
}
</script>