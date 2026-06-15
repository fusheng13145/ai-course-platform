"""
AI智能课程平台 - Python知识图谱与RAG服务
FastAPI入口文件
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List, Dict
import json

app = FastAPI(title="AI课程平台 Python服务", version="1.0.0")

# ==================== 知识图谱模拟数据 ====================
graph_nodes = {
    1: {"node_id": 1, "name": "机器学习", "content": "机器学习是人工智能的核心分支...", "type": "concept", "course_id": 1},
    2: {"node_id": 2, "name": "深度学习", "content": "深度学习使用多层神经网络...", "type": "concept", "course_id": 1},
    3: {"node_id": 3, "name": "神经网络", "content": "神经网络由神经元组成...", "type": "concept", "course_id": 1},
    4: {"node_id": 4, "name": "监督学习", "content": "监督学习需要标注数据...", "type": "concept", "course_id": 1},
    5: {"node_id": 5, "name": "非监督学习", "content": "非监督学习无需标注...", "type": "concept", "course_id": 1},
}

graph_relations = {
    1: {"relation_id": 1, "source_id": 1, "target_id": 2, "relation_type": "HAS_SUBFIELD"},
    2: {"relation_id": 2, "source_id": 2, "target_id": 3, "relation_type": "USES"},
    3: {"relation_id": 3, "source_id": 1, "target_id": 4, "relation_type": "HAS_METHOD"},
    4: {"relation_id": 4, "source_id": 1, "target_id": 5, "relation_type": "HAS_METHOD"},
}

# ==================== RAG模拟数据 ====================
documents = {
    1: {"document_id": 1, "course_id": 1, "document_name": "AI基础教程", "content": "人工智能（AI）是计算机科学的一个分支...深度学习是机器学习的子领域..."},
    2: {"document_id": 2, "course_id": 1, "document_name": "神经网络导论", "content": "神经网络是深度学习的基础...前向传播和反向传播是训练的核心..."},
}

# ==================== 知识图谱接口 ====================

class NodeCreate(BaseModel):
    course_id: int
    name: str
    content: Optional[str] = ""
    type: Optional[str] = "concept"

class RelationCreate(BaseModel):
    source_id: int
    target_id: int
    relation_type: Optional[str] = "RELATED_TO"

@app.get("/api/v1/graph/course/{course_id}", summary="获取课程知识图谱")
def get_graph_by_course(course_id: int):
    course_nodes = [n for n in graph_nodes.values() if n["course_id"] == course_id]
    course_relations = [r for r in graph_relations.values() 
                        if r["source_id"] in [n["node_id"] for n in course_nodes] 
                        or r["target_id"] in [n["node_id"] for n in course_nodes]]
    
    return {
        "success": True,
        "course_id": course_id,
        "nodes": course_nodes,
        "relations": course_relations
    }

@app.get("/api/v1/graph/nodes/{node_id}", summary="获取节点详情")
def get_node_detail(node_id: int):
    node = graph_nodes.get(node_id)
    if not node:
        raise HTTPException(status_code=404, detail="节点不存在")
    return {"success": True, "node": node}

@app.get("/api/v1/graph/nodes/{node_id}/relations", summary="获取节点关联关系")
def get_node_relations(node_id: int, depth: int = 2):
    if node_id not in graph_nodes:
        raise HTTPException(status_code=404, detail="节点不存在")
    
    related = []
    for rel in graph_relations.values():
        if rel["source_id"] == node_id or rel["target_id"] == node_id:
            related.append(rel)
    
    return {"success": True, "node_id": node_id, "relations": related, "depth": depth}

@app.post("/api/v1/graph/nodes", summary="添加知识节点")
def add_node(node: NodeCreate):
    new_id = max(graph_nodes.keys()) + 1 if graph_nodes else 1
    new_node = {
        "node_id": new_id,
        "course_id": node.course_id,
        "name": node.name,
        "content": node.content or "",
        "type": node.type or "concept"
    }
    graph_nodes[new_id] = new_node
    return {"success": True, "node": new_node}

@app.post("/api/v1/graph/relations", summary="添加节点关系")
def add_relation(relation: RelationCreate):
    if relation.source_id not in graph_nodes:
        raise HTTPException(status_code=400, detail="源节点不存在")
    if relation.target_id not in graph_nodes:
        raise HTTPException(status_code=400, detail="目标节点不存在")
    
    new_id = max(graph_relations.keys()) + 1 if graph_relations else 1
    new_relation = {
        "relation_id": new_id,
        "source_id": relation.source_id,
        "target_id": relation.target_id,
        "relation_type": relation.relation_type or "RELATED_TO"
    }
    graph_relations[new_id] = new_relation
    return {"success": True, "relation": new_relation}

@app.delete("/api/v1/graph/nodes/{node_id}", summary="删除节点")
def delete_node(node_id: int):
    if node_id not in graph_nodes:
        raise HTTPException(status_code=404, detail="节点不存在")
    
    del graph_nodes[node_id]
    graph_relations = {k: v for k, v in graph_relations.items() 
                       if v["source_id"] != node_id and v["target_id"] != node_id}
    
    return {"success": True, "message": "节点已删除"}

@app.delete("/api/v1/graph/relations/{relation_id}", summary="删除关系")
def delete_relation(relation_id: int):
    if relation_id not in graph_relations:
        raise HTTPException(status_code=404, detail="关系不存在")
    
    del graph_relations[relation_id]
    return {"success": True, "message": "关系已删除"}

# ==================== RAG接口 ====================

class DocumentCreate(BaseModel):
    course_id: int
    document_name: str
    content: str

class AskRequest(BaseModel):
    question: str
    course_id: Optional[int] = None
    top_k: int = 3

class RetrieveRequest(BaseModel):
    query: str
    course_id: Optional[int] = None
    top_k: int = 5

@app.post("/api/v1/rag/ask", summary="基于知识库问答")
def rag_ask(request: AskRequest):
    course_docs = [d for d in documents.values() 
                   if request.course_id is None or d["course_id"] == request.course_id]
    
    # 模拟RAG检索（实际实现时调用向量数据库）
    retrieved = course_docs[:request.top_k]
    
    # 模拟LLM回答（实际实现时调用大模型）
    answer = f"根据知识库内容，关于'{request.question}'的回答如下：\n\n"
    answer += "深度学习是机器学习的一个子领域，使用多层神经网络来学习数据的特征表示。"
    
    return {
        "success": True,
        "answer": answer,
        "sources": [
            {"document_id": d["document_id"], "document_name": d["document_name"], 
             "content": d["content"][:100] + "...", "score": 0.85} 
            for d in retrieved
        ]
    }

@app.post("/api/v1/rag/documents", summary="添加文档到知识库")
def add_document(doc: DocumentCreate):
    new_id = max(documents.keys()) + 1 if documents else 1
    new_doc = {
        "document_id": new_id,
        "course_id": doc.course_id,
        "document_name": doc.document_name,
        "content": doc.content
    }
    documents[new_id] = new_doc
    return {"success": True, "document": new_doc}

@app.get("/api/v1/rag/documents", summary="获取文档列表")
def list_documents(course_id: Optional[int] = None):
    course_docs = [d for d in documents.values() 
                   if course_id is None or d["course_id"] == course_id]
    return {"success": True, "documents": course_docs}

@app.put("/api/v1/rag/documents/{document_id}", summary="更新文档")
def update_document(document_id: int, content: str):
    if document_id not in documents:
        raise HTTPException(status_code=404, detail="文档不存在")
    
    documents[document_id]["content"] = content
    return {"success": True, "document": documents[document_id]}

@app.delete("/api/v1/rag/documents/{document_id}", summary="删除文档")
def delete_document(document_id: int):
    if document_id not in documents:
        raise HTTPException(status_code=404, detail="文档不存在")
    
    del documents[document_id]
    return {"success": True, "message": "文档已删除"}

@app.post("/api/v1/rag/index/build/{course_id}", summary="构建向量索引")
def build_index(course_id: int):
    return {"success": True, "message": f"课程{course_id}的向量索引构建完成"}

@app.delete("/api/v1/rag/index/{course_id}", summary="清理索引")
def clear_index(course_id: int):
    return {"success": True, "message": f"课程{course_id}的向量索引已清理"}

@app.post("/api/v1/rag/retrieve", summary="仅召回文档")
def retrieve(request: RetrieveRequest):
    course_docs = [d for d in documents.values() 
                   if request.course_id is None or d["course_id"] == request.course_id]
    
    results = [
        {"document_id": d["document_id"], "document_name": d["document_name"],
         "content": d["content"][:150] + "...", "score": 0.8 + i*0.05}
        for i, d in enumerate(course_docs[:request.top_k])
    ]
    
    return {"success": True, "results": results}

# ==================== 健康检查 ====================
@app.get("/health", summary="健康检查")
def health_check():
    return {"status": "healthy", "service": "ai-course-python-service"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)