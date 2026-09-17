package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.submission.SubmissionGradeComponent;
import com.aicourse.service.component.submission.SubmissionJsonComponent;
import com.aicourse.service.component.submission.SubmissionReadComponent;
import com.aicourse.service.component.submission.SubmissionWriteComponent;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 提交服务门面：写/评分/读查重职责委托给 service.component 包下协作者组件。
 * <p>拆分自原 806 行单类（2026-08-29）。保留 extends ServiceImpl 以承载 IService 能力
 * （全仓多处调用 submissionService.list/getById/count/page 等）；@Caching/@Transactional 注解
 * 保留在门面委托方法上（Spring 缓存/事务代理按方法拦截）。</p>
 */
@Service
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final SubmissionWriteComponent writeComponent;
    private final SubmissionGradeComponent gradeComponent;
    private final SubmissionReadComponent readComponent;

    @Autowired
    public SubmissionServiceImpl(SubmissionWriteComponent writeComponent,
                                 SubmissionGradeComponent gradeComponent,
                                 SubmissionReadComponent readComponent) {
        this.writeComponent = writeComponent;
        this.gradeComponent = gradeComponent;
        this.readComponent = readComponent;
    }

    // ===================== 写：提交/草稿/重交/退回 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission submitAssignment(Long taskId, Long studentId, String content, List<String> fileUrls) {
        return writeComponent.submitAssignment(taskId, studentId, content, fileUrls);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission saveDraft(Long taskId, Long studentId, String content, List<String> fileUrls) {
        return writeComponent.saveDraft(taskId, studentId, content, fileUrls);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission resubmit(Long submissionId, String content, List<String> fileUrls) {
        return writeComponent.resubmit(submissionId, content, fileUrls);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission returnSubmission(Long submissionId, String reason) {
        return writeComponent.returnSubmission(submissionId, reason);
    }

    // ===================== 评分：教师评分/教师端批改/AI评分/逾期重算 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "analytics", key = "'platform'"),
            @CacheEvict(cacheNames = "analytics", key = "'overview'"),
            @CacheEvict(cacheNames = "analytics", key = "'teacher_' + #graderId")
    })
    public Submission gradeSubmission(Long submissionId, BigDecimal score, String feedback, Long graderId) {
        return gradeComponent.gradeSubmission(submissionId, score, feedback, graderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission gradeSubmissionByTeacher(Long assignmentId, Long submissionId, Submission grade, Long graderId) {
        return gradeComponent.gradeSubmissionByTeacher(assignmentId, submissionId, grade, graderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Submission aiGradeSubmission(Long submissionId) {
        return gradeComponent.aiGradeSubmission(submissionId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateLatePenalty(Long submissionId) {
        return gradeComponent.calculateLatePenalty(submissionId);
    }

    // ===================== 读/查重：计数/查重/查询/摘要/详情 =====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(Long submissionId) {
        readComponent.incrementViewCount(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlagiarismCheck triggerPlagiarismCheck(Long submissionId) {
        return readComponent.triggerPlagiarismCheck(submissionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Submission getByTaskAndStudent(Long taskId, Long studentId) {
        return readComponent.getByTaskAndStudent(taskId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPlagiarismSummary(Long submissionId) {
        return readComponent.getPlagiarismSummary(submissionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSubmissionDetail(Long submissionId) {
        return readComponent.getSubmissionDetail(submissionId);
    }
}
