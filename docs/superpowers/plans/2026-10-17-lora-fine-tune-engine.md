# LoRA 微调引擎（Python :8003）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `python-services/fine_tune_service/`，实现手册 §37 的 8003 LoRA 微调服务（真实 PEFT 训练 + 显式数据集制品 + 懒加载降级），使 `FT_ENABLED=true` 时链路可坐实 `mode=ENGINE`。

**Architecture:** 严格仿队友两文件分层——`lora_service.py` 薄 HTTP 契约层（只做参数归一、降级语义，不做计算），`lora_engine.py` 引擎层（数据集/任务持久化 + 真实训练 + adapter 导出 + 加载推理）。重依赖（torch/transformers/peft/datasets）全部懒加载：缺失时按 §38 返回 `code=503`，绝不 500，因此 CI 只装 `requirements.txt` 也能全绿。

**Tech Stack:** FastAPI + pydantic v2 + httpx（引擎侧唯一外部调用）；transformers `Trainer` + peft `LoraConfig/PeftModel` + datasets；pytest + FastAPI TestClient。

**Spec:** `docs/superpowers/specs/2026-10-17-lora-fine-tune-engine-design.md`

## Global Constraints

- **不触碰 Java 代码**：`ContractPaths.java` / `LLMFineTuneServiceImpl.java` / `FineTuneController.java` 一字不改（本批次只补引擎侧）。
- **URL 与字段名逐字对齐** `ContractPaths.java:63-70` 与手册 §37；任何路径新增都是**附加**，不得改动既有 8 条形状。
- **命名基准**：目录/模块 snake_case（对齐 `kg_service`/`rag_service`），文档与容器服务名仍用 `fine-tune-service`，URL 用 kebab-case。
- **懒加载纪律**：引擎 import 期不得触发模型下载或联网探测；联网只发生在显式调用与 `FT_ALLOW_DOWNLOAD=1` 时。
- **落盘纪律**：一律「临时文件 → flush/fsync → `os.replace`」；JSON 解析失败先改名隔离（`.corrupt.<stamp>`）再抛 `StoreCorruptedError`，不得静默清空。
- **降级纪律**：引擎不可用 / 无数据集 → `{"error": <可操作文案>, "code": 503, "mode": "UNAVAILABLE"}`；错误响应形状与手册 §38 一致。
- **诚实口径**：任何写入手册的能力必须有本机实测证据；未实跑的（GPU、7B、并发、容器）标注「未验证」。
- **提交纪律**：只 `git add` 本批次文件；工作区已存在他人的在飞改动（`.env.example`、`RateLimitFilter.java`、`application.yaml`、手册、`frontend/src/utils/md5.ts`），**禁止 `git add -A`**。
- **`config`/`params` 双形状**：既接受 §37 文档的 JSON 对象，也接受 Java 实际发送的 JSON 字符串（`LLMFineTuneServiceImpl.java:80`/`:129`）；非法 JSON → 400。
- **`mode` 三值写死**（spec §6）：`ENGINE`＝实质数据来自真实引擎产物；`UNAVAILABLE`＝按 §38 降级；`STRUCTURE`＝仅 `FT_ENGINE_READY=1` 下的 `/start`（任务记录真实、训练线程未启动）。**取证只认 `ENGINE`**；不得用占位开关伪造 `ENGINE`。
- **不做占位导出**：`/export` 对未完成任务如实返回 503。§37 的导出形状由 `test_ft_engine.py` 造假 adapter 目录在 CI 钉住（纯 Python，无需 torch），比占位件更真。

## File Structure

| 文件 | 职责 | 动作 |
| --- | --- | --- |
| `python-services/fine_tune_service/lora_engine.py` | 依赖探针、存储（数据集+任务）、配置归一、训练、导出、推理 | Create |
| `python-services/fine_tune_service/lora_service.py` | FastAPI :8003，8 条 §37 端点 + 6 条数据集/语料端点，pydantic 模型与降级 | Create |
| `python-services/fine_tune_service/test_ft_engine.py` | 引擎纯逻辑单测 + 真实训练用例（`skipif` 门禁） | Create |
| `python-services/fine_tune_service/test_ft_contract.py` | §37 契约护栏（TestClient），含双形状载荷与降级语义 | Create |
| `python-services/fine_tune_service/requirements.txt` | CI 轻量依赖 | Create |
| `python-services/fine_tune_service/requirements-engine.txt` | 真实引擎重依赖 | Create |
| `.gitignore:88` 附近 | 追加 `python-services/fine_tune_service/data/` | Modify |
| `.github/workflows/ci.yml:578-606` 后 | 新增 `python-fine-tune-contract` 作业 | Modify |
| `docs/项目手册.md` | §37/§40/§42/§46.2/附录 D（DR-72/DR-73） | Modify |
| `docs/文档索引.md`、`README.md`、`docs/项目现状分析与未来发展规划-2026-10-13.md` | 引擎读数与 F1/D4-1/D13 状态 | Modify |

---

### Task 1: 引擎层·依赖探针与纯配置逻辑

**Files:**
- Create: `python-services/fine_tune_service/lora_engine.py`
- Create: `python-services/fine_tune_service/test_ft_engine.py`
- Create: `python-services/fine_tune_service/requirements.txt`
- Create: `python-services/fine_tune_service/requirements-engine.txt`

**Interfaces:**
- Consumes: 无（首任务）
- Produces：`ENGINE_VERSION:str`、`StoreCorruptedError`、`UnknownModelError`、`ConfigError`、`engine_available()->bool`、`dep_errors()->list[str]`、`data_dir()->Path`、`as_dict(raw)->dict`、`normalize_config(raw_cfg:dict)->dict`、`KNOWN_MODEL_ALIASES:dict[str,str]`、`known_models()->list[str]`、`resolve_base_model(name:str)->str`、`base_model_cached(hf_id:str)->bool`、`format_size(n:int)->str`、`task_error(cfg:dict)->str|None`

- [ ] **Step 1: 建目录与两份 requirements（照 `rag_service` 双清单口径）**

`requirements.txt`：

```
fastapi>=0.110
uvicorn>=0.27
pydantic>=2.0
httpx>=0.27
```

`requirements-engine.txt`：

```
# LoRA 真实引擎重依赖（本地/部署环境安装；CI 契约护栏仅用 requirements.txt）
# 安装：pip install -r requirements-engine.txt
torch>=2.2
transformers>=4.40
peft>=0.11
datasets>=2.19
accelerate>=0.30
```

- [ ] **Step 2: 写 `lora_engine.py` 的模块头与依赖探针**

模块 docstring 必须像 `rag_engine.py:1-19` 那样写清依赖策略与环境变量面（`FT_DATA_DIR` / `FT_CORPUS_DIR` / `FT_CORPUS_JSON` / `FT_DEFAULT_BASE_MODEL` / `FT_MODEL_MAP` / `FT_MAX_SAMPLES` / `FT_MAX_SEQ_LEN` / `FT_TRAIN_STEPS` / `FT_DEVICE` / `FT_PUBLIC_BASE` / `FT_ALLOW_DOWNLOAD` / `FT_ENGINE_READY`，与 spec §5.1 表逐条对齐）。随后：

```python
from __future__ import annotations

import json
import os
import re
import shutil
import threading
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Optional

ENGINE_VERSION = "1.0"
_LOCK = threading.RLock()

_DEFAULT_MODELS = {"qwen-7b": "Qwen/Qwen2.5-0.5B", "qwen-0.5b": "Qwen/Qwen2.5-0.5B"}


class StoreCorruptedError(RuntimeError):
    """任务/数据集存储损坏且无法解析时抛出。

    语义同 rag_engine.StoreCorruptedError：上层不得把它当"无数据"静默处理。
    """


class UnknownModelError(ValueError):
    """base_model 业务名未在别名表中登记。"""


class ConfigError(ValueError):
    """微调配置字段非法（超出取值范围或类型不符）。"""


_DEP_ERRORS: list[str] = []


def _record_dep_error(name: str, exc: Exception) -> None:
    _DEP_ERRORS.append(f"{name}: {exc}")


try:
    import torch as _torch
except Exception as e:  # pragma: no cover - 环境相关
    _torch = None
    _record_dep_error("torch", e)

try:
    import transformers as _transformers
except Exception as e:  # pragma: no cover
    _transformers = None
    _record_dep_error("transformers", e)

try:
    import peft as _peft
except Exception as e:  # pragma: no cover
    _peft = None
    _record_dep_error("peft", e)

try:
    import datasets as _datasets
except Exception as e:  # pragma: no cover
    _datasets = None
    _record_dep_error("datasets", e)


def engine_available() -> bool:
    """真实训练所需的最低依赖集合是否齐备（不含网络/模型下载）。"""
    return all([_torch, _transformers, _peft, _datasets])


def dep_errors() -> list[str]:
    return list(_DEP_ERRORS)


def data_dir() -> Path:
    default = Path(__file__).resolve().parent / "data"
    return Path(os.getenv("FT_DATA_DIR") or default)
```

- [ ] **Step 3: 写 `test_ft_engine.py` 的失败用例（配置归一 / 别名表 / 字节格式化）**

```python
from pathlib import Path

import pytest

import lora_engine as le


@pytest.fixture(autouse=True)
def isolated_data_dir(tmp_path, monkeypatch):
    monkeypatch.setenv("FT_DATA_DIR", str(tmp_path / "ftdata"))
    le.reset_store()
    yield
    le.reset_store()


def test_as_dict_accepts_object_and_java_json_string():
    assert le.as_dict({"epochs": 3}) == {"epochs": 3}
    assert le.as_dict('{"epochs": 3, "learning_rate": 5e-05}') == {"epochs": 3, "learning_rate": 5e-05}
    assert le.as_dict(None) == {}
    assert le.as_dict("") == {}


def test_as_dict_rejects_non_object_json():
    with pytest.raises(le.ConfigError):
        le.as_dict("[1,2]")
    with pytest.raises(le.ConfigError):
        le.as_dict("{oops")


def test_normalize_config_clamps_and_validates():
    cfg = le.normalize_config({"epochs": 3, "learning_rate": 5e-5, "lora_rank": 8,
                               "lora_alpha": 16, "lora_dropout": 0.1,
                               "target_modules": ["q_proj", "v_proj"]})
    assert cfg["epochs"] == 3 and cfg["lora_rank"] == 8
    assert cfg["target_modules"] == ["q_proj", "v_proj"]
    assert cfg["learning_rate"] == pytest.approx(5e-5)
    with pytest.raises(le.ConfigError):
        le.normalize_config({"lora_dropout": 1.5})
    with pytest.raises(le.ConfigError):
        le.normalize_config({"epochs": 0})


def test_resolve_base_model_alias_table_and_unknown_name():
    assert le.resolve_base_model("qwen-7b") == "Qwen/Qwen2.5-0.5B"
    assert le.resolve_base_model("Qwen/Qwen2.5-0.5B") == "Qwen/Qwen2.5-0.5B"
    with pytest.raises(le.UnknownModelError) as exc:
        le.resolve_base_model("gpt-99")
    assert "FT_MODEL_MAP" in str(exc.value)


def test_format_size_human_string_matches_contract_shape():
    assert le.format_size(15_938_048) == "15.2MB"
    assert le.format_size(0) == "0B"
    assert le.format_size(2048) == "2.0KB"


def test_task_error_is_actionable_when_no_dataset(tmp_path, monkeypatch):
    monkeypatch.delenv("FT_ENGINE_READY", raising=False)
    cfg = le.normalize_config({})
    assert "datasets/build" in le.task_error(cfg)
```

- [ ] **Step 4: 运行确认失败**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: FAIL —`ModuleNotFoundError: No module named 'lora_engine'`（Step 2 只写了探针时则为 `AttributeError: module 'lora_engine' has no attribute 'as_dict'`）

- [ ] **Step 5: 实现 `as_dict` / `normalize_config` / `resolve_base_model` / `format_size` / `task_error`**

