<template>
  <div class="compare-section">
    <div class="section-header">
      <h3 class="section-title">计划 vs 实际进度</h3>
    </div>
    <div class="compare-grid">
      <div class="compare-card">
        <div class="compare-card-header">
          <span class="compare-label">计划进度</span>
          <el-tag size="small" type="info" effect="plain">按教学计划</el-tag>
        </div>
        <el-progress :percentage="plannedProgress" :stroke-width="16" :color="'#94a3b8'" :show-text="false" />
        <div class="compare-detail">
          <span>计划完成 {{ plannedChapters }} / {{ totalChapters }} 章</span>
          <span class="compare-sub">预计还需 {{ plannedRemaining }} 章</span>
        </div>
      </div>
      <div class="compare-card">
        <div class="compare-card-header">
          <span class="compare-label">实际进度</span>
          <el-tag size="small" type="success" effect="plain">实际完成</el-tag>
        </div>
        <el-progress :percentage="overallProgress" :stroke-width="16" :show-text="false" />
        <div class="compare-detail">
          <span>实际完成 {{ completedChapters }} / {{ totalChapters }} 章</span>
          <span class="compare-sub" :class="{ 'compare-behind': behindCount > 0 }">
            {{ behindCount > 0 ? `落后计划 ${behindCount} 章` : '进度正常/超前' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  plannedChapters: { type: [Number, String], default: 0 },
  totalChapters: { type: [Number, String], default: 0 },
  plannedRemaining: { type: [Number, String], default: 0 },
  overallProgress: { type: [Number, String], default: 0 },
  completedChapters: { type: [Number, String], default: 0 },
  behindCount: { type: [Number, String], default: 0 },
  plannedProgress: { type: [Number, String], default: 0 },
})
</script>

<style scoped>
.compare-section {
  margin-bottom: 22px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.compare-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 18px 20px;
}
.compare-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.compare-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.compare-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.compare-sub {
  font-weight: 500;
}
.compare-behind {
  color: var(--ac-danger, #ef4444);
}
@media (max-width: 768px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }
}
</style>
