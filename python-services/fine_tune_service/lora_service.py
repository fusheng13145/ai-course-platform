"""
LoRA 微调服务（8003）· 集成契约 + 真实 PEFT 训练引擎
================================================
端点与 JSON 形状严格对齐 `docs/项目手册.md` §37/§38、Java 端
`FineTuneService` / `LLMFineTuneServiceImpl` 与 `ContractPaths.java:63-70`。
CI 护栏 job `python-fine-tune-contract`（`pytest -q`，含 `test_ft_contract.py`）守护该契约，
替换/演进引擎逻辑时不得破坏端点路径与响应结构。

引擎实现在 `lora_engine.py`（数据集登记、LoRA/PEFT 训练、adapter 导出与推理）：
- 重依赖（torch/transformers/peft/datasets）**懒加载**，缺失时 `/health` 如实报
  `engine_available=false` + `deps_missing`，训练与推理按手册 §38 降级；
- 降级体走 HTTP 200 + `code=503` / `mode=UNAVAILABLE`：Java 端只对非 5xx 原样透传响应体；
- **`/infer` 与 `/export` 永不返回占位成功**：没有真实权重/真实文本时，在契约字段上
  摆一个看起来成功的值就是取证造假。可下载形状由契约测试造假 adapter 目录在 CI 钉住
  （纯 Python，无需 torch 在场）。

`FT_ENGINE_READY=1` 的确切覆盖面：**只**表示"不启动训练线程"，任务记录、状态机、
取消/删除、数据集与语料端点全部照常为真。它不改变任何响应字段的真假。

契约端点（§37 八条）：
  GET  /api/v1/fine-tune/health
  POST /api/v1/fine-tune/start              {course_id, base_model, config}  -> 任务快照
  GET  /api/v1/fine-tune/tasks/{task_id}/status
  GET  /api/v1/fine-tune/tasks?course_id=
  POST /api/v1/fine-tune/tasks/{task_id}/cancel
  POST /api/v1/fine-tune/infer/{task_id}   {prompt, params}                  -> {answer, tokens_used, mode}
  GET  /api/v1/fine-tune/tasks/{task_id}/export                              -> {download_url, file_size}
  DELETE /api/v1/fine-tune/tasks/{task_id}
附加六条（数据集/语料，Java 侧后续批次接入）：
  POST   /api/v1/fine-tune/datasets                    {course_id, samples | jsonl}
  POST   /api/v1/fine-tune/datasets/build/{course_id}  [{source}]
  GET    /api/v1/fine-tune/datasets?course_id=
  GET    /api/v1/fine-tune/datasets/{dataset_id}/samples?offset=&limit=
  DELETE /api/v1/fine-tune/datasets/{dataset_id}
  GET    /api/v1/fine-tune/corpus
权重下载：GET /weights/{ft_YYYYMMDD_NNN.zip}（下载路由而非 StaticFiles，见函数注释）

启动：`uvicorn lora_service:app --host 0.0.0.0 --port 8003`（**单 worker**：训练在进程内
后台线程执行，多 worker 会让 busy 护栏与取消标志各自看见一半状态）。
数据目录 `FT_DATA_DIR`（默认 ./data，已 gitignore）。
"""
from __future__ import annotations

import json
import os
import re
from typing import Optional, Union

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

try:
    import lora_engine
except ImportError:  # 以 python-services 为工作目录、按子包方式启动时（uvicorn fine_tune_service.lora_service:app）
    from . import lora_engine

app = FastAPI(title="AI Course Fine-Tune Service", version="0.1.0")

# 与 Java 端 ContractPaths 的降级文案保持同口径：真正无实现可说时才用它兜错误文本
MSG_FT_UNAVAILABLE = "LoRA 微调引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用"

