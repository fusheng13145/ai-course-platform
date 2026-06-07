package com.aicourse.service;

public interface AIService {
    String chat(String message, String context, Long userId);
    String generateQuestion(Long courseId, String type, String difficulty, int count);
    String generateContent(String type, String params);
    String generateSummary(String content);
    String checkPlagiarism(String text1, String text2);
    String ragAsk(String question, Long courseId);
}
