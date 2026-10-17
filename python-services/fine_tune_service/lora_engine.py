"""
LoRA 真实微调引擎（PEFT 低秩适配：数据集构建 → 训练 → 导出 → 加载推理）
====================================================================
为 lora_service.py 的契约端点（手册 §37 的 8 条 + 数据集/语料附加端点）提供真实实现。
依赖策略：

- 重依赖（torch / transformers / peft / datasets / accelerate）**全部懒加载**，
  缺任一项 `engine_available()` 返回 False，契约层据此按手册 §38 返回
  `code=503 / mode=UNAVAILABLE` 的降级响应——这样 CI（只装 fastapi/pytest）
  无需下载模型即可保持全绿，且**降级不是占位成功**：任何契约字段都不编造假值。
- 基座模型默认**离线**：`base_model_cached()` 只做本地缓存判定（不触发联网），
  未缓存即诚实失败；仅 `FT_ALLOW_DOWNLOAD=1` 才允许首次联网拉取。
- 存储为文件持久化（数据集表 + 任务表 + 样本目录 + adapter + zip），目录由
  `FT_DATA_DIR` 指定，默认 `python-services/fine_tune_service/data/`（已 gitignore），
  测试可用该变量隔离。

训练口径（loss 掩码取舍，显式记录）：采 completion-only 整理器
（`DataCollatorForLanguageModeling(mlm=False)`）对「指令+回答」**整段**计损失，
不给 prompt 段做掩码。理由：对 0.5B 基座做部分掩码依赖对话模板的 token 一致性，
掩错会静默学成「复读指令」且训练不报错；截断风险改由样本构造侧的 `max_seq_length`
兜住。此取舍同步登记在手册 §46.2。

环境变量（引擎只读进程环境、不读 `.env`，同手册 DR-39 口径）：
  FT_DATA_DIR          数据目录（默认 ./data）
  FT_CORPUS_DIR        每课程一文件的讲义目录（<course_id>.json/.md/.txt），回落链优先级最高
  FT_CORPUS_JSON       讲义派生数据集的回落语料（默认 ../../scripts/rag_corpus.json）
  FT_DEFAULT_BASE_MODEL 别名表未命中时的兜底 HF 模型 ID（默认 Qwen/Qwen2.5-0.5B）
  FT_MODEL_MAP         业务名=HF_ID 别名表，分号分隔（如 qwen-7b=Qwen/Qwen2.5-0.5B）
  FT_MAX_SAMPLES       单次训练样本上限（默认 200，CPU 可行性护栏）
  FT_MAX_SEQ_LEN       截断长度（默认 512）
  FT_TRAIN_STEPS       钉死 max_steps（本机分钟级取证用；未设时按 epochs/batch 估完整轮步数）
  FT_ALLOW_DOWNLOAD    0=基座未本地缓存即失败（默认）；1=允许首次联网拉取
  FT_DEVICE            训练/推理设备（默认自动：有 cuda 用 cuda）
  FT_PUBLIC_BASE       download_url 拼接基址（默认 http://localhost:8003，容器/反代须覆盖）
  FT_ENGINE_READY      CI 结构开关（默认 0）：**只**影响 /start 是否启动训练线程
"""
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


# ---------------------------------------------------------------- 待 Task 2 落地（当前为空壳）


def reset_store() -> None:
    pass


def list_datasets(course_id: Optional[int] = None) -> list[dict]:
    return []
