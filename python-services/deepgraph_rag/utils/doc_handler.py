import os
import warnings
import concurrent.futures
import re
import json
import tempfile

os.environ["TRANSFORMERS_VERBOSITY"] = "error"
os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore", module="transformers")
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

import streamlit as st
import networkx as nx
from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_ollama import OllamaEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_community.retrievers import BM25Retriever
from langchain_classic.retrievers import EnsembleRetriever
from langchain_core.documents import Document

from utils.build_graph import (
    build_knowledge_graph,
    build_hierarchical_graph,
    extract_triplets_with_llm,
)
from neo4j_handler import neo4j_db
from utils.persistence import (
    save_uploaded_file,
    save_chapters,
    save_faiss_index,
    save_global_graph,
    save_metadata,
    load_metadata,
)



def chinese_tokenizer(text):
    text = re.sub(r"[^\w\u4e00-\u9fff]+", "", text)
    return list(text)


# 🚀 全新极速版：不依赖大模型，直接正则分析目录区域进行精准提纯和切分
def fast_extract_chapters_from_toc(full_text):
    chapters = {}
    unique_titles = []

    # 1. 快速定位文本中的【目录】区域
    # 目录可能在文档开头（少数）或文档末尾（多数），需要同时搜索
    head_text = full_text[:30000]
    tail_text = full_text[-30000:] if len(full_text) > 30000 else full_text
    
    # 先在开头搜索目录
    match = re.search(r"\n\s*(目录|CONTENTS|Contents|目\s*录)\s*\n", head_text)
    toc_start_in_head = match.start() if match else -1
    
    # 再在末尾搜索目录
    tail_match = re.search(r"\n\s*(目录|CONTENTS|Contents|目\s*录)\s*\n", tail_text)
    toc_start_in_tail = (len(full_text) - 30000 + tail_match.start()) if tail_match else -1
    
    # 确定目录位置：优先取末尾的目录（多数教材目录在末尾）
    # 如果开头和末尾都有目录，取末尾的
    # 如果只有开头有目录，取开头的
    toc_start_idx = -1
    toc_is_at_end = False  # 目录是否在文档末尾
    if toc_start_in_tail > 0:
        toc_start_idx = toc_start_in_tail
        toc_is_at_end = True
    elif toc_start_in_head > 0:
        toc_start_idx = toc_start_in_head
        toc_is_at_end = False
    
    # 确定正文区域：
    # - 如果目录在末尾，正文区域为 [0, toc_start_idx)（目录之前）
    # - 如果目录在开头，正文区域为 [toc_end_idx, len(full_text))（目录之后）
    # - 如果没找到目录，全文都视为正文
    if toc_start_idx > 0 and not toc_is_at_end:
        # 目录在开头，正文在目录之后
        # 💡 关键修复：不再使用固定偏移量 +8000 跳过目录区域，
        # 而是通过检测 tab+页码格式的结束位置来精确确定目录的结尾。
        # 目录行的特征：包含制表符(\t)后跟页码数字，如 "第 1 章  概 述 \t1"
        # 正文中的章节标题则没有这种格式。
        toc_region = full_text[toc_start_idx : toc_start_idx + 12000]
        # 查找目录区域的结束位置：找到第一个不包含 tab+页码 模式的章节标题行
        toc_end_offset = 8000  # 默认回退值
        lines = toc_region.split("\n")
        toc_pattern_count = 0
        for i, line in enumerate(lines):
            line_stripped = line.strip()
            if not line_stripped:
                continue
            # 检查是否包含 tab+页码 格式（目录特征）
            # 目录行特征：包含制表符后跟空格和页码数字，如 "第 1 章  概 述 \t1" 或 "1.2   互联网概述 \t 3"
            if re.search(r"\t\s*\d+\s*$", line):
                toc_pattern_count += 1
            elif toc_pattern_count > 5 and len(line_stripped) > 10:
                # 已经连续匹配到多个目录行，突然遇到没有 tab+页码 的长行，
                # 说明目录已结束，进入正文区域
                toc_end_offset = sum(len(l) + 1 for l in lines[:i])  # +1 for newline
                break
        
        body_start = toc_start_idx + toc_end_offset
        body_end = len(full_text)
    elif toc_start_idx > 0 and toc_is_at_end:
        # 目录在末尾，正文在目录之前
        body_start = 0
        body_end = toc_start_idx
    else:
        # 没找到目录
        body_start = 0
        body_end = len(full_text)
    
    if match:
        # 截取目录部分，一般目录长度在 5000-8000 字左右
        toc_text = full_text[match.end() : match.end() + 8000]

        # 2. 逐行解析目录正文，用正则抓取章节标题
        lines = toc_text.split("\n")
        # 匹配：第1章、第一篇、Chapter 1、1.1、1. 等常见标题头
        heading_pattern = re.compile(
            r"^(第\s*[零一二三四五六七八九十百千万0-9]+\s*[章篇部分节]|Chapter\s*\d+|\d+(?:\.\d+)+\s*|\d+\s+)(.*)",
            re.IGNORECASE,
        )

        for line in lines:
            line = line.strip()
            if not line:
                continue

            # 清理目录行尾部的页码（如：...... 12, ----15,  18）
            clean_line = re.sub(r"[\.\-…\s]+[0-9]+$", "", line).strip()

            if heading_pattern.match(clean_line) and 2 < len(clean_line) < 80:
                if clean_line not in unique_titles:
                    unique_titles.append(clean_line)

    # 3. 兜底方案：如果没找到目录页，或者目录解析失败，直接全量扫描明显的"大章节"标题
    if not unique_titles:
        lines = full_text.split("\n")
        fallback_pattern = re.compile(
            r"^\s*(第\s*[零一二三四五六七八九十百千万0-9]+\s*[章篇部分]|Chapter\s*\d+)(.*)",
            re.IGNORECASE,
        )
        for line in lines:
            line = line.strip()
            if fallback_pattern.match(line) and len(line) < 60:
                if line not in unique_titles:
                    unique_titles.append(line)

    # 4. 终极兜底：如果连章节格式都没有，按均等字数硬切分
    if not unique_titles:
        chunk_size = 8000
        for i in range(0, len(full_text), chunk_size):
            chapters[f"文本切片 {i//chunk_size + 1}"] = full_text[i : i + chunk_size]
        return chapters

    # 5. 直接在正文区域中搜索标题进行切割（不依赖目录中的标题格式）
    # 因为目录中的标题格式（如"第 1 章  概 述"）和正文中的标题格式（如"第1章 概述"）可能不同
    # 所以直接在正文中搜索常见的标题模式
    last_idx = body_start
    last_title = "前言/引言"
    
    body_text = full_text[body_start:body_end]
    
    # 在正文中搜索所有标题
    heading_positions = []
    
    # 模式1: 第X章（行首或前面是换行符）— 一级章节标题
    for m in re.finditer(r'(?:^|\n)\s*(第\s*[一二三四五六七八九十\d]+\s*章\s*\S[^\n]{0,60})', body_text):
        pos = body_start + m.start() + len(m.group(1)) - len(m.group(1).strip())
        title_text = m.group(1).strip()
        title_text = re.sub(r'\s+', ' ', title_text)
        heading_positions.append((pos, title_text, "chapter"))
    
    # 模式2: X.X 或 X.X.X（行首或前面是换行符）— 子章节标题
    for m in re.finditer(r'(?:^|\n)\s*((?:\d+\.)+\d+\s+\S[^\n]{0,60})', body_text):
        pos = body_start + m.start() + len(m.group(1)) - len(m.group(1).strip())
        title_text = m.group(1).strip()
        title_text = re.sub(r'\s+', ' ', title_text)
        # 过滤掉非标题内容（如引用其他章节的"第4章的4.2.2节"）
        if not re.match(r'\d+\.\d+', title_text):
            continue
        heading_positions.append((pos, title_text, "subsection"))
    
    # 按位置排序
    heading_positions.sort(key=lambda x: x[0])
    
    # 去重：如果两个标题位置太近（<50字符），保留第一个
    deduped_positions = []
    for pos, title, htype in heading_positions:
        if not deduped_positions or pos - deduped_positions[-1][0] > 50:
            deduped_positions.append((pos, title, htype))
    
    # 策略：只使用一级章节标题（第X章）作为主要切割点
    # 子章节标题（X.X）合并到所属的一级章节中
    # 先找出一级章节标题的位置
    chapter_positions = [(pos, title) for pos, title, htype in deduped_positions if htype == "chapter"]
    
    if chapter_positions:
        # 使用一级章节标题进行切割
        for pos, title_text in chapter_positions:
            if pos > last_idx:
                text_part = full_text[last_idx:pos].strip()
                if text_part:
                    chapters[last_title] = text_part
                last_title = title_text
                last_idx = pos
    else:
        # 如果没有一级章节标题，使用子章节标题
        for pos, title_text, htype in deduped_positions:
            if pos > last_idx:
                text_part = full_text[last_idx:pos].strip()
                if text_part:
                    chapters[last_title] = text_part
                last_title = title_text
                last_idx = pos

    if last_idx < body_end:
        chapters[last_title] = full_text[last_idx:body_end].strip()
    elif last_idx < len(full_text):
        chapters[last_title] = full_text[last_idx:].strip()

    return {k: v for k, v in chapters.items() if len(v) > 50}


