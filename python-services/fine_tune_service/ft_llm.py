"""
微调服务的生成 provider 开关（与 RAG 侧 `AI_PROVIDER` 同口径）
=================================================================
两种生成路径互不覆盖，由 `FT_LLM_PROVIDER` 决定当前生效的那一种：

- `engine`（默认）：`lora_engine.infer` —— 基座 + LoRA adapter 的真实推理（`mode=ENGINE`）。
  现有 adapter 打在 Qwen2.5-0.5B 上，换基座会维度不匹配，只能重新训练；因此这是
  「微调结果」的唯一真实载体，不可被替换。
- `ollama`：本机 Ollama（默认 `deepseek-r1:8b`，与 RAG 共用 `OLLAMA_*` 配置），
  `mode=OLLAMA`。用于「要更好的输出质量」但不要求 LoRA 权重的场景。

任何非法取值都不静默回落：`/health` 如实报告 `provider`，`/infer` 返回
`mode=UNAVAILABLE, code=503` 并说明非法值，避免把「配置错了」伪装成「已切换」。

调用口径与 `rag_service/rag_engine.py::_call_ollama` 保持一致：`/api/chat`、
`message.content` 才是正式答案，思考模型的 `message.thinking` 只用于空值诊断、
不拼接进答案；`OLLAMA_THINK` 仅接受 true/false。只有标准库（urllib），不新增依赖。
"""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

PROVIDER_ENV = "FT_LLM_PROVIDER"
PROVIDER_DEFAULT = "engine"
PROVIDERS = ("engine", "ollama")


def provider() -> str:
    """当前配置值（原样小写返回，合法性由调用方判定，不做静默兜底）。"""
    return (os.getenv(PROVIDER_ENV) or PROVIDER_DEFAULT).strip().lower()


def provider_valid() -> bool:
    return provider() in PROVIDERS


def ollama_base_url() -> str:
    return (os.getenv("FT_OLLAMA_BASE_URL") or os.getenv("OLLAMA_BASE_URL")
            or "http://localhost:11434").strip().rstrip("/")


def ollama_model() -> str:
    return (os.getenv("FT_OLLAMA_MODEL") or os.getenv("OLLAMA_MODEL")
            or "deepseek-r1:8b").strip()


def _timeout() -> float:
    try:
        return float(os.getenv("FT_OLLAMA_TIMEOUT", "180"))
    except ValueError:
        return 180.0


def _trust_env_for(base_url: str) -> bool:
    """本机回环地址不走系统代理（与 RAG 的 trust_env 口径一致）。"""
    host = base_url.split("://", 1)[-1].split("/", 1)[0].split(":")[0].lower()
    return host not in ("localhost", "127.0.0.1", "::1")


def _opener(base_url: str):
    handlers = [] if _trust_env_for(base_url) else [urllib.request.ProxyHandler({})]
    return urllib.request.build_opener(*handlers)


def _get_json(url: str, timeout: float) -> dict:
    with _opener(url).open(urllib.request.Request(url, headers={"Accept": "application/json"}),
                           timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def _post_json(url: str, payload: dict, timeout: float) -> dict:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST",
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          "Accept": "application/json"})
    with _opener(url).open(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def _fail(model: str, error: str) -> dict:
    return {"answer": None, "tokens_used": 0, "model": model, "provider": "ollama",
            "mode": "UNAVAILABLE", "code": 503, "error": error}


def ollama_health(timeout: float = 3.0) -> dict:
    """轻量探活：`GET /api/tags`。不可达时如实返回 available=false 与错误摘要。"""
    base = ollama_base_url()
    model = ollama_model()
    detail = {"base_url": base, "model": model}
    try:
        tags = _get_json(base + "/api/tags", timeout)
        names = [m.get("name") for m in (tags.get("models") or []) if isinstance(m, dict)]
        detail.update({"available": True, "installed": names,
                       "model_installed": model in names})
    except Exception as exc:  # 网络/超时/非 200 都归为不可用，不伪造成功
        detail.update({"available": False, "error": f"{type(exc).__name__}: {exc}"})
    return detail


def generate(prompt: str, params: dict) -> dict:
    """走本机 Ollama `/api/chat` 生成。失败返回契约降级体（mode=UNAVAILABLE、code=503）。"""
    model = ollama_model()
    base = ollama_base_url()
    options = {}
    try:
        if params.get("temperature") is not None:
            options["temperature"] = float(params["temperature"])
        if params.get("max_tokens") is not None:
            # 只在调用方显式给上限时才下发；思考模型可能把预算全花在 thinking 上
            options["num_predict"] = int(params["max_tokens"])
    except (TypeError, ValueError) as exc:
        return _fail(model, f"params 非法：{exc}")

    payload = {"model": model, "stream": False,
               "messages": [{"role": "user", "content": prompt}]}
    if options:
        payload["options"] = options
    think = (os.getenv("OLLAMA_THINK") or "").strip().lower()
    if think in ("true", "false"):
        payload["think"] = think == "true"
    try:
        raw = _post_json(base + "/api/chat", payload, _timeout())
    except urllib.error.HTTPError as exc:
        body = ""
        try:
            body = exc.read().decode("utf-8", errors="replace")[:300]
        except Exception:
            pass
        return _fail(model, f"Ollama HTTP {exc.code}：{body or exc.reason}")
    except Exception as exc:
        return _fail(model, f"Ollama 调用失败（{base}）：{type(exc).__name__}: {exc}")

    message = raw.get("message") or {}
    answer = (message.get("content") or "").strip()
    thinking = message.get("thinking") or ""
    tokens = int(raw.get("eval_count") or 0) + int(raw.get("prompt_eval_count") or 0)
    if not answer:
        return _fail(model, f"Ollama 返回空答案（thinking {len(thinking)} 字符，"
                            f"可能被 num_predict 截断或模型未产出正式内容）")
    return {"answer": answer, "tokens_used": tokens, "model": model, "provider": "ollama",
            "mode": "OLLAMA", "code": 200,
            "elapsed_ms": int((raw.get("total_duration") or 0) / 1_000_000)}
