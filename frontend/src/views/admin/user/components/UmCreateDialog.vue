<template>
  <Modal wide @close="emit('close')">
    <template #title>新增用户</template>
    <template #sub>创建后用户即可使用管理员设定的密码登录</template>
    <div class="form-grid">
      <div class="field">
        <label>登录账号<span class="req">*</span></label
        ><input v-model="form.username" class="input" placeholder="工号 / 学号" />
      </div>
      <div class="field">
        <label>姓名<span class="req">*</span></label
        ><input v-model="form.fullName" class="input" placeholder="真实姓名" />
      </div>
      <div class="field">
        <label>邮箱<span class="req">*</span></label
        ><input v-model="form.email" class="input" placeholder="name@example.com" />
      </div>
      <div class="field">
        <label>初始密码<span class="req">*</span></label
        ><input
          v-model="form.password"
          class="input"
          type="password"
          placeholder="至少 6 位"
        />
      </div>
      <div class="field">
        <label>角色<span class="req">*</span></label
        ><select v-model="form.role" class="select">
          <option value="STUDENT">学生</option>
          <option value="TEACHER">教师</option>
          <option value="ADMIN">管理员</option>
        </select>
      </div>
      <div class="field">
        <label>所属单位</label
        ><input v-model="form.department" class="input" placeholder="如：计算机学院" />
      </div>
      <div class="field span-2">
        <label>手机号</label><input v-model="form.phone" class="input" placeholder="选填" />
      </div>
    </div>
    <template #footer>
      <button class="btn btn-plain" @click="emit('close')">取消</button>
      <button class="btn btn-primary" :disabled="creating" @click="submit()">
        {{ creating ? '创建中…' : '创建用户' }}
      </button>
    </template>
  </Modal>
</template>

<script setup>
import { reactive } from 'vue'
import { toast } from '@/composables/useToast'
import Modal from '@/components/ui/Modal.vue'

defineProps({
  creating: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'submit'])

const form = reactive({
  username: '',
  fullName: '',
  email: '',
  password: '',
  role: 'STUDENT',
  department: '',
  phone: '',
})

function submit() {
  if (
    !form.username.trim() ||
    !form.fullName.trim() ||
    !form.email.trim() ||
    !form.password
  ) {
    toast.show('请填写账号、姓名、邮箱与初始密码', 'warning')
    return
  }
  emit('submit', { ...form })
}
</script>
