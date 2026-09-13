<template>
  <div class="doc-page">
    <div class="doc-container">
      <div class="doc-header">
        <router-link to="/register" class="back-link">&larr; 返回注册</router-link>
        <h1>隐私政策</h1>
        <p class="doc-subtitle">生效日期：2026 年 7 月 1 日</p>
      </div>

      <div class="doc-body">
        <section>
          <h2>一、引言</h2>
          <p>
            「AI
            智能课程平台」（以下简称"我们"或"本平台"）尊重并保护所有用户的隐私权。本政策旨在向您说明我们会收集哪些信息、如何使用这些信息以及您享有哪些权利。请您仔细阅读本政策。
          </p>
        </section>

        <section>
          <h2>二、我们收集的信息</h2>
          <p><strong>2.1 您主动提供的信息：</strong></p>
          <ul>
            <li>注册信息：用户名、真实姓名、手机号、密码</li>
            <li>角色信息：学生/教师身份选择</li>
            <li>学习记录：课程进度、考试成绩、错题数据</li>
            <li>反馈内容：您提交的建议、评价或问题报告</li>
          </ul>
          <p><strong>2.2 自动收集的信息：</strong></p>
          <ul>
            <li>设备信息：浏览器类型、操作系统、屏幕分辨率</li>
            <li>日志信息：访问时间、页面停留时长、IP 地址</li>
            <li>Cookie 及类似技术：用于保持登录状态和偏好设置</li>
          </ul>
        </section>

        <section>
          <h2>三、信息使用目的</h2>
          <p>我们收集的信息将用于以下用途：</p>
          <ul>
            <li>创建和管理您的账户</li>
            <li>提供、维护和改进我们的服务</li>
            <li>AI 智能辅导功能的学习分析与个性化推荐</li>
            <li>发送服务通知（如课程更新、成绩提醒）</li>
            <li>保障平台安全和防范欺诈行为</li>
            <li>遵守法律法规要求的义务</li>
          </ul>
        </section>

        <section>
          <h2>四、信息的存储与保护</h2>
          <p>
            <strong>4.1 存储期限：</strong
            >我们仅在实现本政策所述目的所需的期限内保留您的个人信息。账户注销后，除法律法规要求外，我们将在
            30 天内删除或匿名化处理您的个人数据。
          </p>
          <p><strong>4.2 安全措施：</strong></p>
          <ul>
            <li>传输层加密（TLS/SSL）保护数据传输安全</li>
            <li>敏感信息（如密码）采用单向哈希加密存储</li>
            <li>严格的访问权限控制和日志审计机制</li>
            <li>定期进行安全评估和漏洞扫描</li>
          </ul>
        </section>

        <section>
          <h2>五、信息的共享</h2>
          <p><strong>5.1 除以下情形外，我们不会向第三方共享您的个人信息：</strong></p>
          <ul>
            <li>获得您的明确同意</li>
            <li>根据法律法规、行政或司法强制要求</li>
            <li>与授权合作伙伴共享（仅限提供服务所必需的最小范围）</li>
            <li>为保护本平台或用户的合法权益所必需</li>
          </ul>
          <p><strong>5.2 我们绝不会出售您的个人信息。</strong></p>
        </section>

        <section>
          <h2>六、Cookies 使用</h2>
          <p>本平台使用 Cookies 和类似技术来：</p>
          <ul>
            <li>记住您的登录状态</li>
            <li>保存您的主题偏好（浅色/深色模式）</li>
            <li>分析平台使用情况以改进用户体验</li>
          </ul>
          <p>您可以通过浏览器设置管理或删除 Cookie，但这可能影响某些功能的正常使用。</p>
        </section>

        <section>
          <h2>七、您的权利</h2>
          <p>根据相关法律法规，您享有以下权利：</p>
          <ul>
            <li><strong>知情权：</strong>了解我们收集了哪些信息及其用途</li>
            <li><strong>访问权：</strong>查阅我们持有的您的个人信息</li>
            <li><strong>更正权：</strong>更正不准确或不完整的信息</li>
            <li><strong>删除权：</strong>在符合法律规定的情况下请求删除您的信息</li>
            <li><strong>撤回同意：</strong>随时撤回之前给予的同意</li>
            <li><strong>注销账户：</strong>通过联系管理员申请注销账户</li>
          </ul>
        </section>

        <section>
          <h2>八、未成年人保护</h2>
          <p>
            本平台主要面向高等教育阶段用户。如果您是未满 18
            周岁的未成年人，请在监护人的指导下阅读和使用本平台。如发现我们无意中收集了未成年人的个人信息，请立即通知我们，我们将尽快处理。
          </p>
        </section>

        <section>
          <h2>九、政策更新</h2>
          <p>
            我们可能适时更新本隐私政策。重大变更时，我们会通过平台公告或弹窗等方式通知您。继续使用即视为接受更新后的政策。
          </p>
        </section>

        <section class="contact-section">
          <h2>十、联系我们</h2>
          <p>如您对本隐私政策有任何疑问、意见或投诉，请联系：</p>
          <ul>
            <li>邮箱：privacy@ai-course.edu.cn</li>
            <li>地址：中国·北京市海淀区</li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// 静态文档页面
</script>

<style scoped>
.doc-page {
  min-height: 100vh;
  background: var(--ac-bg);
  padding: 40px 20px;
  display: flex;
  justify-content: center;
}
.doc-container {
  width: 100%;
  max-width: 720px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  box-shadow: var(--ac-shadow-lg);
  border: 1px solid var(--ac-border);
  overflow: hidden;
}
.doc-header {
  padding: 32px 36px 24px;
  border-bottom: 1px solid var(--ac-border);
  text-align: center;
}
.back-link {
  display: inline-block;
  font-size: 13px;
  color: var(--ac-link);
  text-decoration: none;
  margin-bottom: 16px;
}
.back-link:hover {
  color: var(--ac-link-hover);
}
.doc-header h1 {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}
.doc-subtitle {
  font-size: 13px;
  color: var(--ac-text-muted);
}
.doc-body {
  padding: 28px 36px 36px;
}
.doc-body section {
  margin-bottom: 28px;
}
.doc-body section:last-child {
  margin-bottom: 0;
}
.doc-body h2 {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--ac-border);
}
.doc-body p {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ac-text-secondary);
  margin-bottom: 10px;
}
.doc-body p:last-child {
  margin-bottom: 0;
}
.doc-body ul {
  list-style: none;
  padding: 0;
  margin: 8px 0 0 0;
}
.doc-body ul li {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ac-text-secondary);
  padding-left: 18px;
  position: relative;
}
.doc-body ul li::before {
  content: '•';
  position: absolute;
  left: 6px;
  color: var(--ok);
}
.contact-section {
  background: rgba(22, 163, 74, 0.04);
  padding: 20px;
  border-radius: var(--ac-radius-md);
  border: 1px solid rgba(22, 163, 74, 0.1);
}
@media (max-width: 768px) {
  .doc-page {
    padding: 20px 12px;
  }
  .doc-header,
  .doc-body {
    padding-left: 24px;
    padding-right: 24px;
  }
  .doc-header h1 {
    font-size: 19px;
  }
  .doc-body h2 {
    font-size: 15px;
  }
}
</style>
