# AI 助教系统 · 幻觉率 / 准确率评测方案

> 针对评估短板「AI 能力深度（75/B+）：缺幻觉率量化评测」的补强交付。
> 目的：为申报承诺「降低幻觉」提供**可量化、可对比的答辩证据链**。
>
> ⚠️ **职责边界（2026-08-13）**：RAG 检索引擎由团队另一成员负责，本端仅预留 `RAGService` 接口 + HTTP 代理（8002）。本评测依赖后端 `/rag/*` 端点，须在另一成员 RAG 服务接入（`RAG_ENABLED=true` 指向其服务）后方可实跑；未接入时后端返回 `{"code":503}` 降级，评测无法采集有效数据。

## 目录结构

```
evaluation/
├── qa-set.json      # 60 题专业课 QA 评测集（6 课程域 × 10 题，含参考答案与判定关键词）
├── evaluate.py      # 评测脚本（零第三方依赖）
├── results/         # 评测结果与对比报告（gitignore）
└── README.md        # 本文档
```

## 评测集设计（qa-set.json）

- **规模**：60 题，覆盖 数据结构 / 计算机网络 / 操作系统 / 数据库 / 人工智能基础 / 软件工程。
- **字段**：`question`（问题）、`reference_answer`（参考答案要点）、`keywords`（判定关键词，客观判分依据）、`difficulty`（easy/medium/hard）。
- **用途**：与课程知识图谱知识点一一对应，作为「知识库已收录内容」的判定基准。

## 指标定义

| 指标 | 定义 | 计算方式 |
|------|------|----------|
| **Recall@k** | 检索召回质量：参考答案关键词在 topK 检索命中片段中的覆盖率 | 命中关键词数 / 关键词总数 |
| **答案覆盖率** | 生成答案的要点完整度（客观近似准确率） | 关键词在答案中的命中率 |
| **诚实拒答率** | 低幻觉倾向正向信号：答案声明「知识库未涵盖」等 | 规则匹配（拒绝编造） |
| **Judge 正确率/幻觉率** | 语义级判定（可选，需配置 LLM judge 端点） | LLM-as-judge 输出 0/1 均值 |

## 评测流程（三步）

### 1. 准备知识库
确保后端 `knowledge_points` 表已收录目标课程（courseId）的对应知识点
（可通过 `POST /api/knowledge/extract` 单条 LLM 抽取摄入，或管理端图谱维护；批量推送由团队另一成员图谱服务经既有 `POST /api/knowledge/points` + `/relations` 端点写入）。

### 2. 分模式采集
在另一成员 RAG 服务接入后，按其服务支持的检索模式（如 bm25 / hybrid）分别运行采集：

```bash
# 模式一：bm25（纯词法基线）
python evaluate.py --username admin --password '密码' --course 1 --topk 5 --tag bm25 --ask --out results/run-bm25.json

# 模式二：hybrid（BM25+向量 RRF 融合，默认）
python evaluate.py --username admin --password '密码' --course 1 --topk 5 --tag hybrid --ask --out results/run-hybrid.json
```

可选启用 LLM-as-judge（语义判定）：

```bash
export LLM_JUDGE_URL=https://api.deepseek.com/v1/chat/completions
export LLM_JUDGE_KEY=sk-xxx
export LLM_JUDGE_MODEL=deepseek-chat
```

### 3. 合并生成对比报告

```bash
python evaluate.py --merge results/run-bm25.json results/run-hybrid.json --out results/report.md
```

## 答辩话术建议

> 「我们构建了 60 题专业课 QA 评测集，对 RAG 服务（由团队另一成员负责）提供的不同检索模式进行了对比评测：
> 混合检索的 Recall@k 从 X% 提升至 Y%，答案覆盖率从 X% 提升至 Y%，Judge 幻觉率下降 Z%。
> 对知识库未涵盖的问题，系统会明确声明『知识库未涵盖』而非编造答案，诚实拒答率为 X%。」

> ⚠️ 使用前请将占位符替换为真实运行数据；建议附 2–3 张检索/问答截图作为补充证据。
