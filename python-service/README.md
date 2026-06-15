# Python 知识图谱与 RAG 服务

> 为 AI智能课程平台提供知识图谱和RAG检索能力的Python微服务

## 技术栈

- Python 3.10+
- FastAPI 0.100+
- LangChain / LlamaIndex (RAG)
- Neo4j / NetworkX (知识图谱)
- Sentence-Transformers (嵌入模型)
- FAISS / Pinecone (向量存储)

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# 访问地址
# http://localhost:8000
# http://localhost:8000/docs (API文档)
```

## API 接口规范

### 知识图谱接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/graph/course/{courseId}` | 获取课程知识图谱 |
| GET | `/api/v1/graph/nodes/{nodeId}` | 获取节点详情 |
| GET | `/api/v1/graph/nodes/{nodeId}/relations?depth=2` | 获取节点关联关系 |
| POST | `/api/v1/graph/nodes` | 添加知识节点 |
| POST | `/api/v1/graph/relations` | 添加节点关系 |
| DELETE | `/api/v1/graph/nodes/{nodeId}` | 删除节点 |
| DELETE | `/api/v1/graph/relations/{relationId}` | 删除关系 |

#### POST /api/v1/graph/nodes

请求体:
```json
{
    "course_id": 1,
    "name": "机器学习",
    "content": "机器学习是人工智能的分支...",
    "type": "concept"
}
```

响应:
```json
{
    "success": true,
    "node_id": 1001,
    "name": "机器学习"
}
```

#### POST /api/v1/graph/relations

请求体:
```json
{
    "source_id": 1001,
    "target_id": 1002,
    "relation_type": "RELATED_TO"
}
```

### RAG 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/rag/ask` | 基于知识库问答 |
| POST | `/api/v1/rag/documents` | 添加文档到知识库 |
| GET | `/api/v1/rag/documents?course_id=1` | 获取文档列表 |
| PUT | `/api/v1/rag/documents/{documentId}` | 更新文档 |
| DELETE | `/api/v1/rag/documents/{documentId}` | 删除文档 |
| POST | `/api/v1/rag/index/build/{courseId}` | 构建向量索引 |
| DELETE | `/api/v1/rag/index/{courseId}` | 清理索引 |
| POST | `/api/v1/rag/retrieve` | 仅召回文档 |

#### POST /api/v1/rag/ask

请求体:
```json
{
    "question": "什么是深度学习？",
    "course_id": 1,
    "top_k": 3
}
```

响应:
```json
{
    "success": true,
    "answer": "深度学习是机器学习的一个子领域...",
    "sources": [
        {
            "document_id": 1,
            "document_name": "AI基础教程",
            "content": "深度学习使用多层神经网络...",
            "score": 0.92
        }
    ]
}
```

#### POST /api/v1/rag/retrieve

请求体:
```json
{
    "query": "神经网络",
    "course_id": 1,
    "top_k": 5
}
```

响应:
```json
{
    "success": true,
    "results": [
        {
            "document_id": 1,
            "document_name": "AI基础教程",
            "content": "...",
            "score": 0.92
        }
    ]
}
```

## 项目结构

```
python-service/
├── main.py                 # FastAPI入口
├── requirements.txt        # 依赖列表
├── config.py              # 配置管理
├── graph/                 # 知识图谱模块
│   ├── __init__.py
│   ├── graph_service.py   # 图谱操作服务
│   └── models.py          # 图谱数据模型
├── rag/                   # RAG模块
│   ├── __init__.py
│   ├── rag_service.py     # RAG服务
│   ├── vector_store.py    # 向量存储
│   └── llm_client.py     # 大模型客户端
└── utils/                 # 工具函数
    └── embedding.py       # 嵌入模型封装
```

## 配置说明

环境变量:
```bash
# 向量数据库配置
VECTOR_STORE_TYPE=faiss  # faiss/pinecone/chroma
PINECONE_API_KEY=your_key
PINECONE_ENV=us-west1-gcp

# 嵌入模型配置
EMBEDDING_MODEL=all-MiniLM-L6-v2

# 大模型配置
OPENAI_API_KEY=your_key
DEEPSEEK_API_KEY=your_key

# Neo4j配置（可选）
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=password
```

## 与Java后端集成

Python服务作为独立微服务运行，Java后端通过HTTP调用:

```
Java Backend (8080) ──HTTP──→ Python Service (8000)
```

Java配置:
```yaml
knowledge-graph:
  enabled: true
  base-url: http://localhost:8000

rag:
  enabled: true  
  base-url: http://localhost:8000
```

## 部署建议

### 开发环境
```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 生产环境
```bash
# 使用gunicorn
gunicorn main:app -w 4 -k uvicorn.workers.UvicornWorker -b 0.0.0.0:8000

# 或使用docker
docker build -t ai-course-python .
docker run -p 8000:8000 ai-course-python
```