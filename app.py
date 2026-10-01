import os
import re
import streamlit as st
import json
import base64
import warnings
import networkx as nx  # 引入图谱工具用于存取JSON文件

# 🚀 屏蔽底层依赖库烦人的控制台警告
warnings.filterwarnings("ignore", module="transformers")
warnings.filterwarnings("ignore", module="streamlit")

# 引入所有后端支持函数
from backend import (
    process_docs_backend,
    get_retrieval_context,
    generate_llm_response,
    build_chapter_graph_backend,
)
from config import CROSS_ENCODER_MODEL, EMBEDDINGS_MODEL, OLLAMA_BASE_URL, MODEL

from utils.build_graph import (
    render_echarts_hierarchical_graph,
    render_echarts_global_graph,
    render_learning_path_echarts,
)
from utils.llm_client import set_provider, get_provider_name, get_provider
from neo4j_handler import neo4j_db
from utils.persistence import (
    load_metadata,
    list_saved_documents,
    load_chapters,
    load_global_graph,
    load_faiss_index,
    get_saved_document_path,
    ensure_dirs,
    clear_all_data,
)



def render_html_safely(html_string, height=650):
    b64_html = base64.b64encode(html_string.encode("utf-8")).decode("utf-8")
    iframe_html = f"""
    <div style="resize: vertical; overflow: hidden; border: 1px solid #ddd; border-radius: 8px; padding: 0; height: {height}px; min-height: 400px; width: 100%; display: flex; flex-direction: column;">
        <div style="background-color: #f1f3f4; padding: 4px 8px; font-size: 12px; color: #666; text-align: right; border-bottom: 1px solid #ddd; cursor: s-resize; user-select: none;">
            🔽 拖动右下角可调整图谱高度大小
        </div>
        <iframe src="data:text/html;base64,{b64_html}" style="flex-grow: 1; width: 100%; height: 100%; border: none;"></iframe>
    </div>
    """
    st.markdown(iframe_html, unsafe_allow_html=True)


def load_document_data(doc_name):
    """
    加载指定文档的所有持久化数据到 session_state。
    包括：章节数据、全局图谱、FAISS 向量索引。
    """
    st.session_state.current_source = doc_name
    st.session_state.documents_loaded = True
    if doc_name not in st.session_state.doc_names:
        st.session_state.doc_names.append(doc_name)

    # 加载章节数据
    chapters_data = load_chapters(doc_name)
    if chapters_data:
        # 🚀 检测章节数据是否为旧版格式（内容为目录文本而非正文）
        # 旧版格式特征：章节值包含制表符页码（如 "第1章 概述\t1"）或内容过短
        needs_reprocess = False
        for k, v in chapters_data.items():
            # 如果章节内容包含制表符页码格式（如 "\t1\n"），说明是目录文本
            if re.search(r'\t\d+\s*$', v[:200]):
                needs_reprocess = True
                break
            # 如果章节内容太短（<500字符）且不是前言，说明可能有问题
            if len(v) < 500 and k != "前言/引言":
                needs_reprocess = True
                break
        
        if needs_reprocess:
            st.warning(f"⚠️ 文档「{doc_name}」的章节数据为旧版格式，内容为目录文本而非正文。"
                       f"请点击「开始处理文档」按钮重新处理该文档以获取完整的章节正文内容。")
        
        st.session_state.chapters = chapters_data

    # 加载全局图谱
    global_kg = load_global_graph(doc_name)
    if global_kg and global_kg.number_of_edges() > 0:
        if "retrieval_pipeline" not in st.session_state:
            st.session_state.retrieval_pipeline = {}
        st.session_state.retrieval_pipeline["knowledge_graph"] = global_kg

    # 加载 FAISS 向量索引（需要嵌入模型）
    try:
        from langchain_ollama import OllamaEmbeddings

        embeddings = OllamaEmbeddings(model=EMBEDDINGS_MODEL, base_url=OLLAMA_BASE_URL)
        faiss_db = load_faiss_index(doc_name, embeddings)
        if faiss_db:
            from langchain_community.retrievers import BM25Retriever
            from langchain_classic.retrievers import EnsembleRetriever

            faiss_retriever = faiss_db.as_retriever(search_kwargs={"k": 5})
            bm25_retriever = BM25Retriever.from_texts([""])
            bm25_retriever.k = 5
            ensemble_retriever = EnsembleRetriever(
                retrievers=[bm25_retriever, faiss_retriever], weights=[0.4, 0.6]
            )
            if "retrieval_pipeline" not in st.session_state:
                st.session_state.retrieval_pipeline = {}
            st.session_state.retrieval_pipeline["ensemble"] = ensemble_retriever
    except Exception as e:
        print(f"加载 FAISS 索引失败（问答时仍可使用 Neo4j GraphRAG）: {e}")


def auto_load_existing_data():
    """
    应用启动时自动检测并加载已持久化的数据，
    让用户无需重新上传文档即可继续使用。
    """
    if st.session_state.get("data_loaded"):
        return

    ensure_dirs()
    metadata = load_metadata()
    doc_names = metadata.get("doc_names", [])

    if not doc_names:
        st.session_state.data_loaded = True
        return

    # 只加载磁盘上确实存在的文档
    existing_docs = [d for d in doc_names if get_saved_document_path(d)]
    if not existing_docs:
        st.session_state.data_loaded = True
        return

    st.session_state.doc_names = existing_docs

    # 加载第一个文档的所有数据
    load_document_data(existing_docs[0])

    st.session_state.data_loaded = True



# ==========================
# 1. 界面与状态初始化
# ==========================
st.set_page_config(page_title="AI 助教知识图谱系统", layout="wide")

if "messages" not in st.session_state:
    st.session_state.messages = []
if "documents_loaded" not in st.session_state:
    st.session_state.documents_loaded = False
if "doc_names" not in st.session_state:
    st.session_state.doc_names = []
if "current_source" not in st.session_state:
    st.session_state.current_source = ""
if "enable_hyde" not in st.session_state:
    # HyDE 会为每次提问额外调用一次模型；默认关闭以缩短常规问答响应时间。
    st.session_state.enable_hyde = False
