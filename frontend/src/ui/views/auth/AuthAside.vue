<template>
  <aside class="av-brief" aria-label="平台结构">
    <div class="av-brief-head">
      <h2>平台结构</h2>
    </div>

    <div class="av-brief-grid">
      <article v-for="item in modules" :key="item.title" class="av-brief-tile">
        <div class="av-brief-tile-top">
          <NumberBadge :value="item.no" />
          <Icon :name="item.icon" cls="av-brief-icon" />
        </div>
        <b>{{ item.title }}</b>
        <small>{{ item.note }}</small>
      </article>
    </div>

    <div class="av-brief-roles">
      <h3>账号角色</h3>
      <div v-for="role in roles" :key="role.name" class="av-role-row">
        <span class="av-role-tag">{{ role.name }}</span>
        <span>{{ role.scope }}</span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import Icon from '../../components/Icon.vue'
import NumberBadge from '../../components/NumberBadge.vue'

const modules = [
  { no: 1, icon: 'book', title: '课程与章节', note: '课程 · 章节 · 资料' },
  { no: 2, icon: 'clipboard', title: '作业与考试', note: '提交 · 批阅 · 成绩' },
  { no: 3, icon: 'graph', title: '知识图谱', note: '知识点 · 关系 · 路径' },
  { no: 4, icon: 'sparkle', title: 'AI 助教', note: '知识库 · 问答 · 诊断' },
]

const roles = [
  { name: '学生', scope: '课程学习 · 作业考试 · AI 问答' },
  { name: '教师', scope: '教学班 · 批阅 · 成绩' },
  { name: '管理员', scope: '课程治理 · 权限 · 审计' },
]
</script>

<style scoped>
.av-brief {
  position: relative;
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 32px;
  border-left: 1px solid var(--line-soft);
  overflow: hidden;
}

.av-brief::after {
  content: '';
  position: absolute;
  top: 32px;
  right: 32px;
  width: 4px;
  height: 4px;
  border-radius: var(--r-ctl);
  background: var(--violet);
  opacity: 0.12;
  box-shadow:
    8px 0 var(--violet),
    16px 0 var(--violet),
    24px 0 var(--violet),
    0 8px var(--violet),
    8px 8px var(--violet),
    16px 8px var(--violet),
    24px 8px var(--violet),
    0 16px var(--violet),
    8px 16px var(--violet),
    16px 16px var(--violet),
    24px 16px var(--violet);
}

.av-brief-head {
  position: relative;
  z-index: 1;
}

.av-brief-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink);
}

.av-brief-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.av-brief-tile {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: var(--r-card);
  background: var(--bg);
}

.av-brief-tile-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.av-brief-icon {
  width: 18px;
  height: 18px;
  color: var(--ink-3);
}

.av-brief-tile b {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ink);
}

.av-brief-tile small {
  font-size: 12px;
  line-height: 1.5;
  color: var(--ink-3);
}

.av-brief-roles {
  margin-top: auto;
  display: flex;
  flex-direction: column;
}

.av-brief-roles h3 {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--ink-3);
}

.av-role-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  line-height: 1.5;
  color: var(--ink-3);
}

.av-role-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
  border-radius: var(--r-ctl);
  background: var(--surface-3);
  color: var(--ink);
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .av-brief {
    display: none;
  }
}
</style>