```python
def as_dict(raw) -> dict:
    """把 config/params 归一为 dict。

    Java 端 `LLMFineTuneServiceImpl` 用 `JsonNode.toString()` 把它们当**字符串**发出
    （见手册 §37 与 DR-73），而契约示例是对象——两种形状都必须吃，否则 8003 一上线即全线失败。
    """
    if raw is None or raw == "":
        return {}
    if isinstance(raw, dict):
        return dict(raw)
    if isinstance(raw, (str, bytes)):
        text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
        try:
            parsed = json.loads(text)
        except (json.JSONDecodeError, ValueError) as exc:
            raise ConfigError(f"JSON 解析失败：{exc}") from exc
        if not isinstance(parsed, dict):
            raise ConfigError(f"期望 JSON 对象，实际为 {type(parsed).__name__}")
        return parsed
    raise ConfigError(f"期望对象或 JSON 字符串，实际为 {type(raw).__name__}")


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _range_num(cfg: dict, key: str, lo: float, hi: float, default: float) -> float:
    if key not in cfg or cfg[key] in (None, ""):
        return float(default)
    raw = cfg[key]
    if isinstance(raw, bool):
        raise ConfigError(f"{key} 期望数值，实际为布尔")
    try:
        val = float(raw)
    except (TypeError, ValueError) as exc:
        raise ConfigError(f"{key} 数值非法：{raw!r}") from exc
    if not (lo <= val <= hi):
        raise ConfigError(f"{key}={val} 超出允许区间 [{lo}, {hi}]")
    return val


def normalize_config(raw_cfg: dict) -> dict:
    """§37 的 config 字段 → 带校验的 resolved 配置（缺失项回落到 env / 默认）。"""
    cfg = as_dict(raw_cfg)
    resolved = {
        "epochs": int(_range_num(cfg, "epochs", 1, 20, 3)),
        "learning_rate": _range_num(cfg, "learning_rate", 1e-6, 1e-1, 2e-4),
        "lora_rank": int(_range_num(cfg, "lora_rank", 1, 64, 8)),
        "lora_alpha": int(_range_num(cfg, "lora_alpha", 1, 128, 16)),
        "lora_dropout": _range_num(cfg, "lora_dropout", 0.0, 0.9, 0.1),
        "batch_size": int(_range_num(cfg, "batch_size", 1, 16, 2)),
        "max_seq_length": int(_range_num(cfg, "max_seq_length", 64, 2048,
                                        _env_int("FT_MAX_SEQ_LEN", 512))),
        "dataset_id": (cfg.get("dataset_id") or None),
        "warmup_ratio": _range_num(cfg, "warmup_ratio", 0.0, 0.5, 0.0),
    }
    tm = cfg.get("target_modules")
    if tm in (None, "", []):
        resolved["target_modules"] = ["q_proj", "v_proj"]
    elif isinstance(tm, str):
        resolved["target_modules"] = [s.strip() for s in tm.split(",") if s.strip()]
    elif isinstance(tm, list) and all(isinstance(x, str) and x.strip() for x in tm):
        resolved["target_modules"] = [x.strip() for x in tm]
    else:
        raise ConfigError("target_modules 期望非空字符串列表")
    if not resolved["target_modules"]:
        raise ConfigError("target_modules 不能为空")
    return resolved


def model_aliases() -> dict:
    """`FT_MODEL_MAP`（`名=HF_ID;名=HF_ID`）叠加在内置表之上，环境变量优先。"""
    table = dict(_DEFAULT_MODELS)
    for pair in os.getenv("FT_MODEL_MAP", "").split(";"):
        if "=" in pair:
            name, _, hf_id = pair.partition("=")
            if name.strip() and hf_id.strip():
                table[name.strip()] = hf_id.strip()
    return table


def known_models() -> list[str]:
    return sorted(model_aliases())


def resolve_base_model(name: Optional[str]) -> str:
    """业务名 → 真实 HF 模型 ID。未登记名称**直接报错并回显登记表**，不猜模型。"""
    if not name:
        return os.getenv("FT_DEFAULT_BASE_MODEL", "Qwen/Qwen2.5-0.5B")
    table = model_aliases()
    if name in table:
        return table[name]
    if "/" in name:  # 已是 HF 模型 ID
        return name
    raise UnknownModelError(
        f"未登记的 base_model={name!r}。可用别名：{table}；"
        f"新增映射请设置环境变量 FT_MODEL_MAP='名字=HF模型ID'")


def base_model_cached(hf_id: str) -> bool:
    """基座模型是否已本地缓存（**不触发联网**，语义同 rag_engine.embed_model_cached）。"""
    if not hf_id:
        return False
    try:
        from huggingface_hub import try_to_load_from_cache
        return try_to_load_from_cache(hf_id, "config.json") is not None
    except Exception:
        norm = hf_id.replace("/", "--")
        for base in (
            os.getenv("HF_HUB_CACHE") or "",
            os.path.join(os.getenv("HF_HOME", os.path.expanduser("~/.cache/huggingface")), "hub"),
        ):
            cache = Path(base) / f"models--{norm}" / "snapshots"
            if cache.exists() and any(p.suffix in {".safetensors", ".bin"}
                                      for p in cache.rglob("*") if p.is_file()):
                return True
        return False


def format_size(n: int) -> str:
    """字节数 → §37 的 `"15.2MB"` 形状（真值来自实际落盘大小，不编造）。"""
    n = max(0, int(n))
    if n < 1024:
        return f"{n}B"
    units = ["KB", "MB", "GB", "TB"]
    for i, u in enumerate(units):
        if n < 1024 ** (i + 2):
            return f"{n / (1024 ** (i + 1)):.1f}{u}"
    return f"{n / (1024 ** 4):.1f}TB"


def task_error(cfg: dict) -> Optional[str]:
    """任务可启动性检查：无数据集时给可操作文案（同 rag_service._degrade_error 口径）。"""
    if not cfg.get("dataset_id") and not list_datasets(None):
        return ("尚无训练数据集，请先 POST /api/v1/fine-tune/datasets/build/{course_id}"
                "（或 POST /api/v1/fine-tune/datasets 上传 JSONL）")
    return None
```

- [ ] **Step 6: 运行确认通过**

**实现注记（落地时不得回退成原稿形状）**：`format_size` 的档位判定与除数**不是同一个指数**——单位 i（0=KB…）的成立区间是 `[1024^(i+1), 1024^(i+2))`，除数是 `1024^(i+1)`。原稿把判定写成 `n < 1024**(i+1)` 而除数写 `1024**i`，两个下标各错一位、相减恰好抵消，于是数值对而单位名高一档：`15_938_048` 会渲染成 `"15.2GB"` 而不是 `"15.2MB"`。Step 3 的 `test_format_size_human_string_matches_contract_shape` 正是为钉住这一点而写，落地后请再打印边界值（`1024**2`、`1024**3`、`1024**4`）确认为 `1.0MB / 1.0GB / 1.0TB`。

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: PASS（6 例）；`reset_store` / `list_datasets` 尚未定义会 ImportError → 本步先加两个空壳：`def reset_store(): pass` 与 `def list_datasets(course_id=None): return []`，Task 2 替换为真实实现。

- [ ] **Step 7: Commit**

```bash
git add python-services/fine_tune_service/lora_engine.py \
        python-services/fine_tune_service/test_ft_engine.py \
        python-services/fine_tune_service/requirements.txt \
        python-services/fine_tune_service/requirements-engine.txt
git commit -m "feat(python): LoRA 引擎依赖探针与配置归一层（config 双形状）"
```

---

### Task 2: 数据集层（登记 / 讲义派生 / 语料兜底）

**Files:**
- Modify: `python-services/fine_tune_service/lora_engine.py`
- Modify: `python-services/fine_tune_service/test_ft_engine.py`

**Interfaces:**
- Consumes: Task 1 的 `data_dir()`、`_LOCK`、`StoreCorruptedError`、`as_dict`
- Produces: `reset_store()`、`now_iso()->str`、`_today_str()->str`、`_corpus_dir_or_none()->Path|None`、`_repo_corpus_path()->Path`、`_docs_to_text(list)->tuple[str,list[dict]]`、`corpus_source(course_id)->tuple[str,str]`（返回**纯文本**）、`corpus_catalog()->list[dict]`、`validate_samples(rows)->list[dict]`（`ValueError` 携 1 基行号）、`build_samples(text,str)->list[dict]`、`next_dataset_id()->str`、`add_dataset(course_id,samples,source)->dict`、`build_course_dataset(course_id,source=None)->dict`、`list_datasets(course_id=None)->list[dict]`、`get_dataset(dataset_id)->dict|None`、`latest_dataset_id(course_id)->str|None`、`load_samples(dataset_id)->list[dict]`、`delete_dataset(dataset_id)->dict|None`、`_DATASETS:dict`、`_DATASET_SEQ:dict`

- [ ] **Step 1: 写失败用例（确定性派生 + 两条来源 + 无来源诚实失败）**

```python
def test_validate_samples_accepts_three_shapes():
    rows = [{"instruction": "什么是RR", "output": "时间片轮转"},
            {"question": "什么是FCFS", "answer": "先来先服务"},
            {"messages": [{"role": "user", "content": "什么是SJF"},
                          {"role": "assistant", "content": "最短作业优先"}]}]
    out = le.validate_samples(rows)
    assert [d["instruction"] for d in out] == ["什么是RR", "什么是FCFS", "什么是SJF"]
    assert [d["output"] for d in out] == ["时间片轮转", "先来先服务", "最短作业优先"]
    assert all(d["derived_from"] == "上传" for d in out)


def test_validate_samples_errors_carry_one_based_row_no():
    with pytest.raises(ValueError) as exc:
        le.validate_samples([{"instruction": "q", "output": "a"}, {"instruction": "只有问"}])
    assert "第 2 条" in str(exc.value)
    with pytest.raises(ValueError) as exc:
        le.validate_samples([])
    assert "至少需要一条" in str(exc.value)
    with pytest.raises(ValueError) as exc:
        le.validate_samples(["不是对象"])
    assert "第 1 条" in str(exc.value)


def test_build_samples_is_deterministic_and_traceable():
    text = "## 梯度下降\n梯度下降迭代为 θ←θ−η∇J(θ)，学习率过大将震荡发散。\n"
    a = le.build_samples(text, "优化-梯度下降")
    b = le.build_samples(text, "优化-梯度下降")
    assert a == b
    assert a[0]["output"].startswith("梯度下降")
    assert a[0]["derived_from"] == "优化-梯度下降"
    assert "梯度下降" in a[0]["instruction"]


def test_build_samples_caps_length_and_drops_empty():
    assert le.build_samples("", "空文档") == []
    assert le.build_samples("一" * 5000, "长文") and len(
        le.build_samples("一" * 5000, "长文")[0]["output"]) <= 600


def test_add_and_list_datasets(tmp_path):
    saved = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    assert saved["sample_count"] == 1 and saved["dataset_id"]
    assert saved["created_at"].endswith("+08:00") or "+" in saved["created_at"]
    assert [d["dataset_id"] for d in le.list_datasets(1)] == [saved["dataset_id"]]
    assert le.load_samples(saved["dataset_id"]) == [{"instruction": "q", "output": "a"}]


def test_dataset_id_never_reuses_a_number_after_delete(monkeypatch):
    """按 len() 取号会在删除后撞车（覆盖幸存数据集）——序号必须是只增的按天计数器。"""
    monkeypatch.setattr(le, "_today_str", lambda: "20261017")
    a = le.add_dataset(1, [{"instruction": "q1", "output": "a1"}], source="上传")
    b = le.add_dataset(1, [{"instruction": "q2", "output": "a2"}], source="上传")
    le.delete_dataset(a["dataset_id"])
    c = le.add_dataset(1, [{"instruction": "q3", "output": "a3"}], source="上传")
    assert a["dataset_id"] == "ds_20261017_001"
    assert b["dataset_id"] == "ds_20261017_002"
    assert c["dataset_id"] == "ds_20261017_003"
    assert [d["dataset_id"] for d in le.list_datasets(1)] == [b["dataset_id"], c["dataset_id"]]


def test_build_course_dataset_from_repo_corpus_is_rebuildable(tmp_path, monkeypatch):
    """语料目录缺失时回落仓库内已版本化 JSON——满足 DR-40「目录不入版本控制必须有重建路径」。"""
    monkeypatch.setattr(le, "_corpus_dir_or_none", lambda: None)
    repo_corpus = Path(le.__file__).resolve().parents[2] / "scripts" / "rag_corpus.json"
    monkeypatch.setenv("FT_CORPUS_JSON", str(repo_corpus))
    out = le.build_course_dataset(1)
    assert out["success"] is True and out["sample_count"] > 0
    assert "rag_corpus.json" in out["source"]


def test_build_course_dataset_without_any_source_fails_honestly(tmp_path, monkeypatch):
    monkeypatch.setattr(le, "_corpus_dir_or_none", lambda: None)
    monkeypatch.setenv("FT_CORPUS_JSON", str(tmp_path / "不存在.json"))
    out = le.build_course_dataset(42)
    assert out["success"] is False
    assert "FT_CORPUS_JSON" in out["error"] and "datasets" in out["error"]


def test_delete_dataset_keeps_task_records(tmp_path):
    saved = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    assert le.delete_dataset(saved["dataset_id"])["dataset_id"] == saved["dataset_id"]
    assert le.get_dataset(saved["dataset_id"]) is None
    assert le.load_samples(saved["dataset_id"]) == []
```

- [ ] **Step 2: 运行确认失败**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: FAIL —`AttributeError: module 'lora_engine' has no attribute 'build_samples'`（及 `corpus_source` / `_corpus_dir_or_none`）

- [ ] **Step 3: 实现存储装载与数据集层**

先落盘范式（**与 `rag_engine.py:185-240` 逐条同构**，直接复制 `_quarantine_corrupt` / `_atomic_write_text` / `_atomic_write_bytes` 三个函数，注释保持同源引用），然后：

