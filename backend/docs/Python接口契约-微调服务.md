# Python 接口契约 - 微调服务（LoRA/PEFT）

> 版本：v1 ｜ 更新：2026-08-06 ｜ 服务端：Python 微调微服务（**端口 8003**）｜ 调用方：本端 Spring Boot `LLMFineTuneServiceImpl`
> 职责边界：**LoRA 训练/推理/权重管理由 Python 端实现；本端负责任务编排（HTTP 客户端）与前端入口**。本端 `fine-tune.enabled=false` 或服务不可达时，微调能力整体降级（返回明确不可用信息），不影响其余功能。

---

## 一、服务部署与配置

| 项 | 值 |
|----|----|
| 服务地址 | `http://localhost:8003`（本端 `fine-tune.base-url` 可配） |
| 框架建议 | FastAPI + `peft` + `transformers` + `bitsandbytes`（QLoRA 可降显存） |
| 基座模型 | **本地可部署开源模型**（qwen-7b/qwen-14b 等；DeepSeek 为 API 调用，无法本地 LoRA） |
| 数据集 | 本端按 `courseId` 筛选课程内容（知识点/章节/题库），Python 端可自行拉取本端 `/api/knowledge/points`、`/api/chapters` 等接口构建 instruction 数据集 |

## 二、端点契约（全部前缀 `/api/v1/fine-tune`）

### 1. 健康检查
```
GET /api/v1/fine-tune/health → 200
```
响应：`{"status":"ok"}`（非 200 视为不可达）。

### 2. 启动微调任务
```
POST /api/v1/fine-tune/start
Content-Type: application/json

{
  "course_id": 1,                // 课程ID（数据集筛选）
  "base_model": "qwen-7b",       // 基座模型名
  "config": {                    // LoRA 超参（自由对象，本端原样透传）
    "epochs": 3,
    "learning_rate": 5e-5,
    "lora_rank": 8,
    "lora_alpha": 16,
    "lora_dropout": 0.05,
    "max_seq_length": 2048,
    "batch_size": 4
  }
}
```
响应 `200`：`{"task_id": "ft_20260806_xxxx", "status": "pending"}`（`task_id` 为后续操作句柄）。

### 3. 查询任务状态
```
GET /api/v1/fine-tune/tasks/{task_id}/status
```
响应 `200`：
```json
{ "task_id": "ft_...", "status": "running", "progress": 42,
  "message": "epoch 2/3 训练中", "completed_at": null }
```
`status` 枚举：`pending` / `running` / `completed` / `failed`；`progress` 0-100 整数（前端进度条用）。

### 4. 任务列表
```
GET /api/v1/fine-tune/tasks?course_id=1      // course_id 可选，缺省返回全部
```
响应 `200`：`{ "tasks": [ {task_id, course_id, base_model, status, progress, created_at} ] }`

### 5. 取消任务
```
POST /api/v1/fine-tune/tasks/{task_id}/cancel
```
响应 `200`：`{"task_id": "...", "status": "cancelled"}`（仅 `pending`/`running` 可取消）。

### 6. 微调模型推理（挂载 LoRA adapter）
```
POST /api/v1/fine-tune/infer/{task_id}
Content-Type: application/json
{ "prompt": "什么是导数？", "params": {"temperature": 0.7, "max_tokens": 2048} }
```
响应 `200`：`{"task_id": "...", "content": "微调模型生成的回答文本"}`

### 7. 导出 LoRA 权重
```
GET /api/v1/fine-tune/tasks/{task_id}/export
```
响应 `200`：`{"task_id": "...", "download_url": "http://.../adapter_v1.bin", "size_bytes": 123456}`（权重产物下载链接，本端仅记录不落地）。

## 三、错误约定

| HTTP | 场景 | 响应体 |
|------|------|--------|
| 404 | task_id 不存在 | `{"detail": "task not found"}` |
| 409 | 状态不允许操作（如 completed 后 cancel） | `{"detail": "invalid state transition"}` |
| 500 | 训练异常 | `{"detail": "训练失败原因"}` |

## 四、本端降级语义（Python 端无需处理）

- `fine-tune.enabled=false`（默认）→ 本端所有微调 API 返回"微调服务未启用"（HTTP 503 语义的 ApiResponse）。
- Python 服务不可达 → `healthCheck()` 返回 false，本端接口返回"微调服务不可达"，**不抛 500**。
- 前端展示以健康检查为前置开关：服务不可用时微调页面显示禁用态。

## 五、本端已就绪部分（无需 Python 端配合）

`LLMFineTuneService` 接口与实现（HTTP 客户端、超时/重试配置化、SERVICE_UNAVAILABLE 降级）已存在；`FineTuneController`（`/api/admin/fine-tune/**`）提供对外 API；数据集构建若需本端侧导出，可再增补端点。
