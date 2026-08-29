"""
知识图谱微服务（端口 8001）— 契约基线实现
================================================
对应 Java 端 `com.aicourse.integration` 下游契约（权威单一事实来源见 docs/集成契约-知识图谱与RAG.md）。

职责边界（2026-08-13 用户明确）：知识图谱引擎（图谱构建、多跳推理、GraphRAG）
由团队另一成员负责。本文件提供「契约基线」：纯内存存储 + FastAPI，覆盖 §11.3
全部端点，并补齐 Java 端实际调用的 `/api/v1/graph/ingest` 与 `/api/v1/graph/subgraph`
（§11.3 文档未列，但 Java 端已接线），使 Java 端 `knowledge-graph.enabled=true`
时存在可对话的真实 Python 端，便于联调与契约测试。

启动：
    pip install -r requirements.txt
    uvicorn kg_service:app --host 0.0.0.0 --port 8001

说明：本基线使用内存字典存储，重启即清空，仅用于本地联调与契约验证；
生产环境应替换为持久化存储（Neo4j / NebulaGraph 等）与真实多跳推理引擎。
"""

from collections import deque
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="AI Course Knowledge Graph Service", version="1.0.0")

# ---------------- 内存存储（基线，重启清空） ----------------
_nodes: Dict[int, Dict[str, Any]] = {}
_relations: List[Dict[str, Any]] = []
_node_seq = 1
_rel_seq = 1


# ---------------- 请求体模型 ----------------
class NodeIn(BaseModel):
    course_id: int
    name: str
    content: str = ""
    type: str = "CONCEPT"


class RelationIn(BaseModel):
    source_id: int
    target_id: int
    relation_type: str


class PathPlanIn(BaseModel):
    source_id: int
    target_id: int
    max_hops: int = 2


class IngestIn(BaseModel):
    course_id: int


class SubGraphIn(BaseModel):
    course_id: int
    max_hops: int = 2


# ---------------- 端点 ----------------
@app.get("/api/v1/graph/health")
def health() -> Dict[str, str]:
    return {}


@app.get("/api/v1/graph/course/{course_id}")
def course_graph(course_id: int) -> Dict[str, Any]:
    ns = [n for n in _nodes.values() if n.get("course_id") == course_id]
    ids = {n["id"] for n in ns}
    es = [r for r in _relations if r["source_id"] in ids]
    return {"nodes": ns, "edges": es}


@app.get("/api/v1/graph/nodes/{node_id}")
def get_node(node_id: int) -> Dict[str, Any]:
    if node_id not in _nodes:
        raise HTTPException(status_code=404, detail="node not found")
    return _nodes[node_id]


@app.get("/api/v1/graph/nodes/{node_id}/relations")
def node_relations(node_id: int, depth: int = 1) -> Dict[str, Any]:
    rels = [
        {"node": {"id": r["target_id"]}, "relation_type": r["relation_type"], "depth": 1}
        for r in _relations
        if r["source_id"] == node_id
    ]
    return {"node": _nodes.get(node_id, {"id": node_id}), "relations": rels}


@app.post("/api/v1/graph/nodes")
def create_node(body: NodeIn) -> Dict[str, Any]:
    global _node_seq
    nid = _node_seq
    _node_seq += 1
    node = {
        "id": nid,
        "name": body.name,
        "type": body.type,
        "content": body.content,
        "course_id": body.course_id,
    }
    _nodes[nid] = node
    return {**node, "created_at": "2026-08-16T00:00:00"}


@app.post("/api/v1/graph/relations")
def create_relation(body: RelationIn) -> Dict[str, Any]:
    global _rel_seq
    rid = _rel_seq
    _rel_seq += 1
    rel = {
        "id": rid,
        "source_id": body.source_id,
        "target_id": body.target_id,
        "relation_type": body.relation_type,
    }
    _relations.append(rel)
    return rel


@app.post("/api/v1/graph/path/plan")
def path_plan(body: PathPlanIn) -> Dict[str, Any]:
    """最短跳数路径规划（无向 BFS，受 max_hops 限制）。

    返回结构与 §11.3 一致：{"path":[...], "edges":[...], "hops":n}。
    找不到路径时返回空 path，Java 端据此回退消费侧本地 BFS。
    """
    adj: Dict[int, List[int]] = {}
    for r in _relations:
        adj.setdefault(r["source_id"], []).append(r["target_id"])
        adj.setdefault(r["target_id"], []).append(r["source_id"])

    if body.source_id == body.target_id:
        return {"path": [body.source_id], "edges": [], "hops": 0}
    if body.source_id not in adj:
        return {"path": [], "edges": [], "hops": 0}

    prev: Dict[int, Optional[int]] = {body.source_id: None}
    q = deque([body.source_id])
    reached = False
    while q:
        cur = q.popleft()
        if cur == body.target_id:
            reached = True
            break
        depth = len(prev)  # 近似层级（仅用于 max_hops 粗筛）
        if depth > body.max_hops + 1:
            continue
        for nxt in adj.get(cur, []):
            if nxt not in prev:
                prev[nxt] = cur
                q.append(nxt)

    if not reached:
        return {"path": [], "edges": [], "hops": 0}

    # 回溯路径
    id_path: List[int] = []
    step: Optional[int] = body.target_id
    while step is not None:
        id_path.append(step)
        step = prev.get(step)
    id_path.reverse()

    # 收集相邻边
    edges = []
    for i in range(len(id_path) - 1):
        a, b = id_path[i], id_path[i + 1]
        for r in _relations:
            if (r["source_id"] == a and r["target_id"] == b) or (
                r["source_id"] == b and r["target_id"] == a
            ):
                edges.append(
                    {
                        "source_id": r["source_id"],
                        "target_id": r["target_id"],
                        "relation_type": r["relation_type"],
                    }
                )
                break

    return {"path": id_path, "edges": edges, "hops": max(len(id_path) - 1, 0)}


@app.post("/api/v1/graph/ingest")
def ingest(body: IngestIn) -> Dict[str, Any]:
    """图谱摄取（基线：确认消费侧已同步的知识点数量，不真正重算）。"""
    synced = sum(1 for n in _nodes.values() if n.get("course_id") == body.course_id)
    return {"status": "ok", "course_id": body.course_id, "synced_nodes": synced}


@app.post("/api/v1/graph/subgraph")
def subgraph(body: SubGraphIn) -> Dict[str, Any]:
    ns = [n for n in _nodes.values() if n.get("course_id") == body.course_id]
    ids = {n["id"] for n in ns}
    es = [r for r in _relations if r["source_id"] in ids]
    return {
        "status": "ok",
        "course_id": body.course_id,
        "max_hops": body.max_hops,
        "nodes": ns,
        "edges": es,
    }


@app.delete("/api/v1/graph/nodes/{node_id}")
def delete_node(node_id: int) -> Dict[str, bool]:
    if node_id in _nodes:
        del _nodes[node_id]
    return {"success": True}


@app.delete("/api/v1/graph/relations/{relation_id}")
def delete_relation(relation_id: int) -> Dict[str, bool]:
    global _relations
    _relations = [r for r in _relations if r["id"] != relation_id]
    return {"success": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