if "enable_graph_rag" not in st.session_state:
    st.session_state.enable_graph_rag = True
if "enable_reranking" not in st.session_state:
    st.session_state.enable_reranking = True
if "rag_enabled" not in st.session_state:
    st.session_state.rag_enabled = True
if "answer_mode" not in st.session_state:
    st.session_state.answer_mode = "向量检索 + 知识图谱"  # 默认使用全部
if "temperature" not in st.session_state:
    st.session_state.temperature = 0.7
if "data_loaded" not in st.session_state:
    st.session_state.data_loaded = False


# 🌟 自动加载已持久化的数据
auto_load_existing_data()

# ==========================
# 2. 侧边栏：配置与文件上传
# ==========================
with st.sidebar:
    st.header("📂 知识库与图谱管理")

    # ==========================================
    # 📋 最近文档列表（从持久化数据加载）
    # ==========================================
    with st.expander("📋 最近文档", expanded=True):
        saved_docs = list_saved_documents()
        if saved_docs:
            for doc_name in saved_docs:
                col1, col2 = st.columns([4, 1])
                with col1:
                    is_current = doc_name == st.session_state.get("current_source", "")
                    prefix = "📄" if not is_current else "📌"
                    if st.button(
                        f"{prefix} {doc_name}",
                        key=f"doc_{doc_name}",
                        help=f"点击切换到「{doc_name}」",
                        width='stretch',
                    ):
                        # 使用统一的加载函数加载该文档的所有数据
                        load_document_data(doc_name)
                        st.rerun()

                with col2:
                    if st.button("🗑️", key=f"del_{doc_name}", help=f"删除「{doc_name}」的所有数据"):
                        clear_all_data(doc_name)
                        if doc_name in st.session_state.doc_names:
                            st.session_state.doc_names.remove(doc_name)
                        if st.session_state.get("current_source") == doc_name:
                            st.session_state.current_source = ""
                        st.rerun()
        else:
            st.caption("暂无已保存的文档，请上传新文档。")

    st.divider()

    # 显示已加载的文档
    if st.session_state.doc_names:
        st.info(f"📄 当前会话: {len(st.session_state.doc_names)} 个文档")

    uploaded_files = st.file_uploader(
        "上传新文档 (PDF/TXT/DOCX)", accept_multiple_files=True
    )


    st.markdown("**图谱构建选项**")
    build_hierarchical = st.checkbox(
        "✅ 构建【层次图谱】(基于教程目录, 极快)", value=True
    )
    build_chapter = st.checkbox(
        "✅ 启用【章节图谱】(提取章节结构以按需建图, 极快)", value=True
    )
    build_global = st.checkbox(
        "✅ 构建【全局图谱】(基于正文抽样, 耗时较慢)", value=False
    )

    def load_cross_encoder_with_retry(model_name, max_retries=3):
        """带重试和回退的 CrossEncoder 加载函数"""
        from sentence_transformers import CrossEncoder
        import time

        # 尝试不同的 HuggingFace 镜像端点
        hf_endpoints = [
            os.environ.get("HF_ENDPOINT", "https://hf-mirror.com"),
            "https://huggingface.co",
            None,  # 使用默认
        ]

        last_error = None
        for endpoint in hf_endpoints:
            for attempt in range(max_retries):
                try:
                    if endpoint:
                        os.environ["HF_ENDPOINT"] = endpoint
                    return CrossEncoder(model_name)
                except Exception as e:
                    last_error = e
                    error_msg = str(e)
                    if "SSL" in error_msg or "UNEXPECTED_EOF" in error_msg:
                        print(f"SSL 连接失败，尝试切换镜像端点 {endpoint} (尝试 {attempt+1}/{max_retries})...")
                        time.sleep(2)
                    else:
                        # 非 SSL 错误直接抛出
                        raise e
        raise last_error

    if st.button("开始处理文档"):
        if uploaded_files:
            if not build_hierarchical and not build_global and not build_chapter:
                st.warning("建议至少勾选一种要构建的图谱以获得最佳体验！")
            else:
                with st.spinner("正在解析文档并构建知识系统，请稍候..."):
                    try:
                        reranker = load_cross_encoder_with_retry(CROSS_ENCODER_MODEL)
                        process_docs_backend(
                            uploaded_files,
                            reranker,
                            build_hierarchical,
                            build_global,
                            build_chapter,
                        )
                        st.session_state.documents_loaded = True
                        # 合并新文档名称到已有列表
                        new_names = [f.name for f in uploaded_files]
                        for n in new_names:
                            if n not in st.session_state.doc_names:
                                st.session_state.doc_names.append(n)
                        if st.session_state.doc_names:
                            st.session_state.current_source = (
                                st.session_state.doc_names[0]
                            )
                        st.success("文档及图谱处理成功！")
                    except Exception as e:
                        st.error(f"文档处理失败: {e}")
                        st.info("💡 提示：如果重排模型下载失败，可以在侧边栏关闭「启用 Neural Reranking」选项，或检查网络连接。")

        else:
            st.warning("请先上传至少一个文档。")

    st.divider()
    st.header("⚙️ 检索与大模型设置")

    # 🚀 新增：LLM 提供商选择器
    current_provider = get_provider()
    provider_options = ["ollama", "deepseek"]
    provider_labels = {
        "ollama": f"🦙 Ollama 本地模型 ({MODEL})",
        "deepseek": f"🌐 DeepSeek API (deepseek-chat)",
    }
    selected_provider = st.radio(
        "🤖 知识图谱构建模型",
        options=provider_options,
        format_func=lambda x: provider_labels.get(x, x),
        index=provider_options.index(current_provider) if current_provider in provider_options else 0,
        help="选择用于构建知识图谱的大模型：Ollama（本地免费）或 DeepSeek API（云端更智能）",
    )
    # 如果用户切换了提供商，立即生效
    if selected_provider != current_provider:
        set_provider(selected_provider)
        st.rerun()

    # 显示当前提供商状态
    st.caption(f"当前使用: {get_provider_name()}")

    st.session_state.rag_enabled = st.checkbox(
        "启用 RAG (检索增强)", value=st.session_state.rag_enabled
    )
    st.session_state.enable_hyde = st.checkbox(
        "启用 HyDE (查询扩展)", value=st.session_state.enable_hyde
    )
    st.session_state.enable_graph_rag = st.checkbox(
        "启用 GraphRAG (图谱检索)", value=st.session_state.enable_graph_rag
    )
    st.session_state.enable_reranking = st.checkbox(
        "启用 Neural Reranking", value=st.session_state.enable_reranking
    )
    st.session_state.temperature = st.slider(
        "模型温度 (Temperature)", 0.0, 1.0, st.session_state.temperature
    )

    if st.session_state.doc_names:
        st.session_state.current_source = st.selectbox(
            "当前查看的文档", st.session_state.doc_names
        )

