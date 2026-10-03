<template>
  <ExcelImportDialog
    title="批量导入用户"
    sub="上传 .xlsx 文件一次性创建多个账号；留空初始密码的账号由系统随机生成"
    :columns="COLUMNS"
    :notes="NOTES"
    :importing="importing"
    :result="result"
    @close="emit('close')"
    @submit="(f) => emit('submit', f)"
  >
    <template #extra>
      <div v-if="passwordRows.length" class="pwd">
        <div class="pwd-head">
          <span>随机生成的初始密码（请立即转告用户，关闭后不再显示）</span>
          <button class="btn btn-plain btn-sm" @click="copyAll">复制全部</button>
        </div>
        <div class="pwd-list">
          <div v-for="r in passwordRows" :key="r.username" class="pwd-item">
            <span class="pwd-user">{{ r.username }}</span>
            <code class="pwd-code">{{ r.password }}</code>
          </div>
        </div>
      </div>
    </template>
  </ExcelImportDialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { toast } from '@/composables/useToast'
import ExcelImportDialog from '@/components/ui/ExcelImportDialog.vue'

const props = defineProps({
  importing: { type: Boolean, default: false },
  result: { type: Object, default: null },
})
const emit = defineEmits(['close', 'submit'])

/** 与后端 ExcelUtil.parseUsers 的列顺序严格一致（索引 0~9） */
const COLUMNS = [
  { name: '用户名*', required: true },
  { name: '姓名' },
  { name: '邮箱' },
  { name: '角色' },
  { name: '初始密码' },
  { name: '手机号' },
  { name: '学号' },
  { name: '专业' },
  { name: '年级' },
  { name: '院系' },
]

const NOTES = [
  '仅「用户名」必填；用户名为空的行会被自动跳过',
  '角色填 STUDENT / TEACHER / ADMIN，填错该行会失败但不影响其他行',
  '初始密码留空则随机生成 8 位，导入后仅在本次结果中显示一次',
]

const passwordRows = computed(() => {
  const map = (props.result as unknown as { tempPasswords?: Record<string, string> } | null)
    ?.tempPasswords
  if (!map) return []
  return Object.entries(map).map(([username, password]) => ({ username, password }))
})

async function copyAll() {
  const text = passwordRows.value.map((r) => `${r.username}\t${r.password}`).join('\n')
  try {
    await navigator.clipboard.writeText(text)
    toast.show('已复制全部账号与密码', 'ok')
  } catch {
    toast.show('复制失败，请手动选择复制', 'warning')
  }
}
</script>

<style scoped>
.pwd {
  margin-top: 12px;
  border: 1px solid var(--ac-warning);
  background: var(--ac-warning-bg);
  border-radius: var(--ac-radius-md);
  padding: 11px 12px;
}
.pwd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ac-warning);
}
.pwd-list {
  margin-top: 8px;
  max-height: 168px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pwd-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 8px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-bg-elevated);
  font-size: 12.5px;
}
.pwd-user {
  min-width: 110px;
  color: var(--ac-text-secondary);
  font-weight: 600;
}
.pwd-code {
  font-family: ui-monospace, Consolas, monospace;
  letter-spacing: 0.5px;
  color: var(--ac-text-primary);
}
</style>
