import streamlit as st
from utils.build_graph import retrieve_from_graph
from langchain_core.documents import Document
import requests
import re


# 🚀 Query Expansion with HyDE
def expand_query(query, uri, model):
    try:
        # 💡 关键修复：提供极其详尽、学术化的 HyDE 提示词，提升大模型生成高质量文档向量的概率
        sys_prompt = f"""你是一位资深的计算机专业教授。当前任务是为AI助教系统的向量检索器（HyDE策略）生成一段高质量的“假设性标准教材片段”。
        
        【核心目标】
        用户的提问可能很简短或不规范。你需要凭借深厚的专业知识，设想一篇包含丰富专业术语、学术定义和原理解释的标准解答段落。系统将利用你生成的这段富含特征词的文本，去课程资料库中进行高精度的语义向量匹配。

        【生成要求】
        1. 术语密集：必须密集地使用与用户提问强相关的官方学术术语、底层协议、算法名称，以大幅提高检索命中率。
        2. 科学严谨：内容必须符合计算机科学的严谨学术定义，逻辑客观，不夹杂主观推测。
        3. 长度控制：不要啰嗦，字数控制在150-300字之间，构建出完美的检索特征词上下文即可。
        4. 严格输出：请直接输出假设性教材段落的正文！绝对不要输出任何诸如“好的”、“这段假设性答案如下”的寒暄或解释性废话。

        用户提问：
        {query}
        """
        response = requests.post(
            uri,
            json={
                "model": model,
                "prompt": sys_prompt,
                "stream": False,
            },
        ).json()

        res_text = response.get("response", "")
        # 💡 关键修复：剥离 DeepSeek 思考标签，避免污染检索向量
        res_text = re.sub(r"<think>.*?</think>", "", res_text, flags=re.DOTALL).strip()

        return f"{query}\n{res_text}"
    except Exception as e:
        st.error(f"HyDE 扩展查询失败: {str(e)}")
        return query


# 🚀 Advanced Retrieval Pipeline
def retrieve_documents(query, uri, model, chat_history=""):
    expanded_query = (
        expand_query(f"{chat_history}\n{query}", uri, model)
        if st.session_state.get("enable_hyde", True)
        else query
    )

    # 🔍 BM25 + FAISS 检索
    retrieval_pipeline = st.session_state.get("retrieval_pipeline", {})
    ensemble = retrieval_pipeline.get("ensemble") if retrieval_pipeline else None
    if ensemble:
        docs = ensemble.invoke(expanded_query)
    else:
        docs = []


    # 🚀 GraphRAG 检索
    if st.session_state.get("enable_graph_rag", True):
        kg = retrieval_pipeline.get("knowledge_graph") if retrieval_pipeline else None
        graph_results = retrieve_from_graph(query, kg) if kg else []



        graph_docs = []
        for node in graph_results:
            graph_docs.append(Document(page_content=node))

        if graph_docs:
            docs = graph_docs + docs

    print("========== 追踪点 1：准备检查 docs ==========", flush=True)

    if not docs:
        print("========== 追踪点 2：docs 为空，成功拦截！ ==========", flush=True)
        return []

    print(
        f"========== 追踪点 3：docs 不为空，共有 {len(docs)} 个片段，准备进入重排 ==========",
        flush=True,
    )

    # 🚀 Neural Reranking (if enabled)
    reranker = retrieval_pipeline.get("reranker") if retrieval_pipeline else None
    if st.session_state.get("enable_reranking", True) and reranker:
        pairs = [[query, doc.page_content] for doc in docs]
        print("========== 追踪点 4：送入重排模型前，准备完毕 ==========", flush=True)

        scores = reranker.predict(pairs)


        print("========== 追踪点 5：重排计算成功！ ==========", flush=True)

        ranked_docs = [
            doc
            for _, doc in sorted(zip(scores, docs), reverse=True, key=lambda x: x[0])
        ]

        # 💡 关键修复：使用 .get 获取 max_contexts，永远不会再出现初始化报错
        max_c = st.session_state.get("max_contexts", 3)
        return ranked_docs[:max_c]

    max_c = st.session_state.get("max_contexts", 3)
    return docs[:max_c]
