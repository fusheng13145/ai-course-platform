"""
知识图谱服务（8001）契约冒烟测试
========================================
验证 docs/项目手册.md（第八部分）关键端点的契约正确性，与 Java 端
`KnowledgeGraphServiceImpl` / `HttpKnowledgeGraphProvider` 的下游调用对齐。
使用 FastAPI TestClient 内嵌运行，不依赖外部端口，CI 中稳定快速。

本测试守卫**契约（端点路径 + JSON 形状）**；演进存储后端（kg_store.py：
Neo4j 真实引擎 / 内存基线）时须保持这些端点与响应结构不变。

本地运行：
    pip install -r requirements.txt pytest
    pytest -q contract_test.py
"""

import os

import pytest
from fastapi.testclient import TestClient

import kg_store
from kg_service import app


@pytest.fixture(autouse=True)
def memory_backend(monkeypatch):
    """契约用例强制内存后端并逐例重置，保证 CI/本地结果确定且不触碰 Neo4j。"""
    monkeypatch.setenv("KG_BACKEND", "memory")
    kg_store._store = None
    kg_store.get_store().reset()
    yield
    kg_store._store = None


def _neo4j_store_or_skip():
    """返回可连通的 Neo4jStore；驱动缺失/服务不可达时跳过（CI 恒跳过）。"""
    if kg_store._GraphDatabase is None:
        pytest.skip("neo4j 驱动未安装，跳过真实图引擎用例")
    try:
        store = kg_store.Neo4jStore(
            kg_store._uri() or "bolt://127.0.0.1:7687",
            os.getenv("NEO4J_USERNAME", "neo4j"), os.getenv("NEO4J_PASSWORD", ""))
        store.get_node(-1)  # 触发一次真实查询验证连通
    except Exception as e:
        pytest.skip(f"本地 Neo4j 不可用：{e}")
    return store


def test_health():
    with TestClient(app) as c:
        resp = c.get("/api/v1/graph/health")
        assert resp.status_code == 200


def test_node_create_and_fetch():
    with TestClient(app) as c:
        created = c.post(
            "/api/v1/graph/nodes",
            json={"course_id": 1, "name": "线性回归", "content": "最小二乘", "type": "CONCEPT"},
        )
        assert created.status_code == 200
        nid = created.json()["id"]

        fetched = c.get(f"/api/v1/graph/nodes/{nid}")
        assert fetched.status_code == 200
        assert fetched.json()["name"] == "线性回归"


def test_relation_create_and_node_relations():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 1, "name": "A"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 1, "name": "B"}).json()
        rel = c.post(
            "/api/v1/graph/relations",
            json={"source_id": a["id"], "target_id": b["id"], "relation_type": "PREREQUISITE"},
        )
        assert rel.status_code == 200

        rels = c.get(f"/api/v1/graph/nodes/{a['id']}/relations")
        assert rels.status_code == 200
        assert any(r["relation_type"] == "PREREQUISITE" for r in rels.json()["relations"])


def test_path_plan_shortest_hops():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 2, "name": "X"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 2, "name": "Y"}).json()
        c.post(
            "/api/v1/graph/relations",
            json={"source_id": a["id"], "target_id": b["id"], "relation_type": "PREREQUISITE"},
        )
        plan = c.post(
            "/api/v1/graph/path/plan",
            json={"source_id": a["id"], "target_id": b["id"], "max_hops": 3},
        )
        assert plan.status_code == 200
        body = plan.json()
        assert body["path"] == [a["id"], b["id"]]
        assert body["hops"] == 1


def test_ingest_and_subgraph():
    with TestClient(app) as c:
        c.post("/api/v1/graph/nodes", json={"course_id": 3, "name": "N1"})
        c.post("/api/v1/graph/nodes", json={"course_id": 3, "name": "N2"})

        ing = c.post("/api/v1/graph/ingest", json={"course_id": 3})
        assert ing.status_code == 200
        assert ing.json()["status"] == "ok"

        sub = c.post("/api/v1/graph/subgraph", json={"course_id": 3, "max_hops": 2})
        assert sub.status_code == 200
        body = sub.json()
        assert body["status"] == "ok"
        assert len(body["nodes"]) == 2


def test_course_graph():
    with TestClient(app) as c:
        c.post("/api/v1/graph/nodes", json={"course_id": 4, "name": "C1"})
        c.post("/api/v1/graph/nodes", json={"course_id": 4, "name": "C2"})

        resp = c.get("/api/v1/graph/course/4")
        assert resp.status_code == 200
        assert len(resp.json()["nodes"]) == 2


# ------------------------------------------------------------------
# 真实 Neo4j 引擎验证（本地已启动 Neo4j 时执行；CI 无驱动自动跳过）
# 仅操作 :Kp 标签并自行清理，不影响 Neo4j 库内其他图数据
# ------------------------------------------------------------------
def test_neo4j_store_crud_relations_and_path():
    store = _neo4j_store_or_skip()
    created = []
    try:
        a = store.create_node(999, "OS-进程", "进程是资源分配单位", "CONCEPT")
        b = store.create_node(999, "OS-线程", "线程是调度单位", "CONCEPT")
        d = store.create_node(999, "OS-调度", "CPU 调度", "CONCEPT")
        created = [a["id"], b["id"], d["id"]]
        store.create_relation(a["id"], b["id"], "PREREQUISITE")
        store.create_relation(b["id"], d["id"], "PREREQUISITE")

        assert store.get_node(a["id"])["name"] == "OS-进程"
        rels = store.relations_of(a["id"], depth=2)
        assert {r["node"]["id"] for r in rels} == {b["id"], d["id"]}
        assert any(r["depth"] == 2 for r in rels)

        plan = store.path_plan(a["id"], d["id"], max_hops=3)
        assert plan["path"] == [a["id"], b["id"], d["id"]]
        assert plan["hops"] == 2
        assert len(plan["edges"]) == 2

        ns = store.course_nodes(999)
        assert {n["id"] for n in ns} == set(created)
        es = store.edges_from({n["id"] for n in ns})
        assert len(es) == 2

        # 与内存后端的路径规划结果一致（双实现等价性）
        mem = kg_store.MemoryStore()
        ma = mem.create_node(999, "A", "", "CONCEPT")
        mb = mem.create_node(999, "B", "", "CONCEPT")
        md = mem.create_node(999, "D", "", "CONCEPT")
        mem.create_relation(ma["id"], mb["id"], "PREREQUISITE")
        mem.create_relation(mb["id"], md["id"], "PREREQUISITE")
        mp = mem.path_plan(ma["id"], md["id"], 3)
        assert mp["hops"] == plan["hops"] == 2

        for nid in created:
            assert store.delete_node(nid) is True
        assert store.course_nodes(999) == []
    finally:
        for nid in created:
            store.delete_node(nid)
        store.close()
