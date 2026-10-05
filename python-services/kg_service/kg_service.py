"""
知识图谱微服务（端口 8001）
================================================
对应 Java 端 `com.aicourse.integration` 下游契约（权威单一事实来源见 docs/项目手册.md 第八部分）。
Java 端 `knowledge-graph.enabled=true` 时经 `HttpKnowledgeGraphProvider` 调用本服务的
`/api/v1/graph/*` 契约（path/plan、ingest、subgraph），端点路径与响应字段不得破坏
`test_kg_contract.py` 护栏。

存储后端见 `kg_store.py`：
- Neo4j 真实引擎（:Kp 节点 + [:RELATES] 关系，Cypher shortestPath 多跳路径规划），
  通过 KG_BACKEND=neo4j 或 auto 模式（配置 NEO4J_URI 且可连通时自动启用）开启；
- 内存基线（CI / Neo4j 不可达时），重启清空，行为与历史契约基线一致。

启动（Neo4j 模式示例）：
    pip install -r requirements.txt
    KG_BACKEND=neo4j NEO4J_URI=bolt://localhost:7687 uvicorn kg_service:app --host 0.0.0.0 --port 8001
"""

from typing import Any, Dict, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

try:
    import kg_store
except ImportError:  # 以 python-services 为工作目录、按子包方式启动时
    from . import kg_store

app = FastAPI(title="AI Course Knowledge Graph Service", version="1.1.0")


# ---------------- 请求体模型 ----------------
class NodeIn(BaseModel):
    course_id: int = Field(ge=1)
    name: str = Field(min_length=1, max_length=255)
    content: str = ""
    type: str = "CONCEPT"
    # 可选显式 ID：用于与 Java 侧 MySQL 知识点 ID 对齐的图同步；缺省时服务端自增
    id: Optional[int] = Field(default=None, ge=1)


class RelationIn(BaseModel):
    source_id: int = Field(ge=1)
    target_id: int = Field(ge=1)
    relation_type: str = Field(min_length=1, max_length=255)


class PathPlanIn(BaseModel):
    source_id: int = Field(ge=1)
    target_id: int = Field(ge=1)
    max_hops: int = Field(2, ge=1, le=5)


class IngestIn(BaseModel):
    course_id: int = Field(ge=1)


class SubGraphIn(BaseModel):
    course_id: int = Field(ge=1)
    max_hops: int = Field(2, ge=1, le=5)


# ---------------- 端点 ----------------
@app.get("/api/v1/graph/health")
def health() -> Dict[str, Any]:
    return {"status": "ok", "backend": kg_store.backend_name()}


@app.get("/api/v1/graph/course/{course_id}")
def course_graph(course_id: int) -> Dict[str, Any]:
    store = kg_store.get_store()
    ns = store.course_nodes(course_id)
    ids = {n["id"] for n in ns}
    es = store.edges_from(ids)
    return {"nodes": ns, "edges": es}


@app.get("/api/v1/graph/nodes/{node_id}")
def get_node(node_id: int) -> Dict[str, Any]:
    node = kg_store.get_store().get_node(node_id)
    if node is None:
        raise HTTPException(status_code=404, detail="node not found")
    return node


@app.get("/api/v1/graph/nodes/{node_id}/relations")
def node_relations(node_id: int, depth: int = Query(1, ge=1, le=5)) -> Dict[str, Any]:
    store = kg_store.get_store()
    return {"node": store.get_node(node_id) or {"id": node_id},
            "relations": store.relations_of(node_id, depth)}


@app.post("/api/v1/graph/nodes")
def create_node(body: NodeIn) -> Dict[str, Any]:
    return kg_store.get_store().create_node(
        body.course_id, body.name, body.content, body.type, body.id)


@app.post("/api/v1/graph/relations")
def create_relation(body: RelationIn) -> Dict[str, Any]:
    return kg_store.get_store().create_relation(
        body.source_id, body.target_id, body.relation_type)


@app.post("/api/v1/graph/path/plan")
def path_plan(body: PathPlanIn) -> Dict[str, Any]:
    """最短跳数路径规划（无向，受 max_hops 限制）。

    返回结构与 §11.3 一致：{"path":[...], "edges":[...], "hops":n}。
    找不到路径时返回空 path，Java 端据此回退消费侧本地 BFS。
    """
    return kg_store.get_store().path_plan(body.source_id, body.target_id, body.max_hops)


@app.post("/api/v1/graph/ingest")
def ingest(body: IngestIn) -> Dict[str, Any]:
    """图谱摄取：返回该课程已同步进图引擎的知识点数量。"""
    synced = len(kg_store.get_store().course_nodes(body.course_id))
    return {"status": "ok", "course_id": body.course_id, "synced_nodes": synced}


@app.post("/api/v1/graph/subgraph")
def subgraph(body: SubGraphIn) -> Dict[str, Any]:
    store = kg_store.get_store()
    ns = store.course_nodes(body.course_id)
    ids = {n["id"] for n in ns}
    es = store.edges_from(ids)
    return {
        "status": "ok",
        "course_id": body.course_id,
        "max_hops": body.max_hops,
        "nodes": ns,
        "edges": es,
    }


@app.delete("/api/v1/graph/nodes/{node_id}")
def delete_node(node_id: int) -> Dict[str, bool]:
    kg_store.get_store().delete_node(node_id)
    return {"success": True}


@app.delete("/api/v1/graph/relations/{relation_id}")
def delete_relation(relation_id: int) -> Dict[str, bool]:
    kg_store.get_store().delete_relation(relation_id)
    return {"success": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
