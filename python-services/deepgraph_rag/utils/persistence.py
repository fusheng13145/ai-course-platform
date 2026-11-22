"""
持久化管理模块：将上传的文档、向量数据库、图谱数据等保存到磁盘，
使得应用重启后无需重新上传和构建。
"""
import os
import json
import shutil
import hashlib
import streamlit as st
import networkx as nx


# ======================
# 持久化目录结构
# ======================
# data/
#   documents/          # 保存上传的原始文档文件
#   faiss_index/        # 保存 FAISS 向量索引（使用 hash 目录名避免中文路径问题）
#   chapters/           # 保存章节切分数据 (JSON)
#   global_graphs/      # 保存全局图谱 (JSON)
#   metadata.json       # 保存文档列表和元信息

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")
DOCUMENTS_DIR = os.path.join(DATA_DIR, "documents")
FAISS_DIR = os.path.join(DATA_DIR, "faiss_index")
CHAPTERS_DIR = os.path.join(DATA_DIR, "chapters")
METADATA_PATH = os.path.join(DATA_DIR, "metadata.json")
GLOBAL_GRAPH_DIR = os.path.join(DATA_DIR, "global_graphs")


def ensure_dirs():
    """确保所有持久化目录存在"""
    for d in [DATA_DIR, DOCUMENTS_DIR, FAISS_DIR, CHAPTERS_DIR, GLOBAL_GRAPH_DIR]:
        os.makedirs(d, exist_ok=True)


def safe_hash_name(file_name):
    """
    将文件名转换为安全的哈希字符串，避免中文/特殊字符导致 Windows 路径问题。
    同时保留文件扩展名信息以便识别。
    """
    # 使用 MD5 哈希生成安全目录名
    name_hash = hashlib.md5(file_name.encode("utf-8")).hexdigest()[:12]
    # 提取文件扩展名
    _, ext = os.path.splitext(file_name)
    ext = ext.lower().replace(".", "")
    return f"{name_hash}_{ext}" if ext else name_hash


# ======================
# 元数据管理
# ======================
def load_metadata():
    """加载已保存的文档元数据"""
    ensure_dirs()
    if os.path.exists(METADATA_PATH):
        try:
            with open(METADATA_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"doc_names": [], "build_options": {}}


def save_metadata(metadata):
    """保存文档元数据"""
    ensure_dirs()
    with open(METADATA_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)


# ======================
# 文档持久化
# ======================
def save_uploaded_file(uploaded_file):
    """将上传的文件保存到持久化目录"""
    ensure_dirs()
    file_path = os.path.join(DOCUMENTS_DIR, uploaded_file.name)
    with open(file_path, "wb") as f:
        f.write(uploaded_file.getvalue())
    return file_path


def get_saved_document_path(filename):
    """获取已保存文档的路径"""
    path = os.path.join(DOCUMENTS_DIR, filename)
    return path if os.path.exists(path) else None


def list_saved_documents():
    """列出所有已保存的文档"""
    ensure_dirs()
    files = []
    for f in os.listdir(DOCUMENTS_DIR):
        if os.path.isfile(os.path.join(DOCUMENTS_DIR, f)):
            files.append(f)
    return files


# ======================
# 章节数据持久化
# ======================
def save_chapters(file_name, chapters_dict):
    """保存章节切分数据到磁盘"""
    ensure_dirs()
    safe_name = safe_hash_name(file_name)
    path = os.path.join(CHAPTERS_DIR, f"{safe_name}_chapters.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(chapters_dict, f, ensure_ascii=False, indent=2)
    return path


def load_chapters(file_name):
    """从磁盘加载章节切分数据"""
    safe_name = safe_hash_name(file_name)
    path = os.path.join(CHAPTERS_DIR, f"{safe_name}_chapters.json")
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return None


# ======================
# 全局图谱持久化
# ======================
def save_global_graph(file_name, G):
    """将全局图谱保存到磁盘 (NetworkX JSON 格式)"""
    ensure_dirs()
    safe_name = safe_hash_name(file_name)
    path = os.path.join(GLOBAL_GRAPH_DIR, f"{safe_name}_global_graph.json")
    try:
        data = nx.node_link_data(G)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return path
    except Exception as e:
        print(f"保存全局图谱失败: {e}")
        return None


def load_global_graph(file_name):
    """从磁盘加载全局图谱"""
    safe_name = safe_hash_name(file_name)
    path = os.path.join(GLOBAL_GRAPH_DIR, f"{safe_name}_global_graph.json")
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            return nx.node_link_graph(data)
        except Exception as e:
            print(f"加载全局图谱失败: {e}")
    return None


# ======================
# FAISS 向量索引持久化
# ======================
def save_faiss_index(file_name, faiss_db):
    """保存 FAISS 向量索引到磁盘（使用哈希目录名避免中文路径问题）"""
    ensure_dirs()
    safe_name = safe_hash_name(file_name)
    index_dir = os.path.join(FAISS_DIR, safe_name)
    try:
        # 如果已存在，先删除旧索引
        if os.path.exists(index_dir):
            shutil.rmtree(index_dir)
        faiss_db.save_local(index_dir)
        return index_dir
    except Exception as e:
        print(f"保存 FAISS 索引失败: {e}")
        return None


def load_faiss_index(file_name, embeddings):
    """从磁盘加载 FAISS 向量索引"""
    from langchain_community.vectorstores import FAISS

    safe_name = safe_hash_name(file_name)
    index_dir = os.path.join(FAISS_DIR, safe_name)
    if os.path.exists(index_dir):
        try:
            return FAISS.load_local(index_dir, embeddings, allow_dangerous_deserialization=True)
        except Exception as e:
            print(f"加载 FAISS 索引失败: {e}")
    return None


# ======================
# 清理函数
# ======================
def clear_all_data(file_name):
    """清除指定文档的所有持久化数据"""
    safe_name = safe_hash_name(file_name)

    # 删除文档文件
    doc_path = os.path.join(DOCUMENTS_DIR, file_name)
    if os.path.exists(doc_path):
        os.remove(doc_path)

    # 删除章节数据
    chapters_path = os.path.join(CHAPTERS_DIR, f"{safe_name}_chapters.json")
    if os.path.exists(chapters_path):
        os.remove(chapters_path)

    # 删除全局图谱
    graph_path = os.path.join(GLOBAL_GRAPH_DIR, f"{safe_name}_global_graph.json")
    if os.path.exists(graph_path):
        os.remove(graph_path)

    # 删除 FAISS 索引
    index_dir = os.path.join(FAISS_DIR, safe_name)
    if os.path.exists(index_dir):
        shutil.rmtree(index_dir)

    # 更新元数据
    metadata = load_metadata()
    if file_name in metadata.get("doc_names", []):
        metadata["doc_names"].remove(file_name)
        save_metadata(metadata)
