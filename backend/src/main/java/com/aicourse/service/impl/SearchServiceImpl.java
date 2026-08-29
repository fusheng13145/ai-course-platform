package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Note;
import com.aicourse.entity.Question;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.NoteService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全站知识点搜索服务实现（D6）。
 *
 * <p>分词策略与查重引擎（{@code PlagiarismCheckServiceImpl.tokenize}）同源：
 * CJK 连续段滑窗生成 bigram（二字词元）+ 英文/数字按非字母数字切分小写化。
 * 搜索侧按「名称命中 0.7 + 内容命中 0.3」加权评分，命中率 &gt; 0 才进入候选。
 *
 * <p>设计边界：这是轻量聚合搜索（LIKE 预过滤 + bigram 评分），不做倒排索引与向量召回；
 * 向量语义检索属于 RAG 引擎（8002）职责，二者定位互补、互不替代。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Pattern CJK = Pattern.compile("[\\u4E00-\\u9FFF]+");
    private static final int SNIPPET_LEN = 80;

    private final KnowledgePointService knowledgePointService;
    private final QuestionService questionService;
    private final CourseService courseService;
    private final NoteService noteService;

    public SearchServiceImpl(KnowledgePointService knowledgePointService,
                             QuestionService questionService,
                             CourseService courseService,
                             NoteService noteService) {
        this.knowledgePointService = knowledgePointService;
        this.questionService = questionService;
        this.courseService = courseService;
        this.noteService = noteService;
    }

    @Override
    public List<Map<String, Object>> search(String keyword, List<String> types, Long userId, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();
        Set<String> kwTokens = tokenize(kw);
        if (kwTokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<Hit> hits = new ArrayList<>();
        if (types == null || types.isEmpty() || types.contains("knowledge")) {
            hits.addAll(searchKnowledge(kw, kwTokens));
        }
        if (types == null || types.isEmpty() || types.contains("question")) {
            hits.addAll(searchQuestions(kw, kwTokens));
        }
        if (types == null || types.isEmpty() || types.contains("course")) {
            hits.addAll(searchCourses(kw, kwTokens));
        }
        if (types != null && types.contains("note") && userId != null) {
            hits.addAll(searchNotes(kw, kwTokens, userId));
        }

        hits.sort((a, b) -> Double.compare(b.score, a.score));
        int size = Math.min(limit, hits.size());
        List<Map<String, Object>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(hits.get(i).toMap());
        }
        return result;
    }

    // ---------------- 分类型检索 ----------------

    private List<Hit> searchKnowledge(String kw, Set<String> kwTokens) {
        List<KnowledgePoint> list = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .like(KnowledgePoint::getName, kw)
                        .or()
                        .like(KnowledgePoint::getDescription, kw)
                        .orderByDesc(KnowledgePoint::getCreatedAt));
        List<Hit> hits = new ArrayList<>();
        for (KnowledgePoint kp : list) {
            double nameHit = hitRate(kwTokens, tokenize(kp.getName()));
            double descHit = hitRate(kwTokens, tokenize(kp.getDescription()));
            double score = 0.7 * nameHit + 0.3 * descHit;
            if (score > 0) {
                hits.add(new Hit("knowledge", kp.getId(), kp.getName(),
                        snippet(kp.getDescription()), score));
            }
        }
        return hits;
    }

    private List<Hit> searchQuestions(String kw, Set<String> kwTokens) {
        List<Question> list = questionService.list(
                new LambdaQueryWrapper<Question>()
                        .like(Question::getContent, kw)
                        .orderByDesc(Question::getCreatedAt));
        List<Hit> hits = new ArrayList<>();
        for (Question q : list) {
            String content = stripHtml(q.getContent());
            double contentHit = hitRate(kwTokens, tokenize(content));
            // 题干无独立标题字段，其匹配即内容命中：统一按内容权重 0.3 折算，保持四类量纲一致
            if (contentHit > 0) {
                hits.add(new Hit("question", q.getId(),
                        snippet(content),
                        snippet(q.getExplanation()), 0.3 * contentHit));
            }
        }
        return hits;
    }

    private List<Hit> searchCourses(String kw, Set<String> kwTokens) {
        List<Course> list = courseService.list(
                new LambdaQueryWrapper<Course>()
                        .like(Course::getName, kw)
                        .or()
                        .like(Course::getCode, kw)
                        .or()
                        .like(Course::getDescription, kw)
                        .orderByDesc(Course::getCreatedAt));
        List<Hit> hits = new ArrayList<>();
        for (Course c : list) {
            double nameHit = hitRate(kwTokens, tokenize(c.getName()));
            double codeHit = hitRate(kwTokens, tokenize(c.getCode()));
            double descHit = hitRate(kwTokens, tokenize(c.getDescription()));
            double score = 0.7 * Math.max(nameHit, codeHit) + 0.3 * descHit;
            if (score > 0) {
                hits.add(new Hit("course", c.getId(), c.getName(),
                        snippet(c.getDescription()), score));
            }
        }
        return hits;
    }

    private List<Hit> searchNotes(String kw, Set<String> kwTokens, Long userId) {
        List<Note> list = noteService.list(
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .and(w -> w.like(Note::getTitle, kw).or().like(Note::getContent, kw))
                        .orderByDesc(Note::getUpdatedAt));
        List<Hit> hits = new ArrayList<>();
        for (Note n : list) {
            double titleHit = hitRate(kwTokens, tokenize(n.getTitle()));
            double contentHit = hitRate(kwTokens, tokenize(n.getContent()));
            double score = 0.7 * titleHit + 0.3 * contentHit;
            if (score > 0) {
                hits.add(new Hit("note", n.getId(), n.getTitle(),
                        snippet(n.getContent()), score));
            }
        }
        return hits;
    }

    // ---------------- 评分与分词 ----------------

    /** 查询词元在候选文本词元中的命中率（0-1）；候选文本为空返回 0。 */
    private double hitRate(Set<String> queryTokens, Set<String> textTokens) {
        if (queryTokens.isEmpty() || textTokens.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String t : queryTokens) {
            if (textTokens.contains(t)) {
                hit++;
            }
        }
        return (double) hit / queryTokens.size();
    }

    /**
     * 中英文混合分词（与查重引擎 v2.0 同源策略）：
     * 中文连续段滑窗生成 bigram；英文/数字按非字母数字切分、小写化、保留长度 &gt; 1 的词。
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        Matcher cjk = CJK.matcher(text);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
            } else {
                for (int i = 0; i < run.length() - 1; i++) {
                    tokens.add(run.substring(i, i + 2));
                }
            }
        }
        for (String part : text.split("[^a-zA-Z0-9]+")) {
            String word = part.toLowerCase();
            if (word.length() > 1) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /** 去除 HTML 标签（题库富文本题干）。 */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /** 截断为摘要片段。 */
    private String snippet(String text) {
        String clean = stripHtml(text);
        if (clean == null || clean.isEmpty()) {
            return "";
        }
        return clean.length() <= SNIPPET_LEN ? clean : clean.substring(0, SNIPPET_LEN) + "…";
    }

    // ---------------- 内部结果载体 ----------------

    private static final class Hit {
        final String type;
        final Long id;
        final String title;
        final String snippet;
        final double score;

        Hit(String type, Long id, String title, String snippet, double score) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.snippet = snippet;
            this.score = score;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("type", type);
            m.put("id", id);
            m.put("title", title);
            m.put("snippet", snippet);
            m.put("score", score);
            return m;
        }
    }
}
