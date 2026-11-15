# Checklist

- [x] `CourseEvidenceComponent` 仅在 `mode=ENGINE` 且有召回时返回证据；`UNAVAILABLE/PLACEHOLDER/null/异常` 均返回空且不伪造来源（`CourseEvidenceComponentTest` 覆盖）
- [x] 知识点匹配规则为“名称≥2 字且整体包含于查询（忽略大小写空白）”，按 importance 降序限 5；前置关系查询失败被忽略且有测试
- [x] `AiRagComponent` 改为委托证据组件后，`ragAsk` 的 ENGINE/LOCAL 分支、出处拼装与“无法基于课程知识库回答”约束保持原样（AIServiceImplTest 44 / Extra 36 全绿）
- [x] `generateQuestion` 提示词含【课程资料摘录】与【课程知识点（知识图谱）】两段，仍点名 `content/options/correctAnswers/explanation/score` 键位（AiQuestionGroundingTest 断言真实 prompt）
- [x] `recommendQuestionsJson` schema 含 `knowledgePoint`，`parseQuestions` 仅非空透传且缺失不报错
- [x] `aiGradeSubmission` 在 `courseId` 存在时注入证据与评分口径，`submissionData.aiEvidence` 可核验；无证据时批改不失败、不伪造（3 用例覆盖）
- [x] AI 批改仍只更新 AI 派生列，不整行覆盖教师评分字段（`update(isNull(), any())` 断言）
- [x] `irem.py` 为纯函数/确定性实现，无写库、无新 HTTP 端点；`test_irem.py` 覆盖候选、阈值、空输入、确定性（pytest 44 passed）
- [x] `frontend/` 零改动（`git status --short -- frontend` 为空）
- [x] 本批提交不含图谱写路径，未清理其它课程数据（4007 现值 65 节点/120 边，均为网络课程节点；与上一轮“58 节点”计数口径差异原因未知）
- [x] 每步为单一目的提交（`6c30d65f`、`3d150520`、`f13ffc23`、`98e1a1b8`），可独立回退
- [x] 各步验证命令与实测读数已记录；IREM 效果标注为“未知”，未宣称准确率
- [x] 未发现需要修改前端契约才能闭环的问题（`knowledgePoint`/`aiEvidence` 对前端均为可选字段，未越界）

## 未决 / 未知项
- [ ] 出题/批改的 LLM 级线上验收：本地 Ollama `deepseek-r1:8b` 在 60s（复测 240s）内未返回，推荐接口触发 async 超时 → 未能观测 `knowledgePoint`/`aiEvidence` 实际产出（状态：未知，非代码断言失败）
- [ ] IREM 在真实课程语料上的准确率/召回率：无标注语料，未知