```python
# ---------------------------------------------------------------- 存储表

# 数据集： {dataset_id: {"dataset_id","course_id","source","sample_count","created_at","dir"}}
_DATASETS: dict[str, dict] = {}
_TASKS: dict[str, dict] = {}
# 按天序号（数据集/任务各一张）： {"20261017": 3}——删除后不得复用旧号，否则 ID 撞车
_DATASET_SEQ: dict[str, int] = {}
_TASK_SEQ: dict[str, int] = {}
_loaded = False

# 进程内模型/分词器缓存：训练与推理共用，换基座即失效重建
_MODEL_HOLDER: dict = {"model": None, "tokenizer": None, "hf_id": None}


def now_iso() -> str:
    """带时区的 ISO 时间戳（`created_at`/`updated_at` 与契约响应同形）。"""
    return datetime.now().astimezone().isoformat(timespec="seconds")


def _today_str() -> str:
    """ID 的日期段。单独成函数是为了让测试**钉日期而不是 monkeypatch 标准库**。"""
    return datetime.now().strftime("%Y%m%d")


def _datasets_path() -> Path:
    return data_dir() / "datasets.json"


def _tasks_path() -> Path:
    return data_dir() / "tasks.json"


def _load_store() -> None:
    global _loaded
    if _loaded:
        return
    for path, table, seq in ((_datasets_path(), _DATASETS, _DATASET_SEQ),
                             (_tasks_path(), _TASKS, _TASK_SEQ)):
        if not path.exists():
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, ValueError, OSError) as exc:
            quarantined = _quarantine_corrupt(path)
            raise StoreCorruptedError(
                f"引擎存储损坏（{exc}），已隔离至 {quarantined.name}") from exc
        table.clear()
        table.update(payload.get("records", {}))
        seq.clear()
        seq.update({k: int(v) for k, v in (payload.get("seq") or {}).items()})
    _loaded = True


def _save_datasets() -> None:
    _atomic_write_text(_datasets_path(), json.dumps(
        {"version": ENGINE_VERSION, "records": _DATASETS, "seq": _DATASET_SEQ},
        ensure_ascii=False, indent=2))


def _save_tasks() -> None:
    _atomic_write_text(_tasks_path(), json.dumps(
        {"version": ENGINE_VERSION, "records": _TASKS, "seq": _TASK_SEQ},
        ensure_ascii=False, indent=2))


def reset_store() -> None:
    """清空进程内存储（契约测试隔离用）。"""
    global _loaded
    with _LOCK:
        _DATASETS.clear()
        _TASKS.clear()
        _DATASET_SEQ.clear()
        _TASK_SEQ.clear()
        _MODEL_HOLDER.update({"model": None, "tokenizer": None, "hf_id": None})
        _loaded = False


# ---------------------------------------------------------------- 语料来源

def _corpus_dir_or_none() -> Optional[Path]:
    """`FT_CORPUS_DIR` 存在才返回，否则 None（回落链第二级）。"""
    d = os.getenv("FT_CORPUS_DIR")
    return Path(d) if d else None


def _repo_corpus_path() -> Path:
    """仓库内已版本化语料：`<repo>/scripts/rag_corpus.json`（回落链第三级）。"""
    default = Path(__file__).resolve().parents[2] / "scripts" / "rag_corpus.json"
    return Path(os.getenv("FT_CORPUS_JSON") or default)


def _docs_to_text(docs: list) -> tuple[str, list[dict]]:
    """`[{document_name, content}, ...]` → (拼接文本, 目录行)。目录行供 GET /corpus 用。"""
    parts, toc = [], []
    for d in docs:
        if not isinstance(d, dict):
            continue
        content = (d.get("content") or "").strip()
        if not content:
            continue
        name = d.get("document_name") or d.get("name") or "未命名讲义"
        parts.append(f"# {name}\n{content}")
        toc.append({"course_id": d.get("course_id"), "document_name": name,
                    "chars": len(content)})
    return "\n\n".join(parts), toc


def corpus_source(course_id: int) -> tuple[str, str]:
    """解析课程讲义语料，**统一返回纯文本**（派生逻辑只处理一种形状）。

    优先级：`FT_CORPUS_DIR/<course_id>.{json,md,txt}` → `scripts/rag_corpus.json`
    → `("none", "")`。三处皆无时调用方须给出应设置哪个变量的可操作文案。
    """
    cdir = _corpus_dir_or_none()
    if cdir is not None:
        for suffix in (".json", ".md", ".txt"):
            cand = cdir / f"{course_id}{suffix}"
            if not cand.exists():
                continue
            raw = cand.read_text(encoding="utf-8")
            if suffix == ".json":
                try:
                    payload = json.loads(raw)
                except json.JSONDecodeError:
                    return "none", ""
                docs = payload.get("documents", payload) if isinstance(payload, dict) else payload
                text, _ = _docs_to_text(docs if isinstance(docs, list) else [])
                return ("dir-json", text) if text else ("none", "")
            return "dir-text", raw
    path = _repo_corpus_path()
    if not path.exists():
        return "none", ""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, ValueError):
        return "none", ""
    docs = payload.get("documents", payload) if isinstance(payload, dict) else payload
    if not isinstance(docs, list):
        return "none", ""
    text, _ = _docs_to_text([d for d in docs
                             if isinstance(d, dict) and d.get("course_id") == course_id])
    return ("repo-json", text) if text else ("none", "")


def corpus_catalog() -> list[dict]:
    """仓库语料的课程/文档清单（`GET /corpus` 数据源，不含正文）。"""
    path = _repo_corpus_path()
    if not path.exists():
        return []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, ValueError):
        raise StoreCorruptedError(f"语料文件损坏：{path}")
    docs = payload.get("documents", payload) if isinstance(payload, dict) else payload
    _text, toc = _docs_to_text(docs if isinstance(docs, list) else [])
    return toc


# ---------------------------------------------------------------- 样本校验与派生

def validate_samples(rows: list) -> list[dict]:
    """任意来源的样本 → 规范形状（校验放引擎，路由层保持"不做计算"）。

    接受三种写法：`{instruction,input,output}`、`{question,answer}`、对话 `messages[]`
    （取首条 user 为指令、末条 assistant 为回答）。错误文案带 **1 基行号**，
    调用方可直接把用户上传的 JSONL 第几行有问题报回去。
    """
    out: list[dict] = []
    for i, r in enumerate(rows or [], 1):
        if not isinstance(r, dict):
            raise ValueError(f"第 {i} 条样本必须是对象，实际为 {type(r).__name__}")
        row = dict(r)
        msgs = row.get("messages")
        if isinstance(msgs, list) and msgs:
            user = next((m.get("content", "") for m in msgs
                         if isinstance(m, dict) and m.get("role") == "user"), "")
            asst = next((m.get("content", "") for m in reversed(msgs)
                         if isinstance(m, dict) and m.get("role") == "assistant"), "")
            row.setdefault("instruction", user)
            row.setdefault("output", asst)
        instruction = str(row.get("instruction") or row.get("question") or "").strip()
        output = str(row.get("output") or row.get("answer")
                     or row.get("completion") or "").strip()
        if not instruction or not output:
            raise ValueError(f"第 {i} 条样本缺少 instruction/output（或 question/answer、"
                             f"messages 里的 user/assistant）")
        out.append({"instruction": instruction[:2000],
                    "input": str(row.get("input") or "")[:2000],
                    "output": output[:4000],
                    "derived_from": str(row.get("derived_from") or "上传")})
    if not out:
        raise ValueError("样本为空：至少需要一条 instruction/output 对")
    return out


_SENT_SPLIT = re.compile(r"(?<=[。！？；!?;\n])")


def _heading_title(line: str) -> str:
    return re.sub(r"^#+\s*", "", line).strip()


def build_samples(text: str, source_name: str, limit: int = 400) -> list[dict]:
    """把讲义切成**确定性**指令样本（可复现、可溯源，绝不依赖随机数或 LLM）。

    规则：`#` 标题行设定当前小节；正文按句聚合为 ≤560 字一段，每段一条样本；
    指令为「请讲解「<小节>」的核心要点。」，无小节时回落到该段首句；
    每条带 `derived_from` 供人工检视（手册 §4 的溯源要求）。
    """
    limit = max(1, min(int(limit), _env_int("FT_MAX_SAMPLES", 200)))
    samples: list[dict] = []
    topic = ""

    def emit(body: str):
        body = re.sub(r"\s+", " ", body).strip()
        if not body:
            return
        first = next((s.strip() for s in _SENT_SPLIT.split(body) if s.strip()), body)[:80]
        samples.append({
            "instruction": f"请讲解「{topic}」的核心要点。" if topic else first,
            "input": "",
            "output": body[:560],
            "derived_from": source_name,
        })

    buf = ""
    for raw_line in (text or "").splitlines():
        if len(samples) >= limit:
            break
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("#"):
            emit(buf)
            buf = ""
            topic = _heading_title(line)
            if not topic:          # 空标题行不改变当前小节
                topic = topic or ""
            continue
        for sent in (s for s in _SENT_SPLIT.split(line) if s.strip()):
            if buf and len(buf) + len(sent) > 560:
                emit(buf)
                buf = sent
                if not topic:      # 无小节时长文按首句立指令，避免全部同名
                    topic = ""
            else:
                buf += sent
    emit(buf)
    return samples[:limit]


def next_dataset_id() -> str:
    day = _today_str()
    _DATASET_SEQ[day] = _DATASET_SEQ.get(day, 0) + 1
    return f"ds_{day}_{_DATASET_SEQ[day]:03d}"


def add_dataset(course_id: int, samples: list[dict], source: str = "上传") -> dict:
    """登记一份数据集（样本落 `<dir>/datasets/<id>/samples.json`，可人工检视）。"""
    with _LOCK:
        _load_store()
        dataset_id = next_dataset_id()
        ddir = data_dir() / "datasets" / dataset_id
        ddir.mkdir(parents=True, exist_ok=True)
        _atomic_write_text(ddir / "samples.json",
                           json.dumps(samples, ensure_ascii=False, indent=2))
        record = {
            "dataset_id": dataset_id,
            "course_id": course_id,
            "source": source,
            "sample_count": len(samples),
            "created_at": now_iso(),
            "dir": str(ddir),
        }
        _DATASETS[dataset_id] = record
        _save_datasets()
        return dict(record)


def build_course_dataset(course_id: int, source: Optional[str] = None) -> dict:
    """从课程讲义语料派生数据集。`source` 是调用方**可选**提供的原始文本。

    回落链：`source` → `FT_CORPUS_DIR/<course_id>.*` → 仓库内已版本化 `scripts/rag_corpus.json`。
    `source` 之所以可选：Java 端不会传语料体，强制要求 body 会让 `FT_ENABLED=true` 时
    `/start` 恒 503、链路仍不可用（手册 §4 的设计缺口，计划期补出）。
    """
    if source and source.strip():
        kind, text = "inline", source
    else:
        kind, text = corpus_source(course_id)
        if kind == "none":
            return {"success": False, "sample_count": 0,
                    "error": (f"课程 {course_id} 无可派生讲义语料：回落链三级皆空——"
                              f"请在 body 传 source，或设置 FT_CORPUS_DIR / FT_CORPUS_JSON，"
                              f"或改用 POST /api/v1/fine-tune/datasets 上传样本")}
    samples = build_samples(text, f"{kind}#course_{course_id}")
    if not samples:
        return {"success": False, "sample_count": 0,
                "error": f"课程 {course_id} 语料无有效片段，未生成数据集"}
    cdir = _corpus_dir_or_none()
    label = {"inline": "调用方提供",
             "repo-json": f"{_repo_corpus_path().name} 派生",
             "dir-json": f"{cdir} 派生", "dir-text": f"{cdir} 派生"}[kind]
    saved = add_dataset(course_id, samples, source=label)
    return {"success": True, "dataset_id": saved["dataset_id"],
            "sample_count": saved["sample_count"], "source": saved["source"],
            "created_at": saved["created_at"]}


def list_datasets(course_id: Optional[int] = None) -> list[dict]:
    with _LOCK:
        _load_store()
        rows = [dict(d) for d in _DATASETS.values()]
    if course_id is not None:
        rows = [d for d in rows if d["course_id"] == course_id]
    return sorted(rows, key=lambda d: d["created_at"])


def get_dataset(dataset_id: str) -> Optional[dict]:
    with _LOCK:
        _load_store()
        rec = _DATASETS.get(dataset_id)
        return dict(rec) if rec else None


def latest_dataset_id(course_id: Optional[int]) -> Optional[str]:
    rows = list_datasets(course_id)
    return rows[-1]["dataset_id"] if rows else None


def load_samples(dataset_id: str) -> list[dict]:
    rec = get_dataset(dataset_id)
    if not rec:
        return []
    path = Path(rec["dir"]) / "samples.json"
    if not path.exists():
        return []
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, ValueError):
        raise StoreCorruptedError(f"数据集样本文件损坏：{path}")


def delete_dataset(dataset_id: str) -> Optional[dict]:
    with _LOCK:
        _load_store()
        rec = _DATASETS.pop(dataset_id, None)
        if not rec:
            return None
        _save_datasets()
    shutil.rmtree(Path(rec["dir"]), ignore_errors=True)
    return dict(rec)
```

- [ ] **Step 4: 运行确认通过**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: PASS（15 例）。若 `parents[2]` 定位不到 `scripts/`，改为 `Path(le.__file__).resolve().parents[2]` 实测打印后修正——**不得凭猜写路径**。

- [ ] **Step 5: 手工验一次真实派生量（不留口头结论）**

Run:

```bash
cd python-services/fine_tune_service && python -c "
import lora_engine as le, json
out = le.build_course_dataset(1)
print(out['sample_count'], out['source'])
print(json.dumps(le.load_samples(out['dataset_id'])[:2], ensure_ascii=False, indent=2))
"
```

Expected: `sample_count` > 0 且样本 `output` 为可读中文讲义片段（本机课程 1 有 2 篇讲义，实测打印为准写入手册）。

- [ ] **Step 6: Commit**

```bash
git add python-services/fine_tune_service/lora_engine.py python-services/fine_tune_service/test_ft_engine.py
git commit -m "feat(python): LoRA 数据集层——JSONL 登记 + 讲义确定性派生 + 仓库语料兜底"
```

---

### Task 3: 任务状态机与真实训练

**Files:**
- Modify: `python-services/fine_tune_service/lora_engine.py`
- Modify: `python-services/fine_tune_service/test_ft_engine.py`

**Interfaces:**
- Consumes: Task 1 `normalize_config`/`resolve_base_model`/`engine_available`/`base_model_cached`；Task 2 `load_samples`/`latest_dataset_id`/`get_dataset`/`now_iso`/`_today_str`
- Produces: `next_task_id()->str`、`create_task(course_id,base_model,cfg)->dict`、`get_task(task_id)->dict|None`、`list_tasks(course_id=None)->list[dict]`、`update_task(task_id,**fields)->dict|None`、`run_training(task_id)->dict`（同步训练体，线程包装在 `start_training`）、`start_training(task_id)->dict`、`cancel_task(task_id)->dict`、`delete_task(task_id)->dict|None`、`device()->str`、`_format_sample(sample,chat_template)->str`、`_progress_pct(step,total)->float`、`_record_progress(task_id,total_steps,global_step,epoch,loss)->None`、`_MODEL_HOLDER:dict`、`_CANCEL_FLAGS:dict`

- [ ] **Step 1: 写失败用例（ID 形状 / 状态机 / 取消 / 进度 / 样本格式化）**

```python
def test_next_task_id_shape_and_daily_counter(monkeypatch):
    monkeypatch.setattr(le, "_today_str", lambda: "20261017")
    assert le.next_task_id() == "ft_20261017_001"
    assert le.next_task_id() == "ft_20261017_002"
    monkeypatch.setattr(le, "_today_str", lambda: "20261018")   # 换日重新从 001 起
    assert le.next_task_id() == "ft_20261018_001"


def test_create_task_freezes_config_and_dataset(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    task = le.create_task(1, "qwen-7b", le.normalize_config({"epochs": 2, "dataset_id": ds["dataset_id"]}))
    assert task["status"] == "pending" and task["total_epochs"] == 2
    assert task["dataset_id"] == ds["dataset_id"]
    assert task["base_model"] == "qwen-7b" and task["hf_model_id"] == "Qwen/Qwen2.5-0.5B"
    assert task["progress"] == 0.0 and task["current_epoch"] == 0


def test_progress_pct_is_monotonic_and_capped():
    assert le._progress_pct(0, 10) == 0.0
    assert le._progress_pct(5, 10) == 50.0
    assert le._progress_pct(10, 10) == 99.5  # 运行中封顶，完成才 100
    assert le._progress_pct(3, 0) == 0.0     # 除零保护


def test_record_progress_writes_state_values_not_kwargs(tmp_path):
    """钉住 `_record_progress` 的取数契约：progress 跟 global_step 走。
    若实现误从回调 kwargs 取 `step`（那里没有），progress 会恒为 0.0 且无报错。"""
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    cfg = le.normalize_config({"epochs": 2, "dataset_id": ds["dataset_id"]})
    t = le.create_task(1, "qwen-7b", cfg)
    tid = t["task_id"]
    assert t["total_epochs"] == 2
    le._record_progress(tid, 10, global_step=3, epoch=0.4, loss=1.2345678)
    row = le.get_task(tid)
    assert row["progress"] == 30.0 and row["loss_history"] == [1.234568]
    assert row["current_epoch"] == 1
    le._record_progress(tid, 10, global_step=10, epoch=1.9, loss=0.5)
    row = le.get_task(tid)
    assert row["progress"] == 99.5 and row["current_epoch"] == 2  # 运行中封顶 99.5，轮次不超过 epochs
    assert row["loss_history"] == [1.234568, 0.5]
    le._record_progress(tid, 10, global_step=10, epoch=2.0, loss=None)
    assert le.get_task(tid)["loss_history"] == [1.234568, 0.5]   # 无 loss 的日志行不写历史


def test_cancel_on_pending_marks_cancelled_and_on_finished_is_rejected(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.cancel_task(t["task_id"])
    assert out == {"success": True, "status": "cancelled"}
    assert le.get_task(t["task_id"])["status"] == "cancelled"
    le.update_task(t["task_id"], status="completed")
    again = le.cancel_task(t["task_id"])
    assert again["success"] is False and again["status"] == "completed" and "无法取消" in again["error"]


def test_delete_task_removes_record_and_run_dir(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    assert le.delete_task(t["task_id"])["task_id"] == t["task_id"]
    assert le.get_task(t["task_id"]) is None
    assert le.list_tasks(1) == []


def test_run_training_reports_missing_deps_honestly(tmp_path, monkeypatch):
    """依赖缺失/数据集为空都必须给出可识别失败，不得伪装 running 后静默结束。"""
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    monkeypatch.setattr(le, "engine_available", lambda: False)
    out = le.run_training(t["task_id"])
    assert out["status"] == "failed" and "依赖未安装" in out["error"]
    monkeypatch.setattr(le, "engine_available", lambda: True)
    empty = le.create_task(2, "qwen-7b", le.normalize_config({}))
    assert le.run_training(empty["task_id"])["status"] == "failed"


def test_format_sample_prefers_chat_template_and_falls_back():
    s = {"instruction": "什么是过拟合", "input": "", "output": "模型在训练集上表现好而泛化差"}
    txt = le._format_sample(s, None)
    assert "什么是过拟合" in txt and "泛化差" in txt
    called = {}

    def fake(messages):
        called["messages"] = messages
        return "TPL"

    assert le._format_sample(s, fake) == "TPL"
    assert called["messages"][1]["content"].startswith("模型在训练集")
```

- [ ] **Step 2: 运行确认失败**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: FAIL —`AttributeError: ... has no attribute 'next_task_id'`

- [ ] **Step 3: 实现任务存储 + 训练线程**

```python
# 模块级已具备（Task 2）：_TASKS / _TASK_SEQ / _MODEL_HOLDER / _load_store
#                / _save_tasks / _atomic_write_* / now_iso() / _today_str()

_CANCEL_FLAGS: dict[str, bool] = {}


def next_task_id() -> str:
    day = _today_str()
    _TASK_SEQ[day] = _TASK_SEQ.get(day, 0) + 1
    return f"ft_{day}_{_TASK_SEQ[day]:03d}"


def create_task(course_id: int, base_model: str, cfg: dict) -> dict:
    with _LOCK:
        _load_store()
        task_id = next_task_id()
        dataset_id = cfg.get("dataset_id") or latest_dataset_id(course_id)
        task = {
            "task_id": task_id,
            "course_id": course_id,
            "base_model": base_model,
            "hf_model_id": resolve_base_model(base_model),
            "dataset_id": dataset_id,
            "status": "pending",
            "progress": 0.0,
            "current_epoch": 0,
            "total_epochs": cfg["epochs"],
            "loss": None,
            "config": dict(cfg),
            "created_at": now_iso(),
            "updated_at": now_iso(),
            "error": None,
            "loss_history": [],
            "adapter_dir": str(data_dir() / "runs" / task_id),
        }
        _TASKS[task_id] = task
        _save_tasks()
        return dict(task)


def get_task(task_id: str) -> Optional[dict]:
    with _LOCK:
        _load_store()
        rec = _TASKS.get(task_id)
        return dict(rec) if rec else None


def update_task(task_id: str, **fields) -> Optional[dict]:
    with _LOCK:
        _load_store()
        rec = _TASKS.get(task_id)
        if not rec:
            return None
        rec.update(fields)
        if "loss_history" in fields:
            hist = fields["loss_history"] or []
            rec["loss"] = hist[-1] if hist else rec.get("loss")
        rec["updated_at"] = now_iso()
        _save_tasks()
        return dict(rec)


def list_tasks(course_id: Optional[int] = None) -> list[dict]:
    with _LOCK:
        _load_store()
        rows = [dict(t) for t in _TASKS.values()]
    if course_id is not None:
        rows = [t for t in rows if t["course_id"] == course_id]
    return sorted(rows, key=lambda t: t["created_at"])


def cancel_task(task_id: str) -> dict:
    rec = get_task(task_id)
    if not rec:
        return {"success": False, "status": "not_found", "error": "任务不存在"}
    if rec["status"] in ("completed", "failed", "cancelled"):
        return {"success": False, "status": rec["status"],
                "error": f"任务已结束（{rec['status']}），无法取消"}
    _CANCEL_FLAGS[task_id] = True
    if rec["status"] == "pending":  # 训练线程尚未起跑：直接定终态
        update_task(task_id, status="cancelled", progress=0.0)
        return {"success": True, "status": "cancelled"}
    update_task(task_id, status="cancelling")
    return {"success": True, "status": "cancelled"}


def delete_task(task_id: str) -> Optional[dict]:
    with _LOCK:
        _load_store()
        rec = _TASKS.pop(task_id, None)
        if not rec:
            return None
        _save_tasks()
    shutil.rmtree(Path(rec["adapter_dir"]), ignore_errors=True)
    return dict(rec)


def _progress_pct(step: int, total: int) -> float:
    if not total or total <= 0:
        return 0.0
    return round(max(0.0, min(step / total * 100.0, 99.5)), 1)


def _record_progress(task_id: str, total_steps: int, global_step: int,
                     epoch: float, loss: Optional[float]) -> None:
    """把 Trainer 回调的一次日志写成存储真值。

    单列成函数是为了让「步数/轮次来自 TrainerState」这件事在 CI 里可断言：
    `logs` 只有 loss，`step`/`epoch` 若从 kwargs 取会恒为 None、progress 永远 0.0。
    轮次上限读 `total_epochs`（`create_task` 已冻结），不由调用方重复传。
    """
    if loss is None:
        return
    row = get_task(task_id)
    if not row:
        return
    hist = list(row.get("loss_history") or []) + [round(float(loss), 6)]
    update_task(task_id, loss_history=hist,
                progress=_progress_pct(int(global_step), int(total_steps)),
                current_epoch=min(int(row.get("total_epochs") or 1),
                                  int(float(epoch or 0.0)) + 1))


def _format_sample(sample: dict, chat_template) -> str:
    """指令样本 → 训练文本。有 chat template 走模板（与推理时的应用格式一致），
    否则退回显式标记文本。"""
    if chat_template is not None:
        return chat_template([
            {"role": "user", "content": (sample.get("instruction") or "") +
             (("\n" + sample["input"]) if sample.get("input") else "")},
            {"role": "assistant", "content": sample.get("output") or ""},
        ], tokenize=False, add_generation_prompt=False)
    head = sample.get("instruction") or ""
    inp = sample.get("input") or ""
    return (f"### 指令:\n{head}\n" + (f"{inp}\n" if inp else "")
            + f"### 回答:\n{sample.get('output') or ''}")
```

`run_training` 的真实训练体（**依赖缺失/模型缺失/数据集为空一律 `status="failed"` + 可操作 error，禁止静默**）：

```python
def run_training(task_id: str) -> dict:
    """同步执行一次真实 LoRA 训练（`start_training` 用线程包装它）。

    返回任务终态快照。CPU 可行性护栏：`FT_TRAIN_STEPS` 可钉死 max_steps，
    便于本机在分钟级取证；未设置时按 epochs 跑完整轮。
    """
    rec = get_task(task_id)
    if not rec:
        return {"task_id": task_id, "status": "not_found", "error": "任务不存在"}
    if not engine_available():
        msg = "引擎依赖未安装：" + "; ".join(dep_errors()) + \
              "。请 pip install -r requirements-engine.txt 后重启服务。"
        return update_task(task_id, status="failed", error=msg)
    if rec["status"] == "cancelled":
        return rec
    busy = [t["task_id"] for t in list_tasks()
            if t["status"] in ("running", "cancelling") and t["task_id"] != task_id]
    if busy:  # 单 uvicorn worker 串行训练（手册 §5 边界）：并发提交即诚实失败，不排队不静默
        return update_task(task_id, status="failed",
                           error=f"已有训练在跑（{busy[0]}），当前实现单 worker 串行，请稍后再提交")
    samples = load_samples(rec["dataset_id"]) if rec.get("dataset_id") else []
    if not samples:
        return update_task(task_id, status="failed",
                           error=f"数据集 {rec.get('dataset_id')} 无有效样本，"
                                 f"请先 POST /api/v1/fine-tune/datasets/build/{rec['course_id']}")
    hf_id = rec["hf_model_id"]
    max_len = rec["config"]["max_seq_length"]
    cached = base_model_cached(hf_id)
    if not cached and os.getenv("FT_ALLOW_DOWNLOAD", "0") != "1":
        return update_task(task_id, status="failed",
                           error=f"基座模型 {hf_id} 未本地缓存，且未允许下载"
                                 f"（置 FT_ALLOW_DOWNLOAD=1 可首次自动拉取）")
    try:
        from transformers import (AutoModelForCausalLM, AutoTokenizer, DataCollatorForLanguageModeling,
                                  Trainer, TrainerCallback, TrainingArguments)
        from peft import LoraConfig, PeftModel, TaskType

        tokenizer = _MODEL_HOLDER["tokenizer"] or AutoTokenizer.from_pretrained(
            hf_id, local_files_only=cached)
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token
        template = tokenizer.apply_chat_template if tokenizer.chat_template else None
        texts = [_format_sample(s, template) for s in samples]
        enc = tokenizer(texts, truncation=True, max_length=max_len, padding=False)
        ds_obj = _datasets.Dataset.from_dict({k: v for k, v in enc.items()})

        base = AutoModelForCausalLM.from_pretrained(
            hf_id, local_files_only=cached,
            torch_dtype=_torch.float16 if device() == "cuda" else _torch.float32)
        lora = LoraConfig(task_type=TaskType.CAUSAL_LM, r=rec["config"]["lora_rank"],
                          lora_alpha=rec["config"]["lora_alpha"],
                          lora_dropout=rec["config"]["lora_dropout"],
                          target_modules=rec["config"]["target_modules"])
        model = PeftModel.from_config(base, lora)

        total_steps = _train_steps(len(ds_obj), rec)
        args = TrainingArguments(
            output_dir=str(Path(rec["adapter_dir"]) / "trainer"),
            per_device_train_batch_size=rec["config"]["batch_size"],
            learning_rate=rec["config"]["learning_rate"],
            num_train_epochs=rec["config"]["epochs"],
            max_steps=total_steps,
            warmup_ratio=rec["config"]["warmup_ratio"],
            logging_steps=1, save_strategy="no", report_to=[],
            use_cpu=device() == "cpu",
        )

        class _Progress(TrainerCallback):
            def on_log(self, args, state, control=None, logs=None, **kwargs):
                if _CANCEL_FLAGS.get(task_id):
                    raise _TrainingCancelled()
                _record_progress(task_id, total_steps,
                                 state.global_step, state.epoch, (logs or {}).get("loss"))

        # completion-only：整段「指令+回答」计入损失，不做 prompt 段掩码（理由见本模块 docstring）
        trainer = Trainer(model=model, args=args, train_dataset=ds_obj,
                          data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
                          callbacks=[_Progress()])
        update_task(task_id, status="running", progress=0.0, error=None)
        trainer.train()
        model.save_pretrained(rec["adapter_dir"])
        _atomic_write_text(Path(rec["adapter_dir"]) / "train_state.json", json.dumps(
            {"task_id": task_id, "hf_model_id": hf_id, "config": rec["config"],
             "total_steps": total_steps, "finished_at": now_iso()},
            ensure_ascii=False, indent=2))
        # 只缓存分词器：adapter 已折入 model，缓存裸基座会让下一次训练拿到已带 LoRA 的模型
        _MODEL_HOLDER.update({"model": None, "tokenizer": tokenizer, "hf_id": hf_id})
        return update_task(task_id, status="completed", progress=100.0,
                           current_epoch=rec["config"]["epochs"], error=None)
    except _TrainingCancelled:
        return update_task(task_id, status="cancelled", error="训练已在 step 边界取消")
    except Exception as exc:  # 训练期任何异常都落成可诊断终态，绝不冒泡成 500
        return update_task(task_id, status="failed",
                           error=f"训练失败：{type(exc).__name__}: {exc}")


class _TrainingCancelled(RuntimeError):
    """协作式取消：在 on_log/on_step 边界抛出，由 run_training 收成 cancelled 终态。"""


def device() -> str:
    forced = os.getenv("FT_DEVICE")
    if forced:
        return forced
    return "cuda" if (_torch is not None and _torch.cuda.is_available()) else "cpu"


def _train_steps(n_samples: int, rec: dict) -> int:
    """步数：`FT_TRAIN_STEPS` 优先（本机取证护栏），否则按 epochs/batch 估完整轮步数。"""
    env_steps = os.getenv("FT_TRAIN_STEPS")
    if env_steps:
        return int(env_steps)
    bs = max(1, rec["config"]["batch_size"])
    return max(1, int(-(-n_samples // bs) * rec["config"]["epochs"]))


def start_training(task_id: str) -> dict:
    """后台线程执行训练；HTTP 侧立即返回 pending/running 快照。"""
    threading.Thread(target=run_training, args=(task_id,), daemon=True,
                     name=f"lora-train-{task_id}").start()
    return get_task(task_id) or {"task_id": task_id, "status": "pending"}
```

**实现注记（不得省略）**：
- **loss mask 取舍**：本实现采 completion-only 整理器（`mlm=False`）对「指令+回答」整段计损失，不给 prompt 段做掩码。理由：对 0.5B 基座做部分掩码需要模板一致性保证，做不对会静默学成「复读指令」且不报错；截断风险改由样本构造侧的 `max_seq_length` 兜住。该取舍必须同样写进 `lora_engine.py` 的模块 docstring 与手册 §46.2，不得默默略过（spec §5 末条）。
- **进度真值取自 `TrainerState`，不是回调 kwargs**：`on_log` 的 `logs` 里只有 `loss`/`learning_rate` 等日志项，**步数与轮次在 `state.global_step` / `state.epoch` 上**。写成 `kwargs.get("step")` 会恒为 `None` → `progress` 永远 0.0，而本机取证只看得到「任务完成了」，会误以为进度字段已坐实。形参按 `on_log(self, args, state, control=None, logs=None, **kwargs)` 的官方形状命名，不要用 `**kw` 兜住一切。

- [ ] **Step 4: 运行确认通过**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: PASS（23 例）。`run_training` 的真实训练分支在本任务不执行（无模型缓存 → 走 failed 早退路径），其真实语义由 Task 7 坐实。

- [ ] **Step 5: Commit**

```bash
git add python-services/fine_tune_service/lora_engine.py python-services/fine_tune_service/test_ft_engine.py
git commit -m "feat(python): LoRA 任务状态机与真实 PEFT 训练（后台线程 + 协作式取消）"
```

---

### Task 4: 权重导出与 adapter 推理

**Files:**
- Modify: `python-services/fine_tune_service/lora_engine.py`
- Modify: `python-services/fine_tune_service/test_ft_engine.py`

**Interfaces:**
- Consumes: Task 3 的 `get_task`/`update_task`/`_MODEL_HOLDER`/`device`；Task 1 的 `format_size`/`engine_available`/`base_model_cached`
- Produces: `weights_dir()->Path`、`export_weights(task_id, public_base=None)->dict`、`infer(task_id,prompt,params)->dict`

- [ ] **Step 1: 写失败用例**

```python
def test_export_requires_completed_task(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.export_weights(t["task_id"])
    assert out["code"] == 503 and "尚未完成" in out["error"]


def test_export_zips_adapter_and_reports_real_bytes(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    adir = Path(t["adapter_dir"]); adir.mkdir(parents=True, exist_ok=True)
    (adir / "adapter_config.json").write_text('{"r": 8}', encoding="utf-8")
    (adir / "adapter_model.safetensors").write_bytes(b"\0" * 4096)
    le.update_task(t["task_id"], status="completed")
    out = le.export_weights(t["task_id"], public_base="http://localhost:8003")
    assert out["format"] == "peft_lora"
    assert out["download_url"] == f"http://localhost:8003/weights/{t['task_id']}.zip"
    assert out["file_size"] == le.format_size(out["size_bytes"])
    assert Path(out["local_path"]).exists() and out["size_bytes"] > 0


def test_infer_without_engine_deps_is_recognizable_failure(tmp_path, monkeypatch):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    le.update_task(t["task_id"], status="completed")
    monkeypatch.setattr(le, "engine_available", lambda: False)
    out = le.infer(t["task_id"], "什么是过拟合", {"temperature": 0.7, "max_tokens": 64})
    assert out["code"] == 503 and "requirements-engine.txt" in out["error"]


def test_infer_rejects_unfinished_task(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.infer(t["task_id"], "q", {})
    assert out["code"] == 503 and "未完成" in out["error"]
```

- [ ] **Step 2: 运行确认失败**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q`
Expected: FAIL —`AttributeError: ... has no attribute 'export_weights'`

- [ ] **Step 3: 实现导出与推理**

```python
def weights_dir() -> Path:
    return data_dir() / "weights"


def export_weights(task_id: str, public_base: Optional[str] = None) -> dict:
    """把 adapter 目录打成 zip 落 `data/weights/`，由契约层的 `/weights/{name}` 路由真实可下载。

    file_size 与 download_url 均为落盘真值——§37 的 "15.2MB" 形状不是装饰，
    它是「导出确实发生」的取证点。
    """
    rec = get_task(task_id)
    if not rec:
        return {"error": "任务不存在", "code": 404, "mode": "UNAVAILABLE"}
    if rec["status"] != "completed":
        return {"error": f"任务 {task_id} 尚未完成（当前 {rec['status']}），无权重可导出",
                "code": 503, "mode": "UNAVAILABLE"}
    adir = Path(rec["adapter_dir"])
    if not adir.exists() or not any(adir.glob("adapter_*.json")) and not any(adir.glob("*.safetensors")):
        return {"error": f"任务 {task_id} 的 adapter 目录缺失，无法导出",
                "code": 503, "mode": "UNAVAILABLE"}
    weights_dir().mkdir(parents=True, exist_ok=True)
    zip_path = weights_dir() / f"{task_id}.zip"
    tmp = weights_dir() / f".{task_id}.zip.tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(adir.rglob("*")):
            if f.is_file() and "trainer" not in f.parts:
                zf.write(f, arcname=f.relative_to(adir).as_posix())
    os.replace(tmp, zip_path)
    size = zip_path.stat().st_size
    base = (public_base or os.getenv("FT_PUBLIC_BASE", "http://localhost:8003")).rstrip("/")
    return {"download_url": f"{base}/weights/{task_id}.zip", "file_size": format_size(size),
            "size_bytes": size, "format": "peft_lora", "task_id": task_id,
            "local_path": str(zip_path), "code": 200, "mode": "ENGINE"}


def infer(task_id: str, prompt: str, params: dict) -> dict:
    """真实推理：基座 + adapter 载入后 generate，tokens_used 取实际生成 token 数。"""
    rec = get_task(task_id)
    if not rec:
        return {"error": "任务不存在", "code": 404, "mode": "UNAVAILABLE"}
    if rec["status"] != "completed":
        return {"error": f"任务 {task_id} 未完成（{rec['status']}），adapter 不可用于推理",
                "code": 503, "mode": "UNAVAILABLE"}
    if not engine_available():
        return {"error": "引擎依赖未安装：" + "; ".join(dep_errors())
                + "。请 pip install -r requirements-engine.txt 后重试。",
                "code": 503, "mode": "UNAVAILABLE"}
    p = as_dict(params)
    temperature = float(p.get("temperature", 0.7))
    max_tokens = int(p.get("max_tokens", 512))
    hf_id = rec["hf_model_id"]
    cached = base_model_cached(hf_id)
    if not cached and os.getenv("FT_ALLOW_DOWNLOAD", "0") != "1":
        return {"error": f"基座模型 {hf_id} 未本地缓存且未允许下载（FT_ALLOW_DOWNLOAD=1）",
                "code": 503, "mode": "UNAVAILABLE"}
    try:
        from transformers import AutoModelForCausalLM, AutoTokenizer
        from peft import PeftModel
        tokenizer = _MODEL_HOLDER["tokenizer"] or AutoTokenizer.from_pretrained(
            hf_id, local_files_only=cached)
        if _MODEL_HOLDER["model"] is None:
            base = AutoModelForCausalLM.from_pretrained(
                hf_id, local_files_only=cached,
                torch_dtype=_torch.float16 if device() == "cuda" else _torch.float32)
            _MODEL_HOLDER["model"] = PeftModel.from_pretrained(base, rec["adapter_dir"])
            _MODEL_HOLDER["hf_id"] = hf_id
            _MODEL_HOLDER["tokenizer"] = tokenizer
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token
        model = _MODEL_HOLDER["model"].to(device())
        messages = [{"role": "user", "content": prompt}]
        if tokenizer.chat_template:
            text = tokenizer.apply_chat_template(messages, tokenize=False,
                                                add_generation_prompt=True)
        else:
            text = f"### 指令:\n{prompt}\n### 回答:\n"
        inputs = tokenizer(text, return_tensors="pt").to(device())
        new_tokens = max(1, min(max_tokens, 1024))
        gen = model.generate(
            **inputs, max_new_tokens=new_tokens,
            do_sample=temperature > 0, temperature=max(0.1, temperature),
            pad_token_id=tokenizer.pad_token_id, eos_token_id=tokenizer.eos_token_id)
        prompt_len = int(inputs["input_ids"].shape[1])
        answer = tokenizer.decode(gen[0][prompt_len:], skip_special_tokens=True).strip()
        return {"answer": answer, "tokens_used": int(gen.shape[1]) - prompt_len,
                "model": f"{rec['base_model']}-lora-{task_id}", "code": 200, "mode": "ENGINE"}
    except Exception as exc:
        return {"answer": None, "error": f"推理失败：{type(exc).__name__}: {exc}",
                "code": 503, "mode": "UNAVAILABLE",
                "model": f"{rec['base_model']}-lora-{task_id}", "tokens_used": 0}
```

**实现注记**：`tokens_used` 必须是 `gen.shape[1] - prompt_len` 的**实际生成 token 数**，不是 `max_tokens` 的回显——§37 把它列为响应字段，取假值就等于在契约字段上造假。

- [ ] **Step 4: 运行确认通过并肉眼复核 generate 调用**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_engine.py -q && python -l -c "import lora_engine"`
Expected: PASS（27 例）；`python -c "import lora_engine"` 无 SyntaxError；`grep -n "do_temperature" lora_engine.py` 必须**无输出**。

- [ ] **Step 5: Commit**

```bash
git add python-services/fine_tune_service/lora_engine.py python-services/fine_tune_service/test_ft_engine.py
git commit -m "feat(python): LoRA 权重 zip 导出与 adapter 真实推理（tokens_used/file_size 取真值）"
```

---

### Task 5: 契约层 `lora_service.py`（§37 八条 + 六条数据集/语料端点 + 权重下载路由）

**Files:**
- Create: `python-services/fine_tune_service/lora_service.py`
- Create: `python-services/fine_tune_service/test_ft_contract.py`

**Interfaces:**
- Consumes: Task 1–4 的 `lora_engine` 公开函数（本任务**不调用任何下划线符号**）：`ENGINE_VERSION`、`engine_available`、`dep_errors`、`device`、`known_models`、`as_dict`、`normalize_config`、`resolve_base_model`、`task_error`、`latest_dataset_id`、`create_task`、`start_training`、`get_task`、`list_tasks`、`cancel_task`、`delete_task`、`export_weights`、`infer`、`validate_samples`、`add_dataset`、`build_course_dataset`、`list_datasets`、`get_dataset`、`load_samples`、`delete_dataset`、`corpus_catalog`、`weights_dir`，异常类 `ConfigError`/`UnknownModelError`/`StoreCorruptedError`
- Produces: `app`（FastAPI，title `AI Course Fine-Tune Service`，version `0.1.0`）、`MSG_FT_UNAVAILABLE`、`ready()->bool`、响应模型 `TaskOut`/`InferOut`/`ExportOut`、端点函数名 `health/start_task/task_status/list_tasks/cancel_task/infer_task/export_task/delete_task/create_dataset/build_dataset/list_datasets/get_samples/delete_dataset/list_corpus/download_weight`

- [ ] **Step 1: 写契约测试（先钉 §37 八条路径、Java 真实载荷形状与降级语义）**

```python
"""8003 契约护栏：路径与字段名以 `ContractPaths.java:63-70` 与手册 §37 为准。
CI 作业 `python-fine-tune-contract` 守护本文件，演进引擎逻辑时不得破坏端点形状。"""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import lora_engine as le
import lora_service as svc

P = "/api/v1/fine-tune"


@pytest.fixture(autouse=True)
def isolated(tmp_path, monkeypatch):
    monkeypatch.setenv("FT_DATA_DIR", str(tmp_path / "ftdata"))
    monkeypatch.setenv("FT_ENGINE_READY", "1")   # 不启动训练线程：状态机必须可确定性断言
    monkeypatch.delenv("FT_CORPUS_DIR", raising=False)
    monkeypatch.delenv("FT_PUBLIC_BASE", raising=False)
    le.reset_store()
    yield
    le.reset_store()


@pytest.fixture
def client():
    return TestClient(svc.app)


def _ds(client, course_id=1, n=1):
    return client.post(f"{P}/datasets", json={"course_id": course_id, "samples": [
        {"instruction": f"q{i}", "output": f"a{i}"} for i in range(n)]}).json()


def _start(client, course_id=1, cfg=None, base_model="qwen-7b"):
    ds = _ds(client, course_id)
    body = {"course_id": course_id, "base_model": base_model,
            "config": {"dataset_id": ds["dataset_id"], **(cfg or {})}}
    return client.post(f"{P}/start", json=body), ds


def test_health_reports_engine_truth(client):
    body = client.get(f"{P}/health").json()
    assert body["status"] == "ok"
    assert body["engine_available"] == le.engine_available()   # 真值来自探针，不是声明
    assert "deps_missing" in body and "known_models" in body
    assert body["datasets"] == 0 and body["tasks"] == 0


def test_start_accepts_spec_object_config(client):
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={
        "course_id": 1, "base_model": "qwen-7b",
        "config": {"epochs": 3, "learning_rate": 5e-05, "lora_rank": 8, "lora_alpha": 16,
                   "lora_dropout": 0.1, "target_modules": ["q_proj", "v_proj"],
                   "dataset_id": ds["dataset_id"]}})
    assert r.status_code == 200
    body = r.json()
    assert {"task_id", "status", "created_at"} <= set(body)
    assert body["task_id"].startswith("ft_") and body["status"] == "pending"
    assert body["mode"] == "STRUCTURE"      # FT_ENGINE_READY=1：任务记录真实、训练线程未起
    assert body["code"] == 200


def test_start_accepts_java_string_config(client):
    """Java 端 `config` 是 JsonNode.toString()（LLMFineTuneServiceImpl.java:80）——上线即踩的点。"""
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={
        "course_id": 1, "base_model": "qwen-7b",
        "config": json.dumps({"epochs": 2, "lora_rank": 4, "dataset_id": ds["dataset_id"]})})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["code"] == 200 and body["status"] == "pending"
    assert le.get_task(body["task_id"])["config"]["lora_rank"] == 4


def test_start_rejects_malformed_config(client):
    r = client.post(f"{P}/start", json={"course_id": 1, "base_model": "qwen-7b",
                                        "config": "{oops"})
    assert r.status_code == 400
    assert "config" in r.json()["detail"]


def test_start_rejects_unregistered_base_model_with_alias_table(client):
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={"course_id": 1, "base_model": "gpt-99",
                                        "config": {"dataset_id": ds["dataset_id"]}})
    assert r.status_code == 400
    detail = r.json()["detail"]
    assert "FT_MODEL_MAP" in detail and "qwen-7b" in detail   # 回显登记表，不猜模型


def test_start_without_dataset_degrades_actionably(client):
    r = client.post(f"{P}/start", json={"course_id": 99, "base_model": "qwen-7b"})
    assert r.status_code == 200          # Java 对非 5xx 才原样透传，故降级放 body
    body = r.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert "datasets/build/99" in body["error"] and body["status"] == "rejected"


def test_task_status_roundtrip_pinned_fields(client):
    r, ds = _start(client, 7, {"epochs": 2})
    tid = r.json()["task_id"]
    st = client.get(f"{P}/tasks/{tid}/status").json()
    assert {"task_id", "status", "progress", "current_epoch", "total_epochs",
            "base_model", "dataset_id", "mode"} <= set(st)
    assert st["task_id"] == tid and st["base_model"] == "qwen-7b"
    assert st["dataset_id"] == ds["dataset_id"] and st["total_epochs"] == 2
    assert st["progress"] == 0.0 and st["current_epoch"] == 0
    assert "adapter_dir" not in st and "dir" not in st         # 不泄露文件系统路径


def test_unknown_ids_are_404_everywhere(client):
    for path, verb in ((f"{P}/tasks/ft_19700101_999/status", "get"),
                       (f"{P}/tasks/ft_19700101_999/export", "get"),
                       (f"{P}/tasks/ft_19700101_999", "delete"),
                       (f"{P}/tasks/ft_19700101_999/cancel", "post"),
                       (f"{P}/datasets/ds_19700101_999/samples", "get")):
        assert getattr(client, verb)(path).status_code == 404, path
    assert client.post(f"{P}/infer/ft_19700101_999",
                       json={"prompt": "q"}).status_code == 404


def test_list_tasks_sorted_and_filtered(client):
    a, _ = _start(client, 3)
    b, _ = _start(client, 4)
    a, b = a.json()["task_id"], b.json()["task_id"]
    assert [t["task_id"] for t in client.get(f"{P}/tasks?course_id=3").json()["tasks"]] == [a]
    assert [t["task_id"] for t in client.get(f"{P}/tasks").json()["tasks"]] == [a, b]


def test_cancel_and_delete_roundtrip(client):
    r, _ = _start(client, 5)
    tid = r.json()["task_id"]
    out = client.post(f"{P}/tasks/{tid}/cancel").json()
    assert out["success"] is True and out["status"] == "cancelled"
    again = client.post(f"{P}/tasks/{tid}/cancel").json()
    assert again["success"] is False and again["status"] == "cancelled" and "无法取消" in again["error"]
    deleted = client.delete(f"{P}/tasks/{tid}").json()
    assert deleted["success"] is True and deleted["task_id"] == tid
    assert client.get(f"{P}/tasks/{tid}/status").status_code == 404


def test_export_on_unfinished_task_degrades_instead_of_faking_success(client):
    r, _ = _start(client, 1)
    ex = client.get(f"{P}/tasks/{r.json()['task_id']}/export")
    assert ex.status_code == 200
    body = ex.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert body["download_url"] is None and "尚未完成" in body["error"]
    # 占位导出已从设计里删除：宁给 503，也不在 download_url 上摆一个假链接


def test_infer_degrades_per_contract_without_real_engine(client):
    """没有真实模型就没有真实文本：`/infer` 永不占位（手册 §38）。"""
    r, _ = _start(client, 1)
    resp = client.post(f"{P}/infer/{r.json()['task_id']}",
                       json={"prompt": "什么是过拟合", "params": {"temperature": 0.7}})
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert body["answer"] is None and body["tokens_used"] == 0 and body["error"]


def test_infer_accepts_java_string_params(client):
    """Java 端 `params` 同样是字符串（LLMFineTuneServiceImpl.java:129）。"""
    r, _ = _start(client, 1)
    resp = client.post(f"{P}/infer/{r.json()['task_id']}",
                       json={"prompt": "q", "params": json.dumps({"temperature": 0.2})})
    assert resp.status_code == 200 and resp.json()["code"] == 503   # 形状吃下，仍如实降级
    bad = client.post(f"{P}/infer/{r.json()['task_id']}", json={"prompt": "q",
                                                               "params": "{oops"})
    assert bad.status_code == 400 and "params" in bad.json()["detail"]


def test_exported_zip_is_really_downloadable_and_bytes_match(client):
    """§37 的 file_size/download_url 必须是磁盘真值：下载路由 + 实际字节逐处核对。"""
    r, _ = _start(client, 1)
    tid = r.json()["task_id"]
    adir = Path(le.get_task(tid)["adapter_dir"])
    adir.mkdir(parents=True, exist_ok=True)
    (adir / "adapter_config.json").write_text('{"r": 8, "peft_type": "LORA"}', encoding="utf-8")
    (adir / "adapter_model.safetensors").write_bytes(b"\0" * 8192)
    le.update_task(tid, status="completed")

    body = client.get(f"{P}/tasks/{tid}/export").json()
    assert body["code"] == 200 and body["mode"] == "ENGINE" and body["format"] == "peft_lora"
    assert body["download_url"] == f"http://localhost:8003/weights/{tid}.zip"
    assert body["file_size"] == le.format_size(body["size_bytes"])
    assert "local_path" not in body                              # 服务器路径不出契约

    got = client.get(f"/weights/{tid}.zip")
    assert got.status_code == 200 and len(got.content) == body["size_bytes"] > 0
    assert client.get("/weights/ft_19700101_999.zip").status_code == 404


def test_weight_download_route_rejects_path_traversal(client):
    """下载路由只认 `<task_id>.zip` 形状：编码点号与斜杠都不放行。"""
    for evil in ("/weights/%2e%2e%2fdatasets.json", "/weights/..%2Ftasks.json",
                 "/weights/datasets.json", "/weights/a%2Fb.zip"):
        assert client.get(evil).status_code == 404, evil


def test_dataset_upload_jsonl_mixed_shapes_and_samples_endpoint(client):
    jsonl = "\n".join([json.dumps({"instruction": "什么是RR", "output": "时间片轮转调度"}),
                       json.dumps({"question": "什么是FCFS", "answer": "先来先服务"}), ""])
    r = client.post(f"{P}/datasets", json={"course_id": 3, "jsonl": jsonl})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["sample_count"] == 2 and body["dataset_id"].startswith("ds_")
    assert body["source"] == "上传 JSONL" and "dir" not in body
    got = client.get(f"{P}/datasets/{body['dataset_id']}/samples").json()
    assert got["returned"] == 2 and got["sample_count"] == 2 and got["offset"] == 0
    assert got["samples"][1]["output"] == "先来先服务"
    rows = client.get(f"{P}/datasets?course_id=3").json()["datasets"]
    assert [d["dataset_id"] for d in rows] == [body["dataset_id"]]
    assert set(rows[0]) == {"dataset_id", "course_id", "sample_count", "source", "created_at"}


def test_samples_endpoint_pages_with_offset_and_limit(client):
    saved = client.post(f"{P}/datasets", json={"course_id": 6, "samples": [
        {"instruction": f"q{i}", "output": f"a{i}"} for i in range(120)]}).json()
    page = client.get(f"{P}/datasets/{saved['dataset_id']}/samples?offset=100&limit=50").json()
    assert page["returned"] == 20 and page["samples"][0]["instruction"] == "q100"
    over = client.get(f"{P}/datasets/{saved['dataset_id']}/samples?limit=501")
    assert over.status_code == 422                               # pydantic 校验先把关


def test_dataset_upload_errors_carry_one_based_row_no(client):
    bad = client.post(f"{P}/datasets", json={"course_id": 3, "jsonl": "{bad json"})
    assert bad.status_code == 400 and "jsonl 第 1 行" in bad.json()["detail"]
    miss = client.post(f"{P}/datasets", json={"course_id": 3, "samples": [
        {"instruction": "q", "output": "a"}, {"instruction": "只有问"}]})
    assert miss.status_code == 400 and "第 2 条" in miss.json()["detail"]
    empty = client.post(f"{P}/datasets", json={"course_id": 3, "samples": []})
    assert empty.status_code == 400 and "至少需要一条" in empty.json()["detail"]
    neither = client.post(f"{P}/datasets", json={"course_id": 3})
    assert neither.status_code == 400 and "samples" in neither.json()["detail"]


def test_dataset_build_with_inline_source_and_no_body(client):
    built = client.post(f"{P}/datasets/build/3",
                        json={"source": "## 调度\n先来先服务会让长作业饿死。"}).json()
    assert built["success"] is True and built["sample_count"] >= 1
    assert "调用方提供" in built["source"] and built["mode"] == "ENGINE"

    # 无 body：走回落链（FT_CORPUS_DIR 未设 → 仓库内已版本化 scripts/rag_corpus.json）
    fallback = client.post(f"{P}/datasets/build/1").json()
    assert fallback["success"] is True and "rag_corpus.json" in fallback["source"]


def test_dataset_build_without_any_corpus_fails_honestly(client, tmp_path, monkeypatch):
    monkeypatch.setenv("FT_CORPUS_JSON", str(tmp_path / "绝不存在的语料.json"))
    out = client.post(f"{P}/datasets/build/42").json()
    assert out["success"] is False and out["code"] == 503 and out["mode"] == "UNAVAILABLE"
    assert "FT_CORPUS_DIR" in out["error"] and "datasets" in out["error"]


def test_delete_dataset_roundtrip(client):
    ds = _ds(client, 8)
    out = client.delete(f"{P}/datasets/{ds['dataset_id']}").json()
    assert out["success"] is True and out["dataset_id"] == ds["dataset_id"]
    assert client.get(f"{P}/datasets?course_id=8").json()["datasets"] == []


def test_corpus_lists_derivable_documents(client):
    body = client.get(f"{P}/corpus").json()
    assert body["code"] == 200 and body["mode"] == "ENGINE"
    assert any(d["course_id"] == 1 and d["chars"] > 0 for d in body["documents"])
    assert 1 in body["courses"]
```

- [ ] **Step 2: 运行确认失败**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_contract.py -q`
Expected: FAIL —`ModuleNotFoundError: No module named 'lora_service'`

- [ ] **Step 3: 实现 `lora_service.py`（模块头 → 请求/响应模型 → 端点，逐字可运行）**

docstring 照 `rag_service.py:1-29` 写：契约来源（手册 §37 + `ContractPaths.java:63-70`）、CI 护栏作业名、懒加载与降级语义、`FT_ENGINE_READY` 的确切覆盖面、端点清单（§37 八条 + 附加六条 + `/weights` 下载）、启动命令与单 worker 边界。

```python
from __future__ import annotations

import json
import os
import re
from pathlib import Path
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

# 下载路由只认 `<task_id>.zip`：任务号本身是 ft_数字_数字，绝不接受路径分隔符或点号目录
_WEIGHT_NAME = re.compile(r"^ft_\d{8}_\d{3}\.zip$")


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
    if not cfg.get("dataset_id"):
        cfg["dataset_id"] = lora_engine.latest_dataset_id(body.course_id)
    err = lora_engine.task_error(cfg)
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
```

**实现注记（不得省略）**：
- **`mode` 与 `code` 同向**：`code=200 → mode=ENGINE`、`code=503 → mode=UNAVAILABLE`，唯一例外是 `ready()` 下的 `/start`（`STRUCTURE`）。引擎是否真的在场由 `/health` 的 `engine_available`/`deps_missing` 说明——**不用 `mode` 冒充能力**。
- **响应字段白名单**：`_task_out`/`_ds_out`/`ExportOut` 只出契约字段，`adapter_dir`、`dir`、`local_path`、`config` 属服务器内部项，不进响应。
- **`/weights/{name}` 用下载路由而非 StaticFiles**：后者在 import 期固化目录，与按用例注入的 `FT_DATA_DIR` 冲突；改路由后既跟着环境走又能拦路径穿越（`_WEIGHT_NAME` + `target.parent != root` 双闸）。
- **可选 body 的两种写法都要测**：`build_dataset` 的 `body: Optional[BuildIn] = None` 让 Java/管理端不传 body 也能派生（回落链在引擎里）；`test_dataset_build_*` 各钉一例。

- [ ] **Step 4: 运行确认通过**

Run: `cd python-services/fine_tune_service && python -m pytest test_ft_contract.py -q`
Expected: PASS（22 例）

- [ ] **Step 5: 路径逐字比对 `ContractPaths.java`（不靠肉眼）**

Run: `cd python-services/fine_tune_service && python - <<'PY'`

```python
import pathlib, re
import lora_service as svc

java = pathlib.Path(
    "../backend/src/main/java/com/aicourse/integration/ContractPaths.java"
).read_text(encoding="utf-8")
consts = {k: v for k, v in re.findall(r'\b(FT_\w+)\s*=\s*"([^"]+)"', java)
          if v.startswith("/api")}
have = {(r.path, m) for r in svc.app.routes if hasattr(r, "methods")
        for m in r.methods - {"HEAD", "OPTIONS"}}
VERB = {"FT_HEALTH": "GET", "FT_START": "POST", "FT_TASK_STATUS": "GET", "FT_TASKS": "GET",
        "FT_TASK_CANCEL": "POST", "FT_INFER": "POST", "FT_TASK_EXPORT": "GET", "FT_TASK": "DELETE"}
assert len(consts) == 8, f"Java 侧常量数变了：{sorted(consts)}"
bad = []
for name, path in sorted(consts.items()):
    tmpl = path.replace("{taskId}", "{task_id}")
    ok = (tmpl, VERB[name]) in have
    print(f"{'OK ' if ok else '!! '}{name:16} {VERB[name]:6} {tmpl}")
    bad += [] if ok else [name]
print("全部命中" if not bad else f"缺：{bad}")
```

Expected: 8 行全 `OK`、末行 `全部命中`。

- [ ] **Step 6: Commit**

```bash
git add python-services/fine_tune_service/lora_service.py python-services/fine_tune_service/test_ft_contract.py
git commit -m "feat(python): 8003 LoRA 契约层——§37 八端点 + 数据集/语料六端点 + 权重下载路由"
```

---

### Task 6: 依赖清单、忽略规则与 CI 门禁

**Files:**
- Modify: `.gitignore:88` 附近
- Modify: `.github/workflows/ci.yml:606` 之后

**Interfaces:**
- Consumes: Task 1–5 的全部测试
- Produces: CI 作业 `python-fine-tune-contract`

- [ ] **Step 1: `.gitignore` 追加数据目录（紧跟 rag 那行，保持同形）**

```
python-services/rag_service/data/
python-services/fine_tune_service/data/
```

- [ ] **Step 2: `ci.yml` 新增作业（逐字仿 `python-rag-contract`）**

```yaml
  python-fine-tune-contract:
    name: Python LoRA Fine-Tune Service Contract (8003)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: python-services/fine_tune_service
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"
          cache: pip
          cache-dependency-path: python-services/fine_tune_service/requirements.txt

      - name: Install dependencies
        run: pip install -r requirements.txt pytest

      - name: Contract + engine tests (FastAPI TestClient)
        # 引擎重依赖（torch/transformers/peft/datasets）在 CI 不安装：
        # lora_engine 与用例都做软依赖处理——缺依赖的真实训练用例按 skipif 跳过，
        # 纯逻辑与契约用例照常跑。
        # FT_ENGINE_READY=1 只是**结构开关**：让 /start 登记真实任务记录但不启动训练线程，
        # 使契约用例能断言 pending 形状而不与后台线程竞态。它不改变 mode 语义，
        # 也不产生任何占位成功响应——取证只认引擎真跑出的 mode=ENGINE。
        env:
          FT_ENGINE_READY: "1"
        run: pytest -q
```

- [ ] **Step 3: 本机模拟 CI 姿态实跑（不装重依赖也能绿）**

Run:

```bash
cd python-services/fine_tune_service && FT_ENGINE_READY=1 python -m pytest -q
```

Expected: 全绿，且 `test_ft_engine.py` 中真实训练用例为 `skipped`（不是 failed）。

- [ ] **Step 4: 三服务互不影响复跑**

Run: `cd python-services/kg_service && python -m pytest -q`；`cd python-services/rag_service && python -m pytest -q`
Expected: 两者各自全绿（把实测数量记入 Task 8 的手册台账，**不得沿用文档旧数**）

- [ ] **Step 5: YAML 语法自检**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml',encoding='utf-8')); print('ci.yml OK')"`
Expected: `ci.yml OK`（若本机无 PyYAML，改用 `python -m json.tool` 不适用于 YAML → 记录「本机未复现，属 CI 口径」，**不得声称验过**）

- [ ] **Step 6: Commit**

```bash
git add .gitignore .github/workflows/ci.yml
git commit -m "build(ci): 新增 8003 LoRA 契约门禁作业并忽略引擎数据目录"
```

---

### Task 7: 本机真跑取证（坐实 `mode=ENGINE`）

**Files:**
- Modify: `python-services/fine_tune_service/test_ft_engine.py`（新增真实训练用例）
- Create（条件步骤）: `scripts/fine_tune_dataset.py` —— **仅当 Step 0 的三判据有一项不达标时才创建**，见 Step 0

**Interfaces:**
- Consumes: Task 1–6 全部
- Produces: 手册 §46.2 与 §18.5 的实测取证记录（loss 序列、zip 字节、推理文本）

- [ ] **Step 0: 语料充足性判据（先实测再决定要不要另起数据脚本）**

Run:

```bash
cd python-services/fine_tune_service && python - <<'PY'
import lora_engine as le
built = le.build_course_dataset(1)
print("built:", {k: v for k, v in built.items() if k != "samples"})
rows = le.load_samples(built["dataset_id"]) if built["success"] else []
print("count:", len(rows))
for r in rows[:5]:
    print("---", r["instruction"], "|", r["output"][:120])
PY
```

三判据**全部**满足才继续用 `rag_corpus.json` 派生，否则先做数据再取证：
1. **主题相关**：打印出的 instruction/output 确实是该课程的教学内容，不是切碎的残句或纯目录；
2. **可配对**：每条都有语义完整的 `instruction` 与非空 `output`（不是把同一段文字复制两遍）；
3. **量足够**：单课 ≥20 条，否则 8 步训练连一次完整 epoch 都不到，loss 序列无意义。

不达标时创建 `scripts/fine_tune_dataset.py`：读仓库权威语料（或后端讲义）→ 生成 `{instruction, output}` 列表 → 只调用公开入口 `le.validate_samples()` + `le.add_dataset()` 落库。**不写私有符号、不改 `corpus_source()` 的回落链**——回落链只负责「找不到语料时诚实失败」，数据补齐是脚本的职责。批 2 若把 Java 侧 `POST /datasets` 接上，此脚本可退役为纯种子工具。

- [ ] **Step 1: 装引擎依赖（不动 torch）**

Run: `python -m pip install peft datasets accelerate`
Expected: 成功；随后 `python -c "import peft,datasets,transformers,torch,lora_engine" && python -c "import lora_engine as le; print(le.engine_available())"` 打印 `True`

- [ ] **Step 2: 下载 0.5B 基座并确认缓存命中**

Run:

```bash
python -c "
from huggingface_hub import snapshot_download
p = snapshot_download('Qwen/Qwen2.5-0.5B', allow_patterns=['*.json','*.safetensors','*.txt'])
print(p)
"
cd python-services/fine_tune_service && python -c "import lora_engine as le; print('cached:', le.base_model_cached('Qwen/Qwen2.5-0.5B'))"
```

Expected: `cached: True`（联网失败即停在本步并报告，不得改口径）

- [ ] **Step 3: 起服务并跑全链路（真训练，分钟级）**

Run:

```bash
cd python-services/fine_tune_service
FT_ALLOW_DOWNLOAD=0 FT_TRAIN_STEPS=8 uvicorn lora_service:app --host 127.0.0.1 --port 8003 &
python - <<'PY'
import httpx, time, json
B = "http://127.0.0.1:8003/api/v1/fine-tune"
print(httpx.get(f"{B}/health").json())
print(httpx.post(f"{B}/datasets/build/1").json())
t = httpx.post(f"{B}/start", json={"course_id": 1, "base_model": "qwen-7b",
                                   "config": {"epochs": 1, "lora_rank": 4}}).json()
print(t)
tid = t["task_id"]
for _ in range(120):
    st = httpx.get(f"{B}/tasks/{tid}/status").json()
    print(st["status"], st["progress"], st["loss"])
    if st["status"] in ("completed", "failed", "cancelled"):
        break
    time.sleep(5)
print("EXPORT", httpx.get(f"{B}/tasks/{tid}/export").json())
print("INFER", json.dumps(httpx.post(f"{B}/infer/{tid}", json={
    "prompt": "请讲解梯度下降", "params": {"temperature": 0.3, "max_tokens": 48}}).json(),
    ensure_ascii=False))
PY
```

Expected: `/health` 的 `engine_available` 为 `true`；`status` 经 `pending → running → completed`；`loss` 为**真实下降序列**（记录首/末值与步数）；`export` 返回 `code=200`/`mode=ENGINE` 且 `download_url` 可直接 GET 到字节，`file_size == format_size(size_bytes)`；`infer` 返回 `mode=ENGINE` 且 `answer` 非空、`tokens_used > 0`。全部原值留档，作为手册取证的唯一来源。

- [ ] **Step 4: 下载 zip 校验字节真值**

Run: `python -c "import httpx; r=httpx.get('<Step3 打印的 download_url>'); print(len(r.content), r.headers.get('content-type'))"`
Expected: 字节数 > 0 且与 `export` 响应里的 `size_bytes` 完全相等（不等即为缺陷，须修 `format_size` 或落盘逻辑）；`content-type` 为 `application/zip`。

- [ ] **Step 5: 把真实训练写成 `skipif` 用例（换机可复现，CI 自动跳过）**

```python
requires_real_engine = pytest.mark.skipif(
    not (le.engine_available() and le.base_model_cached("Qwen/Qwen2.5-0.5B")),
    reason="需 peft/transformers 与本地已缓存基座模型")


@requires_real_engine
def test_real_lora_training_reduces_loss_and_exports(tmp_path, monkeypatch):
    monkeypatch.setenv("FT_TRAIN_STEPS", "6")
    monkeypatch.setenv("FT_ALLOW_DOWNLOAD", "0")
    built = le.build_course_dataset(1)
    assert built["success"], built
    cfg = le.normalize_config({"epochs": 1, "lora_rank": 4, "dataset_id": built["dataset_id"]})
    task = le.create_task(1, "qwen-7b", cfg)
    done = le.run_training(task["task_id"])
    assert done["status"] == "completed", done
    hist = done["loss_history"]
    assert len(hist) >= 2 and hist[-1] < hist[0]        # 真实下降，不是编造
    ex = le.export_weights(task["task_id"])
    assert ex["code"] == 200 and Path(ex["local_path"]).stat().st_size == ex["size_bytes"]
    out = le.infer(task["task_id"], "请讲解梯度下降", {"max_tokens": 32})
    assert out["mode"] == "ENGINE" and out["answer"] and out["tokens_used"] > 0
```

Run: `python -m pytest test_ft_engine.py -q`
Expected: 全绿（真实训练用例在本机执行，其余环境 skip）

- [ ] **Step 6: 全量复跑 + Commit**

```bash
cd python-services/fine_tune_service && python -m pytest -q && FT_ENGINE_READY=0 python -m pytest -q
git add python-services/fine_tune_service/test_ft_engine.py
git commit -m "test(python): 8003 真实 LoRA 训练用例（loss 下降/导出字节/推理三处真值断言）"
```

若走了 Step 0 的分支创建了 `scripts/fine_tune_dataset.py`，Step 6 的 `git add` 把它一并加上、commit message 末尾追加「+ 数据集种子脚本」；**同时在手册 §46.2 说明取证用的是哪一份语料**，否则换机复现时会误以为 `rag_corpus.json` 够用。

---

### Task 8: 手册与索引文档回写

**Files:**
- Modify: `docs/项目手册.md`（§37、§40、§42、§46.2、§18.5、附录 D）
- Modify: `docs/文档索引.md`、`README.md`、`docs/项目现状分析与未来发展规划-2026-10-13.md`

**Interfaces:**
- Consumes: Task 7 的实测留档
- Produces: 无代码接口，交付口径文档

- [ ] **Step 1: 先重新核实磁盘现状（手册/README 有他人在飞改动）**

Run: `git status --short && git diff --stat -- docs/项目手册.md README.md`
Expected: 确认待改文件是否仍有未提交改动；有则**先问项目主**再动，不得覆盖。

- [ ] **Step 2: §37 契约节回写**

在 §37 追加实现落地说明：8 条端点已交付 + 6 条附加数据集/语料端点清单（`POST /datasets`、`POST /datasets/build/{course_id}`、`GET /datasets`、`GET /datasets/{dataset_id}/samples`、`DELETE /datasets/{dataset_id}`、`GET /corpus`，标注「附加，不改 §37 形状」）与 `/weights/{weight_name}` 下载路由；`config`/`params` 双形状说明与 **DR-73** 残项（Java 侧仍发字符串，是否改发对象属跨端决策）；任务状态机、单 worker 边界与 `mode`/`code` 同向三值规则。

- [ ] **Step 3: §40 降级表 / §42 契约测试表 / §46.2 LoRA 条**

- §40 增行：`微调服务（8003）引擎依赖缺失 / 无数据集 / 存储损坏` → `{"error": 可操作文案, "code": 503, "mode": "UNAVAILABLE"}`（HTTP 仍为 200，理由同 §38：Java 侧只透传非 5xx），Java 侧仍按 §40 返回 `微调服务暂不可用`。存储损坏项由 `lora_service.py` 的 app 级 `StoreCorruptedError` 处理器统一产出。
- §42 增行：`test_ft_contract.py`（22 例）/ `test_ft_engine.py`（28 例，其中真实训练用例 1 例带 `skipif` 门禁），逐字写本机**实测**数；实测与计划预期不一致时以实测为准并回头改本计划。
- §46.2 的 LoRA 条由 ❌ 改为 ✅/🟡：**只写 Task 7 实测到的东西**（模型 ID、步数、loss 首末值、zip 字节、推理文本样例、`mode=ENGINE` 命中的端点），并明确未覆盖面：GPU、7B 级基座、多并发、容器化。

- [ ] **Step 4: 附录 D 登记 DR-72 / DR-73，并把 DR-23 残项收窄**

DR-72：LoRA(8003) 引擎侧交付（含取证与未完成面）。DR-73：`config`/`params` 两端形状漂移（引擎双形状兼容已落地，Java 侧待决）。DR-23 的「`:8003` LoRA 微调无任何 Python 实现」改为「已交付，残项仅剩 kg/rag/ft 的 Dockerfile」。

- [ ] **Step 5: 索引与 README 读数**

`docs/文档索引.md` 的 `python-services/` 行由「⚠️ 8003 无任何 Python 实现」改为三引擎读数；`README.md` 引擎计数（8001/8002 → 8001/8002/8003）与 B.2 台账测试数按实测刷新；`项目现状分析-2026-10-13` 的 F1、D4-1、D13 行同步。

- [ ] **Step 6: 全文清扫过期表述**

Run: `grep -rn "无任何 Python 实现\|8003.*未交付\|等待队友交付" docs README.md`
Expected: 仅剩「Dockerfile 仍待补」等**确实未完成**的表述；出现 8003 实现类过期句即须改回。

- [ ] **Step 7: Commit**

```bash
git add docs/项目手册.md docs/文档索引.md README.md docs/项目现状分析与未来发展规划-2026-10-13.md
git commit -m "docs(manual,index,readme): D1-3 LoRA(8003) 引擎回写——DR-72/DR-73 入表 + §37/§40/§42/§46.2 实测口径"
```

---

## 自检（写完后跑一遍）

- **spec 覆盖**：§3 落点与分层→File Structure + Task 1–5（引擎层四段递进、契约层独立成任务）；§4 数据集→Task 2（派生/校验/分页/删除）+ Task 5（六条附加端点）；§5 引擎设计含 §5.1 环境变量→Task 1（依赖探针与 `normalize_config`）/ 3（状态机与真实训练）/ 4（导出与推理）/ 5（`ready()` 只读 `FT_ENGINE_READY`）；§6 端点总表→Task 5 Step 3 与 Step 5（含与 `ContractPaths.java` 的逐字自动比对，不靠肉眼）；§7 漂移→Task 1（`as_dict` 双形状）+ Task 5（`test_start_accepts_java_string_config`）；§8 门禁与取证→Task 6 + Task 7；§9 回写与批次切分→Task 8；§10 验收→Task 6 Step 3/4、Task 7 Step 5/6、Task 8 Step 6。无遗漏。
- **计划期发现的设计缺口（已就地补进 spec）**：
  1. `datasets/build` 的 `source` 必须**可选并回落仓库语料**，否则 Java 链路无 body 可传、`/start` 恒 503 → spec §4。
  2. `mode` 与 `code` 必须**同向**且只三值（`ENGINE`/`UNAVAILABLE`/`STRUCTURE`），否则 `mode` 会被误读成能力声明；引擎在场与否由 `/health` 独任 → spec §6。
  3. `FT_ENGINE_READY` 必须**只**决定 `/start` 是否拉起训练线程。原设计让它返回占位成功步骤，既在契约字段里编造假值、又与后台线程竞态（真实训练环境下 `status` 已不是 `pending`）→ spec §8；导出同理不做占位件，未完成任务如实 503。
  4. 静态目录不能用 `StaticFiles`：它在 import 期钉死目录，与按用例注入的 `FT_DATA_DIR` 冲突 → 改为 `/weights/{weight_name}` 下载路由（白名单正则 + `target.parent != root` 双闸）→ spec §5。
  5. 样本形状校验必须在**引擎**（`validate_samples`）而非路由：pydantic 的 `list[dict]` 会把非对象行变成 422，抢走本该由契约给出的 400 可操作文案 → spec §4。
  6. `dataset_id` 号段不能由 `len+1` 推出：删除后复用号码会让历史任务的 `dataset_id` 指向另一份数据 → Task 2 用 `_DATASET_SEQ` 单调递增并有回归用例。
  7. Trainer 回调的步数/轮次只能取 `state.global_step` / `state.epoch`：`logs` 里没有 `step`，从 kwargs 取会恒为 `None` → `progress` 永远 0.0，而这类缺陷在真跑取证时只表现为「任务完成了但进度是 0」，极易被误读成已坐实 → 写入逻辑单列 `_record_progress()` 并用假 state 在 CI 断言（spec §5）。
- **类型与命名一致性**：`export_weights(task_id, public_base=None)` 在 Task 4 定义、Task 5 与 Task 7 调用签名一致；导出字节数字段统一叫 `size_bytes`（不叫 `bytes`，避免遮蔽内置且与 `file_size` 人类可读串区分）；`normalize_config` 产出的键（`epochs/learning_rate/lora_rank/lora_alpha/lora_dropout/batch_size/max_seq_length/dataset_id/target_modules/warmup_ratio`）在 Task 3 `_train_steps`/`TrainingArguments` 与 Task 7 测试中逐字一致；进程内持有表统一大写 `_MODEL_HOLDER`/`_CANCEL_FLAGS`；时间戳与 ID 日期段由 Task 2 的 `now_iso()`/`_today_str()` 单点提供（Task 3 起复用，不重复定义）；设备解析单点为 Task 3 的 `device() -> str`（Task 4/5 复用，`/health` 与训练共用同一真值）。
- **测试计数递进**：`test_ft_engine.py` 6（Task 1）→ 15（Task 2）→ 23（Task 3）→ 27（Task 4）→ 28（Task 7 加真实训练用例）；`test_ft_contract.py` 22（Task 5）。手册 §42 记**实测数**，与计划预期不符时以实测为准并回改本计划。
