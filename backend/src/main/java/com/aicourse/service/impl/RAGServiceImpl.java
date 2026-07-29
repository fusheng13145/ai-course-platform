package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 服务实现类（自包含版）
 * <p>
 * 原设计通过 HTTP 调用 Python RAG 微服务（端口 8002），但该微服务并未随项目交付，
 * 导致智能答疑（{@code aiService.ragAsk}）整体降级为纯 LLM 调用、且无法基于课程知识库作答。
 * 为满足需求文档「基于 LLM 的 RAG 框架（向量化/语义召回/生成融合，抑制幻觉）」与「智能答疑」
 * 的硬性要求，本实现改为自包含：
 * <ul>
 *   <li>知识库 = {@code knowledge_points} 表中课程的「知识点名称 + 描述」；</li>
 *   <li>召回 = Okapi BM25 算法（中文 bigram 分词，无需外部向量服务）；</li>
 *   <li>生成 = 将 topK 召回片段拼装为 context，交由可插拔的 {@link LLMProvider} 融合生成答案，
 *        并严格约束「仅依据所给知识库、未涵盖时如实说明」，以抑制幻觉；</li>
 *   <li>文档管理接口（增删改查/建索引）直连知识点表，使「知识库管理」真实可用。</li>
 * </ul>
 * 该实现无外部进程依赖；后续若引入 Milvus / Python 向量服务，只需替换 {@code retrieve} 的召回实现。
 */
@Service
@Slf4j
public class RAGServiceImpl implements RAGService {


    private final KnowledgePointService knowledgePointService;
    private final LLMProvider llmProvider;
    private final ObjectMapper objectMapper;
    private final int defaultTopK;

    /** BM25 k1 参数：控制词频饱和度，默认 1.5（标准 Okapi 值） */
    private final double bm25K1;
    /** BM25 b 参数：控制文档长度归一化强度，默认 0.75（标准 Okapi 值） */
    private final double bm25B;

    /**
     * 课程语料预计算索引缓存（courseId → 预计算的 BM25 索引）。
     * 避免每次检索都全量加载知识点 + 逐条分词 + DF 遍历的 O(N*M) 开销，
     * 将大部分计算从检索路径移至缓存构建路径。
     * 线程安全：ConcurrentHashMap + 原子 computeIfAbsent。
     * 失效策略：知识库变更时通过 {@link #evictCorpusCache(Long)} 清除。
     */
    private final ConcurrentHashMap<Long, CachedCorpus> corpusCache = new ConcurrentHashMap<>();

    // @Lazy 打破与 AIServiceImpl 的构造期循环依赖（RAG 内部化引入）：
    // AIServiceImpl -> RAGServiceImpl -> KnowledgePointServiceImpl -> {Question/WrongQuestion}ServiceImpl -> AIServiceImpl
    // RAG 仅在运行时（ask/文档管理）使用 KnowledgePointService 加载语料，构造期无需立即就绪。
    public RAGServiceImpl(@Lazy KnowledgePointService knowledgePointService,
                          LLMProvider llmProvider,
                          ObjectMapper objectMapper,
                          @org.springframework.beans.factory.annotation.Value("${rag.default-top-k:5}") int defaultTopK,
                          @org.springframework.beans.factory.annotation.Value("${rag.bm25.k1:1.5}") double bm25K1,
                          @org.springframework.beans.factory.annotation.Value("${rag.bm25.b:0.75}") double bm25B) {
        this.knowledgePointService = knowledgePointService;
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
        this.defaultTopK = defaultTopK;
        this.bm25K1 = bm25K1;
        this.bm25B = bm25B;
    }

    @Override
    public boolean healthCheck() {
        // 自包含实现，始终可用
        return true;
    }

    @Override
    @Cacheable(cacheNames = "rag",
            key = "#question + '_' + #courseId + '_' + #topK",
            unless = "#result == null or #result.contains('\"code\":503')")
    public String ask(String question, Long courseId, int topK) {
        int k = topK > 0 ? topK : defaultTopK;
        CachedCorpus corpus = getOrBuildCorpus(courseId);
        if (corpus == null || corpus.isEmpty()) {
            return jsonAnswer("知识库中暂未收录与「" + safe(question) + "」相关的知识点，建议先完善该课程的知识图谱或向教师提问。",
                    Collections.emptyList(), "retrieval-only");
        }
        List<ScoredPoint> hits = retrieveScored(question, corpus, k);
        if (hits.isEmpty()) {
            return jsonAnswer("知识库中暂未收录与「" + safe(question) + "」相关的知识点，建议先完善该课程的知识图谱或向教师提问。",
                    Collections.emptyList(), "retrieval-only");
        }

        // 拼装检索上下文
        StringBuilder ctx = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            KnowledgePoint p = hits.get(i).point;
            ctx.append("\n【知识点").append(i + 1).append("】").append(nullToEmpty(p.getName())).append("\n")
               .append(nullToEmpty(p.getDescription())).append("\n");
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", p.getId());
            s.put("name", p.getName());
            s.put("score", round(hits.get(i).score));
            sources.add(s);
        }

