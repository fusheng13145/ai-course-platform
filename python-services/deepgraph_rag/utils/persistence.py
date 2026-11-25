"""本地持久化存储（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

承载课程文档的落盘形态：原文、FAISS 向量索引、章节切分、全局图谱。
设计约束：

1. **文件名只由内容身份决定**：`sha1(文档名)[:16]` 做前缀，避免中文/空格/斜杠
   文件名在 Windows 与容器里出现非法路径。
2. **不依赖 Streamlit**：只认 `DG_DATA_DIR` 环境变量或包内 `data/` 目录，
   进程与请求之间没有隐式会话状态。
3. **读取一律容错**：文件缺失/损坏返回 None 或空表，由上层按「无数据」降级，
   绝不用异常打断接口。
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
from typing import Any, Dict, List, Optional

_HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _resolve_data_dir() -> str:
    configured = (os.getenv("DG_DATA_DIR") or os.getenv("DEEPGRAPH_DATA_DIR") or "").strip()
    return configured or os.path.join(_HERE, "data")


# 模块常量：api_server 会在非 ASCII 安装路径下重定位这些常量（属部署配置）
DATA_DIR = _resolve_data_dir()
DOCUMENTS_DIR = os.path.join(DATA_DIR, "documents")
FAISS_DIR = os.path.join(DATA_DIR, "faiss_index")
CHAPTERS_DIR = os.path.join(DATA_DIR, "chapters")
GLOBAL_GRAPH_DIR = os.path.join(DATA_DIR, "global_graphs")
METADATA_PATH = os.path.join(DATA_DIR, "metadata.json")

_UNSAFE = re.compile(r'[\\/:*?"<>|\r\n\t]+')


def safe_hash_name(name: str) -> str:
    """文档名的稳定短哈希（同一名字永远同一前缀）。"""
    return hashlib.sha1((name or "document").encode("utf-8")).hexdigest()[:16]


def safe_file_stem(name: str, limit: int = 60) -> str:
    """可读性用的安全名（只为人眼分辨，身份仍由哈希前缀保证）。"""
    cleaned = _UNSAFE.sub("_", (name or "document").strip()) or "document"
    return cleaned[:limit]


def ensure_dirs() -> None:
    for path in (DATA_DIR, DOCUMENTS_DIR, FAISS_DIR, CHAPTERS_DIR, GLOBAL_GRAPH_DIR):
        os.makedirs(path, exist_ok=True)


def _stem(name: str) -> str:
    return f"{safe_hash_name(name)}_{safe_file_stem(name)}"


def document_path(name: str) -> str:
    return os.path.join(DOCUMENTS_DIR, f"{_stem(name)}.txt")


def faiss_dir(name: str) -> str:
    return os.path.join(FAISS_DIR, _stem(name))


def chapters_path(name: str) -> str:
    return os.path.join(CHAPTERS_DIR, f"{_stem(name)}_chapters.json")


def global_graph_path(name: str) -> str:
    # api_server 按 {safe_hash_name(name)}_global_graph.json 拼路径，命名必须一致
    return os.path.join(GLOBAL_GRAPH_DIR, f"{safe_hash_name(name)}_global_graph.json")


# ---------------------------------------------------------------- metadata
def load_metadata() -> Dict[str, Any]:
    try:
        with open(METADATA_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict) and isinstance(data.get("doc_names"), list):
            return data
    except Exception:
        pass
    return {"doc_names": []}


def save_metadata(metadata: Dict[str, Any]) -> None:
    ensure_dirs()
    tmp = METADATA_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)
    os.replace(tmp, METADATA_PATH)


def register_document(name: str) -> None:
    metadata = load_metadata()
    names = [n for n in metadata.get("doc_names", []) if n != name]
    names.append(name)
    metadata["doc_names"] = names
    save_metadata(metadata)


def unregister_document(name: str) -> None:
    metadata = load_metadata()
    metadata["doc_names"] = [n for n in metadata.get("doc_names", []) if n != name]
    save_metadata(metadata)


def list_documents() -> List[str]:
    return list(load_metadata().get("doc_names", []))


# ---------------------------------------------------------------- 原文
def save_document_text(name: str, text: str) -> str:
    ensure_dirs()
    path = document_path(name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text or "")
    register_document(name)
    return path


def load_document_text(name: str) -> Optional[str]:
    path = document_path(name)
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:
        return None


def get_saved_document_path(name: str) -> Optional[str]:
    """磁盘上真实存在的原文路径；不存在返回 None（列表/删除的唯一判据）。"""
    path = document_path(name)
    return path if os.path.exists(path) else None


# ---------------------------------------------------------------- FAISS
def save_faiss_index(name: str, faiss_db: Any) -> None:
    ensure_dirs()
    target = faiss_dir(name)
    os.makedirs(target, exist_ok=True)
    faiss_db.save_local(target)


def load_faiss_index(name: str, embeddings: Any) -> Optional[Any]:
    target = faiss_dir(name)
    if not os.path.isdir(target):
        return None
    try:
        from langchain_community.vectorstores import FAISS

        return FAISS.load_local(target, embeddings, allow_dangerous_deserialization=True)
    except Exception:
        return None


# ---------------------------------------------------------------- 章节
def save_chapters(name: str, chapters: Dict[str, str]) -> None:
    ensure_dirs()
    with open(chapters_path(name), "w", encoding="utf-8") as f:
        json.dump(chapters or {}, f, ensure_ascii=False, indent=2)


def load_chapters(name: str) -> Dict[str, str]:
    path = chapters_path(name)
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


# ---------------------------------------------------------------- 图谱
def save_global_graph(name: str, graph: Any) -> None:
    if graph is None:
        return
    ensure_dirs()
    try:
        import networkx as nx

        payload = nx.node_link_data(graph)
    except Exception:
        return
    with open(global_graph_path(name), "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)


def load_global_graph(name: str) -> Optional[Any]:
    path = global_graph_path(name)
    if not os.path.exists(path):
        return None
    try:
        import networkx as nx

        with open(path, "r", encoding="utf-8") as f:
            return nx.node_link_graph(json.load(f))
    except Exception:
        return None


# ---------------------------------------------------------------- 清理
def clear_all_data(name: str) -> None:
    for path in (document_path(name), chapters_path(name), global_graph_path(name)):
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception:
            pass
    target = faiss_dir(name)
    if os.path.isdir(target):
        shutil.rmtree(target, ignore_errors=True)
    unregister_document(name)
