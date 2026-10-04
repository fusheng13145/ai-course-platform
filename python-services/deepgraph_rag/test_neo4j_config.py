"""
Neo4j 凭据/连接回归测试（缺陷5）
================================
固化两条修复：
1. `config.py` 不再内置任何默认 Neo4j 密码，缺失时给出明确配置错误，且错误信息不泄露口令；
2. `neo4j_handler.Neo4jGraphManager` 改为惰性建连——**导入模块 / 构造对象都不连接**，
   首次使用 driver 时才建立，`close()` 释放并允许后续重建。

运行（依赖已装的 neo4j/networkx/streamlit；无需真实 Neo4j）：
    cd deepgraph_rag && python -m pytest -q test_neo4j_config.py
"""
from __future__ import annotations

import pytest

import config
import neo4j_handler
from neo4j_handler import Neo4jGraphManager


def test_no_hardcoded_default_password_in_source():
    src = open(config.__file__, encoding="utf-8").read()
    assert "123456Zyj" not in src                 # 旧硬编码口令已移除
    assert 'NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "")' in src  # 无默认值


def test_require_password_missing_raises_clear_error(monkeypatch):
    monkeypatch.setenv("NEO4J_PASSWORD", "")
    with pytest.raises(RuntimeError) as exc:
        config.require_neo4j_password()
    msg = str(exc.value)
    assert "NEO4J_PASSWORD" in msg                # 指明需配置哪个变量
    assert "123456" not in msg                    # 不回显任何口令


def test_require_password_returns_configured_value(monkeypatch):
    monkeypatch.setenv("NEO4J_PASSWORD", "from-env")
    assert config.require_neo4j_password() == "from-env"


def test_manager_construction_does_not_connect():
    # 惰性：构造对象不应触碰驱动/网络（Neo4j 未启动也不会抛错）
    m = Neo4jGraphManager()
    assert m._driver is None


def test_module_import_creates_global_without_connecting():
    # 模块级单例 neo4j_db 在导入时只创建对象，不建立连接（旧代码在此直接建连）
    assert isinstance(neo4j_handler.neo4j_db, Neo4jGraphManager)
    assert neo4j_handler.neo4j_db._driver is None


def test_driver_property_lazy_build_once_and_close_releases(monkeypatch):
    built = []

    class FakeDriver:
        def __init__(self, uri, auth):
            self.uri, self.auth, self.closed = uri, auth, False

        def close(self):
            self.closed = True

    class FakeGraphDatabase:
        @staticmethod
        def driver(uri, auth=None):
            d = FakeDriver(uri, auth)
            built.append(d)
            return d

    monkeypatch.setattr(neo4j_handler, "GraphDatabase", FakeGraphDatabase)
    monkeypatch.setattr(neo4j_handler, "require_neo4j_password", lambda: "s3cret")

    m = Neo4jGraphManager()
    assert built == []                             # 构造未建连

    drv = m.driver                                 # 首次访问才建连
    assert len(built) == 1
    assert drv.auth == (neo4j_handler.NEO4J_USERNAME, "s3cret")
    assert m.driver is drv                         # 复用同一连接，不重复建
    assert len(built) == 1

    m.close()
    assert drv.closed is True                      # 连接被释放
    assert m._driver is None

    _ = m.driver                                   # 关闭后再用会重新建连
    assert len(built) == 2
