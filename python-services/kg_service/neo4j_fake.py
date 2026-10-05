"""
Neo4j 假驱动（测试专用，非 pytest 用例）
==========================================
在不连接真实 Neo4j 的前提下驱动 `kg_store.Neo4jStore`：记录每条 Cypher 与其参数，
返回脚本化的结果，并统计会话/连接生命周期，用于断言**参数化正确**（用户 id 绝不
拼进 Cypher 字符串）与**连接被释放**。test_kg_contract.py 与 test_kg_store.py 共用。
"""
from __future__ import annotations

from typing import Any, Callable, List, Optional


class FakeResult:
    """模拟 neo4j Result：single() 返回脚本 dict/None，迭代返回 dict 列表。"""

    def __init__(self, single: Any, rows: Optional[list] = None) -> None:
        self._single = single
        self._rows = rows or []

    def single(self):
        return self._single

    def __iter__(self):
        return iter(self._rows)


class FakeTx:
    def __init__(self, driver: "FakeDriver") -> None:
        self._driver = driver

    def run(self, query: str, **params) -> FakeResult:
        return self._driver._execute(query, params)

    def commit(self) -> None:
        self._driver.commits += 1


class FakeSession:
    def __init__(self, driver: "FakeDriver") -> None:
        self._driver = driver

    def __enter__(self):
        self._driver.sessions_opened += 1
        return self

    def __exit__(self, *exc):
        self._driver.sessions_closed += 1
        return False

    def run(self, query: str, **params) -> FakeResult:
        return self._driver._execute(query, params)

    def begin_transaction(self) -> FakeTx:
        return FakeTx(self._driver)


class FakeDriver:
    """最小可用的 Neo4j 驱动替身。

    respond: 可选 (query, params)->FakeResult 的脚本函数；缺省返回空结果。
    calls:   [(query, params), ...] 依调用顺序记录，供断言参数化。
    """

    def __init__(self, respond: Optional[Callable[[str, dict], FakeResult]] = None) -> None:
        self.calls: List[Any] = []
        self.commits = 0
        self.sessions_opened = 0
        self.sessions_closed = 0
        self.closed = False
        self._respond = respond

    def session(self) -> FakeSession:
        return FakeSession(self)

    def close(self) -> None:
        self.closed = True

    def queries_containing(self, needle: str) -> list:
        """返回 query 文本包含 needle 的所有调用的 params 列表。"""
        return [p for q, p in self.calls if needle in q]

    def _execute(self, query: str, params: dict) -> FakeResult:
        self.calls.append((query, params))
        if self._respond is not None:
            return self._respond(query, params)
        return FakeResult(None)


class FakeGraphDatabase:
    """替身 _GraphDatabase.driver(...)，记录 auth/uri 并复用同一 FakeDriver。"""

    def __init__(self, driver: FakeDriver) -> None:
        self._driver = driver
        self.last_uri = None
        self.last_auth = None

    def driver(self, uri, auth=None):
        self.last_uri = uri
        self.last_auth = auth
        return self._driver
