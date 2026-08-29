"""
知识图谱基线服务（8001）契约冒烟测试
========================================
验证 docs/集成契约-知识图谱与RAG.md 关键端点的契约正确性，与 Java 端
`KnowledgeGraphServiceImpl` 的下游调用对齐。使用 FastAPI TestClient
内嵌运行，不依赖外部端口，CI 中稳定快速。

本地运行：
    pip install -r requirements.txt pytest
    pytest -q contract_test.py
"""

from fastapi.testclient import TestClient

from kg_service import app


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