def parallel_extract_triplets(docs, max_workers=4):
    triplets_pool = []
    valid_docs = [doc for doc in docs if len(doc.page_content) > 50]

    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_doc = {
            executor.submit(extract_triplets_with_llm, doc.page_content): doc
            for doc in valid_docs
        }
        for future in concurrent.futures.as_completed(future_to_doc):
            try:
                res = future.result()
                if res:
                    triplets_pool.append(res)
            except Exception as e:
                print(f"Extraction error: {e}")
    return triplets_pool


def load_single_file(file):
    """
    加载单个上传文件，返回 (file_name, raw_docs) 元组。
    如果加载失败，返回 (file_name, None)。
    """
    suffix = os.path.splitext(file.name)[-1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp_file:
        tmp_file.write(file.getvalue())
        tmp_path = tmp_file.name

    if suffix.lower() == ".pdf":
        loader = PyPDFLoader(tmp_path)
    elif suffix.lower() == ".txt":
        loader = TextLoader(tmp_path, encoding="utf-8")
    elif suffix.lower() == ".docx":
        loader = Docx2txtLoader(tmp_path)
    else:
        os.remove(tmp_path)
        return file.name, None

    try:
        loaded = loader.load()
        return file.name, loaded
    except Exception as e:
        st.warning(f"⚠️ 文件 {file.name} 加载失败: {e}")
        return file.name, None
    finally:
        os.remove(tmp_path)


def process_single_file(
    file_name,
    raw_docs,
    embeddings_model,
    ollama_url,
    build_hierarchical,
    build_global,
    build_chapter,
):
    """
    处理单个文件的所有构建步骤。
    返回 (retrieval_pipeline, chapters_dict) 或 (None, {})。
    """
    # 过滤掉内容为空的文档
    raw_docs = [doc for doc in raw_docs if doc.page_content and doc.page_content.strip()]
    if not raw_docs:
        st.warning(f"⚠️ 文件「{file_name}」内容为空，跳过处理。")
        return None, {}

    full_text = "\n".join([doc.page_content for doc in raw_docs])
    chapters_dict = {}

    # ===============================================
    # 🌲 1. 层次图谱 (按需使用大模型提取树状JSON)
    # ===============================================
    if build_hierarchical:
        st.write(f"🌲 正在为「{file_name}」构建大纲层次图谱...")
        
        # 🚩【修复】使用全文而非仅目录区域，让 build_hierarchical_graph 内部智能处理文本截取
        # 同时传递 progress_callback 支持进度回调
        tree_data = build_hierarchical_graph(full_text, file_name)
        os.makedirs("graph_data", exist_ok=True)
        # 🚩【修复】使用 _hierarchical 后缀避免与其他图谱文件名冲突
        json_path = os.path.join("graph_data", f"{file_name}_hierarchical.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(tree_data, f, ensure_ascii=False)

    # ===============================================
    # 📂 2. 章节图谱基础数据 (纯正则切分，毫秒级)
    # ===============================================
    if build_chapter:
        st.write(f"📂 正在为「{file_name}」解析目录并切分章节...")
        chapters_dict = fast_extract_chapters_from_toc(full_text)
        st.write(f"✅ 「{file_name}」分离出 {len(chapters_dict)} 个章节模块！")
    else:
        chapters_dict = {}

    # ===============================================
    # 🧱 3. 向量检索建库
    # ===============================================
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=100)
    split_docs = text_splitter.split_documents(raw_docs)

    if not split_docs:
        st.warning(f"⚠️ 文件「{file_name}」切分后没有有效文本块，跳过向量建库。")
        return None, chapters_dict

    st.write(f"🧱 正在为「{file_name}」构建向量数据库...")
    embeddings = OllamaEmbeddings(model=embeddings_model, base_url=ollama_url)
    faiss_db = FAISS.from_documents(split_docs, embeddings)
    faiss_retriever = faiss_db.as_retriever(search_kwargs={"k": 5})

    bm25_retriever = BM25Retriever.from_documents(split_docs)
    bm25_retriever.k = 5

    ensemble_retriever = EnsembleRetriever(
        retrievers=[bm25_retriever, faiss_retriever], weights=[0.4, 0.6]
    )

    retrieval_pipeline = {"ensemble": ensemble_retriever, "knowledge_graph": None}

    # ===============================================
    # 🌍 4. 全局图谱构建
    # ===============================================
    if build_global:
        st.write(f"🌍 正在为「{file_name}」构建全局图谱...")
        global_graph_result = build_knowledge_graph(split_docs)
        if not (
            isinstance(global_graph_result, nx.Graph)
            and global_graph_result.number_of_edges() > 0
        ):
            triplets_pool = parallel_extract_triplets(split_docs[:20], max_workers=3)
            fallback_kg = nx.Graph()
            for triplet_list in triplets_pool:
                for triplet in triplet_list:
                    if isinstance(triplet, list) and len(triplet) == 3:
                        fallback_kg.add_edge(triplet[0], triplet[2], label=triplet[1])
            global_graph_result = fallback_kg
        retrieval_pipeline["knowledge_graph"] = global_graph_result

        # 🚀 将全局图谱存入 Neo4j
        if global_graph_result and global_graph_result.number_of_edges() > 0:
            try:
                st.write(f"🗄️ 正在将「{file_name}」的全局图谱存入 Neo4j...")
                neo4j_db.clear_source_data(file_name)
                neo4j_db.insert_triplets_from_graph(
                    global_graph_result, file_name, graph_type="global"
                )
                st.write(f"✅ 「{file_name}」全局图谱已存入 Neo4j（共 {global_graph_result.number_of_edges()} 条关系）")
            except Exception as e:
                st.warning(f"⚠️ Neo4j 存储失败（请确保 Neo4j 服务已启动）: {e}")

    # ===============================================
    # 💾 5. 持久化保存当前文件的数据
    # ===============================================
    try:
        # 保存原始文档文件
        save_uploaded_file_raw(file_name, raw_docs)

        # 保存 FAISS 向量索引
        save_faiss_index(file_name, faiss_db)

        # 保存章节数据
        if chapters_dict:
            save_chapters(file_name, chapters_dict)

        # 保存全局图谱到本地磁盘
        if build_global and retrieval_pipeline.get("knowledge_graph"):
            kg = retrieval_pipeline["knowledge_graph"]
            if kg and kg.number_of_edges() > 0:
                save_global_graph(file_name, kg)

        # 更新元数据
        metadata = load_metadata()
        doc_names = metadata.get("doc_names", [])
        if file_name not in doc_names:
            doc_names.append(file_name)
        metadata["doc_names"] = doc_names
        metadata["build_options"] = {
            "build_hierarchical": build_hierarchical,
            "build_global": build_global,
            "build_chapter": build_chapter,
        }
        save_metadata(metadata)
    except Exception as e:
        st.warning(f"⚠️ 文件「{file_name}」持久化保存时出现异常（不影响核心功能）: {e}")

    return retrieval_pipeline, chapters_dict


def save_uploaded_file_raw(file_name, raw_docs):
    """
    将原始文档内容保存到持久化目录（用于后续重新加载）。
    由于原始 uploaded_file 对象在 Streamlit rerun 后可能失效，
    这里将 raw_docs 的文本内容保存为 .txt 文件。
    """
    from utils.persistence import DOCUMENTS_DIR, ensure_dirs
    ensure_dirs()
    # 将所有文档内容合并保存为一个 .txt 文件
    full_text = "\n".join([doc.page_content for doc in raw_docs if doc.page_content])
    file_path = os.path.join(DOCUMENTS_DIR, file_name)
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(full_text)
    return file_path


# 🚀 优化版：每个文件独立处理，互不影响
def process_documents(
    uploaded_files,
    reranker_model,
    embeddings_model,
    ollama_url,
    build_hierarchical=True,
    build_global=True,
    build_chapter=True,
):
    """
    处理上传的多个文档，每个文件独立构建向量数据库、图谱、章节数据。
    
    返回:
        tuple: (aggregated_retrieval_pipeline, all_chapters_dict)
        - aggregated_retrieval_pipeline: 合并所有文件的检索管道
        - all_chapters_dict: 所有文件的章节数据 {file_name: chapters_dict}
    """
    if not uploaded_files:
        st.error("❌ 没有上传任何文件。")
        return None, {}

    # 第一步：加载所有文件
    file_results = []
    for file in uploaded_files:
        file_name, raw_docs = load_single_file(file)
        if raw_docs is None:
            st.warning(f"⚠️ 文件「{file_name}」加载失败，已跳过。")
            continue
        file_results.append((file_name, raw_docs))

    if not file_results:
        st.error("❌ 所有上传的文件均无法加载或解析。请检查文件格式是否正确（支持 PDF、TXT、DOCX）。")
        return None, {}

    # 第二步：每个文件独立处理
    all_retrieval_pipelines = {}
    all_chapters = {}
    success_count = 0

    for file_name, raw_docs in file_results:
        st.markdown(f"---")
        st.subheader(f"📄 正在处理：{file_name}")
        
        pipeline, chapters = process_single_file(
            file_name,
            raw_docs,
            embeddings_model,
            ollama_url,
            build_hierarchical,
            build_global,
            build_chapter,
        )
        
        if pipeline is not None:
            all_retrieval_pipelines[file_name] = pipeline
            success_count += 1
        
        if chapters:
            all_chapters[file_name] = chapters

    if not all_retrieval_pipelines:
        st.warning("⚠️ 所有文件均未成功构建检索管道，但章节数据可能已保存。")
        # 如果没有任何检索管道，返回 None
        if not all_chapters:
            return None, {}
        return None, all_chapters

    # 第三步：合并所有文件的检索管道
    # 使用第一个成功文件的检索管道作为主管道
    first_file = list(all_retrieval_pipelines.keys())[0]
    aggregated_pipeline = all_retrieval_pipelines[first_file]

    # 如果有多个文件，将知识图谱合并
    if len(all_retrieval_pipelines) > 1:
        combined_kg = nx.Graph()
        for fname, pipeline in all_retrieval_pipelines.items():
            kg = pipeline.get("knowledge_graph")
            if kg and kg.number_of_edges() > 0:
                combined_kg = nx.compose(combined_kg, kg)
        if combined_kg.number_of_edges() > 0:
            aggregated_pipeline["knowledge_graph"] = combined_kg

    st.success(f"✅ 全部处理完成！成功处理 {success_count}/{len(file_results)} 个文件。")
    
    # 将所有文件的章节数据存入 session_state
    st.session_state.all_chapters = all_chapters
    # 同时将第一个文件的章节设为当前章节（兼容旧代码）
    if all_chapters:
        st.session_state.chapters = list(all_chapters.values())[0]

    return aggregated_pipeline, all_chapters
