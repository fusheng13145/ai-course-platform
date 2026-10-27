# LoRA 微调引擎（Python :8003）· 设计定稿

- 日期：2026-10-17
- 批次登记：手册附录 D 新增 **DR-78**（引擎侧交付）、**DR-79**（`config`/`params` 两端形状漂移）。原拟号码 DR-72/DR-73 已被并发批次（`dafa3c0`，2026-10-18）占用，本批按实测最高号 DR-77 顺延；登记前须重跑 `grep -oh "DR-[0-9]\+" docs/项目手册.md | sort -n | tail -1`
- 分支：`leyon`
- 状态：**设计定稿**（项目主逐节批准，并对语料来源与取证深度两处授权"按工业级最优、不计代价"定案），转入实施计划。

## 1. 需求来源与决策链

项目主要求：Python 侧由另一成员实现的只有 KG(8001) 与 RAG(8002)，**LoRA(8003) 一个文件都没有**，按方案应当补齐，且须**仿照其代码风格**。

已确认的三个决策（项目主选定）：

1. **引擎真实度**：真实 PEFT 训练 + 重依赖懒加载降级（逐条仿 `rag_engine.py`），不做模拟进度的骨架。
2. **训练语料**：项目主不熟此节，授权按"工业级最优"定案 → 本设计采**显式数据集制品**（§4），明确否掉 `/start` 时静默去读 RAG 语料的隐式兜底。
3. **取证深度**：本机真跑一次训练取证（§8），只补装 `peft/datasets/accelerate`，**不动现有 `torch 2.11.0+cpu`**。

## 2. 现状与取证

以下均为本会话实读/实测，非文档转述：

| 位置 | 实测事实 |
| --- | --- |
| `python-services/` | 仅 `kg_service`、`rag_service` 两个目录，**无任何 8003 文件**（2026-10-17 `ls` 复核） |
| `docs/项目手册.md:1841` | §37「LoRA 微调服务（8003）契约」定义 8 条路径与响应字段 |
| `backend/.../integration/ContractPaths.java:63-70` | `FT_HEALTH`/`FT_START`/`FT_TASK_STATUS`/`FT_TASKS`/`FT_TASK_CANCEL`/`FT_INFER`/`FT_TASK_EXPORT`/`FT_TASK` 八常量已就绪 |
| `backend/.../service/impl/LLMFineTuneServiceImpl.java:75-157` | 8 个方法真实调用（含 `deleteTask`），`fine-tune.enabled=false` 时统一返回 `{"error":"微调服务暂不可用","code":503}` |
| `LLMFineTuneServiceImpl.java:80`、`:129` | `Map.of(..., "config", config)` / `Map.of(..., "params", params)`——**`config`/`params` 以 String 发出** |
| `backend/.../controller/FineTuneController.java:58`、`:101` | `configJson = config.toString()`、`paramsJson = params.toString()`，即 Java 把 `JsonNode` 序列化成字符串再传 |
| `FineTuneController.java:50-113` | 对外暴露 health/start/status/list/cancel/infer/export **7 个**入口，**`deleteTask` 无控制器入口** |
| `backend/.../controller/FineTuneControllerTest.java` | 全部 mock `LLMFineTuneService`，**不触及真实引擎**，故 Python 落地不影响该测试 |
| `python-services/rag_service/rag_service.py:143-157` | `_engine_ready_for` / `_degrade_error` 的"可操作降级文案"范式 |
| `python-services/rag_service/rag_engine.py:89-119` | `embed_model_cached()` **绝不发网络探测**，命中缓存才 `local_files_only=True` |
| `rag_engine.py:185-223` | `_quarantine_corrupt` + `_atomic_write_text/bytes`（临时文件→fsync→`os.replace`）落盘范式 |
| `.github/workflows/ci.yml:578-606` | `python-rag-contract`：`working-directory` + 仅装 `requirements.txt` + `pytest -q`；重依赖不装、`skipif` 收敛 |
| `.gitignore:88` | 已有 `python-services/rag_service/data/`，需同形追加 8003 数据目录 |
| `scripts/rag_corpus.json` | 顶层 `{version, description, documents}`，**12 篇**讲义，每条 `{course_id, document_name, content}`，覆盖课程 1 与 4007 |
| 本机 Python 环境 | 3.11.9；`torch 2.11.0+cpu`、`transformers`、`numpy`、`jieba`、`faiss`、`sentence_transformers`、`fastapi`、`pytest` **已装**；`peft`/`datasets`/`accelerate` **缺失**；HF 缓存仅 `BAAI/bge-small-zh-v1.5`；GPU 为 RTX 4060 8G 但 torch 是 CPU 构建 |

