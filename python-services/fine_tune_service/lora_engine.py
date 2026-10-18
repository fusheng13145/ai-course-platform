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
  FT_ALLOW_DOWNLOAD    0=基座未本地缓存即失败（默认）；1=允许首次联网拉取。
                       该开关在 import 期生效（Hub 的离线常量亦在 import 期读取），改动需重启服务。
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


# 离线优先：默认绝不让 Hub 发网络探测。必须在下面的 try-import 之前落定——
# huggingface_hub 在 import 期就把 HF_HUB_OFFLINE 读成模块常量，运行期再改环境变量无效。
# 本机取证坐实的原因（transformers 5.5.4）：即使传 `local_files_only=True`，加载仍会向
# https://huggingface.co/api/models/<id> 发一次 revision 解析请求；该域名不可达时不是快速
# 失败，而是挂到 ConnectTimeout（约 21s）后整次训练以"失败"收场——本地明明有缓存，本该秒开。
# FT_ALLOW_DOWNLOAD=1 时不钉离线，允许首次拉取；改这个开关需要重启服务才生效。
if os.getenv("FT_ALLOW_DOWNLOAD", "0") != "1":
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")


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


def task_error(cfg: dict, course_id: int) -> Optional[str]:
    """任务可启动性检查：本课程无数据集时给可操作文案（同 rag_service._degrade_error 口径）。

    必须按 `course_id` 判定而不是"全局有没有数据集"：否则课程 A 有数据时，
    课程 B 的 /start 会放行并建出一个 `dataset_id=None`、永远练不成的任务。
    文案里带真实课程号，调用方照抄就能跑通下一步。
    """
    if cfg.get("dataset_id") or latest_dataset_id(course_id):
        return None
    return (f"尚无训练数据集，请先 POST /api/v1/fine-tune/datasets/build/{course_id}"
            "（或 POST /api/v1/fine-tune/datasets 上传 JSONL）")


# ---------------------------------------------------------------- 原子落盘（同源 rag_engine.py:185-223）


def _quarantine_corrupt(path: Path) -> Path:
    """把损坏的存储文件改名保留（加时间戳），供事后排查而非直接删除。"""
    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    target = path.with_name(f"{path.name}.corrupt.{stamp}")
    try:
        os.replace(path, target)
    except OSError:
        # 改名失败（例如同秒冲突）时退回带随机后缀，确保原文件不被后续写入覆盖
        target = path.with_name(f"{path.name}.corrupt.{stamp}.{os.getpid()}")
        try:
            os.replace(path, target)
        except OSError:
            pass
    return target


def _atomic_write_text(path: Path, text: str) -> None:
    """先写临时文件再 os.replace 原子替换，避免进程崩溃留下半截 JSON。"""
    _atomic_write_bytes(path, text.encode("utf-8"))


def _atomic_write_bytes(path: Path, payload: bytes) -> None:
    """字节级原子替换落盘（先写临时文件、flush/fsync 再 os.replace）。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.tmp")
    try:
        with open(tmp, "wb") as fh:
            fh.write(payload)
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(tmp, path)
    finally:
        if tmp.exists():
            try:
                tmp.unlink()
            except OSError:
                pass


# ---------------------------------------------------------------- 存储表

# 数据集： {dataset_id: {"dataset_id","course_id","source","sample_count","created_at","dir"}}
_DATASETS: dict[str, dict] = {}
_TASKS: dict[str, dict] = {}
# 按天序号（数据集/任务各一张）： {"20261017": 3}——删除后不得复用旧号，否则 ID 撞车
_DATASET_SEQ: dict[str, int] = {}
_TASK_SEQ: dict[str, int] = {}
_loaded = False

# 进程内模型/分词器缓存：训练与推理共用，换基座即失效重建。
# task_id 是 adapter 的归属：缓存的是「基座+某个 adapter」，换任务必须重载，否则会拿 A 的权重冒充 B 推理。
_MODEL_HOLDER: dict = {"model": None, "tokenizer": None, "hf_id": None, "task_id": None}

# 协作式取消标志（task_id → 是否已请求取消）：由 cancel_task 置位、训练回调在 step 边界读取
_CANCEL_FLAGS: dict[str, bool] = {}


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
        _MODEL_HOLDER.update({"model": None, "tokenizer": None, "hf_id": None, "task_id": None})
        _CANCEL_FLAGS.clear()  # 按天序号会重号，残留的取消标志会把新任务一起跑就判死
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
            new_topic = _heading_title(line)
            if new_topic:      # 空标题行不改变当前小节
                topic = new_topic
            continue
        for sent in (s for s in _SENT_SPLIT.split(line) if s.strip()):
            if buf and len(buf) + len(sent) > 560:
                emit(buf)
                buf = sent
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


# ---------------------------------------------------------------- 任务状态机


class _TrainingCancelled(RuntimeError):
    """协作式取消：在 on_log/on_step 边界抛出，由 run_training 收成 cancelled 终态。"""


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
        from peft import LoraConfig, TaskType, get_peft_model

        # 缓存命中即秒开：Hub 的联网探测已由模块顶部的离线钉死关闭（见 FT_ALLOW_DOWNLOAD 注释）
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
        # peft 没有 `PeftModel.from_config`（那是 transformers 的形状）：从配置建 LoRA 用
        # get_peft_model；`PeftModel.from_pretrained` 只用于从目录载入已训好的 adapter
        model = get_peft_model(base, lora)

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
        _MODEL_HOLDER.update({"model": None, "tokenizer": tokenizer, "hf_id": hf_id, "task_id": None})
        return update_task(task_id, status="completed", progress=100.0,
                           current_epoch=rec["config"]["epochs"], error=None)
    except _TrainingCancelled:
        return update_task(task_id, status="cancelled", error="训练已在 step 边界取消")
    except Exception as exc:  # 训练期任何异常都落成可诊断终态，绝不冒泡成 500
        return update_task(task_id, status="failed",
                           error=f"训练失败：{type(exc).__name__}: {exc}")


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


# ---------------------------------------------------------------- 权重导出与推理


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
    if not adir.exists() or (not any(adir.glob("adapter_*.json")) and not any(adir.glob("*.safetensors"))):
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


def _adapter_cache_matches(task_id: str) -> bool:
    """缓存中的模型是否就是本次要用的 adapter。

    单独成函数是为了让「换任务必须重载 adapter」在 CI 里可断言：缓存键若漏掉
    task_id，推理任务 B 会静默使用任务 A 的权重并照样回报 `-lora-B`。
    """
    return _MODEL_HOLDER["model"] is not None and _MODEL_HOLDER.get("task_id") == task_id


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
        if not _adapter_cache_matches(task_id):
            base = AutoModelForCausalLM.from_pretrained(
                hf_id, local_files_only=cached,
                torch_dtype=_torch.float16 if device() == "cuda" else _torch.float32)
            _MODEL_HOLDER.update({"model": PeftModel.from_pretrained(base, rec["adapter_dir"]),
                                  "tokenizer": tokenizer, "hf_id": hf_id, "task_id": task_id})
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
