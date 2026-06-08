<template>
  <div>
    <el-page-header :title="course.name" @back="$router.push('/teacher/courses')" style="margin-bottom:20px" />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="info">
        <div class="content-card">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程名称">{{ course.name }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ course.category || '未分类' }}</el-descriptions-item>
            <el-descriptions-item label="学生人数">{{ course.studentCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="course.approvalStatus === 'APPROVED' ? 'success' : course.approvalStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">{{ course.approvalStatus === 'APPROVED' ? '已通过' : course.approvalStatus === 'REJECTED' ? '已拒绝' : '待审核' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="学分">{{ course.credits || 0 }}</el-descriptions-item>
            <el-descriptions-item label="难度">{{ course.difficulty || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="简介" :span="2">{{ course.description || '暂无简介' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:20px">
            <el-button type="primary" @click="showEditDialog">编辑课程</el-button>
            <el-button @click="toggleStatus" :loading="toggling">{{ course.status === 'ACTIVE' ? '停用课程' : '启用课程' }}</el-button>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane label="章节管理" name="chapters">
        <div class="table-toolbar"><el-button type="primary" @click="showAddChapterDialog"><el-icon><Plus /></el-icon>添加章节</el-button></div>
        <div class="content-card">
          <el-table :data="chapters" stripe style="width:100%" v-loading="chaptersLoading">
            <el-table-column prop="id" label="序号" width="60" />
            <el-table-column prop="title" label="章节名称" min-width="200" />
            <el-table-column prop="duration" label="时长(分钟)" width="100"><template #default="{row}">{{ row.duration || 0 }}</template></el-table-column>
            <el-table-column prop="orderIndex" label="排序" width="80" />
            <el-table-column prop="isPublished" label="发布状态" width="100"><template #default="{row}"><el-tag :type="row.isPublished ? 'success' : 'info'" size="small">{{ row.isPublished ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{row}">
                <el-button text type="primary" @click="editChapter(row)">编辑</el-button>
                <el-button text type="danger" @click="deleteChapter(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!chaptersLoading && chapters.length === 0" description="暂无章节" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="学生管理" name="students">
        <div class="content-card">
          <el-table :data="students" stripe style="width:100%" v-loading="studentsLoading">
            <el-table-column prop="studentId" label="学号" width="120" />
            <el-table-column prop="studentName" label="姓名" min-width="120" />
            <el-table-column prop="progress" label="进度" width="120"><template #default="{row}"><el-progress :percentage="Math.round((row.progress || 0) * 100)" :stroke-width="6" /></template></el-table-column>
            <el-table-column prop="approvalStatus" label="状态" width="100"><template #default="{row}"><el-tag :type="row.approvalStatus === 'APPROVED' ? 'success' : 'warning'" size="small">{{ row.approvalStatus === 'APPROVED' ? '已通过' : '待审核' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{row}">
                <el-button text type="success" v-if="row.approvalStatus === 'PENDING'" @click="approveStudent(row)">通过</el-button>
                <el-button text type="danger" v-if="row.approvalStatus === 'PENDING'" @click="rejectStudent(row)">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!studentsLoading && students.length === 0" description="暂无学生" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Add/Edit Chapter Dialog -->
    <el-dialog v-model="chapterDialogVisible" :title="editingChapter ? '编辑章节' : '添加章节'" width="500px">
      <el-form :model="chapterForm" label-width="80px">
        <el-form-item label="章节名称"><el-input v-model="chapterForm.title" placeholder="输入章节名称" /></el-form-item>
        <el-form-item label="时长(分钟)"><el-input-number v-model="chapterForm.duration" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="chapterForm.orderIndex" :min="0" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="chapterForm.content" type="textarea" :rows="4" placeholder="章节内容" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="chapterDialogVisible = false">取消</el-button><el-button type="primary" @click="saveChapter" :loading="savingChapter">保存</el-button></template>
    </el-dialog>

    <!-- Edit Course Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑课程" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="课程名称"><el-input v-model="editForm.name" placeholder="输入课程名称" /></el-form-item>
        <el-form-item label="课程编码"><el-input v-model="editForm.code" placeholder="输入课程编码" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" style="width:100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="学分"><el-input-number v-model="editForm.credits" :min="0" :max="10" :precision="1" /></el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editForm.difficulty" style="width:100%">
            <el-option label="入门" value="BEGINNER" />
            <el-option label="初级" value="EASY" />
            <el-option label="中级" value="MEDIUM" />
            <el-option label="高级" value="HARD" />
            <el-option label="专家" value="EXPERT" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名方式">
          <el-select v-model="editForm.enrollmentType" style="width:100%">
            <el-option label="开放报名" value="OPEN" />
            <el-option label="需审核" value="APPROVAL" />
            <el-option label="限制报名" value="RESTRICTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大人数"><el-input-number v-model="editForm.maxStudents" :min="0" /></el-form-item>
        <el-form-item label="课程简介"><el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="输入课程简介" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editDialogVisible = false">取消</el-button><el-button type="primary" @click="saveCourse" :loading="savingCourse">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import courseApi from '@/api/course'
import chapterApi from '@/api/chapter'
import enrollmentApi from '@/api/enrollment'

const route = useRoute()
const activeTab = ref('info')
const course = reactive({ id: null, name: '加载中...', category: '', approvalStatus: '', description: '', studentCount: 0, credits: 0, difficulty: '', status: '' })
const chapters = ref([])
const students = ref([])
const chaptersLoading = ref(false)
const studentsLoading = ref(false)
const toggling = ref(false)

// Chapter dialog
const chapterDialogVisible = ref(false)
const editingChapter = ref(null)
const chapterForm = reactive({ title: '', duration: 0, orderIndex: 0, content: '' })
const savingChapter = ref(false)

// Course edit dialog
const editDialogVisible = ref(false)
const editForm = reactive({ name: '', code: '', category: '', description: '', credits: 0, difficulty: 'MEDIUM', enrollmentType: 'OPEN', maxStudents: 100 })
const savingCourse = ref(false)
const categories = ref(['编程开发', '人工智能', '数据分析', '设计创意', '其他'])

onMounted(async () => {
  const courseId = route.params.id
  course.id = courseId
  await loadCourse()
  await loadChapters()
  await loadStudents()
})

async function loadCourse() {
  try {
    const res = await courseApi.getCourseById(course.id)
    if (res.data) Object.assign(course, res.data)
  } catch (e) {
    ElMessage.error('加载课程失败')
  }
}

async function loadChapters() {
  chaptersLoading.value = true
  try {
    const res = await chapterApi.getChapters(course.id)
    chapters.value = res.data || res || []
  } catch (e) {
    console.error('Failed to load chapters:', e)
  } finally {
    chaptersLoading.value = false
  }
}

async function loadStudents() {
  studentsLoading.value = true
  try {
    const res = await courseApi.getStudents(course.id)
    const list = res.data?.list || res.data || res || []
    students.value = list
  } catch (e) {
    console.error('Failed to load students:', e)
  } finally {
    studentsLoading.value = false
  }
}

function showEditDialog() {
  Object.assign(editForm, {
    name: course.name || '',
    code: course.code || '',
    category: course.category || '',
    description: course.description || '',
    credits: course.credits || 0,
    difficulty: course.difficulty || 'MEDIUM',
    enrollmentType: course.enrollmentType || 'OPEN',
    maxStudents: course.maxStudents || 100
  })
  editDialogVisible.value = true
}

async function saveCourse() {
  if (!editForm.name) {
    ElMessage.warning('请输入课程名称')
    return
  }
  savingCourse.value = true
  try {
    await courseApi.updateCourse(course.id, editForm)
    Object.assign(course, editForm)
    editDialogVisible.value = false
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    savingCourse.value = false
  }
}

async function toggleStatus() {
  toggling.value = true
  try {
    await courseApi.toggleCourseStatus(course.id)
    course.status = course.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    ElMessage.success('状态切换成功')
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    toggling.value = false
  }
}

function showAddChapterDialog() {
  editingChapter.value = null
  Object.assign(chapterForm, { title: '', duration: 0, orderIndex: chapters.value.length, content: '' })
  chapterDialogVisible.value = true
}

function editChapter(row) {
  editingChapter.value = row
  Object.assign(chapterForm, { title: row.title, duration: row.duration || 0, orderIndex: row.orderIndex || 0, content: row.content || '' })
  chapterDialogVisible.value = true
}

async function saveChapter() {
  if (!chapterForm.title) {
    ElMessage.warning('请输入章节名称')
    return
  }
  savingChapter.value = true
  try {
    const data = { ...chapterForm, courseId: Number(course.id) }
    if (editingChapter.value) {
      await chapterApi.updateChapter(editingChapter.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await chapterApi.createChapter(data)
      ElMessage.success('添加成功')
    }
    chapterDialogVisible.value = false
    await loadChapters()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingChapter.value = false
  }
}

async function deleteChapter(row) {
  try {
    await ElMessageBox.confirm('确定删除该章节？', '提示', { type: 'warning' })
    await chapterApi.deleteChapter(row.id)
    chapters.value = chapters.value.filter(c => c.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function approveStudent(row) {
  try {
    await enrollmentApi.approveEnrollment(row.id)
    row.approvalStatus = 'APPROVED'
    ElMessage.success('审核通过')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function rejectStudent(row) {
  try {
    await ElMessageBox.confirm('确定拒绝该学生？', '提示', { type: 'warning' })
    await enrollmentApi.rejectEnrollment(row.id)
    row.approvalStatus = 'REJECTED'
    ElMessage.success('已拒绝')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}
</script>