**结论**：消费侧与契约层已完备，缺的全在引擎侧；本设计只需新增 `python-services/fine_tune_service/`，**不触碰 Java 代码**。

## 3. 落点与分层（严格仿队友）

```
python-services/fine_tune_service/
  lora_service.py          # FastAPI :8003 薄契约层：8 条 §37 端点 + 6 条数据集/语料端点 + pydantic 模型 + 降级
  lora_engine.py           # 引擎层：数据集构建、任务存储、真实 PEFT 训练、adapter 导出、加载推理（重依赖懒加载）
  requirements.txt         # fastapi / uvicorn / pydantic / httpx                ← CI 只装这些
  requirements-engine.txt  # torch / transformers / peft / datasets / accelerate
  test_ft_contract.py      # §37 契约护栏（TestClient）
  test_ft_engine.py        # 引擎单测 + 真实训练用例（skipif 门禁）
```

分层与命名对齐既有两服务：路由层不做计算、引擎层不感知 HTTP；目录 snake_case、容器/文档服务名仍为 `fine-tune-service`、URL 前缀逐字取 `ContractPaths.FT_*`。

## 4. 训练语料：数据集是一等制品

工业标准要求"参与训练的每条样本可检视、可重建、可回收"，因此**不做静默兜底**。端点面（§37 之外的**附加**端点，共 6 条）：

| 端点 | 语义 |
| --- | --- |
| `POST /api/v1/fine-tune/datasets` | 登记样本：JSONL 文本或 `{instruction,input,output}`/`{question,answer}`/`messages` 三形状数组 → `{dataset_id, course_id, sample_count, source}`；校验在**引擎** `validate_samples()`，非法行 → 400 且文案带 1-based 行号 |
| `POST /api/v1/fine-tune/datasets/build/{course_id}` | 从课程讲义语料**确定性派生**指令样本 → `{success, dataset_id, sample_count, source, created_at}`；**`source` 为可选 body**（见下）；镜像 RAG 既有 `index/build/{courseId}` 惯例 |
| `GET /api/v1/fine-tune/datasets?course_id=` | 列出数据集 `{datasets:[...]}`（供 `/start` 选择与人工检视） |
| `GET /api/v1/fine-tune/datasets/{dataset_id}/samples` | **分页**返回样本原文 `?offset=&limit=`（默认 50、上限 500，超限 422）→ `{dataset_id, course_id, sample_count, offset, limit, returned, samples}`；未知 id → 404。"可检视"是数据集作为制品的必要条件，分页而非截断：整段截断会让人误以为样本比实际少 |
| `DELETE /api/v1/fine-tune/datasets/{dataset_id}` | `{success:true}`，删除表项与样本目录（可回收；镜像 RAG `DELETE /documents/{id}`）；未知 id → 404 |
| `GET /api/v1/fine-tune/corpus` | 列出可派生语料 `[{course_id, document_name, chars}]`，供管理员确认该建哪门课的数据集 |

**`source` 必须可选（计划期补出的设计缺口）**：Java 端不会传语料体，若 `build` 强制要求 body，则 `FT_ENABLED=true` 时 `/start` 恒 503、链路仍不可用。定案的回落链：调用方 `source` → `FT_CORPUS_DIR/<course_id>.{json,md,txt}` → 仓库内已版本化 `scripts/rag_corpus.json`；三处皆无 → 诚实失败并给出应设置的变量名。

- `/start` 经自由形态 `config.dataset_id` 选定数据集，缺省取该课程最新一份 → **§37 的 8 条路径零扩张**。
- 该课程无数据集时按 §38 诚实降级，文案可操作、与 `rag_service._degrade_error` 同口径：
  `{"error":"课程 1 尚无训练数据集，请先 POST /api/v1/fine-tune/datasets/build/1 或上传 JSONL","code":503,"mode":"UNAVAILABLE"}`
- 可选 LLM 合成指令样本**本轮不做**：本机 `DEEPSEEK_API_KEY` 实测 401（手册 DR-38），写进验收路径会产出假证据。

