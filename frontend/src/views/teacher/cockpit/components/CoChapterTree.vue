<template>
  <div class="l1-card">
    <div class="l1-head">
      <div class="l1-title">章节与任务点</div>
    </div>
    <div class="l1-body">
      <div v-if="chapterTree.length" class="tree">
        <div v-for="node in chapterTree" :key="node.id" class="tree-node">
          <div class="tree-row">
            <span class="tree-title">{{ node.title }}</span>
            <span class="tree-meta"
              >任务点 {{ node.taskCount || 0 }} · 提交 {{ node.submittedCount || 0 }}/{{
                node.studentCount || 0
              }}
              · 完成率 {{ pct(node.completionRate) }}</span
            >
            <el-tag size="small" :type="node.isPublished ? 'success' : 'info'" effect="light">{{
              node.isPublished ? '已发布' : '草稿'
            }}</el-tag>
          </div>
          <div v-if="node.taskPoints && node.taskPoints.length" class="tree-tasks">
            <span v-for="tp in node.taskPoints" :key="tp.id" class="task-chip">
              {{ tp.title }} <em>{{ tp.taskType }}</em>
            </span>
          </div>
          <div v-if="node.children && node.children.length" class="tree-children">
            <div v-for="c in node.children" :key="c.id" class="tree-node tree-node--child">
              <div class="tree-row">
                <span class="tree-title">{{ c.title }}</span>
                <span class="tree-meta"
                  >任务点 {{ c.taskCount || 0 }} · 完成率 {{ pct(c.completionRate) }}</span
                >
              </div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else :description="emptyText" :image-size="70" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  chapterTree: { type: Array, default: () => [] },
  // 加载失败时由父组件传入诚实文案，避免把「失败」显示成「暂无章节」
  emptyText: { type: String, default: '暂无章节' },
})

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
</script>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tree-node {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.tree-node--child {
  background: transparent;
  border-style: dashed;
  margin-left: 22px;
}
.tree-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tree-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.tree-meta {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.tree-tasks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.task-chip {
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.task-chip em {
  font-style: normal;
  opacity: 0.7;
  margin-left: 4px;
}
.tree-children {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}
</style>
