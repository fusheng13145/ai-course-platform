package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.vo.QuestionVO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.ExamPaperQuestionVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 试卷管理接口控制器
 */
@Tag(name = "试卷管理", description = "试卷的新增、查询、修改、删除、发布、自动组卷等功能")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperController {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperController.class);

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ExamSessionService examSessionService;
    private final com.aicourse.security.ExamPaperAccessControl examPaperAccessControl;

    public ExamPaperController(ExamPaperService examPaperService,
                               ExamQuestionService examQuestionService,
                               QuestionService questionService,
                               ExamSessionService examSessionService,
                               com.aicourse.security.ExamPaperAccessControl examPaperAccessControl) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.examSessionService = examSessionService;
        this.examPaperAccessControl = examPaperAccessControl;
    }

    /**
     * 分页查询试卷列表（支持课程筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param courseId 课程ID
     * @return 分页试卷数据
     */
    @GetMapping
    @Operation(summary = "分页查询试卷列表")
    public ApiResponse<PagedResponse<ExamPaper>> listByParams(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<ExamPaper> queryWrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            queryWrapper.eq(ExamPaper::getCourseId, courseId);
        }
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(examPaperPage));
    }

    /**
     * 根据课程ID分页查询试卷
     * @param courseId 课程ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页试卷数据
     */
    @GetMapping("/course/{courseId}")
    @Operation(summary = "根据课程ID分页查询试卷")
    public ApiResponse<PagedResponse<ExamPaper>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<ExamPaper> queryWrapper = new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getCourseId, courseId);
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(examPaperPage));
    }

    /**
     * 根据ID查询试卷详情
     * @param id 试卷ID
     * @return 试卷信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询试卷详情")
    public ApiResponse<ExamPaper> getById(@PathVariable Long id) {
        ExamPaper examPaper = examPaperService.getById(id);
        return ApiResponse.success(examPaper);
    }

    /**
     * 新增试卷（教师/管理员权限）
     * @param exam 试卷信息
     * @param auth 认证信息
     * @return 新增试卷数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "新增试卷")
    public ApiResponse<ExamPaper> create(@Valid @RequestBody ExamPaper exam, Authentication auth) {
        exam.setCreatorId((Long) auth.getPrincipal());
        examPaperService.save(exam);
        return ApiResponse.success("创建成功", exam);
    }

    /**
     * 修改试卷（教师/管理员权限）
     * @param id 试卷ID
     * @param exam 待更新试卷数据
     * @return 更新后试卷信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改试卷")
    public ApiResponse<ExamPaper> update(@PathVariable Long id, @Valid @RequestBody ExamPaper exam, Authentication auth) {
        // 归属校验（2026-09-14 修复）：仅试卷所属课程的授课教师或管理员可修改
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        exam.setId(id);
        examPaperService.updateById(exam);
        ExamPaper updatedExam = examPaperService.getById(id);
        return ApiResponse.success("更新成功", updatedExam);
    }

    /**
     * 删除试卷（教师/管理员权限）
     * @param id 试卷ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除试卷")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        // 归属校验（2026-09-14 修复）：仅试卷所属课程的授课教师或管理员可删除
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        examPaperService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 查询试卷关联的题目列表
     * @param id 试卷ID
     * @return 题目详情集合
     */
    @GetMapping("/{id}/questions")
    @Operation(summary = "查询试卷关联的题目列表")
    public ApiResponse<List<ExamPaperQuestionVO>> getQuestions(@PathVariable Long id) {
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, id);
        List<ExamQuestion> examQuestionList = examQuestionService.list(queryWrapper);
        List<ExamPaperQuestionVO> resultList = new ArrayList<>();

        for (ExamQuestion examQuestion : examQuestionList) {
            Question question = questionService.getById(examQuestion.getQuestionId());
            if (question == null) {
                continue;
            }
            resultList.add(new ExamPaperQuestionVO(
                    examQuestion.getId(), examQuestion.getOrderIndex(),
                    examQuestion.getPoints(), question));
        }
        return ApiResponse.success(resultList);
    }

    /**
     * 查询试卷完整详情（含题目列表）
     * @param id 试卷ID
     * @return 试卷详情（包含题目信息）
     */
    @GetMapping("/{id}/detail")
    @Operation(summary = "查询试卷完整详情")
    public ApiResponse<ExamPaperDetailVO> getDetail(@PathVariable Long id) {
        ExamPaperDetailVO detail = examPaperService.getExamPaperDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 发布试卷（教师/管理员权限）
     * @param id 试卷ID
     * @return 发布后试卷信息
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布试卷")
    public ApiResponse<ExamPaper> publish(@PathVariable Long id, Authentication auth) {
        // 归属校验：仅试卷所属课程的授课教师或管理员可发布（修复他师越权发布）
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        examPaperService.publishExamPaper(id);
        ExamPaper paper = examPaperService.getById(id);
        return ApiResponse.success("发布成功", paper);
    }

    /**
     * 自动组卷（教师/管理员权限）
     * 按题型数量和难度比例从题库随机抽取题目生成试卷
     * @param dto 组卷参数
     * @param auth 认证信息
     * @return 生成的试卷
     */
    @PostMapping("/auto-compose")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "自动组卷")
    public ApiResponse<ExamPaper> autoCompose(@Valid @RequestBody AutoComposeDTO dto, Authentication auth) {
        Long creatorId = (Long) auth.getPrincipal();
        // 归属校验：仅目标课程的授课教师或管理员可组卷（修复他师越权为他人课程组卷）
        examPaperAccessControl.assertCanCompose(dto.getCourseId(), auth);
        ExamPaper paper = examPaperService.autoComposeExamPaper(dto, creatorId);
        return ApiResponse.success("自动组卷成功", paper);
    }


    /**
     * 复制试卷（教师/管理员权限）
     * @param id 源试卷ID
     * @param body 包含新标题
     * @return 新试卷信息
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制试卷")
    public ApiResponse<ExamPaper> copy(@PathVariable Long id, @Valid @RequestBody Map<String, String> body) {
        String newTitle = body.get("newTitle");
        ExamPaper newPaper = examPaperService.copyExamPaper(id, newTitle);
        return ApiResponse.success("复制成功", newPaper);
    }

    /**
     * 获取试卷统计（题型分布、分值分布）
     * @param id 试卷ID
     * @return 统计数据
     */
    @GetMapping("/{id}/statistics")
    @Operation(summary = "获取试卷统计")
    public ApiResponse<ExamPaperStatsVO> statistics(@PathVariable Long id) {
        ExamPaperStatsVO stats = examPaperService.getPaperStatistics(id);
        return ApiResponse.success(stats);
    }

    public ExamSessionService getExamSessionService() {
        return examSessionService;
    }
}