# 下载路由只认 `<task_id>.zip`：任务号本身是 ft_数字_数字，绝不接受路径分隔符或点号目录。
# 序号位数放宽到 3 位以上：任务号按天只增，第 1000 个任务是 4 位，卡死 3 位会让导出真的下不下来。
_WEIGHT_NAME = re.compile(r"^ft_\d{8}_\d{3,}\.zip$")


def ready() -> bool:
    """CI 结构开关：**只**影响 `/start` 是否启动训练线程（spec §8、手册 §42）。

    不为 `/export` 或 `/infer` 造占位成功：没有真实权重与真实文本时，在契约字段上
    摆一个看起来成功的值就是取证造假。导出形状改由 `test_ft_contract.py` 造假
    adapter 目录在 CI 钉住（纯 Python，无需 torch），既真又不依赖引擎在场。
    """
    return os.getenv("FT_ENGINE_READY", "0") == "1"


@app.exception_handler(lora_engine.StoreCorruptedError)
async def _on_corrupt(_request, exc):
    """存储损坏按 §38 出降级体（HTTP 200 + code=503），绝不冒泡成 500、也绝不静默返回空。"""
    return JSONResponse(status_code=200, content={
        "error": f"引擎存储损坏：{exc}", "mode": "UNAVAILABLE", "code": 503})


class StartIn(BaseModel):
    course_id: int = Field(ge=1)
    base_model: str = Field("qwen-7b", min_length=1, max_length=120)
    # Java 发 JSON 字符串、§37 示例是对象（DR-73）：形状在此放宽，归一交给引擎的 as_dict
    config: Union[dict, str, None] = None


