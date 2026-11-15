package com.aicourse.service;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


/**
 * 提交接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface SubmissionService extends IService<Submission> {

    /**
     * 任务集合维度按日提交数聚合（V7 批：教师驾驶舱 14 天趋势的单条 GROUP BY）。
     * 无记录的日期不出行（date=yyyy-MM-dd, subs=当日提交数），由调用方补零；taskIds 为空时调用方短路。
     */
    java.util.List<java.util.Map<String, Object>> dailySubmissionsByTasks(java.util.List<Long> taskIds, java.time.LocalDateTime since);

    /**
     * 提交作业（含逾期判断）
     * @param taskId 任务ID
     * @param studentId 学生ID
     * @param content 文字内容
     * @param fileUrls 附件URL列表
     * @return 提交记录
     */
    Submission submitAssignment(Long taskId, Long studentId, String content, List<String> fileUrls);

    /**
     * 重新提交
     * @param submissionId 提交记录ID
     * @param content 文字内容
     * @param fileUrls 附件URL列表
     * @return 重新提交记录
     */
    Submission resubmit(Long submissionId, String content, List<String> fileUrls);

    /**
     * 退回作业
     * @param submissionId 提交记录ID
     * @param reason 退回原因
     * @return 退回后的提交记录
     */
    Submission returnSubmission(Long submissionId, String reason);

    /**
     * 教师评分
     * @param submissionId 提交记录ID
     * @param score 得分
     * @param feedback 反馈评语
     * @param graderId 批阅人ID
     * @return 评分后的提交记录
     */
    Submission gradeSubmission(Long submissionId, BigDecimal score, String feedback, Long graderId);

    /**
     * 教师端批改作业（/teacher/assignments/{id}/submissions/{sid} 接口业务下沉）：
     * 校验提交记录存在且属于指定作业，其余语义与 {@link #gradeSubmission} 完全一致
     * （2026-11-14 P1-2 批阅语义统一：此前本路径不含逾期扣分/百分比/reviewCount，
     * 同一「教师批阅」动作两条入口行为分叉，现收口为单一落库口径）。
     *
     * @param assignmentId 作业ID（用于归属一致性校验）
     * @param submissionId 提交记录ID
     * @param grade        评分数据（score/feedback）
     * @param graderId     批阅人ID
     * @return 批改后的提交记录
     */
    Submission gradeSubmissionByTeacher(Long assignmentId, Long submissionId, Submission grade, Long graderId);

    /**
     * AI智能评分
     * @param submissionId 提交记录ID
     * @return AI评分后的提交记录
     */
    Submission aiGradeSubmission(Long submissionId);

    /**
     * 计算逾期扣分
     * @param submissionId 提交记录ID
     * @return 逾期扣分金额
     */
    BigDecimal calculateLatePenalty(Long submissionId);

    /**
     * 增加查看次数
     * @param submissionId 提交记录ID
     */
    void incrementViewCount(Long submissionId);

    /**
     * 触发查重检测
     * @param submissionId 提交记录ID
     * @return 查重检测结果
     */
    PlagiarismCheck triggerPlagiarismCheck(Long submissionId);

    /**
     * 获取学生的任务提交记录
     * @param taskId 任务ID
     * @param studentId 学生ID
     * @return 提交记录（按 attemptNumber 倒序、createdAt 倒序取最新一条）
     */
    Submission getByTaskAndStudent(Long taskId, Long studentId);

    /**
     * 获取提交的查重摘要信息
     * @param submissionId 提交记录ID
     * @return 查重摘要（包含相似度、详情、风险等级等）
     */
    Map<String, Object> getPlagiarismSummary(Long submissionId);

    /**
     * 获取提交的完整详细信息（含批阅次数、查重、逾期等扩展数据）
     * @param submissionId 提交记录ID
     * @return 完整详情Map
     */
    Map<String, Object> getSubmissionDetail(Long submissionId);

    /**
     * 保存作业草稿（对标超星：可先存草稿再正式提交）
     * @param taskId 任务ID
     * @param studentId 学生ID
     * @param content 文字内容
     * @param fileUrls 附件URL列表
     * @return 提交记录
     */
    Submission saveDraft(Long taskId, Long studentId, String content, List<String> fileUrls);
}
