"""引擎集线器（Engine Hub）：把 KG(8001) / DeepGraph+知识库(8501) / LoRA 微调(8003)
三套 Python 引擎合并为**一个进程、一个端口**（默认 8600）。

设计要点（为什么不是 mount / 不是路由平铺）：

1. **按原始 path 前缀分发，不剥前缀。** 三套引擎的路由本身就是全路径
   （`/api/v1/graph/*`、`/api/v1/deepgraph/*`、`/api/v1/rag/*`、`/api/v1/fine-tune/*`），
   互相不重叠。因此分发器把原始 `scope` 原样交给对应子 app，路径一个字符都不变——
   Java 侧 `KG_BASE_URL` / `RAG_BASE_URL` / `DG_RAG_BASE_URL` / `FT_BASE_URL`
   只改 host:port，**契约与客户端代码零改动**。
   若改用 Starlette `Mount(prefix, app)`，Mount 会剥掉前缀，子 app 里写死的
   `/api/v1/graph/health` 会变成 `/health` 而 404，故不采用。

2. **直接调用子 app 的 ASGI 可调用对象，而不是复制 Route。** 复制 Route 会丢掉各子 app
   自己的 `@app.middleware("http")`（内部认证 `X-Internal-Key`）与 exception_handler
   （kg 的 503 降级体、deepgraph 的 422/HTTP/未捕获三件套、ft 的存储损坏降级体）。
   直接 ASGI 调用等于把它们整套语义原样带过来——合并只发生在「进程与端口」这一层。

3. **lifespan 组合。** 三个子 app 的启动/关闭钩子按顺序进入同一个 AsyncExitStack，
   关闭时逆序退出；任一子 app 抛错不影响其它（各自异常在进入时被记录并继续）。

4. **模块平铺安全。** 三个服务目录下 24 个 .py 无同名文件（已扫描），因此把三个目录
   依次加入 `sys.path` 后按模块名 import 不会互相覆盖。

用法：
    python -m uvicorn hub:app --host 0.0.0.0 --port 8600     # 在 python-services/engine_hub 下
    ENGINE_HUB_PORT=8600 python engine_hub/hub.py             # 本机裸跑
"""

from __future__ import annotations

import json
import os
import sys
import traceback
from contextlib import AsyncExitStack
from pathlib import Path
from typing import Any, Callable, Dict, List, Tuple

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent  # python-services/

# 三个引擎目录依次入 sys.path（顺序即优先级；已确认无同名模块）
_ENGINE_DIRS = ("kg_service", "deepgraph_rag", "fine_tune_service")
for _d in _ENGINE_DIRS:
    _path = str(ROOT / _d)
    if _path not in sys.path:
        sys.path.insert(0, _path)

DEFAULT_PORT = int(os.getenv("ENGINE_HUB_PORT", "8600"))

# 能力开关（`.env` 的 KG_ENABLED / DG_RAG_ENABLED / FT_ENABLED=false 由此下发）：
# 被跳过 = 进程内不 import 该引擎（省 torch 与模型内存），其前缀返回 404 并如实标注 skipped。
# **故意不提供「静默不加载」以外的退化**：跳过的能力在 /health 里标 ready=false + skipped=true，
# 不会被看成「已合并但健康」。
_SKIP = {
    name.strip().lower()
    for name in (os.getenv("ENGINE_HUB_SKIP", "") or "").split(",")
    if name.strip()
}

# 前缀 -> (能力名, 模块名)；模块名用于 import 后取 `app`
_ROUTES: Tuple[Tuple[str, str, str], ...] = (
    ("/api/v1/graph", "kg", "kg_service"),
    ("/api/v1/deepgraph", "deepgraph", "api_server"),
    ("/api/v1/rag", "deepgraph", "api_server"),   # Q-03 收敛：知识库 9 端点已并入 8501
    ("/api/v1/fine-tune", "ft", "lora_service"),
)

_LOAD_ERRORS: Dict[str, str] = {}


def _load_app(module_name: str) -> Any:
    """按模块名导入并取其模块级 `app`；失败只登记，不阻塞其它引擎。"""
    try:
        module = __import__(module_name)
        return getattr(module, "app")
    except Exception as exc:  # noqa: BLE001 - 引擎缺失按能力降级，而非整栈起不来
        detail = f"{type(exc).__name__}: {exc}"
        _LOAD_ERRORS[module_name] = detail
        print(f"[engine-hub] 引擎 {module_name} 加载失败（该能力降级）：{detail}")
        return None


