<template>
  <div class="error-page">
    <div class="error-card">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" @click="$router.push('/login')">返回首页</el-button>
    </div>
  </div>
</template>
<style scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-bg);
}
.error-card {
  text-align: center;
}
.error-card h1 {
  font-size: 120px;
  color: var(--ac-primary);
  margin: 0;
  line-height: 1;
}
.error-card p {
  font-size: 16px;
  color: var(--ac-text-muted);
  margin: 16px 0 24px;
}
</style>
