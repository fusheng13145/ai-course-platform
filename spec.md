# C8/C9：出题批改证据接地与 IREM 隐式关系抽取 Spec

## Why
方案（`引擎侧真实链路方案-2026-11-13.md` §6）要求补齐两项核心业务能力：
- **C8**：出题与批改当前仅把 `courseId` 拼进提示词，不带检索召回、不带知识图谱，存在超纲与幻觉风险（§1.6 实测）。
- **C9**：IREM 隐式关系抽取全仓无实现（§1.5），只有 4 类显式关系，研究能力缺位。

C2（问答走 `mode=ENGINE` + Java 侧真实 LLM 生成）与 C3（图谱 ingest 真实快照）已完成并分别提交为 `9fc82294`、`f9e29123`；本轮只做 C8、C9，C7 未获批不做。

## What Changes
- 新增 `CourseEvidenceComponent`，把 `AiRagComponent` 中的私有证据逻辑（仅 ENGINE 检索、知识点匹配、前置知识、渲染、出处映射）上收为可复用组件。
- `AiRagComponent` 改为委托该组件，对外行为、mode 语义与降级路径**保持不变**。
- 出题接地：`AiDialogueComponent.generateQuestion` 与 `AiStructuredComponent.recommendQuestionsJson` 在提示词中加入课程知识点（图谱）与课程资料摘录，要求不超纲；推荐题 JSON 增加 `knowledgePoint` 字段并由 `parseQuestions` 透传（前端不改，多余字段无害）。
- 批改接地：`SubmissionGradeComponent.aiGradeSubmission` 按 `task.getCourseId()` 取知识点 + 按任务标题/要求检索证据，写入提示词，并把证据名落到 `submissionData.aiEvidence` 便于核验。
- 新增 Python 模块 `python-services/kg_service/irem.py`：基于共现与规则产出**候选**隐式关系，不自动写库、不新增 HTTP 端点；附合成数据单测。
- 非变更：`frontend/` 一行不改；不改既有出题 JSON 键位契约 `content/options/correctAnswers/explanation/score`（DR-89）；不动 Neo4j course 4007；不清理其它课程数据。

## Impact
- Affected specs：AI 生成链路（问答/出题/批改）、知识图谱抽取能力。
- Affected code：
  - `backend/src/main/java/com/aicourse/service/component/ai/CourseEvidenceComponent.java`（新增）
  - `backend/src/main/java/com/aicourse/service/component/ai/AiRagComponent.java`
  - `backend/src/main/java/com/aicourse/service/component/ai/AiDialogueComponent.java`
  - `backend/src/main/java/com/aicourse/service/component/ai/AiStructuredComponent.java`
  - `backend/src/main/java/com/aicourse/service/component/submission/SubmissionGradeComponent.java`
  - `backend/src/test/java/com/aicourse/service/impl/AIServiceImplTest.java`、`AIServiceImplExtraTest.java`（构造点）
  - `backend/src/test/java/com/aicourse/service/component/**`（新增/更新组件测试）
  - `python-services/kg_service/irem.py`、`python-services/kg_service/test_irem.py`（新增）

## ADDED Requirements

### Requirement: 课程证据组件（统一证据出口）
系统 SHALL 提供 `CourseEvidenceComponent`，作为问答/出题/批改共用的课程证据来源。

#### Scenario: 仅接受真实引擎召回
- **WHEN** 以 `(query, courseId, topK)` 请求证据且 `courseId` 非空
- **THEN** 仅当 `RAGService.retrieveTyped` 返回 `mode=ENGINE` 时返回召回片段
- **AND** `UNAVAILABLE`、`PLACEHOLDER`、`null` 或抛异常时返回空结果，不降级为任何伪造来源

#### Scenario: 知识点匹配与渲染
- **WHEN** 课程存在知识点且问题/查询命中知识点名称（名称长度 ≥2 且整体出现，忽略大小写与空白）
- **THEN** 按 `importance` 降序、最多 5 条返回，渲染文本含知识点描述（压缩并截断 200 字）与前置知识（最多 3 条）
- **AND** 前置关系查询失败时忽略前置知识，不中断调用方

