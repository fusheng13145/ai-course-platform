package com.aicourse.service;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

public interface SubmissionService extends IService<Submission> {

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
     * @return 提交记录（最新一条）
     */
    Submission getByTaskAndStudent(Long taskId, Long studentId);
}
