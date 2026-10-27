<template>
  <!-- 新建/编辑知识点 -->
  <Modal
    :model-value="formDialog"
    :title="isEdit ? '编辑知识点' : '新建知识点'"
    width="520px"
    append-to-body
    @update:model-value="emit('update:formDialog', $event)"
  >
    <el-form :model="kpForm" label-width="90px">
      <el-form-item label="名称" required>
        <el-input v-model="kpForm.name" placeholder="知识点名称" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="kpForm.description"
          type="textarea"
          :rows="3"
          placeholder="知识点描述"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="kpForm.difficulty" class="w-100">
          <el-option label="简单" value="EASY" />
          <el-option label="中等" value="MEDIUM" />
          <el-option label="困难" value="HARD" />
        </el-select>
      </el-form-item>
      <el-form-item label="重要度">
        <el-rate v-model="kpForm.importance" :max="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:formDialog', false)">取消</el-button>
      <el-button type="primary" :loading="formSubmitting" @click="emit('submit')">保存</el-button>
    </template>
  </Modal>

  <!-- AI 抽取 -->
  <Modal
    :model-value="extractDialog"
    title="AI 抽取知识点"
    width="620px"
    append-to-body
    @update:model-value="emit('update:extractDialog', $event)"
  >
    <el-input
      :model-value="extractText"
      type="textarea"
      :rows="7"
      placeholder="粘贴课程内容文本，AI 将自动抽取知识点与关系…"
      @update:model-value="emit('update:extractText', $event)"
    />
    <div class="extract-actions">
      <el-button type="primary" :loading="extractLoading" @click="emit('extract')">
        <el-icon><MagicStick /></el-icon>开始抽取
      </el-button>
    </div>
    <div v-if="extractResult" v-loading="extractLoading" class="extract-result">
      <div class="er-stats">
        <div class="er-stat">
          <b>{{ extractResult.nodeCount ?? (extractResult.nodes?.length || 0) }}</b
          ><span>知识点</span>
        </div>
        <div class="er-stat">
          <b>{{ extractResult.relationCount ?? (extractResult.relations?.length || 0) }}</b
          ><span>关系</span>
        </div>
      </div>
      <div v-if="extractNodes.length" class="er-section">
        <div class="er-sec-title">抽取出的知识点</div>
        <div class="er-chips">
          <el-tag
            v-for="(n, i) in extractNodes"
            :key="i"
            class="er-chip"
            type="primary"
            effect="light"
            >{{ nodeName(n) }}</el-tag
          >
        </div>
      </div>
      <div v-if="extractRelations.length" class="er-section">
        <div class="er-sec-title">抽取出的关系</div>
        <ul class="er-rels">
          <li v-for="(r, i) in extractRelations" :key="i">{{ relText(r) }}</li>
        </ul>
      </div>
      <div v-if="extractRaw" class="er-json">
        <div class="er-sec-title">JSON 概览</div>
        <pre>{{ extractRaw }}</pre>
      </div>
    </div>
  </Modal>
</template>

<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

/**
 * 知识图谱对话框（新建/编辑 + AI 抽取）
 * <p>表单对象经 props 注入（共享 reactive 对象编辑嵌套属性）；AI 抽取展示节点/关系
 * 结果与 JSON 概览。操作经事件上抛父组件（useKnowledgeGraph）。</p>
 */
defineProps({
  formDialog: { type: Boolean, default: false },
  isEdit: { type: Boolean, default: false },
  formSubmitting: { type: Boolean, default: false },
  kpForm: { type: Object, required: true },
  extractDialog: { type: Boolean, default: false },
  extractText: { type: String, default: '' },
  extractLoading: { type: Boolean, default: false },
  extractResult: { type: Object, default: null },
  extractRaw: { type: String, default: '' },
  extractNodes: { type: Array, default: () => [] },
  extractRelations: { type: Array, default: () => [] },
  nodeName: { type: Function, required: true },
  relText: { type: Function, required: true },
})
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）

const emit = defineEmits([
  'update:formDialog',
  'update:extractDialog',
  'update:extractText',
  'submit',
  'extract',
])
</script>

<style scoped>
.w-100 {
  width: 100%;
}
.extract-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.extract-result {
  margin-top: 14px;
}
.er-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.er-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-primary-bg);
}
.er-stat b {
  font-size: 22px;
  color: var(--ac-primary);
}
.er-stat span {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}
.er-section {
  margin-bottom: 12px;
}
.er-sec-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}
.er-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.er-chip {
  margin: 0;
}
.er-rels {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.9;
}
.er-json pre {
  background: var(--ac-surface-2);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 12px;
  font-size: 12px;
  color: var(--ac-text-secondary);
  overflow-x: auto;
}
</style>
