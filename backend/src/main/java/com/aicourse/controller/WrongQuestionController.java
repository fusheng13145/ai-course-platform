package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.WrongQuestionPracticeDTO;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.WrongQuestionGroupVO;
import com.aicourse.vo.WrongQuestionStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 错题本接口控制器
 * 提供错题记录、掌握管理、练习、统计、推荐等RESTful API
 */
@RestController
@RequestMapping("/wrong-questions")
@Tag(name = "错题本")
public class WrongQuestionController {

    private static final Logger log = LoggerFactory.getLogger(WrongQuestionController.class);

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 获取错题本列表（分页）
     * @param courseId 课程ID（可选）
     * @param page 页码
     * @param size 每页大小
     * @param auth 认证信息
     * @return 分页错题列表
     */
    @GetMapping
    public ApiResponse<PagedResponse<WrongQuestion>> list(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(userId, courseId, page, size);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 记录错题（考试/练习时自动调用）
     * @param params 错题参数（questionId, courseId, wrongAnswer, sourceType）
     * @param auth 认证信息
     * @return 错题记录
     */
    @PostMapping
    public ApiResponse<WrongQuestion> record(@Valid @RequestBody Map<String, String> params, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long questionId = Long.valueOf(params.get("questionId"));
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId")) : null;
        String wrongAnswer = params.get("wrongAnswer");
        String sourceType = params.getOrDefault("sourceType", "PRACTICE");

        WrongQuestion wrongQuestion = wrongQuestionService.recordWrongAnswer(
                userId, questionId, courseId, wrongAnswer, sourceType);
        return ApiResponse.success("错题记录成功", wrongQuestion);
    }

    /**
     * 标记已掌握
     * @param id 错题ID
     * @return 操作结果
     */
    @PatchMapping("/{id}/mastered")
    public ApiResponse<Void> markMastered(@PathVariable Long id, Authentication auth) {
        requireOwnedWrongQuestion(id, auth);
        wrongQuestionService.markMastered(id);
        return ApiResponse.success("已标记为掌握", null);
    }

    /**
     * 取消掌握标记（再次答错时调用）
     * @param id 错题ID
     * @return 操作结果
     */
    @PatchMapping("/{id}/unmaster")
    public ApiResponse<Void> unmarkMastered(@PathVariable Long id, Authentication auth) {
        requireOwnedWrongQuestion(id, auth);
        wrongQuestionService.unmarkMastered(id);
        return ApiResponse.success("已取消掌握标记", null);
    }

    /**
     * 获取未掌握的错题
     * @param courseId 课程ID（可选）
     * @param auth 认证信息
     * @return 未掌握的错题列表
     */
    @GetMapping("/unmastered")
    public ApiResponse<List<WrongQuestion>> getUnmastered(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<WrongQuestion> unmasteredList = wrongQuestionService.getUnmastered(userId, courseId);
        return ApiResponse.success(unmasteredList);
    }

    /**
     * 按知识点分类的错题。
     *
     * <p>修复（2026-09-23，P2-8）：原返回 {@code Map<String, List<WrongQuestion>>}，
     * 其键为知识点的原始 JSON 文本（如 {@code ["Python基础"]}），而前端按
     * {@code Array<{knowledgePointId, knowledgePointName, questions}>} 消费 ——
     * 结构不匹配使「按知识点补标签」增强逻辑静默失效。现改为结构化分组数组。</p>
     *
     * @param courseId 课程ID（可选）
     * @param auth 认证信息
     * @return 按知识点分组的错题列表（按题目数量倒序）
     */
    @GetMapping("/by-kp")
    public ApiResponse<List<WrongQuestionGroupVO>> getByKnowledgePoints(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<WrongQuestionGroupVO> grouped = wrongQuestionService.getByKnowledgePoints(userId, courseId);
        return ApiResponse.success(grouped);
    }

    /**
     * 错题统计
     * @param courseId 课程ID（可选）
     * @param auth 认证信息
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<WrongQuestionStatsVO> stats(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        WrongQuestionStatsVO stats = wrongQuestionService.getWrongQuestionStats(userId, courseId);
        return ApiResponse.success(stats);
    }

    /**
     * 生成错题练习卷（随机选取N道未掌握错题对应的题目）
     * @param params 参数（courseId, count）
     * @param auth 认证信息
     * @return 练习题目列表
     */
    @PostMapping("/practice-paper")
    public ApiResponse<List<Question>> generatePracticePaper(
            @Valid @RequestBody WrongQuestionPracticeDTO params, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int count = params.getCount() != null ? params.getCount() : 10;
        List<Question> questions = wrongQuestionService.generatePracticePaper(
                userId, params.getCourseId(), count);
        return ApiResponse.success(questions);
    }

    /**
     * 推荐相关题目（基于同一知识点的其他题目）
     * @param id 错题ID
     * @return 推荐的相关题目列表
     */
    @GetMapping("/{id}/related")
    public ApiResponse<List<Question>> recommendRelated(@PathVariable Long id) {
        List<Question> questions = wrongQuestionService.recommendRelatedQuestions(id);
        return ApiResponse.success(questions);
    }

    /**
     * 修改错题（学生/管理员权限）
     * @param id 错题ID
     * @param wq 待更新错题数据
     * @return 更新后错题信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<WrongQuestion> update(@PathVariable Long id, @Valid @RequestBody WrongQuestion wq,
                                             Authentication auth) {
        requireOwnedWrongQuestion(id, auth);
        // 白名单更新：仅允许学生修正错因/AI分析/知识点，杜绝通过请求体注入 studentId/courseId 等字段
        WrongQuestion update = new WrongQuestion();
        update.setId(id);
        update.setReason(wq.getReason());
        update.setAiAnalysis(wq.getAiAnalysis());
        update.setKnowledgePointName(wq.getKnowledgePointName());
        wrongQuestionService.updateById(update);
        WrongQuestion updatedWq = wrongQuestionService.getById(id);
        return ApiResponse.success("更新成功", updatedWq);
    }

    /**
     * 删除错题（学生/管理员权限）
     * @param id 错题ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        requireOwnedWrongQuestion(id, auth);
        wrongQuestionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    // ===================== 对象级授权 =====================

    private WrongQuestion requireOwnedWrongQuestion(Long id, Authentication auth) {
        WrongQuestion wq = wrongQuestionService.getById(id);
        if (wq == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "错题记录不存在");
        }
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !userId.equals(wq.getStudentId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作他人错题");
        }
        return wq;
    }
}
