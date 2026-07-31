<template>
  <div class="error-page">
    <div class="error-card animate-in">
      <h1>403</h1>
      <p>抱歉，您没有权限访问此页面</p>
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