## 5. 引擎设计

- **懒加载**：`torch`/`transformers`/`peft`/`datasets`/`accelerate` 逐个 `try import` 并记 `_DEP_ERRORS`；缺任一 → `engine_available()=False`，契约层按 §38 返回 `code=503`，**绝不 500**，CI 因此仍只装 `requirements.txt` 全绿。
- **离线安全**：`base_model_cached()` 采 `try_to_load_from_cache` 纯本地判定（同 `embed_model_cached`），命中才 `local_files_only=True`，避免 CI/离线环境联网探测长时间挂起。
- **模型别名表**：`base_model` 是业务名（Java 默认 `qwen-7b`），经 `FT_DEFAULT_BASE_MODEL` + `FT_MODEL_MAP`（`名=HF_ID;…`）解析真实 HF 模型 ID，本机开发默认 `Qwen/Qwen2.5-0.5B`；**未登记名称直接报错并回显登记表**，不猜模型。
- **训练**：`LoraConfig(r=, lora_alpha=, lora_dropout=, target_modules=)` 全量取自 `config` 并做范围校验；`PeftModel` + transformers `Trainer` + `DataCollatorForLanguageModeling(mlm=False)`（completion-only，见本节末掩码取舍）。**不引入 `trl`**：其 API 断裂频繁，引进来是给仓库加风险。
- **异步与状态机**：训练在后台线程执行，`pending → running → completed/failed/cancelled`；`progress`/`current_epoch`/`loss` 由 Trainer 回调**真实写入任务存储**，`/status` 读到的就是实测值。回调里**步数与轮次取自 `TrainerState.global_step` / `.epoch`，不是 kwargs**（`on_log` 的 `logs` 只有 loss；从 kwargs 取 `step` 会恒 None、`progress` 永远 0.0 且无报错），写入逻辑单列成 `_record_progress()` 以便 CI 用假 state 断言。取消用协作式标志在 step 边界生效。
- **导出是真的**：adapter 目录 `save_pretrained` 后 zip 至 `data/weights/<task_id>.zip`，`download_url` 指向 `/weights/{weight_name}` 下载路由（`FileResponse`）→ 可真实下载、`file_size` 由实际字节数格式化为 `"15.2MB"` 形状、`format` 固定 `peft_lora`。**不用 `StaticFiles`**：它在 import 期就把目录钉死，与按用例注入的 `FT_DATA_DIR` 冲突（测试隔离会指向错目录）；改路由后按 `weights_dir()` 当前值解析，并用 `ft_YYYYMMDD_NNN.zip` 白名单 + `target.parent != root` 双闸拦路径穿越。未完成任务不做占位导出，如实 503。
- **推理是真的**：基座 + adapter 载入后真实 `generate`，`tokens_used` 取生成实际 token 数，`model` 为 `<base_model>-lora-<task_id>`；缺依赖/缺权重按契约降级。
- **存储**：`data/datasets.json` + `data/tasks.json` 两张表（各带 `records` 与 `seq`；样本另存 `datasets/<id>/samples.json` 供人工检视），原子落盘 + 解析失败改名隔离并抛 `StoreCorruptedError`（与 `rag_engine` 逐条同构）；`FT_DATA_DIR` 注入做测试隔离；`.gitignore` 追加 `python-services/fine_tune_service/data/`。**ID 号段按日期单调递增、删除后不复用**：`ds_YYYYMMDD_NNN`/`ft_YYYYMMDD_NNN` 的 NNN 来自持久化 `seq`，若由 `len+1` 推出，删掉一份数据集后再建同名号会让历史任务的 `dataset_id` 悄悄指向另一份数据。
- **loss 掩码取舍（显式记录，不默默略过）**：采 completion-only 整理器（`DataCollatorForLanguageModeling(mlm=False)`）对「指令+回答」整段计损失，而非只掩掉 prompt 段。理由：对 0.5B 基座做部分掩码依赖模板 token 一致性，掩错会静默学成"复读指令"且无报错；截断风险改由 `max_seq_length` 与 `FT_MAX_SAMPLES` 在样本构造侧兜住。此取舍须同时写进 `lora_engine.py` docstring 与手册 §46.2。
- **单进程假设**：训练线程与内存态任务表绑定于单 uvicorn worker，多 worker 需外置队列——本轮在手册登记为边界，不做分布式。

