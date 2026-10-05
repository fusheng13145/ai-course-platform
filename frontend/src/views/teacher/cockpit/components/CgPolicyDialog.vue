<template>
  <el-dialog
    :model-value="modelValue"
    :title="policyForm.id ? '编辑成绩方案' : '新建成绩方案'"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="policyForm" label-width="92px">
      <el-form-item label="方案名称" required>
        <el-input v-model="policyForm.name" placeholder="如：平时成绩+期末" maxlength="40" />
      </el-form-item>
      <el-form-item label="构成项">
        <div class="comp-list">
          <div v-for="(c, i) in policyForm.components" :key="i" class="comp-row">
            <el-input v-model="c.key" placeholder="标识 key" class="comp-key" />
            <el-input v-model="c.name" placeholder="名称" class="comp-name" />
            <el-input-number
              v-model="c.weight"
              :min="0"
              :max="100"
              :step="5"
              controls-position="right"
              class="comp-weight"
            />
            <el-button :icon="Delete" circle plain type="danger" @click="removeComponent(i)" />
          </div>
          <el-button :icon="Plus" plain @click="addComponent">添加构成项</el-button>
        </div>
      </el-form-item>
      <el-alert
        v-if="!weightBalanced"
        :title="'构成项权重合计为 ' + weightSumText + '%，必须等于 100% 才能保存（后端同样会校验）'"
        type="error"
        :closable="false"
        show-icon
      />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!weightBalanced" @click="savePolicy"
        >保存</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'
import gradeApi from '@/api/grade'
import { Plus, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  policy: { type: Object, default: null },
  courseId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const saving = ref(false)
const policyForm = reactive({ id: null, courseId: null, name: '', components: [] })
const weightSum = computed(() =>
  policyForm.components.reduce((s, c) => s + (Number(c.weight) || 0), 0),
)
// 与后端 WEIGHT_TOLERANCE 一致：33.3+33.3+33.4 在浮点下并非精确 100
const weightBalanced = computed(() => Math.abs(weightSum.value - 100) <= 0.01)
const weightSumText = computed(() => Math.round(weightSum.value * 100) / 100)

function addComponent() {
  policyForm.components.push({ key: '', name: '', weight: 0 })
}
function removeComponent(i) {
  policyForm.components.splice(i, 1)
}

function open(existing) {
  if (existing) {
    policyForm.id = existing.id
    policyForm.name = existing.name
    policyForm.components = (existing.components || []).map((c) => ({ ...c }))
  } else {
    policyForm.id = null
    policyForm.name = ''
    policyForm.components = [
      { key: 'usual', name: '平时', weight: 40 },
      { key: 'final', name: '期末', weight: 60 },
    ]
  }
}

watch(
  () => props.modelValue,
  (openVisible) => {
    if (openVisible) {
      policyForm.courseId = props.courseId
      open(props.policy)
    }
  },
)

async function savePolicy() {
  if (!policyForm.name.trim()) {
    ElMessage.warning('请填写方案名称')
    return
  }
  if (!policyForm.components.length) {
    ElMessage.warning('请至少添加一个构成项')
    return
  }
  for (const c of policyForm.components) {
    if (!c.key.trim() || !c.name.trim()) {
      ElMessage.warning('构成项的标识与名称均不能为空')
      return
    }
  }
  if (!weightBalanced.value) {
    ElMessage.error('构成项权重合计必须为 100%，当前为 ' + weightSumText.value + '%')
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId: props.courseId,
      name: policyForm.name.trim(),
      components: policyForm.components.map((c) => ({
        key: c.key.trim(),
        name: c.name.trim(),
        weight: Number(c.weight) || 0,
      })),
    }
    const r = await gradeApi.saveGradePolicy(payload)
    const body = unwrap(r)
    ElMessage.success('成绩方案已保存')
    emit('update:modelValue', false)
    emit(
      'saved',
      body || {
        id: policyForm.id,
        courseId: props.courseId,
        name: payload.name,
        components: payload.components,
      },
    )
  } catch (e) {
    logger.error('保存成绩方案失败', e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.comp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.comp-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.comp-key {
  width: 130px;
}
.comp-name {
  flex: 1;
}
.comp-weight {
  width: 130px;
}
</style>