        if (llmProvider == null || !llmProvider.isConfigured()) {
            // 未配置大模型：直接回退展示知识库原文，保证「基于知识库的问答」可用且诚实
            String fallback = "（当前未配置大模型 API Key，以下为知识库中与您问题最相关的知识点）\n" + ctx;
            return jsonAnswer(fallback, sources, "retrieval-only");
        }

        String systemPrompt = "你是一个严谨的课程 AI 助教。请严格【仅依据】下面提供的「知识库上下文」回答用户问题，"
                + "使用中文、条理清晰。若上下文不足以回答问题，请明确说明「知识库未涵盖该内容」，"
                + "不要编造或引入上下文之外的知识（抑制幻觉）。可在结尾用「参考知识点：xxx」标注来源。";
        String userPrompt = "用户问题：" + question + "\n\n===== 知识库上下文 =====" + ctx
                + "\n===== 请基于上述上下文作答 =====";

        try {
            String answer = llmProvider.complete(
                    List.of(Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)), 0.3);
            if (answer == null || answer.isBlank()) {
                return jsonAnswer("大模型未返回有效内容，请稍后重试。\n" + ctx, sources, "retrieval-only");
            }
            return jsonAnswer(answer.trim(), sources, llmProvider.name());
        } catch (Exception e) {
            log.warn("RAG 生成失败，回退展示知识库原文: {}", e.getMessage());
            return jsonAnswer("（生成答案时出现问题，以下为知识库相关原文）\n" + ctx, sources, "retrieval-only");
        }
    }

    @Override
    public String addDocument(Long courseId, String documentName, String content) {
        try {
            KnowledgePoint kp = new KnowledgePoint();
            kp.setCourseId(courseId);
            kp.setName(documentName);
            kp.setDescription(content);
            kp.setPointType("DOCUMENT");
            knowledgePointService.save(kp);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "ok");
            r.put("id", kp.getId());
            r.put("name", documentName);
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            log.warn("RAG 添加文档失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"" + safe(e.getMessage()) + "\"}";
        }
    }

    @Override
    public String deleteDocument(Long documentId) {
        try {
            knowledgePointService.removeById(documentId);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "ok");
            r.put("deleted", documentId);
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            log.warn("RAG 删除文档失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"" + safe(e.getMessage()) + "\"}";
        }
    }

    @Override
    public String listDocuments(Long courseId) {
        try {
            List<KnowledgePoint> list = loadCorpus(courseId);
            List<Map<String, Object>> docs = new ArrayList<>();
            for (KnowledgePoint p : list) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", p.getId());
                d.put("name", p.getName());
                d.put("description", p.getDescription());
                d.put("type", p.getPointType());
                docs.add(d);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "ok");
            r.put("count", docs.size());
            r.put("documents", docs);
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            log.warn("RAG 列举文档失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"" + safe(e.getMessage()) + "\"}";
        }
    }

    @Override
    public String updateDocument(Long documentId, String content) {
        try {
            KnowledgePoint p = knowledgePointService.getById(documentId);
            if (p == null) {
                return "{\"status\":\"error\",\"message\":\"文档不存在\"}";
            }
            p.setDescription(content);
            knowledgePointService.updateById(p);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "ok");
            r.put("id", documentId);
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            log.warn("RAG 更新文档失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"" + safe(e.getMessage()) + "\"}";
        }
    }

    @Override
    public String buildIndex(Long courseId) {
        // 自包含实现：知识库索引由 knowledge_points 表实时构建，无需离线建索引
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("note", "索引由课程知识库实时构建，无需离线处理");
        return toJson(r);
    }

    @Override
    public String clearIndex(Long courseId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.put("note", "自包含检索无需清理外部索引");
        return toJson(r);
    }

    @Override
    @Cacheable(cacheNames = "rag-retrieve",
            key = "#query + '_' + #courseId + '_' + #topK",
            unless = "#result == null or #result.contains('\"code\":503')")
    public String retrieve(String query, Long courseId, int topK) {
        int k = topK > 0 ? topK : defaultTopK;
        CachedCorpus corpus = getOrBuildCorpus(courseId);
        List<ScoredPoint> hits = corpus == null ? Collections.emptyList() : retrieveScored(query, corpus, k);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScoredPoint h : hits) {
            KnowledgePoint p = h.point;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("description", p.getDescription());
            m.put("score", round(h.score));
            results.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("query", query);
        r.put("count", results.size());
        r.put("results", results);
        return toJson(r);
    }

    // ===================== 检索核心 =====================

    /**
     * 加载课程知识库语料。
     * 修复：courseId 为空时不再扫描全库（原 LIMIT 1000 在大表下仍有隐患且结果任意），
     * 改为 fail-closed 返回空列表并记警告日志；RAG 应答应始终绑定明确课程上下文。
     */
    private List<KnowledgePoint> loadCorpus(Long courseId) {
        if (courseId == null) {
            log.warn("[RAG] 未提供 courseId，拒绝扫描全库知识库，返回空语料");
            return Collections.emptyList();
        }
        return knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
    }

    /**
     * 基于 Okapi BM25 算法召回 topK（无缓存版本，直接对原始语料计算）。
     */
    private List<ScoredPoint> retrieveScored(String query, List<KnowledgePoint> corpus, int topK) {
        if (query == null || query.isBlank() || corpus.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> qtf = tokenize(query);
        if (qtf.isEmpty()) {
            return Collections.emptyList();
        }

        // 文档频率 + 文档长度
        int N = corpus.size();
        Map<String, Integer> df = new HashMap<>();
        List<Map<String, Integer>> docTfs = new ArrayList<>();
        List<Integer> docLengths = new ArrayList<>();
        int totalLen = 0;
        for (KnowledgePoint p : corpus) {
            String text = nullToEmpty(p.getName()) + " " + nullToEmpty(p.getDescription());
            Map<String, Integer> dtf = tokenize(text);
            docTfs.add(dtf);
            int len = dtf.values().stream().mapToInt(Integer::intValue).sum();
            docLengths.add(len);
            totalLen += len;
            for (String term : dtf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
        double avgDL = N > 0 ? (double) totalLen / N : 1.0;

        List<ScoredPoint> scored = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            if (docTfs.get(i).isEmpty()) continue;
            double score = bm25Score(qtf, docTfs.get(i), i, df, N, docLengths, avgDL);
            if (score > 0) {
                scored.add(new ScoredPoint(corpus.get(i), score));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    /**
     * 基于缓存的 BM25 索引进行相似度召回。
     * 只需要 tokenize 查询文本，文档索引已预计算完毕。
     */
    private List<ScoredPoint> retrieveScored(String query, CachedCorpus corpus, int topK) {
        if (query == null || query.isBlank() || corpus == null || corpus.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> qtf = tokenize(query);
        if (qtf.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredPoint> scored = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            if (corpus.docTfs.get(i).isEmpty()) continue;
            double score = bm25Score(qtf, corpus.docTfs.get(i), i, corpus.df, corpus.N,
                    corpus.docLengths, corpus.avgDL);
            if (score > 0) {
                scored.add(new ScoredPoint(corpus.points.get(i), score));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    // ===================== 缓存构建与失效 =====================

    /**
     * 获取或构建课程语料的 TF-IDF 索引缓存。
     * 首次访问时惰性构建，后续直接复用。
     */
    private CachedCorpus getOrBuildCorpus(Long courseId) {
        if (courseId == null) return null;
        return corpusCache.computeIfAbsent(courseId, this::buildCorpus);
    }

    /**
     * 为指定课程构建 BM25 索引（词频 + 文档频率 + 文档长度统计）。
     * 该计算仅在首次查询或缓存失效时执行一次。
     */
    private CachedCorpus buildCorpus(Long courseId) {
        List<KnowledgePoint> points = loadCorpus(courseId);
        if (points == null || points.isEmpty()) {
            return null;
        }
        // 文档频率 + 词频 + 文档长度
        Map<String, Integer> df = new HashMap<>();
        List<Map<String, Integer>> docTfs = new ArrayList<>(points.size());
        List<Integer> docLengths = new ArrayList<>(points.size());
        int totalLen = 0;
        for (KnowledgePoint p : points) {
            Map<String, Integer> tf = tokenize(textOf(p));
            docTfs.add(tf);
            int len = tf.values().stream().mapToInt(Integer::intValue).sum();
            docLengths.add(len);
            totalLen += len;
            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
        int N = points.size();
        double avgDL = N > 0 ? (double) totalLen / N : 1.0;
        log.info("[RAG] 课程 {} BM25 索引构建完成：{} 个知识点，平均文档长度 {:.1f}", courseId, N, avgDL);
        return new CachedCorpus(points, df, N, docTfs, docLengths, avgDL);
    }

    /**
     * 手动失效指定课程的缓存（知识库变更时由外部调用）。
     */
    public void evictCorpusCache(Long courseId) {
        if (courseId != null) {
            corpusCache.remove(courseId);
            log.info("[RAG] 课程 {} 索引已清除", courseId);
        }
    }

    /** 拼接知识点的可索引文本 */
    private static String textOf(KnowledgePoint p) {
        return nullToEmpty(p.getName()) + " " + nullToEmpty(p.getDescription());
    }

    /**
     * 中文感知分词：CJK 字符取连续 bigram，拉丁/数字取小写词
     */
    private Map<String, Integer> tokenize(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) return tf;
        StringBuilder cjk = new StringBuilder();
        StringBuilder latin = new StringBuilder();
        Runnable flushLatin = () -> {
            if (latin.length() > 0) {
                String w = latin.toString().toLowerCase(Locale.ROOT);
                if (w.length() >= 2) tf.merge(w, 1, Integer::sum);
                latin.setLength(0);
            }
        };
        // 将当前 CJK 片段切成 bigram 并入词频表，然后清空片段。
        // 关键修复：遇到标点时必须先结算已积累的 CJK 片段，否则会整段丢弃（旧实现 cjk.setLength(0) 直接清空，
        // 导致以标点结尾/包含标点的知识点描述词频恒为空、召回恒为 0）。
        Runnable flushCjk = () -> {
            if (cjk.length() > 1) {
                String s = cjk.toString();
                for (int i = 0; i + 1 < s.length(); i++) {
                    tf.merge(s.substring(i, i + 2), 1, Integer::sum);
                }
            }
            cjk.setLength(0);
        };
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isIdeographic(c)) {
                flushLatin.run();
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else {
                // 标点等非语义字符：先结算已积累的 CJK 片段，再清空
                flushLatin.run();
                flushCjk.run();
            }
        }
        flushLatin.run();
        flushCjk.run();
        return tf;
    }

    // ===================== BM25 评分 =====================

    /**
     * Okapi BM25 评分：对查询中的每个词项计算 IDF * TF 归一化得分并求和。
     *
     * @param queryTerms  查询词项 → 频率
     * @param docTf       文档词项 → 频率
     * @param docIdx      文档索引（用于获取文档长度）
     * @param df          全局文档频率
     * @param N           文档总数
     * @param docLengths  各文档长度
     * @param avgDL       平均文档长度
     * @return BM25 得分（非归一化，仅用于排序）
     */
    private double bm25Score(Map<String, Integer> queryTerms,
                              Map<String, Integer> docTf,
                              int docIdx,
                              Map<String, Integer> df,
                              int N,
                              List<Integer> docLengths,
                              double avgDL) {
        double score = 0;
        double docLen = docLengths.get(docIdx);
        for (Map.Entry<String, Integer> qe : queryTerms.entrySet()) {
            String term = qe.getKey();
            int tf = docTf.getOrDefault(term, 0);
            if (tf == 0) continue;
            int d = df.getOrDefault(term, 0);
            if (d == 0) continue;
            // BM25 IDF: log((N - df + 0.5) / (df + 0.5) + 1)
            double idf = Math.log((N - d + 0.5) / (d + 0.5) + 1.0);
            // 文档长度归一化
            double lengthNorm = 1.0 - bm25B + bm25B * docLen / avgDL;
            double numerator = tf * (bm25K1 + 1.0);
            double denominator = tf + bm25K1 * lengthNorm;
            score += idf * numerator / denominator;
        }
        return score;
    }

    // ===================== 工具 =====================

    private String jsonAnswer(String answer, List<Map<String, Object>> sources, String model) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("answer", answer);
        r.put("sources", sources);
        r.put("model", model);
        return toJson(r);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("RAG JSON 序列化失败: {}", e.getMessage());
            return "{\"error\":\"序列化失败\",\"code\":500}";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** 召回结果承载 */
    private static class ScoredPoint {
        final KnowledgePoint point;
        final double score;
        ScoredPoint(KnowledgePoint point, double score) {
            this.point = point;
            this.score = score;
        }
    }

    /**
     * 课程语料预计算索引：存储 BM25 召回所需的全部预计算数据。
     * 文档频率、各文档词频与长度在缓存构建时一次性计算，查询阶段仅做 BM25 评分。
     */
    private static class CachedCorpus {
        final List<KnowledgePoint> points;
        final Map<String, Integer> df;          // 文档频率（全局）
        final int N;                             // 文档总数
        final List<Map<String, Integer>> docTfs; // 各文档的词频
        final List<Integer> docLengths;          // 各文档的 term 总数
        final double avgDL;                      // 平均文档长度（BM25 长度归一化用）

        CachedCorpus(List<KnowledgePoint> points, Map<String, Integer> df, int N,
                     List<Map<String, Integer>> docTfs, List<Integer> docLengths, double avgDL) {
            this.points = points;
            this.df = df;
            this.N = N;
            this.docTfs = docTfs;
            this.docLengths = docLengths;
            this.avgDL = avgDL;
        }

        boolean isEmpty() {
            return points == null || points.isEmpty();
        }

        int size() {
            return points == null ? 0 : points.size();
        }
    }
}
