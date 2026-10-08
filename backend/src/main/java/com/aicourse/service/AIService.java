package com.aicourse.service;

import com.aicourse.vo.QaAnswerVO;

/**
 * AI 能力业务接口
 */
public interface AIService {

    /**
     * AI 对话交互
     * @param message 用户提问消息
     * @param context 上下文内容
     * @param userId 用户ID
     * @return 回答内容
     */
    String chat(String message, String context, Long userId);

    /**
     * AI 多轮对话交互（直接传入完整消息历史）
     * @param messages 角色-内容消息列表（role/content）
     * @param userId 用户ID
     * @return 回答内容
     */
    String chat(java.util.List<java.util.Map<String, String>> messages, Long userId);

    /**
     * AI 生成题目
     * @param courseId 课程ID
     * @param type 题目类型
     * @param difficulty 难度等级
     * @param count 题目数量
     * @return 生成结果
     */
    String generateQuestion(Long courseId, String type, String difficulty, int count);

    /**
     * 智能内容生成
     * @param type 内容类型
     * @param params 扩展参数
     * @return 生成内容
     */
    String generateContent(String type, String params);

    /**
     * 文本内容摘要总结
     * @param content 原始文本
     * @return 摘要内容
     */
    String generateSummary(String content);

    /**
     * 文本相似度/抄袭检测
     * @param text1 文本一
     * @param text2 文本二
     * @return 检测结果
     */
    String checkPlagiarism(String text1, String text2);

    /**
     * 基于知识库的 RAG 问答
     * @param question 提问内容
     * @param courseId 课程ID
     * @return 答案与检索出处（降级为纯 LLM 直答时 sources 为空列表）
     */
    QaAnswerVO ragAsk(String question, Long courseId);

    // ===================== 结构化输出（健壮性增强） =====================

    /**
     * 薄弱点结构化分析：返回 JSON 文本 {"weakPoints":[{"description","severity"}]}
     */
    String analyzeWeakPointsJson(String statsJson);

    /**
     * 将模型返回的薄弱点 JSON 健壮解析为列表（失败回退为单条文本）
     */
    java.util.List<java.util.Map<String, String>> parseWeakPoints(String jsonText);

    /**
     * 题目推荐结构化输出：返回 JSON 文本 {"questions":[{"content","type","difficulty"}]}
     */
    String recommendQuestionsJson(Long courseId, String difficulty, int count);

    /**
     * 学习建议结构化输出：返回 JSON 文本 {"recommendations":[{"resource","reason"}]}
     */
    String recommendLearningJson(String courseList);

    /**
     * 基于真实学情数据的学习推荐（增强版）：
     * <p>根据学生的知识掌握度、课程进度、薄弱项等真实数据做数据驱动推荐，LLM 仅负责解释生成。
     * context 参数可包含：courseList(课程名)、progress(进度)、weakPoints([{name,mastery}])、recentScores
     */
    String recommendLearningWithContext(java.util.Map<String, Object> context);

    /**
     * 知识图谱 AI 抽取：从课程内容中抽取知识点与关系，返回结构化 JSON
     * 格式：{"nodes":[{"name","content","type"}],"relations":[{"source","target","relationType"}]}
     */
    String extractKnowledgeGraphJson(String content);

    /**
     * 学情 AI 建议：聚合学情统计与薄弱点，生成结构化学习建议
     * 返回 JSON 文本 {"summary":"...","items":[{"title","detail","level"}]}
     */
    String generateLearningAdviceJson(String statsJson, String weakPointsJson);

    /**
     * 管理员 AI 监控：基于日志文本检测异常，返回结构化洞察
     * 返回 JSON 文本 {"alerts":[{"level":"HIGH|MEDIUM|LOW","title","detail"}]}
     */
    String analyzeAnomaliesJson(String logsJson);

    /**
     * 将题目推荐 JSON 健壮解析为列表（兼容 ```json ``` 代码块 / 裸 JSON）。
     * 解析失败时回退为单条文本记录（与原 Controller 内联实现行为一致）。
     * @param jsonText LLM 返回的题目推荐 JSON 文本
     * @param diff 默认难度（单条记录未携带难度时使用）
     * @return 题目推荐列表 [{content, type, difficulty}]
     */
    java.util.List<java.util.Map<String, Object>> parseQuestions(String jsonText, String diff);

    /**
     * 知识点薄弱项分析：基于用户学情统计生成薄弱点列表。
     * 内部完成学情 JSON 序列化、LLM 分析调用与健壮解析；
     * 失败时返回单条降级提示（不抛异常，与原 HTTP 行为一致）。
     * @param userStats 用户学情统计数据（AnalyticsService.getUserDashboard 的结果）
     * @return 薄弱点列表 [{description, severity}]
     */
    java.util.List<java.util.Map<String, String>> analyzeWeakPoints(java.util.Map<String, Object> userStats);

    /**
     * 学情 AI 建议：基于学情统计生成结构化学习建议 {"summary","items"}。
     * 内部完成学情 JSON 序列化、薄弱点分析与学习建议生成、结果健壮解析；
     * LLM 未返回有效内容时抛出 {@link com.aicourse.exception.BusinessException}（与原 HTTP 行为一致）。
     * @param userStats 用户学情统计数据
     * @return 结构化建议 {"summary": String, "items": [{title, detail, level}]}
     */
    java.util.Map<String, Object> generateLearningAdvice(java.util.Map<String, Object> userStats);

    /**
     * 当前是否有可用的 LLM 供应商（已配置 Key）；未配置时调用结构化方法会返回清晰提示而非崩溃
     */
    boolean isProviderConfigured();

    /**
     * 流式 AI 对话（SSE 逐 token 推送）
     * <p>
     * 与 {@link #chat} 不同，本方法通过 SSE 流式返回回答内容，前端可实时渲染打字机效果。
     * 底层委托至 {@link com.aicourse.llm.LLMProvider#stream}，请求体自动附带
     * {@code "stream": true} 并逐行解析 delta.content。
     *
     * @param question 用户提问内容（会经 PromptGuard 注入中和）
     * @return 流式内容增量（由 reactor.core.publisher.Flux 封装，供 Spring MVC SSE 响应使用）
     */
    reactor.core.publisher.Flux<String> streamChat(String question);
}