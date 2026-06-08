<template>
  <div class="morandi-empty">
    <div class="morandi-empty__illustration">
      <svg viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <!-- 书本/文档插画 -->
        <rect x="40" y="50" width="120" height="85" rx="8" fill="#E8E2DA" stroke="#CBBBAA" stroke-width="1.5"/>
        <rect x="48" y="58" width="104" height="10" rx="3" fill="#D5CEC5"/>
        <rect x="48" y="76" width="70" height="7" rx="2" fill="#E2DCD4"/>
        <rect x="48" y="89" width="90" height="7" rx="2" fill="#E2DCD4"/>
        <rect x="48" y="102" width="55" height="7" rx="2" fill="#E2DCD4"/>
        <rect x="48" y="115" width="80" height="7" rx="2" fill="#E2DCD4"/>
        <!-- 装饰圆点 -->
        <circle cx="155" cy="35" r="8" fill="#B89A8A" opacity="0.25"/>
        <circle cx="170" cy="60" r="5" fill="#7A8BA5" opacity="0.2"/>
        <circle cx="30" cy="75" r="6" fill="#CBB58A" opacity="0.22"/>
        <!-- 搜索图标 -->
        <g transform="translate(125, 105)">
          <circle cx="14" cy="14" r="11" stroke="#928B83" stroke-width="2.5" fill="none"/>
          <line x1="22" y1="22" x2="30" y2="30" stroke="#928B83" stroke-width="2.5" stroke-linecap="round"/>
        </g>
      </svg>
    </div>
    <p v-if="description" class="morandi-empty__description">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' }
})
</script>

<style scoped>
.morandi-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.morandi-empty__illustration {
  width: 200px;
  max-width: 100%;
  margin-bottom: 20px;
  opacity: 0.85;
}

.morandi-empty__illustration svg {
  width: 100%;
  height: auto;
}

.morandi-empty__description {
  font-size: 15px;
  color: var(--ac-text-muted);
  margin: 0 0 16px;
  line-height: 1.5;
}
</style>
