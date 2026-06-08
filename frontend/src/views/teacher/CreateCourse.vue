<template>
  <div class="fade-in content-card" style="max-width:800px;margin:0 auto">
    <h2 style="margin-bottom:24px">创建课程</h2>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="课程名称" prop="name"><el-input v-model="form.name" placeholder="输入课程名称" /></el-form-item>
      <el-form-item label="课程编码" prop="code"><el-input v-model="form.code" placeholder="输入课程编码（如：CS101）" /></el-form-item>
      <el-form-item label="课程分类" prop="category">
        <el-select v-model="form.category" style="width:100%" placeholder="选择分类">
          <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
      </el-form-item>
      <el-form-item label="学分"><el-input-number v-model="form.credits" :min="0" :max="10" :precision="1" /></el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" style="width:100%">
          <el-option label="入门" value="BEGINNER" />
          <el-option label="初级" value="EASY" />
          <el-option label="中级" value="MEDIUM" />
          <el-option label="高级" value="HARD" />
          <el-option label="专家" value="EXPERT" />
        </el-select>
      </el-form-item>
      <el-form-item label="报名方式">
        <el-select v-model="form.enrollmentType" style="width:100%">
          <el-option label="开放报名" value="OPEN" />
          <el-option label="需审核" value="APPROVAL" />
          <el-option label="限制报名" value="RESTRICTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="最大人数"><el-input-number v-model="form.maxStudents" :min="0" /></el-form-item>
      <el-form-item label="课程简介" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" placeholder="输入课程简介" /></el-form-item>
      <el-form-item label="封面图片">
        <el-upload class="avatar-uploader" :action="uploadUrl" :headers="uploadHeaders" :show-file-list="false" :on-success="handleUploadSuccess" :before-upload="beforeUpload">
          <img v-if="form.coverImage" :src="form.coverImage" style="width:148px;height:148px;display:block" />
          <el-icon v-else :size="24"><Plus /></el-icon>
        </el-upload>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="handleSubmit" :loading="submitting">提交审核</el-button><el-button @click="$router.back()">取消</el-button></el-form-item>
    </el-form>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import courseApi from '@/api/course'

const auth = useAuthStore()
const formRef = ref(null)
const submitting = ref(false)
const categories = ref(['编程开发', '人工智能', '数据分析', '设计创意', '其他'])

const form = reactive({
  name: '',
  code: '',
  category: '',
  description: '',
  credits: 2,
  difficulty: 'MEDIUM',
  enrollmentType: 'OPEN',
  maxStudents: 100,
  coverImage: ''
})

const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

const uploadUrl = '/api/common/upload'
const uploadHeaders = { Authorization: `Bearer ${auth.token}` }

function beforeUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

function handleUploadSuccess(res) {
  if (res.data?.url) {
    form.coverImage = res.data.url
    ElMessage.success('上传成功')
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const data = {
        ...form,
        status: 'DRAFT',
        approvalStatus: 'PENDING'
      }
      await courseApi.createCourse(data)
      ElMessage.success('提交成功，等待审核')
      formRef.value?.resetFields()
      Object.assign(form, { name: '', code: '', category: '', description: '', credits: 2, difficulty: 'MEDIUM', enrollmentType: 'OPEN', maxStudents: 100, coverImage: '' })
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>