# Tasks

- [x] Task 1（C8-1）: 抽出课程证据组件，问答行为不变（提交 `6c30d65f`）
  - [x] SubTask 1.1: 新建 `service/component/ai/CourseEvidenceComponent.java`，提供 `retrieve/loadPoints/matchPoints/renderPoints/renderSources/scopeText/pointSources/evidenceNames`
  - [x] SubTask 1.2: `AiRagComponent` 改为 `(CourseEvidenceComponent, AiCoreComponent)`，证据逻辑全部委托；mode 语义与降级文案不变
  - [x] SubTask 1.3: 更新 `AIServiceImplTest`、`AIServiceImplExtraTest` 构造点
  - [x] SubTask 1.4: 新增 `CourseEvidenceComponentTest`（17 用例）
  - [x] SubTask 1.5: 实测 AIServiceImplTest 44 / AIServiceImplExtraTest 36 / CourseEvidenceComponentTest 17，Failures=0，Errors=0
  - [x] SubTask 1.6: 单一目的提交（5 文件）

- [x] Task 2（C8-2）: 出题接地（提交 `3d150520`，依赖 Task 1）
  - [x] SubTask 2.1: `generateQuestion` 注入【课程资料摘录】【课程知识点（知识图谱）】与“不得超纲”，保留键位要求
  - [x] SubTask 2.2: `recommendQuestionsJson` schema 增加 `knowledgePoint`；`parseQuestions` 仅非空透传
  - [x] SubTask 2.3: 新增 `AiQuestionGroundingTest`（4 用例）
  - [x] SubTask 2.4: 实测 44 / 36 / 4，Failures=0，Errors=0
  - [x] SubTask 2.5: 单一目的提交（5 文件，不含批改相关文件）

- [x] Task 3（C8-3）: 批改接地（提交 `f13ffc23`，依赖 Task 1）
  - [x] SubTask 3.1: `aiGradeSubmission` 按 `task.getCourseId()` 取知识点与检索证据，追加参考段与批改口径
  - [x] SubTask 3.2: 证据名写入 `submissionData.aiEvidence`（为空不写）；只更新 AI 列
  - [x] SubTask 3.3: 新增 `SubmissionGradeGroundingTest`（3 用例）
  - [x] SubTask 3.4: 实测 SubmissionServiceImplTest 42 / SubmissionGradeGroundingTest 3，Failures=0，Errors=0
  - [x] SubTask 3.5: 单一目的提交（3 文件）

- [x] Task 4（C9）: IREM 隐式关系候选抽取（提交 `98e1a1b8`）
  - [x] SubTask 4.1: 新增 `python-services/kg_service/irem.py`（共现窗口 + INCLUDES/PREREQUISITE/RELATED，纯函数、确定性、无 I/O 写操作）
  - [x] SubTask 4.2: 新增 `test_irem.py`（10 用例）
  - [x] SubTask 4.3: `python -m pytest python-services/kg_service -q` → 44 passed（基线 34）；未新增端点、未写库
  - [x] SubTask 4.4: 单一目的提交，说明标注效果“未知”

- [x] Task 5: 收尾核验（不额外提交）
  - [x] SubTask 5.1: `git status --short -- frontend` 为空；本批提交不含任何图谱写路径；4007 现读数 65 节点/120 边，节点均为网络课程内容（无 Python/课程 1 节点），与上一轮记录的“58 节点”存在计数口径差异，原因未知
  - [x] SubTask 5.2: 重启全栈 `python scripts\start-all.py all --no-color`，`/api/actuator/health/probe`=UP，端口 7687/8001/8002/8080/5173 全 True；RAG 直连课程 1 返回 `mode=ENGINE`、3 条章节召回；图谱课程 1 = 4 节点/3 边。出题/批改接口的 LLM 级实测**受阻**：本地 Ollama `deepseek-r1:8b` 在应用 read-timeout（60s，临时调至 240s 复测）内未返回，推荐接口先触发 async 超时（120s）返回 500，未观察到 `knowledgePoint`/`aiEvidence`，结论标“未知”
  - [x] SubTask 5.3: 读数与“未知”项已汇总到最终回复；本机已恢复原始启动参数（无 OLLAMA_THINK/AI_READ_TIMEOUT 覆盖），Ollama 托盘服务已恢复

# Task Dependencies
- Task 2 依赖 Task 1
- Task 3 依赖 Task 1
- Task 4 独立，可与 Task 2/3 并行
- Task 5 依赖 Task 1-4