# 同一模块（api_server / lora_service）只加载一次，供多处前缀复用
_APPS_BY_MODULE: Dict[str, Any] = {}
for _prefix, _capability, _module in _ROUTES:
    if _capability in _SKIP:
        continue
    if _module not in _APPS_BY_MODULE:
        _APPS_BY_MODULE[_module] = _load_app(_module)

# 真正用于分发的表：前缀 -> 能力名 / ASGI 可调用对象
_DISPATCH: List[Tuple[str, str, Any]] = []
for _prefix, _capability, _module in _ROUTES:
    if _capability in _SKIP:
        continue
    _app = _APPS_BY_MODULE.get(_module)
    if _app is not None:
        _DISPATCH.append((_prefix, _capability, _app))


def _hub_health() -> Dict[str, Any]:
    services = []
    for capability in ("kg", "deepgraph", "rag", "ft"):
        modules = [m for _, c, m in _ROUTES if c == capability]
        skipped = capability in _SKIP
        ready = (not skipped) and all(_APPS_BY_MODULE.get(m) is not None for m in modules)
        services.append({
            "capability": capability,
            "ready": ready,
            "skipped": skipped,
            "error": next((_LOAD_ERRORS[m] for m in modules if m in _LOAD_ERRORS), None),
        })
    all_ready = all(s["ready"] for s in services if not s["skipped"])
    return {
        "code": 200,
        "mode": "ENGINE_HUB",
        "status": "ok" if all_ready else "degraded",
        "port": DEFAULT_PORT,
        "services": services,
    }


async def _send_json(send: Callable, status: int, payload: Dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    await send({
        "type": "http.response.start",
        "status": status,
        "headers": [(b"content-type", b"application/json; charset=utf-8"),
                    (b"content-length", str(len(body)).encode("ascii"))],
    })
    await send({"type": "http.response.body", "body": body})


class EngineHub:
    """ASGI 分发器：`/health` 自答，其余按前缀原样转给子 app。"""

    def __init__(self, dispatch: List[Tuple[str, str, Any]]) -> None:
        self._dispatch = dispatch

    async def _lifespan(self, receive: Callable, send: Callable) -> None:
        async with AsyncExitStack() as stack:
            for _, _, sub in self._dispatch:
                context = getattr(getattr(sub, "router", None), "lifespan_context", None)
                if context is None:
                    continue
                try:
                    await stack.enter_async_context(context(sub))
                except Exception as exc:  # noqa: BLE001
                    print(f"[engine-hub] 子引擎 lifespan 进入失败：{exc}")
            while True:
                message = await receive()
                if message["type"] == "lifespan.startup":
                    await send({"type": "lifespan.startup.complete"})
                elif message["type"] == "lifespan.shutdown":
                    await send({"type": "lifespan.shutdown.complete"})
                    return

    async def __call__(self, scope: Dict[str, Any], receive: Callable, send: Callable) -> None:
        if scope["type"] == "lifespan":
            await self._lifespan(receive, send)
            return

        if scope["type"] != "http":
            await _send_json(send, 404, {"code": 404, "message": "engine-hub 仅承载 HTTP 能力"})
            return

        path = scope.get("path") or "/"
        if path in ("/health", "/api/v1/hub/health"):
            await _send_json(send, 200, _hub_health())
            return

        for prefix, _, sub in self._dispatch:
            if path == prefix or path.startswith(prefix + "/"):
                # 关键：path 不剥前缀，原样交给子 app（子 app 的 Route 是全路径）
                await sub(scope, receive, send)
                return

        await _send_json(send, 404, {
            "code": 404,
            "message": "engine-hub 无此前缀能力",
            "prefixes": [p for p, _, _ in self._dispatch],
        })


app = EngineHub(_DISPATCH)


def main() -> None:
    import uvicorn

    print(f"[engine-hub] 端口 {DEFAULT_PORT}；已挂载前缀：{[p for p, _, _ in _DISPATCH]}")
    for module, detail in _LOAD_ERRORS.items():
        print(f"[engine-hub] 降级：{module} -> {detail}")
    uvicorn.run(app, host=os.getenv("ENGINE_HUB_HOST", "0.0.0.0"), port=DEFAULT_PORT, log_level="info")


if __name__ == "__main__":
    try:
        main()
    except Exception:  # pragma: no cover
        traceback.print_exc()
        sys.exit(1)
