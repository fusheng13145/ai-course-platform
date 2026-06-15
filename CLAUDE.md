## 核心行为准则
1. 编码前先思考：不要假设，不要隐藏困惑。有疑问立刻停下来提问。
2. 简单优先：用最少的代码解决问题，拒绝过度设计和无谓的抽象。
3. 外科手术式修改：只改需求相关的行，严禁顺手重构相邻代码或乱改格式。
4. 目标驱动：任务必须转化为可验证的目标，修 Bug 前必须先写测试复现。

👇 以下为个人经常用到的自定义的私有规范 (Custom Rules) 👇

## 1. 安全与合规规范 (Critical Security)
- 严禁硬编码：绝对不允许在代码中写死任何 API Key、密码、Token 或敏感配置，必须使用 `.env` 环境变量。
- 数据隐私：涉及用户敏感信息（如手机号、身份证）的日志打印，必须进行脱敏处理。

## 2. 技术栈与架构约束 (Tech Stack & Architecture)
- 统一架构：本项目后端严格采用三层架构（Controller -> Service -> Mapper），禁止在 Controller 层直接编写业务逻辑。
- 语言特性：全面采用 TypeScript，严禁无故使用 `any` 类型，必须定义严格的 Interface。

## 3. 常用指令速查 (Environment Commands)
AI 助手在需要运行或测试时，请直接参考以下指令：
- 启动本地开发：`npm run dev`
- 运行单元测试：`npm run test`
- 代码格式检查：`npm run lint`
- 执行数据库迁移：`npm run db:migrate`
## 4. Git 提交规范 (Commit Messages)
当协助我生成 Git Commit 信息时，必须严格遵守以下格式：
- 格式：<type>(<scope>): <subject>
- 示例：feat(auth): 增加微信登录功能 或 fix(user): 修复用户头像上传失败的Bug