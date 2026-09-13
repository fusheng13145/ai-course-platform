<template>
  <div class="playground">
    <!-- 智能问答测试 -->
    <div class="content-card playground-card">
      <div class="card-title">
        <el-icon><ChatDotRound /></el-icon><span>智能问答测试</span>
      </div>
      <p class="card-hint">
        基于所选课程知识库作答；未配置大模型 Key 时回退展示知识库原文（抑制幻觉）。
      </p>
      <div class="qa-input">
        <el-input
          v-model="askForm.question"
          placeholder="输入问题，如：什么是反向传播？"
          @keyup.enter="doAsk"
        />
        <el-input-number
          v-model="askForm.topK"
          :min="1"
          :max="10"
          controls-position="right"
          class="w-110"
        />
        <el-button type="primary" :loading="asking" :disabled="!selectedCourseId" @click="doAsk"
          >提问</el-button
        >
      </div>
      <div v-if="askResult" class="qa-result">
        <div class="qa-answer"><strong>回答：</strong>{{ askResult.answer }}</div>
        <div v-if="askResult.sources && askResult.sources.length" class="qa-sources">
          <strong>参考知识点：</strong>
          <span v-for="s in askResult.sources" :key="s.id" class="src-chip"
            >{{ s.name }} <em>({{ s.score }})</em></span
          >
        </div>
        <div class="qa-model">
          模型：<el-tag size="small" effect="plain">{{ askResult.model }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 语义召回测试 -->
    <div class="content-card playground-card">
      <div class="card-title">
        <el-icon><Search /></el-icon><span>语义召回测试</span>
      </div>
      <p class="card-hint">基于词频/IDF 加权的中文 bigram 余弦相似度召回 topK 知识点。</p>
      <div class="qa-input">
        <el-input
          v-model="retrieveForm.query"
          placeholder="输入查询词，如：梯度下降"
          @keyup.enter="doRetrieve"
        />
        <el-input-number
          v-model="retrieveForm.topK"
          :min="1"
          :max="10"
          controls-position="right"
          class="w-110"
        />
        <el-button
          type="primary"
          :loading="retrieving"
          :disabled="!selectedCourseId"
          @click="doRetrieve"
          >召回</el-button
        >
      </div>
      <div v-if="retrieveResult" class="retrieve-result">
        <div class="retrieve-count">命中 {{ retrieveResult.count }} 条</div>
        <div v-for="r in retrieveResult.results" :key="r.id" class="retrieve-item">
          <div class="retrieve-head">
            <span class="retrieve-name">{{ r.name }}</span>
            <span class="retrieve-score">相似度 {{ r.score }}</span>
          </div>
          <div class="retrieve-desc">{{ preview(r.description) }}</div>
        </div>
        <el-empty
          v-if="!retrieveResult.count"
          description="未召回相关知识点，建议补充知识库语料"
          :image-size="80"
        >
          <template #image><div class="empty-deco">🔍</div></template>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, ChatDotRound } from '@element-plus/icons-vue'

/**
 * RAG 联调面板
 * <p>智能问答测试（基于知识库融合生成）与语义召回测试双卡片。</p>
 */
defineProps({
  /** 当前选中课程（问答/召回禁用条件） */
  selectedCourseId: { type: [String, Number], default: null },
  /** 问答表单（共享对象：question/topK） */
  askForm: { type: Object, required: true },
  /** 问答结果 */
  askResult: { type: Object, default: null },
  /** 问答加载态 */
  asking: { type: Boolean, default: false },
  /** 召回表单（共享对象：query/topK） */
  retrieveForm: { type: Object, required: true },
  /** 召回结果 */
  retrieveResult: { type: Object, default: null },
  /** 召回加载态 */
  retrieving: { type: Boolean, default: false },
  /** 内容预览截断函数 */
  preview: { type: Function, required: true },
})

defineEmits(['ask', 'retrieve'])
</script>

<style scoped>
.playground {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
}
.playground-card {
  display: flex;
  flex-direction: column;
}
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.card-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin: 6px 0 12px;
}
.qa-input {
  display: flex;
  gap: 8px;
  align-items: center;
}
.w-110 {
  width: 110px;
}
.qa-result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--ac-border);
}
.qa-answer {
  font-size: 14px;
  line-height: 1.7;
  color: var(--ac-text-primary);
  white-space: pre-wrap;
}
.qa-sources {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.src-chip {
  background: var(--ac-bg-subtle, var(--surface-3));
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--ac-text-secondary);
}
.src-chip em {
  color: var(--ac-text-muted);
  font-style: normal;
}
.qa-model {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.retrieve-result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--ac-border);
  max-height: 320px;
  overflow-y: auto;
}
.retrieve-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-bottom: 8px;
}
.retrieve-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--ac-bg-subtle, var(--surface-2));
  margin-bottom: 8px;
}
.retrieve-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.retrieve-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--ac-text-primary);
}
.retrieve-score {
  font-size: 12px;
  color: var(--info);
}
.retrieve-desc {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 4px;
  line-height: 1.5;
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 900px) {
  .playground {
    grid-template-columns: 1fr;
  }
}
</style>
