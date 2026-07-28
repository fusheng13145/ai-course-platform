package com.aicourse.service;

import com.aicourse.entity.ExamSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ExamSessionService extends IService<ExamSession> {

    /**
     * 开始考试（含规则校验：时间窗口、尝试次数限制）
     * @param examPaperId 试卷ID
     * @param studentId 学生ID
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @return 考试会话
     */
    ExamSession startExam(Long examPaperId, Long studentId, String ipAddress, String userAgent);

    /**
     * 自动保存作答答案
     * @param sessionId 会话ID
     * @param answers 答案Map
     */
    void autoSaveAnswers(Long sessionId, Map<String, Object> answers);

    /**
     * 提交试卷（含防重复提交）
     * @param sessionId 会话ID
     * @param answers 答案Map
     * @return 提交后的会话
     */
    ExamSession submitExam(Long sessionId, Map<String, Object> answers);

    /**
     * 强制交卷（管理员操作）
     * @param sessionId 会话ID
     * @return 强制提交后的会话
     */
    ExamSession forceSubmit(Long sessionId);

    /**
     * 自动阅卷（客观题：单选/多选/判断）
     * @param sessionId 会话ID
     * @return 阅卷结果
     */
    Map<String, Object> autoGrade(Long sessionId);

    /**
     * 人工阅卷（主观题）
     * @param sessionId 会话ID
     * @param questionId 题目ID
     * @param score 得分
     * @param comment 评语
     */
    void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment);

    /**
     * 计算试卷总分
     * @param sessionId 会话ID
     * @return 总分
     */
    BigDecimal calculateTotalScore(Long sessionId);

    /**
     * 生成成绩报告
     * @param sessionId 会话ID
     * @return 成绩报告
     */
    Map<String, Object> generateScoreReport(Long sessionId);

    /**
     * 获取正在进行的考试列表
     * @return 进行中的考试会话列表
     */
    List<ExamSession> getActiveExams();

    /**
     * 检测作弊嫌疑
     * @param sessionId 会话ID
     * @return 作弊嫌疑列表
     */
    List<String> detectCheating(Long sessionId);

    /**
     * 获取考试剩余时间(秒)
     * @param sessionId 会话ID
     * @return 剩余秒数
     */
    Long getRemainingTime(Long sessionId);

    /**
     * 获取学生某试卷的最新会话
     * @param examPaperId 试卷ID
     * @param studentId 学生ID
     * @return 最新会话
     */
    ExamSession getLatestSession(Long examPaperId, Long studentId);

    /**
     * 作弊检测与标记（检查IP变化、异常行为等，将检测结果写入记录）
     * @param sessionId 会话ID
     * @return 作弊检测结果（包含检测到的可疑项列表和综合判定）
     */
    Map<String, Object> checkCheating(Long sessionId);

    /**
     * 获取考试会话中的题目列表（含学生答案）
     * @param sessionId 会话ID
     * @return 题目与答案列表
     */
    List<Map<String, Object>> getSessionQuestions(Long sessionId);

    /**
     * 超时自动提交
     * @param sessionId 会话ID
     */
    void timeoutSubmit(Long sessionId);
}
