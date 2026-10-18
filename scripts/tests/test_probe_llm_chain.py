"""DR-86：`probe-llm-chain.py` 的目标端口必须可配。

坑的来源：`PROBE_PORT` 写死 8081（那是探针自带 `backend` 模式起第二只 jar 的端口），
而 `start-all.py` 起的后端在 **8080**。用一键启动的姿态跑 `chains` 时，探针连的是
8081 → `WinError 10061 连接被拒绝` → 打印"请先跑 backend"，于是四条真实链路永远验不到，
现象与"后端没起来"完全同形。判据：默认值必须逐字保持 8081（自带模式不变），
但 `PROBE_PORT` 环境变量必须能覆盖它。
"""
import importlib.util
import os
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "probe-llm-chain.py"


def load_probe():
    spec = importlib.util.spec_from_file_location("probe_llm_chain", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_probe_port_defaults_to_8081_when_env_absent():
    env = {k: v for k, v in os.environ.items() if k != "PROBE_PORT"}
    with mock.patch.dict(os.environ, env, clear=True):
        assert load_probe().PROBE_PORT == "8081"


def test_probe_port_honours_env_override():
    with mock.patch.dict(os.environ, {"PROBE_PORT": "8080"}, clear=True):
        module = load_probe()
        assert module.PROBE_PORT == "8080"
        # 端口必须真的进入请求基址，否则覆盖只是装饰
        assert module.base_url() == "http://127.0.0.1:8080/api"