#### Scenario: 问答行为不回归
- **WHEN** C2 的 `AiRagComponent.ragAsk` 改为委托证据组件后执行
- **THEN** ENGINE 路径仍返回 `mode=ENGINE` 且出处含文档与知识点；降级路径仍返回 `mode=LOCAL`，无证据时保留“无法基于课程知识库回答”的约束语义

### Requirement: 出题接地
系统 SHALL 在真实出题路径中注入课程知识点与课程资料摘录，使题目受课程范围约束。

#### Scenario: 对话式出题
- **WHEN** `AiDialogueComponent.generateQuestion(courseId, type, difficulty, count)` 被调用
- **THEN** 提示词包含【课程知识点（知识图谱）】与【课程资料摘录】两段（无证据时省略且不报错）
- **AND** 保留既有键位要求：`content/options/correctAnswers/explanation/score`

#### Scenario: 题目推荐结构化输出
- **WHEN** `AiStructuredComponent.recommendQuestionsJson` 被调用
- **THEN** 提示词同样接地，schema 增加 `knowledgePoint` 字段
- **AND** `parseQuestions` 透传该字段（缺失时不影响既有字段），前端无需改动

### Requirement: 批改接地
系统 SHALL 在 AI 批改中引用课程证据，并留下可核验痕迹。

#### Scenario: 有课程归属的作业
- **WHEN** `SubmissionGradeComponent.aiGradeSubmission` 处理带 `courseId` 的任务提交
- **THEN** 提示词加入参考证据段，并声明“以课程资料为准，合要点计分、明显违背扣分”
- **AND** `submissionData.aiEvidence` 写入证据名列表
- **AND** 原有 `aiSuggestedScore/aiFeedback/aiHighlights/aiSuggestions/aiGradedAt` 写入行为不变

#### Scenario: 无证据时
- **WHEN** 检索不可用或课程无知识点
- **THEN** 批改仍按原方式完成，不因证据缺失而失败，也不伪造证据名

### Requirement: IREM 候选隐式关系推断
系统 SHALL 提供离线、确定性的隐式关系候选推断模块，且不自动写入任何存储。

#### Scenario: 合成语料推断
- **WHEN** 向 `irem.infer_implicit_relations(nodes, texts, ...)` 传入知识点与文本
- **THEN** 输出候选关系列表（含 `source_id/target_id/relation_type/score/evidence`），相同输入产生相同输出

#### Scenario: 不写库、不改契约
- **WHEN** 模块被导入或调用
- **THEN** 不执行任何 MySQL/Neo4j 写操作，不注册新 HTTP 端点
- **AND** 效果无法验证的事实须在文档与交付说明中标注为“未知”，不得宣称准确率

### Requirement: 交付纪律
系统变更 SHALL 按步独立提交、独立可回退，并如实给出验证读数。

#### Scenario: 单步提交
- **WHEN** 某一步（证据组件 / 出题 / 批改 / IREM）完成
- **THEN** 生成一个单一目的提交，回退该提交不影响其它步骤的既有行为
- **AND** 步末运行相关测试并记录实测通过数；未验证项标注“未知”

#### Scenario: 越界即停
- **WHEN** 发现闭环需要修改前端契约、改动 course 4007 或超出 C8/C9 范围
- **THEN** 立即停止并汇报，不擅自扩张

## MODIFIED Requirements

### Requirement: AI 出题 JSON 契约
在保持 `content/options/correctAnswers/explanation/score` 必选键不变的前提下，允许模型额外返回 `knowledgePoint`；该字段仅用于可观测性，不参与落库强校验。

### Requirement: AI 批改写入范围
AI 批改只更新 AI 派生列（`ai_feedback`、`submission_data`），不得整行覆盖教师已写入的 `status/score/graderId/gradedAt`（沿用既有 `updateAiColumns` 纪律）。

## REMOVED Requirements
无。