# ==========================================
# 主界面展示区：划分为 4 个标签页
# ==========================================
st.title("🧠 计算机专业 AI 助教平台")

# 使用 CSS 美化聊天界面
st.markdown("""
<style>
/* 聊天输入框紧随「问题回复」区域下方，随页面正常流式排布（不再固定到视口底部）。 */
[data-testid="stChatInput"] {
    position: static;
    width: 100%;
    max-width: 100%;
    margin-top: 0.5rem;
}

/* 输入框与上方回复区域之间保留一点底部留白 */
[data-testid="stMainBlockContainer"] {
    padding-bottom: 2rem;
}
</style>
""", unsafe_allow_html=True)

tab1, tab2, tab3, tab4, tab5 = st.tabs(
    ["💬 智能问答", "🌐 全局知识图谱", "📂 层次知识图谱", "📖 章节知识图谱 (推荐)", "🗺️ 学习路径规划"]
)


with tab1:
    st.header("💬 智能问答")

    # 回答模式选择器
    col_mode, col_info = st.columns([2, 3])
    with col_mode:
        answer_mode = st.radio(
            "AI 回答模式",
            options=["向量检索 + 知识图谱", "仅知识图谱", "仅向量检索", "仅大模型自身知识"],
            index=0,
            key="answer_mode",
            horizontal=True,
            help="选择 AI 回答问题的知识来源方式",
        )
    with col_info:
        mode_hints = {
            "向量检索 + 知识图谱": "🔄 结合文档向量检索和知识图谱关系，提供最全面的回答",
            "仅知识图谱": "🌐 仅基于知识图谱中的实体关系进行回答，适合验证概念关联",
            "仅向量检索": "📄 仅基于文档向量相似度检索，适合查找原文内容",
            "仅大模型自身知识": "🧠 仅依靠大模型自身知识回答，不检索任何文档",
        }
        st.info(mode_hints[answer_mode])

    # ==========================================
    # 问题回复区域（消息历史）：固定高度、内部滚动
    # ==========================================
    messages_container = st.container(height=420, border=True)
    with messages_container:
        for msg in st.session_state.messages:
            with st.chat_message(msg["role"]):
                st.markdown(msg["content"])
                if msg["role"] == "assistant" and msg.get("llm_prompt"):
                    with st.expander("📤 查看发送给大模型的完整 Prompt", expanded=False):
                        st.code(msg["llm_prompt"])

    # ==========================================
    # 问题输入框 - 位于上方「问题回复」区域的下方，仅在智能问答标签页显示
    # ==========================================
    if prompt := st.chat_input("请输入您关于计算机课程的问题..."):
        st.session_state.messages.append({"role": "user", "content": prompt})
        with messages_container:
            with st.chat_message("user"):
                st.markdown(prompt)

        with messages_container:
            with st.chat_message("assistant"):
                try:
                    with st.spinner("正在思考中..."):
                        context = get_retrieval_context(
                            prompt,
                            st.session_state.messages,
                            st.session_state.get("current_source", ""),
                            st.session_state.get("answer_mode", "向量检索 + 知识图谱"),
                        )
                        response, llm_prompt = generate_llm_response(prompt, context)

                    st.markdown(response)
                    with st.expander("📤 查看发送给大模型的完整 Prompt", expanded=False):
                        st.code(llm_prompt)
                    st.session_state.messages.append(
                        {"role": "assistant", "content": response, "llm_prompt": llm_prompt}
                    )
                except Exception as e:
                    st.error(f"⚠️ 问答生成失败: {e}")