### 5.1 环境变量面（引擎只读进程环境、不读 `.env`，同 DR-39 口径）

| 变量 | 默认 | 语义 |
| --- | --- | --- |
| `FT_DATA_DIR` | `fine_tune_service/data` | 数据集、任务表、adapter、zip 的根目录 |
| `FT_CORPUS_DIR` | 未设 | 每课程一文件的讲义目录（`<course_id>.json/.md/.txt`），回落链优先级最高 |
| `FT_CORPUS_JSON` | `scripts/rag_corpus.json` | 讲义派生数据集的**回落**语料（仓库内已版本化，满足 DR-40 重建路径要求） |
| `FT_DEFAULT_BASE_MODEL` | `Qwen/Qwen2.5-0.5B` | 别名表未命中时的兜底 HF 模型 ID |
| `FT_MODEL_MAP` | 空 | `业务名=HF_ID;业务名=HF_ID` 别名表（如 `qwen-7b=Qwen/Qwen2.5-0.5B`） |
| `FT_MAX_SAMPLES` | `200` | 单次训练样本上限（CPU 可行性护栏） |
| `FT_MAX_SEQ_LEN` | `512` | 截断长度 |
| `FT_TRAIN_STEPS` | 未设 | 钉死 `max_steps`（本机分钟级取证用；未设时按 epochs/batch 估完整轮步数） |
| `FT_ALLOW_DOWNLOAD` | `0` | `0`=基座模型未本地缓存即诚实失败；`1`=允许首次联网拉取 |
| `FT_DEVICE` | 自动（有 cuda 用 cuda） | 训练/推理设备 |
| `FT_PUBLIC_BASE` | `http://localhost:8003` | `download_url` 拼接基址（容器/反代下须覆盖） |
| `FT_ENGINE_READY` | `0` | CI 结构开关：只影响 `/start` 是否启动训练线程（见 §8） |

## 6. 端点契约总表

| §37 契约 | Python 实现要点 |
| --- | --- |
| `GET /health` | 200；body `{status, engine_available, deps_missing[], base_model_default}`（Java 侧 `toBodilessEntity()`，多余字段无害） |
| `POST /start` | `{task_id:"ft_YYYYMMDD_NNN", status:"pending", created_at}`；训练线程随后启动 |
| `GET /tasks/{id}/status` | `{task_id,status,progress,current_epoch,total_epochs,loss,base_model,dataset_id,mode}`；本行**恒 `mode=ENGINE`/`code=200`**（存储记录本身就是引擎产物，任务失败由 `status:"failed"` 与 `error` 表达，不用 `mode` 冒充能力；引擎在场与否由 `/health` 独任）。表里的数字始终是实测写入值；未知 id → 404 |
| `GET /tasks?course_id=` | `{tasks:[...]}`，按 `created_at` 升序 |
| `POST /tasks/{id}/cancel` | `{success:true,status:"cancelled"}`；对 `completed`/`failed`/`cancelled` 等终态任务仍返 HTTP 200 与同一形状，但 `{success:false,status:<当前状态>,error:"任务已结束，无法取消"}`（保持 §37 形状，仅 `success` 取反） |
| `POST /infer/{id}` | `{answer,tokens_used,model,mode}`；未完成任务明确拒绝 |
| `GET /tasks/{id}/export` | `{download_url,file_size,format:"peft_lora",mode}` |
| `DELETE /tasks/{id}` | `{success:true}`，删除目录与表项；未知 id → 404 |

响应形状与 KG/RAG 归一：成功/失败都带 `code`，错误带 `error`；`mode` 作为额外诊断字段，**三值且含义写死**——`ENGINE`（本次响应的实质数据确实来自真实引擎产物）/ `UNAVAILABLE`（按 §38 降级）/ `STRUCTURE`（仅 `FT_ENGINE_READY=1` 下的 `/start`：任务记录真实存在，但未启动训练线程）。取证时只认 `ENGINE`；Java 原样透传字符串，加字段不破坏契约。

## 7. 契约漂移处理（DR-79）

`LLMFineTuneServiceImpl.java:80`/`:129` 把 `config`/`params` 当 **String** 发，§37 示例是**对象**；两端已不一致，只因引擎缺席从未暴露。定案：

