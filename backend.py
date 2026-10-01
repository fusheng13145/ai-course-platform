import requests
import json
import re
from config import OLLAMA_API_URL, OLLAMA_BASE_URL, EMBEDDINGS_MODEL, MODEL
from neo4j_handler import neo4j_db

from utils.retriever_pipeline import retrieve_documents
from utils.doc_handler import process_documents
from utils.build_graph import build_chapter_knowledge_graph
from utils.llm_client import call_llm

# 🚀 接收来自 app.py 的构建选项，新增 build_chapter 参数
def process_docs_backend(uploaded_files, reranker, build_hierarchical=True, build_global=True, build_chapter=True):
    import streamlit as st
    import traceback
    try:
        result = process_documents(uploaded_files, reranker, EMBEDDINGS_MODEL, OLLAMA_BASE_URL, build_hierarchical, build_global, build_chapter)
        # result 现在是 (aggregated_pipeline, all_chapters)
        # aggregated_pipeline 可能为 None（所有文件都未成功构建检索管道）
        if result is None:
            raise RuntimeError("文档加载失败，请检查文件格式是否正确（支持 PDF、TXT、DOCX）。")
        aggregated_pipeline, all_chapters = result
        if aggregated_pipeline is None and not all_chapters:
            raise RuntimeError("文档加载失败，请检查文件格式是否正确（支持 PDF、TXT、DOCX）。")
        # 将 reranker 存入 session_state，供自动加载时使用
        st.session_state["reranker"] = reranker
    except Exception as e:
        error_detail = traceback.format_exc()
        print(f"process_docs_backend 异常详情:\n{error_detail}")
        raise e


# 🚀 暴露章节知识图谱构建方法
def build_chapter_graph_backend(chapter_name, chapter_text, progress_callback=None):
    """
    构建章节知识图谱，支持进度回调。
    
    参数:
        progress_callback: 可选的回调函数，用于报告构建进度
            回调签名: progress_callback(stage, message, detail)
    """
    return build_chapter_knowledge_graph(chapter_name, chapter_text, progress_callback)


def extract_entities_from_prompt(prompt):
    sys_prompt = f"请从以下用户问题中提取出核心技术实体词。仅返回由逗号分隔的实体列表，绝对不要输出任何前缀、Markdown标记或思考过程。\n用户问题：{prompt}"
    try:
        response = requests.post(
            OLLAMA_API_URL,
            json={
                "model": MODEL,
                "prompt": sys_prompt,
                "stream": False,  
                "options": {"temperature": 0.2}
            }
        )
        response.raise_for_status()
        result_text = response.json().get("response", "").strip()
        result_text = re.sub(r'<think>.*?</think>', '', result_text, flags=re.DOTALL).strip()
        result_text = re.sub(r'```[a-zA-Z]*\n(.*?)```', r'\1', result_text, flags=re.DOTALL).strip()
        
        entities = [e.strip() for e in result_text.replace("，", ",").split(",") if e.strip()]
        return entities
    except Exception as e:
        print(f"实体提取请求失败: {e}")
        return []

def get_retrieval_context(prompt, chat_history, source_doc, answer_mode="向量检索 + 知识图谱"):
    """
    根据 answer_mode 获取不同来源的上下文。
    
    模式说明：
    - "向量检索 + 知识图谱": 同时使用向量检索和图谱查询
    - "仅知识图谱": 仅使用知识图谱查询
    - "仅向量检索": 仅使用向量检索
    - "仅大模型自身知识": 不检索任何内容
    """
    vector_context = ""
    graph_context = ""

    # 向量检索
    if answer_mode in ["向量检索 + 知识图谱", "仅向量检索"]:
        vector_docs = retrieve_documents(prompt, OLLAMA_API_URL, MODEL, chat_history)
        vector_context = "\n".join([f"> 来源 {i+1}: {d.page_content}" for i, d in enumerate(vector_docs)]) if vector_docs else ""

    # 知识图谱查询
    if answer_mode in ["向量检索 + 知识图谱", "仅知识图谱"]:
        entities_in_prompt = extract_entities_from_prompt(prompt)
        if entities_in_prompt:
            try:
                graph_context = neo4j_db.query_graph_context(entities_in_prompt, source_doc)
            except Exception as e:
                print(f"图谱查询异常: {e}")

    # 构建最终上下文
    parts = []
    if vector_context:
        parts.append(f"【向量检索上下文】\n{vector_context}")
    if graph_context:
        parts.append(f"【图谱关系上下文】\n{graph_context}")
    
    if not parts:
        return ""  # 仅大模型自身知识模式，返回空上下文
    
    return "\n\n".join(parts)


def generate_llm_response(prompt, context):
    if context:
        sys_prompt = f"""你是一个智能教学助手。请根据提供的上下文回答用户的问题。
上下文信息：
{context}
"""
    else:
        sys_prompt = f"""你是一个智能教学助手。请直接根据你自身的知识回答用户的问题，不要编造不存在的文档或图谱信息。
"""
    # 使用统一客户端，使侧边栏选择的 Ollama / DeepSeek 提供商同时作用于问答。
    result = call_llm(prompt, system_prompt=sys_prompt)
    return result, sys_prompt


