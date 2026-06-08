package com.aicourse.service;

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
     * @return 问答结果
     */
    String ragAsk(String question, Long courseId);
}