with tab2:
    st.header("🌐 全局实体关系图谱")
    if st.session_state.get("current_source"):
        if (
            "retrieval_pipeline" in st.session_state
            and st.session_state.retrieval_pipeline.get("knowledge_graph")
        ):
            st.info("展示当前文档中提取的核心实体及其交互关系。")
            kg = st.session_state.retrieval_pipeline["knowledge_graph"]
            html_content = render_echarts_global_graph(kg)
            render_html_safely(html_content, height=600)

            # ==========================================
            # 节点与关系管理表格
            # ==========================================
            st.divider()
            st.subheader("📋 节点与关系管理")
            st.caption("下方表格展示图谱中的所有节点和关系，可勾选后删除。删除后图谱会同步更新。")

            # 获取所有节点和边
            nodes_list = list(kg.nodes())
            edges_list = list(kg.edges(data=True))

            # ---- 节点管理 ----
            with st.expander(f"🔵 节点列表（共 {len(nodes_list)} 个）", expanded=False):
                if nodes_list:
                    # 分页显示，每页20个
                    page_size = 20
                    total_pages = max(1, (len(nodes_list) + page_size - 1) // page_size)
                    if "global_node_page" not in st.session_state:
                        st.session_state.global_node_page = 1
                    
                    col1, col2 = st.columns([3, 1])
                    with col1:
                        st.caption(f"第 {st.session_state.global_node_page}/{total_pages} 页")
                    with col2:
                        page = st.number_input("跳转页码", min_value=1, max_value=total_pages, 
                                               value=st.session_state.global_node_page, key="global_node_page_input")
                        st.session_state.global_node_page = page
                    
                    start_idx = (page - 1) * page_size
                    end_idx = min(start_idx + page_size, len(nodes_list))
                    page_nodes = nodes_list[start_idx:end_idx]
                    
                    # 构建节点数据
                    node_data = []
                    for i, node in enumerate(page_nodes, start=start_idx + 1):
                        degree = kg.degree(node)
                        node_data.append({"序号": i, "节点名称": str(node), "关联数量": degree})
                    
                    st.dataframe(node_data, width='stretch', hide_index=True)
                    
                    # 节点删除（带搜索功能）
                    st.markdown("##### 🗑️ 删除节点")
                    col_search1, col_search2 = st.columns([3, 1])
                    with col_search1:
                        node_search = st.text_input("🔍 搜索节点名称", placeholder="输入关键词快速筛选...", key="global_node_search")
                    with col_search2:
                        st.caption("共 " + str(len(nodes_list)) + " 个节点")
                    
                    # 根据搜索词过滤节点
                    filtered_nodes = [str(n) for n in nodes_list]
                    if node_search:
                        filtered_nodes = [n for n in filtered_nodes if node_search.lower() in n.lower()]
                    
                    node_to_delete = st.selectbox("选择要删除的节点", options=[""] + filtered_nodes, key="global_node_delete")
                    if node_to_delete and st.button("确认删除该节点", key="global_node_del_btn", type="primary"):
                        if node_to_delete in kg:
                            kg.remove_node(node_to_delete)
                            st.success(f"已删除节点「{node_to_delete}」")
                            st.rerun()

                    # 节点重命名
                    st.markdown("##### ✏️ 重命名节点")
                    col_rename1, col_rename2 = st.columns([3, 1])
                    with col_rename1:
                        node_to_rename = st.selectbox("选择要重命名的节点", options=[""] + filtered_nodes, key="global_node_rename_select")
                    with col_rename2:
                        st.caption(" ")
                    if node_to_rename:
                        new_name = st.text_input("输入新名称", placeholder="请输入新的节点名称...", key="global_node_new_name")
                        if new_name and new_name.strip() and st.button("确认重命名", key="global_node_rename_btn", type="primary"):
                            new_name = new_name.strip()
                            if new_name != node_to_rename:
                                if new_name not in kg:
                                    # 执行重命名：保留所有边，迁移到新节点
                                    neighbors = list(kg.neighbors(node_to_rename))
                                    for neighbor in neighbors:
                                        edge_data = kg.get_edge_data(node_to_rename, neighbor)
                                        if edge_data:
                                            kg.add_edge(new_name, neighbor, **edge_data)
                                    kg.remove_node(node_to_rename)
                                    st.success(f"✅ 节点「{node_to_rename}」已重命名为「{new_name}」")
                                    st.rerun()
                                else:
                                    st.warning(f"⚠️ 节点「{new_name}」已存在，请使用其他名称")
                            else:
                                st.info("新名称与原名称相同，无需修改")

                else:
                    st.info("暂无节点数据")

            # ---- 关系管理 ----
            with st.expander(f"🔗 关系列表（共 {len(edges_list)} 条）", expanded=False):
                if edges_list:
                    page_size_e = 20
                    total_pages_e = max(1, (len(edges_list) + page_size_e - 1) // page_size_e)
                    if "global_edge_page" not in st.session_state:
                        st.session_state.global_edge_page = 1
                    
                    col1, col2 = st.columns([3, 1])
                    with col1:
                        st.caption(f"第 {st.session_state.global_edge_page}/{total_pages_e} 页")
                    with col2:
                        page_e = st.number_input("跳转页码", min_value=1, max_value=total_pages_e,
                                                 value=st.session_state.global_edge_page, key="global_edge_page_input")
                        st.session_state.global_edge_page = page_e
                    
                    start_idx_e = (page_e - 1) * page_size_e
                    end_idx_e = min(start_idx_e + page_size_e, len(edges_list))
                    page_edges = edges_list[start_idx_e:end_idx_e]
                    
                    edge_data = []
                    for i, (u, v, data) in enumerate(page_edges, start=start_idx_e + 1):
                        rel = data.get("label", "相关") if data else "相关"
                        edge_data.append({"序号": i, "源节点": str(u), "关系": rel, "目标节点": str(v)})
                    
                    st.dataframe(edge_data, width='stretch', hide_index=True)
                    
                    # 关系删除（带搜索功能）
                    st.markdown("##### 🗑️ 删除关系")
                    col_search_e1, col_search_e2 = st.columns([3, 1])
                    with col_search_e1:
                        edge_search = st.text_input("🔍 搜索关系（源节点/目标节点/关系类型）", placeholder="输入关键词快速筛选...", key="global_edge_search")
                    with col_search_e2:
                        st.caption("共 " + str(len(edges_list)) + " 条关系")
                    
                    edge_options_raw = [f"{str(u)} → [{data.get('label', '相关')}] → {str(v)}" for u, v, data in edges_list]
                    if edge_search:
                        edge_options_raw = [e for e in edge_options_raw if edge_search.lower() in e.lower()]
                    edge_options = [""] + edge_options_raw
                    edge_to_delete = st.selectbox("选择要删除的关系", options=edge_options, key="global_edge_delete")

                    if edge_to_delete and st.button("确认删除该关系", key="global_edge_del_btn", type="primary"):
                        # 解析选中的关系
                        match = re.match(r"(.+?) → \[(.+?)\] → (.+)", edge_to_delete)
                        if match:
                            src, rel, tgt = match.group(1), match.group(2), match.group(3)
                            if kg.has_edge(src, tgt):
                                kg.remove_edge(src, tgt)
                                st.success(f"已删除关系「{src} → [{rel}] → {tgt}」")
                                st.rerun()
                else:
                    st.info("暂无关系数据")
        else:
            st.warning(
                "⚠️ 未能成功提取出全局关系，或您在左侧关闭了【全局知识图谱】的构建。建议使用【章节知识图谱】。"
            )
    else:
        st.warning("请先在左侧上传并处理文档。")


with tab3:
    st.header("📂 层次知识图谱")
    if st.session_state.get("current_source"):
        # 🚩【修复】使用 _hierarchical 后缀匹配 doc_handler.py 中的新文件名
        json_path = os.path.join(
            "graph_data", f"{st.session_state.current_source}_hierarchical.json"
        )
        if os.path.exists(json_path):
            st.info("展示基于计算机教程**目录大纲**提取生成的严谨层次知识结构树。")
            with open(json_path, "r", encoding="utf-8") as f:
                tree_data = json.load(f)
            html_content = render_echarts_hierarchical_graph(tree_data)
            render_html_safely(html_content, height=600)
        else:
            st.warning("暂无层次图谱数据，或您在左侧关闭了【层次知识图谱】。")
    else:
        st.warning("请先在左侧上传并处理文档。")

with tab4:
    st.header("📖 章节知识图谱")
    if not st.session_state.get("documents_loaded"):
        st.warning("⚠️ 请先在左侧侧边栏上传并处理文档。")
    elif "chapters" in st.session_state and st.session_state.chapters:
        st.info(
            "💡 大型教材内容繁杂，通过针对特定章节构建图谱既能节省时间，又能提供高精度的知识网络。构建结果会自动存档。"
        )

        # 用户下拉选择
        selected_chapter = st.selectbox(
            "📌 请选择要构建图谱的章节：",
            options=list(st.session_state.chapters.keys()),
        )
        chapter_text = st.session_state.chapters[selected_chapter]
        st.caption(
            f"📊 当前章节数据量：约 {len(chapter_text)} 字符 (大层级章节将自动包含其内部所有子节数据)"
        )

        # 构建图谱安全保存路径
        safe_chapter_name = (
            selected_chapter.replace("/", "_").replace("\\", "_").replace(" ", "")
        )
        graph_save_path = os.path.join(
            "graph_data",
            f"{st.session_state.current_source}_kg_{safe_chapter_name}.json",
        )

        # 🌟 无缝体验：如果在本地磁盘发现这个章节曾经构建过，则瞬间静默加载到会话中
        if f"kg_{selected_chapter}" not in st.session_state:
            if os.path.exists(graph_save_path):
                try:
                    with open(graph_save_path, "r", encoding="utf-8") as f:
                        saved_graph_data = json.load(f)
                        st.session_state[f"kg_{selected_chapter}"] = nx.node_link_graph(
                            saved_graph_data
                        )
                except Exception as e:
                    pass

        # UI：根据缓存状态展示不同的按钮和提示
        if f"kg_{selected_chapter}" in st.session_state:
            st.success("✅ 该章节的知识图谱已就绪 (本地极速缓存读取)。")
            if st.button("🔄 重新构建并刷新该章图谱 (耗时)"):
                # 使用 st.status 展示构建过程
                with st.status(f"🔄 正在重新构建【{selected_chapter}】的知识图谱...", expanded=True) as status:
                    # 使用普通 Python 列表存储日志（线程安全，append 是原子操作）
                    # 注意：不能使用 st.session_state，因为后台线程没有 ScriptRunContext
                    build_logs = []
                    
                    # 进度日志容器
                    progress_log = st.container()
                    log_placeholder = progress_log.empty()
                    
                    # 定义进度回调函数 - 仅追加到普通 Python 列表（线程安全）
                    # 注意：回调可能从后台线程调用，不能直接调用 Streamlit UI 操作
                    def progress_callback(stage, message, detail):
                        build_logs.append({
                            "stage": stage,
                            "message": message,
                            "detail": detail,
                        })
                    
                    chapter_kg = build_chapter_graph_backend(
                        selected_chapter, chapter_text, progress_callback
                    )
                    
                    # 构建完成后，从普通列表读取所有日志并渲染到 UI
                    if build_logs:
                        log_text = ""
                        for log in build_logs:
                            icon = {
                                "start": "🚀", "split": "📄", "extract": "🔍", 
                                "extract_progress": "⏳", "extract_error": "❌",
                                "preclean": "🧹", "llm_request": "📤", "llm_wait": "⏰",
                                "llm_response": "📥", "parse": "🔧", "complete": "✅",
                                "assemble": "🔗", "assemble_complete": "🔗",
                                "prune": "✂️", "error": "❌"
                            }.get(log["stage"], "•")
                            log_text += f"{icon} **{log['message']}**\n"
                            # 显示详细信息（可折叠）- 完整展示，不截断
                            if log["detail"] and len(log["detail"]) > 0:
                                # 对关键阶段（preclean、llm_request、llm_response）使用更大的展示区域
                                if log["stage"] in ["preclean", "llm_request", "llm_response"]:
                                    log_text += f"  <details><summary>查看详情（完整内容）</summary><pre style='font-size:12px;max-height:400px;overflow:auto;white-space:pre-wrap;background:#f5f5f5;padding:10px;border-radius:4px;'>{log['detail']}</pre></details>\n"
                                else:
                                    log_text += f"  <details><summary>查看详情</summary><pre style='font-size:12px;max-height:200px;overflow:auto;white-space:pre-wrap;'>{log['detail']}</pre></details>\n"
                        log_placeholder.markdown(log_text, unsafe_allow_html=True)
                    
                    if chapter_kg and chapter_kg.number_of_edges() > 0:
                        st.session_state[f"kg_{selected_chapter}"] = chapter_kg
                        status.update(label=f"✅ 图谱构建完成！共 {chapter_kg.number_of_edges()} 条关系", state="complete")
                        
                        # 构建完立刻写入本地保存
                        try:
                            with open(graph_save_path, "w", encoding="utf-8") as f:
                                json.dump(
                                    nx.node_link_data(chapter_kg), f, ensure_ascii=False
                                )
                        except:
                            pass
                        # 🚀 将章节图谱存入 Neo4j（同时存入主文档源和章节源）
                        try:
                            source_doc = st.session_state.get("current_source", "")
                            chapter_source = f"{source_doc}_chapter_{safe_chapter_name}"
                            # 先清除该章节的旧数据
                            neo4j_db.clear_source_data(chapter_source)
                            # 存入章节专属源（保留章节粒度）
                            neo4j_db.insert_triplets_from_graph(
                                chapter_kg, chapter_source, graph_type="chapter"
                            )
                            # 也存入主文档源（供学习路径规划等全局查询使用）
                            neo4j_db.insert_triplets_from_graph(
                                chapter_kg, source_doc, graph_type="chapter"
                            )
                        except Exception as e:
                            st.warning(f"⚠️ Neo4j 存储失败: {e}")
                        
                        # 🚀 刷新页面以立即显示图谱
                        st.rerun()
                    else:
                        status.update(label="❌ 未能提取出有效的三元组关系", state="error")

        else:
            if st.button("🚀 开始构建该章知识图谱"):
                # 使用 st.status 展示构建过程
                with st.status(f"🚀 正在构建【{selected_chapter}】的知识图谱...", expanded=True) as status:
                    # 使用普通 Python 列表存储日志（线程安全，append 是原子操作）
                    build_logs = []
                    
                    # 进度日志容器
                    progress_log = st.container()
                    log_placeholder = progress_log.empty()
                    
                    # 定义进度回调函数 - 仅追加到普通 Python 列表（线程安全）
                    # 注意：回调可能从后台线程调用，不能直接调用 Streamlit UI 操作
                    def progress_callback(stage, message, detail):
                        build_logs.append({
                            "stage": stage,
                            "message": message,
                            "detail": detail,
                        })
                    
                    chapter_kg = build_chapter_graph_backend(
                        selected_chapter, chapter_text, progress_callback
                    )

                    # 构建完成后，从普通列表读取所有日志并渲染到 UI
                    if build_logs:
                        log_text = ""
                        for log in build_logs:
                            icon = {
                                "start": "🚀", "split": "📄", "extract": "🔍", 
                                "extract_progress": "⏳", "extract_error": "❌",
                                "preclean": "🧹", "llm_request": "📤", "llm_wait": "⏰",
                                "llm_response": "📥", "parse": "🔧", "complete": "✅",
                                "assemble": "🔗", "assemble_complete": "🔗",
                                "prune": "✂️", "error": "❌"
                            }.get(log["stage"], "•")
                            log_text += f"{icon} **{log['message']}**\n"
                            # 显示详细信息（可折叠）- 完整展示，不截断
                            if log["detail"] and len(log["detail"]) > 0:
                                # 对关键阶段（preclean、llm_request、llm_response）使用更大的展示区域
                                if log["stage"] in ["preclean", "llm_request", "llm_response"]:
                                    log_text += f"  <details><summary>查看详情（完整内容）</summary><pre style='font-size:12px;max-height:400px;overflow:auto;white-space:pre-wrap;background:#f5f5f5;padding:10px;border-radius:4px;'>{log['detail']}</pre></details>\n"
                                else:
                                    log_text += f"  <details><summary>查看详情</summary><pre style='font-size:12px;max-height:200px;overflow:auto;white-space:pre-wrap;'>{log['detail']}</pre></details>\n"
                        log_placeholder.markdown(log_text, unsafe_allow_html=True)


                    if chapter_kg and chapter_kg.number_of_edges() > 0:
                        st.session_state[f"kg_{selected_chapter}"] = chapter_kg
                        status.update(label=f"✅ 图谱构建完成！共 {chapter_kg.number_of_edges()} 条关系", state="complete")

                        # 构建完立刻写入本地保存
                        os.makedirs("graph_data", exist_ok=True)
                        try:
                            with open(graph_save_path, "w", encoding="utf-8") as f:
                                json.dump(
                                    nx.node_link_data(chapter_kg), f, ensure_ascii=False
                                )
                        except Exception as e:
                            st.warning(f"本地存档失败: {e}")

                        # 🚀 将章节图谱存入 Neo4j（同时存入主文档源和章节源）
                        try:
                            source_doc = st.session_state.get("current_source", "")
                            chapter_source = f"{source_doc}_chapter_{safe_chapter_name}"
                            # 先清除该章节的旧数据
                            neo4j_db.clear_source_data(chapter_source)
                            # 存入章节专属源（保留章节粒度）
                            neo4j_db.insert_triplets_from_graph(
                                chapter_kg, chapter_source, graph_type="chapter"
                            )
                            # 也存入主文档源（供学习路径规划等全局查询使用）
                            neo4j_db.insert_triplets_from_graph(
                                chapter_kg, source_doc, graph_type="chapter"
                            )
                            st.success(f"🗄️ 章节图谱已同步存入 Neo4j 数据库")
                        except Exception as e:
                            st.warning(f"⚠️ Neo4j 存储失败（请确保 Neo4j 服务已启动）: {e}")

                        # 🚀 刷新页面以立即显示图谱
                        st.rerun()

                    else:
                        status.update(label="❌ 未能提取出有效的三元组关系", state="error")




        # 渲染选中章节的图谱
        if f"kg_{selected_chapter}" in st.session_state:
            st.markdown(f"#### 🌌 {selected_chapter} 知识网络")
            chapter_kg = st.session_state[f"kg_{selected_chapter}"]
            html_content = render_echarts_global_graph(chapter_kg)
            render_html_safely(html_content, height=650)

            # ==========================================
            # 章节图谱节点与关系管理表格
            # ==========================================
            st.divider()
            st.subheader("📋 节点与关系管理")
            st.caption("下方表格展示该章节图谱中的所有节点和关系，可删除。删除后图谱会同步更新。")

            chapter_nodes = list(chapter_kg.nodes())
            chapter_edges = list(chapter_kg.edges(data=True))

            # ---- 节点管理 ----
            with st.expander(f"🔵 节点列表（共 {len(chapter_nodes)} 个）", expanded=False):
                if chapter_nodes:
                    page_size = 20
                    total_pages = max(1, (len(chapter_nodes) + page_size - 1) // page_size)
                    if "chapter_node_page" not in st.session_state:
                        st.session_state.chapter_node_page = 1
                    
                    col1, col2 = st.columns([3, 1])
                    with col1:
                        st.caption(f"第 {st.session_state.chapter_node_page}/{total_pages} 页")
                    with col2:
                        page = st.number_input("跳转页码", min_value=1, max_value=total_pages,
                                               value=st.session_state.chapter_node_page, key="chapter_node_page_input")
                        st.session_state.chapter_node_page = page
                    
                    start_idx = (page - 1) * page_size
                    end_idx = min(start_idx + page_size, len(chapter_nodes))
                    page_nodes = chapter_nodes[start_idx:end_idx]
                    
                    node_data = []
                    for i, node in enumerate(page_nodes, start=start_idx + 1):
                        degree = chapter_kg.degree(node)
                        node_data.append({"序号": i, "节点名称": str(node), "关联数量": degree})
                    
                    st.dataframe(node_data, width='stretch', hide_index=True)
                    
                    st.markdown("##### 🗑️ 删除节点")
                    col_search1, col_search2 = st.columns([3, 1])
                    with col_search1:
                        chapter_node_search = st.text_input("🔍 搜索节点名称", placeholder="输入关键词快速筛选...", key="chapter_node_search")
                    with col_search2:
                        st.caption("共 " + str(len(chapter_nodes)) + " 个节点")
                    
                    filtered_chapter_nodes = [str(n) for n in chapter_nodes]
                    if chapter_node_search:
                        filtered_chapter_nodes = [n for n in filtered_chapter_nodes if chapter_node_search.lower() in n.lower()]
                    
                    node_to_delete = st.selectbox("选择要删除的节点", options=[""] + filtered_chapter_nodes, key="chapter_node_delete")
                    if node_to_delete and st.button("确认删除该节点", key="chapter_node_del_btn", type="primary"):
                        if node_to_delete in chapter_kg:
                            chapter_kg.remove_node(node_to_delete)
                            st.success(f"已删除节点「{node_to_delete}」")
                            st.rerun()

                    # 节点重命名
                    st.markdown("##### ✏️ 重命名节点")
                    col_rename1, col_rename2 = st.columns([3, 1])
                    with col_rename1:
                        node_to_rename = st.selectbox("选择要重命名的节点", options=[""] + filtered_chapter_nodes, key="chapter_node_rename_select")
                    with col_rename2:
                        st.caption(" ")
                    if node_to_rename:
                        new_name = st.text_input("输入新名称", placeholder="请输入新的节点名称...", key="chapter_node_new_name")
                        if new_name and new_name.strip() and st.button("确认重命名", key="chapter_node_rename_btn", type="primary"):
                            new_name = new_name.strip()
                            if new_name != node_to_rename:
                                if new_name not in chapter_kg:
                                    # 执行重命名：保留所有边，迁移到新节点
                                    neighbors = list(chapter_kg.neighbors(node_to_rename))
                                    for neighbor in neighbors:
                                        edge_data = chapter_kg.get_edge_data(node_to_rename, neighbor)
                                        if edge_data:
                                            chapter_kg.add_edge(new_name, neighbor, **edge_data)
                                    chapter_kg.remove_node(node_to_rename)
                                    st.success(f"✅ 节点「{node_to_rename}」已重命名为「{new_name}」")
                                    st.rerun()
                                else:
                                    st.warning(f"⚠️ 节点「{new_name}」已存在，请使用其他名称")
                            else:
                                st.info("新名称与原名称相同，无需修改")

                else:
                    st.info("暂无节点数据")

            # ---- 关系管理 ----
            with st.expander(f"🔗 关系列表（共 {len(chapter_edges)} 条）", expanded=False):
                if chapter_edges:
                    page_size_e = 20
                    total_pages_e = max(1, (len(chapter_edges) + page_size_e - 1) // page_size_e)
                    if "chapter_edge_page" not in st.session_state:
                        st.session_state.chapter_edge_page = 1
                    
                    col1, col2 = st.columns([3, 1])
                    with col1:
                        st.caption(f"第 {st.session_state.chapter_edge_page}/{total_pages_e} 页")
                    with col2:
                        page_e = st.number_input("跳转页码", min_value=1, max_value=total_pages_e,
                                                 value=st.session_state.chapter_edge_page, key="chapter_edge_page_input")
                        st.session_state.chapter_edge_page = page_e
                    
                    start_idx_e = (page_e - 1) * page_size_e
                    end_idx_e = min(start_idx_e + page_size_e, len(chapter_edges))
                    page_edges = chapter_edges[start_idx_e:end_idx_e]
                    
                    edge_data = []
                    for i, (u, v, data) in enumerate(page_edges, start=start_idx_e + 1):
                        rel = data.get("label", "相关") if data else "相关"
                        edge_data.append({"序号": i, "源节点": str(u), "关系": rel, "目标节点": str(v)})
                    
                    st.dataframe(edge_data, width='stretch', hide_index=True)
                    
                    st.markdown("##### 🗑️ 删除关系")
                    col_search_e1, col_search_e2 = st.columns([3, 1])
                    with col_search_e1:
                        chapter_edge_search = st.text_input("🔍 搜索关系（源节点/目标节点/关系类型）", placeholder="输入关键词快速筛选...", key="chapter_edge_search")
                    with col_search_e2:
                        st.caption("共 " + str(len(chapter_edges)) + " 条关系")
                    
                    chapter_edge_options_raw = [f"{str(u)} → [{data.get('label', '相关')}] → {str(v)}" for u, v, data in chapter_edges]
                    if chapter_edge_search:
                        chapter_edge_options_raw = [e for e in chapter_edge_options_raw if chapter_edge_search.lower() in e.lower()]
                    edge_options = [""] + chapter_edge_options_raw
                    edge_to_delete = st.selectbox("选择要删除的关系", options=edge_options, key="chapter_edge_delete")

                    if edge_to_delete and st.button("确认删除该关系", key="chapter_edge_del_btn", type="primary"):
                        match = re.match(r"(.+?) → \[(.+?)\] → (.+)", edge_to_delete)
                        if match:
                            src, rel, tgt = match.group(1), match.group(2), match.group(3)
                            if chapter_kg.has_edge(src, tgt):
                                chapter_kg.remove_edge(src, tgt)
                                st.success(f"已删除关系「{src} → [{rel}] → {tgt}」")
                                st.rerun()
                else:
                    st.info("暂无关系数据")

    else:
        st.warning(
            "⚠️ 未检测到章节划分数据。请确保在左侧配置区勾选了【启用章节图谱】，然后重新处理文档。"
        )

with tab5:
    st.header("🗺️ 学习路径规划")

    if not st.session_state.get("current_source"):
        st.warning("⚠️ 请先在左侧侧边栏上传并处理文档，或从最近文档列表中选择一个文档。")
    else:
        st.markdown("""
        ### 💡 功能介绍
        
        基于知识图谱中的实体关系，自动分析学习某个知识点需要先掌握哪些前置知识。
        
        **使用方式**：输入你想学习的目标知识点（如「TCP协议」、「路由算法」、「数据链路层」等），
        系统会从知识图谱中反向查找所有前置依赖，生成从基础到进阶的完整学习路径。
        """)

        # 输入目标知识点
        target_knowledge = st.text_input(
            "🎯 输入你想学习的目标知识点：",
            placeholder="例如：TCP协议、路由算法、数据链路层、HTTP协议...",
            key="learning_path_input",
        )

        col1, col2, col3 = st.columns([1, 1, 2])
        with col1:
            max_depth = st.selectbox(
                "递归深度",
                options=[1, 2, 3, 4],
                index=2,  # 默认3层
                help="深度越大，找到的前置知识越全面，但可能包含较远的相关概念",
            )
        with col2:
            # 🚀 新增：跨文档搜索开关
            search_all_sources = st.checkbox(
                "📚 整本书搜索",
                value=True,
                help="开启后将在所有已构建图谱的文档/章节中搜索知识点，不限于当前文档",
            )
        with col3:

            # 添加一个快速选择标签（使用 query params 避免直接修改 widget state）
            st.markdown("##### 💡 快速示例")
            example_tags = ["TCP协议", "路由算法", "数据链路层", "HTTP协议", "IP地址"]
            tag_cols = st.columns(len(example_tags))
            for i, tag in enumerate(example_tags):
                with tag_cols[i]:
                    if st.button(tag, key=f"tag_{tag}", width='stretch'):
                        # 使用 query params 传递选中的标签，避免直接修改 widget state
                        st.query_params["quick_tag"] = tag
                        st.rerun()
            
            # 检查是否有快速标签被点击
            quick_tag = st.query_params.get("quick_tag")
            if quick_tag and not target_knowledge:
                target_knowledge = quick_tag
                st.query_params.clear()


        # 🔍 调试信息（可折叠）
        with st.expander("🔧 调试：查看 Neo4j 数据库状态", expanded=False):
            try:
                all_sources = neo4j_db.debug_list_all_sources()
                st.write("**Neo4j 中所有 source 标签：**")
                for s in all_sources:
                    st.code(s)
                
                # 搜索当前文档源的数据
                current_source = st.session_state.get("current_source", "")
                st.write(f"**当前文档源：** `{current_source}`")
                
                # 搜索当前文档源下的实体
                entities_in_db = neo4j_db.debug_search_entity("")
                if entities_in_db:
                    st.write(f"**Neo4j 中所有实体（共 {len(entities_in_db)} 个）：**")
                    st.write(", ".join(entities_in_db[:30]))
                    if len(entities_in_db) > 30:
                        st.write(f"... 还有 {len(entities_in_db) - 30} 个")
                else:
                    st.warning("⚠️ Neo4j 中没有任何实体数据！请先在「章节知识图谱」中构建图谱。")
            except Exception as e:
                st.error(f"Neo4j 连接失败: {e}")
                st.info("💡 请确保 Neo4j 数据库服务已启动（docker-compose up -d neo4j）")

        # 初始化学习路径缓存
        if "learning_path_cache" not in st.session_state:
            st.session_state.learning_path_cache = {}

        if st.button("🔍 生成学习路径", type="primary", width='stretch'):
            if not target_knowledge.strip():
                st.warning("请输入目标知识点名称")
            else:
                # 检查缓存
                cache_key = f"{target_knowledge.strip()}_{max_depth}"
                if cache_key in st.session_state.learning_path_cache:
                    result = st.session_state.learning_path_cache[cache_key]
                    st.info("📦 已从缓存中读取结果（相同查询）")
                    st.markdown(result)
                else:
                    with st.spinner(f"正在知识图谱中分析「{target_knowledge}」的前置知识..."):
                        try:
                            source_doc = st.session_state.get("current_source", "")
                            result = neo4j_db.query_learning_path(
                                target_entity=target_knowledge.strip(),
                                source_doc=source_doc,
                                max_depth=max_depth,
                                search_all_sources=search_all_sources,
                            )

                            # 存入缓存
                            st.session_state.learning_path_cache[cache_key] = result
                            st.markdown(result)
                            # 如果有图谱数据被存储到 session_state，刷新页面以显示可视化
                            if "learning_path_graph" in st.session_state:
                                st.rerun()
                        except Exception as e:
                            st.error(f"学习路径生成失败: {e}")
                            st.info("💡 请确保：1) Neo4j 数据库已启动  2) 当前文档的知识图谱已构建并存入 Neo4j")

        # 可视化学习路径图（如果有图谱数据）
        if "learning_path_graph" in st.session_state and "learning_path_target" in st.session_state:
            st.divider()
            st.subheader("📊 学习路径可视化")
            st.caption("下图展示了从基础知识点到目标知识点的依赖关系，节点颜色越深表示越基础。")
            
            G = st.session_state["learning_path_graph"]
            target = st.session_state["learning_path_target"]
            
            html_content = render_learning_path_echarts(G, target)
            if html_content:
                render_html_safely(html_content, height=550)
            
            # 清除按钮
            if st.button("🗑️ 清除当前可视化", key="clear_viz"):
                del st.session_state["learning_path_graph"]
                del st.session_state["learning_path_target"]
                st.rerun()
