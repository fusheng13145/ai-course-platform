<template>
  <div class="fade-in content-card form-root">
    <PageHeader title="创建课程" subtitle="填写课程基本信息，提交后进入审核流程" icon="book" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" scroll-to-error>
      <el-form-item label="课程名称" prop="name"
        ><el-input v-model="form.name" placeholder="输入课程名称"
      /></el-form-item>
      <el-form-item label="课程编码" prop="code"
        ><el-input v-model="form.code" placeholder="输入课程编码（如：CS101）"
      /></el-form-item>
      <el-form-item label="课程分类" prop="category">
        <el-select v-model="form.category" class="w-100" placeholder="选择分类">
          <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
      </el-form-item>
      <el-form-item label="学分"
        ><el-input-number v-model="form.credits" :min="0" :max="10" :precision="1"
      /></el-form-item>
      <el-form-item label="难度">
        <el-select v-model="form.difficulty" class="w-100">
          <el-option label="入门" value="BEGINNER" />
          <el-option label="初级" value="EASY" />
          <el-option label="中级" value="MEDIUM" />
          <el-option label="高级" value="HARD" />
          <el-option label="专家" value="EXPERT" />
        </el-select>
      </el-form-item>
      <el-form-item label="报名方式">
        <el-select v-model="form.enrollmentType" class="w-100">
          <el-option label="开放报名" value="OPEN" />
          <el-option label="需审核" value="APPROVAL" />
          <el-option label="限制报名" value="RESTRICTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="最大人数"
        ><el-input-number v-model="form.maxStudents" :min="0"
      /></el-form-item>
      <el-form-item label="课程简介" prop="description"
        ><el-input v-model="form.description" type="textarea" :rows="4" placeholder="输入课程简介"
      /></el-form-item>
      <el-form-item label="封面图片">
        <el-upload
          class="avatar-uploader"
          :show-file-list="false"
          :http-request="handleCoverUpload"
          :before-upload="beforeUpload"
        >
          <img v-if="form.coverImage" :src="form.coverImage" class="cover-img" />
          <el-icon v-else :size="24"><Plus /></el-icon>
        </el-upload>
      </el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="submitting" @click="handleSubmit">提交审核</el-button
        ><el-button @click="$router.back()">取消</el-button></el-form-item
      >
    </el-form>
  </div>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import courseApi from '@/api/course'
import commonApi from '@/api/common'

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
  coverImage: '',
})

const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

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

/**
 * 自定义上传：走统一 request 实例（HttpOnly Cookie 自动携带 + CSRF 双提交 + 统一错误处理），
 * 替代 el-upload 原生 action 直传（原实现使用恒空的 Bearer token 且绕过拦截器）。
 */
async function handleCoverUpload({ file }) {
  try {
    const res = await commonApi.uploadFile(file)
    const url = res.data?.url
    if (url) {
      form.coverImage = url
      ElMessage.success('上传成功')
    } else {
      ElMessage.error('上传失败：响应缺少文件地址')
    }
  } catch (e) {
    console.error('封面上传失败:', e)
    ElMessage.error('封面上传失败，请稍后重试')
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
        approvalStatus: 'PENDING',
      }
      await courseApi.createCourse(data)
      ElMessage.success('提交成功，等待审核')
      formRef.value?.resetFields()
      Object.assign(form, {
        name: '',
        code: '',
        category: '',
        description: '',
        credits: 2,
        difficulty: 'MEDIUM',
        enrollmentType: 'OPEN',
        maxStudents: 100,
        coverImage: '',
      })
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.form-root {
  max-width: 800px;
  margin: 0 auto;
}
.w-100 {
  width: 100%;
}
.cover-img {
  width: 148px;
  height: 148px;
  display: block;
}
</style>
