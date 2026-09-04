<!-- 提交 PR 前请确认：本地质量门禁全过（后端 mvn verify / 前端 lint+vitest+build） -->

## 变更摘要
<!-- 用一两句话说明本次改动解决了什么问题 -->

- 关联 Issue：#<!-- 填 Issue 号，无则说明 -->

## 改动内容
<!-- 逐项列出，含关键文件路径 -->

- [ ] 后端：<!-- 如：新增 ClassroomService.countPendingByCourse 聚合逻辑 -->
- [ ] 前端：<!-- 如：CourseCockpit 接入真实待批数据 -->
- [ ] 测试：<!-- 如：ClassroomServiceImplPendingTest 5 用例 -->
- [ ] 文档：<!-- 如：README / docs 手册 -->

## 测试与验证
<!-- 列出实际执行的验证（命令 + 结果），如：
- 后端：mvn -o test -Dtest='XxxTest' → 12 用例 0 失败
- 前端：npx eslint src/... → 0 问题；npm run build → 成功
- 契约：契约测试类是否受影响 -->

## 影响面与风险
<!-- 是否有破坏性变更（schema / API 契约 / 配置键）？是否涉及 Python 引擎契约（ContractPaths）？ -->

- [ ] 无破坏性变更
- [ ] 数据库结构变更（已附迁移 SQL 说明）
- [ ] API / 集成契约变更（已同步 docs/项目手册.md 第八部分与 ContractPaths）

## 截图（可选）
<!-- 前端 UI 改动请附截图 -->