class InferIn(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    params: Union[dict, str, None] = None


class DatasetIn(BaseModel):
    course_id: int = Field(ge=1)
    samples: Optional[list[dict]] = None
    jsonl: Optional[str] = None


class BuildIn(BaseModel):
    source: Optional[str] = None


class TaskOut(BaseModel):
    task_id: str
    course_id: Optional[int] = None
    base_model: Optional[str] = None
    dataset_id: Optional[str] = None
    status: str
    progress: float = 0.0
    current_epoch: int = 0
    total_epochs: int = 0
    loss: Optional[float] = None
    error: Optional[str] = None
    created_at: Optional[str] = None
    updated_at: Optional[str] = None
    mode: str = "ENGINE"
    code: int = 200


class InferOut(BaseModel):
    answer: Optional[str] = None
    tokens_used: int = 0
    model: Optional[str] = None
    mode: str = "UNAVAILABLE"
    error: Optional[str] = None
    code: int = 503


class ExportOut(BaseModel):
    download_url: Optional[str] = None
    file_size: Optional[str] = None
    size_bytes: Optional[int] = None
    format: Optional[str] = None
    mode: str = "UNAVAILABLE"
    error: Optional[str] = None
    code: int = 503


_TASK_KEYS = ("task_id", "course_id", "base_model", "dataset_id", "status", "progress",
              "current_epoch", "total_epochs", "loss", "error", "created_at", "updated_at")


def _task_out(rec: dict, mode: str = "ENGINE", code: int = 200) -> TaskOut:
    """存储记录 → 契约响应：字段白名单，`adapter_dir`/`config` 等内部项不出门。"""
    return TaskOut(**{k: rec.get(k) for k in _TASK_KEYS}, mode=mode, code=code)


def _ds_out(rec: dict) -> dict:
    return {"dataset_id": rec["dataset_id"], "course_id": rec["course_id"],
            "sample_count": rec["sample_count"], "source": rec["source"],
            "created_at": rec["created_at"]}


# ---------------------------------------------------------------- §37 八条 + 权重下载


@app.get("/api/v1/fine-tune/health")
def health():
    return {"status": "ok", "engine": f"lora-{lora_engine.ENGINE_VERSION}",
            "engine_available": lora_engine.engine_available(),
            "deps_missing": lora_engine.dep_errors(),
            "device": lora_engine.device(),
            "datasets": len(lora_engine.list_datasets()),
            "tasks": len(lora_engine.list_tasks()),
            "base_model_default": os.getenv("FT_DEFAULT_BASE_MODEL", "Qwen/Qwen2.5-0.5B"),
            "known_models": lora_engine.known_models()}


@app.post("/api/v1/fine-tune/start", response_model=TaskOut)
def start_task(body: StartIn):
    try:
        cfg = lora_engine.normalize_config(lora_engine.as_dict(body.config))
    except lora_engine.ConfigError as exc:
        raise HTTPException(400, detail=f"config 非法：{exc}")
    try:
        lora_engine.resolve_base_model(body.base_model)
    except lora_engine.UnknownModelError as exc:
        raise HTTPException(400, detail=str(exc))
    # 数据集按课程判定：本课程没数据就拒绝，绝不借用别课程的语料建一个练不成的任务
    err = lora_engine.task_error(cfg, body.course_id)
    if err:
        # /start 只有这一条失败出口：保持 TaskOut 单一形状，用 task_id="" + code=503 表达
        return TaskOut(task_id="", course_id=body.course_id, status="rejected",
                       error=err, mode="UNAVAILABLE", code=503)
    task = lora_engine.create_task(body.course_id, body.base_model, cfg)
    if ready():
        return _task_out(task, mode="STRUCTURE")
    lora_engine.start_training(task["task_id"])
    return _task_out(lora_engine.get_task(task["task_id"]) or task)


@app.get("/api/v1/fine-tune/tasks/{task_id}/status", response_model=TaskOut)
def task_status(task_id: str):
    rec = lora_engine.get_task(task_id)
    if not rec:
        raise HTTPException(404, detail="task not found")
    return _task_out(rec)


@app.get("/api/v1/fine-tune/tasks")
def list_tasks(course_id: Optional[int] = Query(None)):
    return {"tasks": [_task_out(t).model_dump() for t in lora_engine.list_tasks(course_id)],
            "mode": "ENGINE", "code": 200}


@app.post("/api/v1/fine-tune/tasks/{task_id}/cancel")
def cancel_task(task_id: str):
    out = lora_engine.cancel_task(task_id)
    if out.get("status") == "not_found":
        raise HTTPException(404, detail="task not found")
    return out


@app.post("/api/v1/fine-tune/infer/{task_id}", response_model=InferOut)
def infer_task(task_id: str, body: InferIn):
    try:
        out = lora_engine.infer(task_id, body.prompt, lora_engine.as_dict(body.params))
    except lora_engine.ConfigError as exc:
        raise HTTPException(400, detail=f"params 非法：{exc}")
    if out.get("code") == 404:
        raise HTTPException(404, detail=out.get("error") or "task not found")
    return InferOut(answer=out.get("answer"), tokens_used=int(out.get("tokens_used") or 0),
                    model=out.get("model"), mode=out.get("mode", "UNAVAILABLE"),
                    error=out.get("error"), code=int(out.get("code", 503)))


@app.get("/api/v1/fine-tune/tasks/{task_id}/export", response_model=ExportOut)
def export_task(task_id: str):
    out = lora_engine.export_weights(task_id)
    if out.get("code") == 404:
        raise HTTPException(404, detail=out.get("error") or "task not found")
    if out.get("code") != 200:
        return ExportOut(error=out.get("error"), mode="UNAVAILABLE", code=503)
    return ExportOut(download_url=out["download_url"], file_size=out["file_size"],
                     size_bytes=out["size_bytes"], format=out["format"],
                     mode="ENGINE", code=200)


@app.delete("/api/v1/fine-tune/tasks/{task_id}")
def delete_task(task_id: str):
    removed = lora_engine.delete_task(task_id)
    if removed is None:
        raise HTTPException(404, detail="task not found")
    return {"success": True, "task_id": task_id, "mode": "ENGINE", "code": 200}


@app.get("/weights/{weight_name}")
def download_weight(weight_name: str):
    """导出的 zip 真的可下载（§37 的 download_url 不是装饰）。

    不用 StaticFiles：它在 import 期就把目录钉死，而 `FT_DATA_DIR` 是按用例注入的
    （测试隔离），钉死后会指向错误的目录。这里按 `weights_dir()` 的当前值解析，
    并只接受 `ft_YYYYMMDD_NNN.zip` 形状——编码斜杠/点号一律 404。
    """
    if not _WEIGHT_NAME.match(weight_name):
        raise HTTPException(404, detail="weight not found")
    root = lora_engine.weights_dir().resolve()
    target = (root / weight_name).resolve()
    if target.parent != root or not target.is_file():
        raise HTTPException(404, detail="weight not found")
    return FileResponse(str(target), media_type="application/zip", filename=weight_name)


# ---------------------------------------------------------------- 附加六条（数据集/语料）


def _rows_of(body: DatasetIn) -> list:
    if body.jsonl:
        rows = []
        for i, line in enumerate(body.jsonl.splitlines(), 1):
            if not line.strip():
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError as exc:
                raise HTTPException(400, detail=f"jsonl 第 {i} 行解析失败：{exc}")
        return rows
    return list(body.samples or [])


@app.post("/api/v1/fine-tune/datasets")
def create_dataset(body: DatasetIn):
    if body.jsonl is None and body.samples is None:
        raise HTTPException(400, detail="必须提供 samples 数组或 jsonl 文本其一")
    try:
        samples = lora_engine.validate_samples(_rows_of(body))
    except ValueError as exc:
        raise HTTPException(400, detail=str(exc))
    saved = lora_engine.add_dataset(body.course_id, samples,
                                    source="上传 JSONL" if body.jsonl else "上传样本")
    return {**_ds_out(saved), "mode": "ENGINE", "code": 200}


@app.post("/api/v1/fine-tune/datasets/build/{course_id}")
def build_dataset(course_id: int, body: Optional[BuildIn] = None):
    out = lora_engine.build_course_dataset(course_id, body.source if body else None)
    return {**out, "mode": "ENGINE" if out.get("success") else "UNAVAILABLE",
            "code": 200 if out.get("success") else 503}


@app.get("/api/v1/fine-tune/datasets")
def list_datasets(course_id: Optional[int] = Query(None)):
    return {"datasets": [_ds_out(d) for d in lora_engine.list_datasets(course_id)],
            "mode": "ENGINE", "code": 200}


@app.get("/api/v1/fine-tune/datasets/{dataset_id}/samples")
def get_samples(dataset_id: str, offset: int = Query(0, ge=0),
                limit: int = Query(50, ge=1, le=500)):
    rec = lora_engine.get_dataset(dataset_id)
    if not rec:
        raise HTTPException(404, detail="dataset not found")
    window = lora_engine.load_samples(dataset_id)[offset:offset + limit]
    return {"dataset_id": dataset_id, "course_id": rec["course_id"],
            "sample_count": rec["sample_count"], "offset": offset, "limit": limit,
            "returned": len(window), "samples": window, "mode": "ENGINE", "code": 200}


@app.delete("/api/v1/fine-tune/datasets/{dataset_id}")
def delete_dataset(dataset_id: str):
    removed = lora_engine.delete_dataset(dataset_id)
    if removed is None:
        raise HTTPException(404, detail="dataset not found")
    return {"success": True, "dataset_id": dataset_id, "mode": "ENGINE", "code": 200}


@app.get("/api/v1/fine-tune/corpus")
def list_corpus():
    rows = lora_engine.corpus_catalog()
    return {"documents": rows,
            "courses": sorted({d["course_id"] for d in rows if d.get("course_id") is not None}),
            "mode": "ENGINE", "code": 200}


if __name__ == "__main__":  # 直跑入口（同 kg/rag 服务风格）
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8003)