- Python 端 `_as_dict()` 同时接受对象与 JSON 字符串；非法 JSON → 400 并说明期望形状；字符串内的数值（如 `5e-5`）按 §37 语义解析。
- 契约测试**双形状各钉一例**：Java 真实载荷形状（`config` 为字符串）与 §37 文档形状（`config` 为对象），保证 8003 上线不因 Java 写法全线失败。
- Java 侧是否改发对象，属独立跨端决策：本批次**不动 Java 代码**，仅在手册 §37 记为残项。

## 8. 门禁与本机取证

- `ci.yml` 新增 `python-fine-tune-contract`，与 `python-rag-contract` 同形（`working-directory: python-services/fine_tune_service`、`pip install -r requirements.txt pytest`、`FT_ENGINE_READY=1 pytest -q`）。
- **`FT_ENGINE_READY=1` 的确切覆盖面**（计划期收窄，避免"看起来成功"的假象）：**只影响 `/start`**——照常 `create_task` 落真实任务记录，但**不启动训练线程**，并把 `mode` 标为 `STRUCTURE`。其余端点一律走真实存储与真实降级：`GET /export` 对未完成任务如实返回 `code=503`（原设计的"占位 zip 导出"已删除——它只是在契约字段上造一个看起来成功的 `download_url`，而 §37 的导出形状在 CI 可由 `test_ft_engine.py::test_export_zips_adapter_and_reports_real_bytes` 用纯 Python 造假 adapter 目录钉住，无需 torch，也无需说谎）。`/health` 的 `engine_available:false` 与 `deps_missing` 始终如实报告，占位开关无法掩饰引擎缺失。默认值 `0`；**真跑取证（§8 末）必须在未置该变量时进行**。
- **真跑取证顺序**：补装 `peft datasets accelerate` → 下载 `Qwen/Qwen2.5-0.5B` → 起 8003 → `datasets/build/1` → `/start` → 轮询 `/status` 记录真实 loss 序列 → `/export` 下载 zip 校验字节 → `/infer` 取真实文本。
- **为何不换 CUDA torch**：`sentence_transformers` 依赖当前 `torch 2.11.0+cpu`，替换为 cu128 构建需卸装全局 torch（约 2.5GB）且一旦失败会把已验收的 RAG(8002) 拖回未验证态；收益仅取证更快，故不动。GPU/7B 属部署选型，登记为边界而非本轮能力。
- 手册按实测口径写：坐实到 `mode=ENGINE` 的写 ENGINE，未覆盖的（GPU、7B、并发、容器）明确标注"未验证"。

## 9. 文档回写与批次切分

回写面：§37（实现落地 + 数据集端点 + §7 漂移记项）、§40 降级表、§42 契约测试表、§46.2 LoRA 条由 ❌ 改为实测口径、附录 D 新增 DR-78/DR-79 并把 DR-23 残项收窄、`docs/文档索引.md` 与 `README.md` 引擎读数（8001/8002 → 8001/8002/8003）、B.2 台账测试计数、`项目现状分析与未来发展规划-2026-10-13.md` 的 F1 / D4-1 / D13 状态。

明确**不做**（附理由）：

- **Dockerfile**：本批次不产出，否则同批出现未 `docker build` 验证的交付物；DR-23 残项由"无实现 + 缺容器化"收窄为"仅剩容器化"。
- **Java 侧数据集入口**：`ContractPaths` 增常量 + `FineTuneController` 暴露 + 前端上传页属跨端契约扩张，列**批次 2**。
- **LLM 合成数据集**：受 DR-38 密钥 401 阻塞。

## 10. 验收标准

1. `pytest -q` 在 `python-services/fine_tune_service` 全绿，且**未装** `requirements-engine.txt` 时同样全绿（懒加载降级语义成立）。
2. 8 条 §37 路径逐条有契约用例，含 `config`/`params` 双形状各一例；数据集/语料六端点有用例。
3. 三服务契约作业互不影响：`kg_service`、`rag_service` 现有测试全量复跑绿。
4. Java 侧未改动，`mvn -o test -DskipITs` 口径下 `FineTuneControllerTest` 等仍绿。
5. 本机真跑一次训练：拿到真实 loss 下降序列、可下载的 `peft_lora` zip、真实推理文本，并写入手册。
6. 手册/索引/README 无残留"8003 无任何 Python 实现"的过期表述。
