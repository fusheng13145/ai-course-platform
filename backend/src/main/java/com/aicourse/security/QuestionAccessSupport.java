package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.QuestionService;
import com.aicourse.service.component.ai.AiGenerationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 题库控制器拆分的共享校验。
 *
 * <p>题目存在性与 AI 出题治理闸由读面 / 写面 / AI 面三个控制器共用，
 * 避免同一规则在三处各写一份。</p>
 */
@Component
public class QuestionAccessSupport {

    private final QuestionService questionService;
    /** 角色矩阵「限制教师出题权限」的判定：平台级治理开关（ADMIN 不受限）。 */
    private final AiGenerationPolicy aiGenerationPolicy;

    public QuestionAccessSupport(QuestionService questionService,
                                 AiGenerationPolicy aiGenerationPolicy) {
        this.questionService = questionService;
        this.aiGenerationPolicy = aiGenerationPolicy;
    }

    public Question requireQuestion(Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    /**
     * AI 出题权限校验（**两层**，2026-10-13 修订为"平台总闸 + 逐人名单"）：
     * ① 管理员可在管理端关闭「教师 AI 出题」总闸 → 对所有非 ADMIN 调用方 403（紧急一键全禁）；
     * ② 管理员可把**特定教师**加入限制名单 → 只对该教师 403。
     * ADMIN 两层都不受限（治理者保留能力）。
     *
     * <p>刻意<b>不</b>改 {@code @PreAuthorize}（那是角色门，与治理开关是两件事）：
     * 角色门写死"谁能进来"，本校验表达"进来之后当前是否被允许"。</p>
     */
    public void assertQuestionGenerationAllowed(Authentication auth) {
        if (RoleUtil.isAdmin(auth)) {
            return;
        }
        Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        if (!aiGenerationPolicy.isTeacherQuestionGenerationEnabled()) {
            throw new BusinessException(ApiCode.FORBIDDEN,
                    "管理员已限制教师的 AI 出题权限，请联系管理员开通后再试。");
        }
        if (aiGenerationPolicy.isUserRestricted(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN,
                    "该账号已被管理员限制 AI 出题权限，请联系管理员解除后再试。");
        }
    }
}
