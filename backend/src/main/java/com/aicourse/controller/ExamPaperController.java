package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.application.exam.ExamPaperApplicationService;
import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamPaperSaveDTO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.ExamPaperAccessControl;
import com.aicourse.service.ExamPaperService;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.aicourse.vo.ExamPaperVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 试卷管理接口控制器。
 *
 * <p><b>阶段3 · 3E-03A 后的边界</b>：本类只做 HTTP——参数接收、DTO 校验、VO 转换、权限注解。
 * 建卷/改卷/删卷/归档/审核流转/组卷/复制这些**多步编排**已移入
 * {@link ExamPaperApplicationService}；列表查询与 {@code courseName} 回填也随之下移，
 * 因此本类不再拼查询 DSL、不再依赖目标教学班服务、不再持有考试会话服务。</p>
 *
 * <p>留在本类的只有纯读取端点（详情/统计）与它们的对象级授权。</p>
 */
@Tag(name = "试卷管理", description = "试卷的新增、查询、修改、删除、发布、自动组卷等功能")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;
    private final ExamPaperAccessControl examPaperAccessControl;
    private final ActivityVisibilityGuard activityVisibilityGuard;
    private final ExamPaperApplicationService examPaperApplicationService;

    public ExamPaperController(ExamPaperService examPaperService,
                               ExamPaperAccessControl examPaperAccessControl,
                               ActivityVisibilityGuard activityVisibilityGuard,
                               ExamPaperApplicationService examPaperApplicationService) {
        this.examPaperService = examPaperService;
        this.examPaperAccessControl = examPaperAccessControl;
        this.activityVisibilityGuard = activityVisibilityGuard;
        this.examPaperApplicationService = examPaperApplicationService;
    }

    /** 试卷分页转出参模型（阶段 3 · Q-05）。 */
    private static PagedResponse<ExamPaperVO> toPagedVO(Page<ExamPaper> page) {
        return PagedResponse.of(page.getRecords().stream().map(ExamPaperVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    private ExamPaper requirePaper(Long id) {
        ExamPaper paper = examPaperService.getById(id);
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在：" + id);
        }
        return paper;
    }

    /**
     * 分页查询试卷列表。
     * @param page 页码
     * @param size 每页条数
     * @param courseId 课程ID
     * @param status 试卷状态（仅教师/管理员生效；学生恒为已发布）
     * @param keyword 标题/描述关键词
     * @param auth 当前登录主体（用于按角色过滤可见范围）
     */
    @GetMapping
    @Operation(summary = "分页查询试卷列表")
    public ApiResponse<PagedResponse<ExamPaperVO>> listByParams(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teachingClassId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return ApiResponse.success(toPagedVO(examPaperApplicationService.listByParams(
                page, size, courseId, teachingClassId, status, keyword, auth)));
    }

    /** 根据课程ID分页查询试卷。 */
    @GetMapping("/course/{courseId}")
    @Operation(summary = "根据课程ID分页查询试卷")
    public ApiResponse<PagedResponse<ExamPaperVO>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long teachingClassId,
            Authentication auth) {
        return ApiResponse.success(toPagedVO(examPaperApplicationService.listByCourse(
                courseId, page, size, teachingClassId, auth)));
    }

    /** 根据ID查询试卷详情。 */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询试卷详情")
    public ApiResponse<ExamPaperVO> getById(@PathVariable Long id, Authentication auth) {
        ExamPaper examPaper = examPaperService.getById(id);
        activityVisibilityGuard.assertVisibleExamPaper(examPaper, auth);
        return ApiResponse.success(ExamPaperVO.from(examPaper));
    }

    /** 新增试卷（教师/管理员权限）。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "新增试卷")
    public ApiResponse<ExamPaperVO> create(@Valid @RequestBody ExamPaperSaveDTO dto, Authentication auth) {
        return ApiResponse.success("创建成功", ExamPaperVO.from(examPaperApplicationService.create(dto, auth)));
    }

    /** 修改试卷（教师/管理员权限）。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改试卷")
    public ApiResponse<ExamPaperVO> update(@PathVariable Long id,
                                           @Valid @RequestBody ExamPaperSaveDTO dto, Authentication auth) {
        return ApiResponse.success("更新成功",
                ExamPaperVO.from(examPaperApplicationService.update(id, dto, auth)));
    }

    /** 删除试卷（教师/管理员权限）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除试卷",
            description = "无考试记录时物理删除；已产生考试会话的试卷返回 409，避免外键级联销毁考试记录")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        examPaperApplicationService.delete(id, auth);
        return ApiResponse.success("删除成功", null);
    }

    /** 归档试卷（教师/管理员权限）。 */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "归档试卷",
            description = "PUBLISHED → ARCHIVED，幂等；用于已产生考试记录、不可物理删除的试卷")
    public ApiResponse<ExamPaperVO> archive(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success("已归档", ExamPaperVO.from(examPaperApplicationService.archive(id, auth)));
    }

    /** 查询试卷完整详情（含题目列表）。 */
    @GetMapping("/{id}/detail")
    @Operation(summary = "查询试卷完整详情")
    public ApiResponse<ExamPaperDetailVO> getDetail(@PathVariable Long id, Authentication auth) {
        // 对象级授权（同 /questions）：详情含全部题目内容，学生须已选课、教师须授课
        ExamPaper paper = requirePaper(id);
        examPaperAccessControl.assertCanRead(paper, auth);
        activityVisibilityGuard.assertVisibleExamPaper(paper, auth);
        return ApiResponse.success(examPaperService.getExamPaperDetail(id));
    }

    /** 发布试卷 / 提交审核（教师提交；管理员走 /approve）。 */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布试卷 / 提交审核",
            description = "教师提交审核（DRAFT/REJECTED → PENDING_REVIEW）；管理员发布入口按 WP6 返回 403，"
                    + "审核通过走 /approve")
    public ApiResponse<ExamPaperVO> publish(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success("已提交审核", ExamPaperVO.from(examPaperApplicationService.publish(id, auth)));
    }

    /** 审核通过（管理员）：仅待审核态可操作。 */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审核通过试卷", description = "PENDING_REVIEW → PUBLISHED，并推送选课学生通知")
    public ApiResponse<ExamPaperVO> approve(@PathVariable Long id) {
        return ApiResponse.success("审核通过", ExamPaperVO.from(examPaperApplicationService.approve(id)));
    }

    /** 审核驳回（管理员）：仅待审核态可操作。 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审核驳回试卷",
            description = "PENDING_REVIEW → REJECTED，并向创建人发送驳回通知；原因仅进通知与日志，不落库")
    public ApiResponse<ExamPaperVO> reject(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.success("已驳回", ExamPaperVO.from(examPaperApplicationService.reject(id, reason)));
    }

    /** 自动组卷（教师/管理员权限）。 */
    @PostMapping("/auto-compose")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "自动组卷")
    public ApiResponse<ExamPaperVO> autoCompose(@Valid @RequestBody AutoComposeDTO dto, Authentication auth) {
        Long creatorId = (Long) auth.getPrincipal();
        return ApiResponse.success("自动组卷成功",
                ExamPaperVO.from(examPaperApplicationService.autoCompose(dto, creatorId, auth)));
    }

    /** 复制试卷（教师/管理员权限）。 */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制试卷")
    public ApiResponse<ExamPaperVO> copy(@PathVariable Long id, @Valid @RequestBody Map<String, String> body,
                                         Authentication auth) {
        return ApiResponse.success("复制成功",
                ExamPaperVO.from(examPaperApplicationService.copy(id, body.get("newTitle"), auth)));
    }

    /** 获取试卷统计（题型分布、分值分布）。 */
    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取试卷统计")
    public ApiResponse<ExamPaperStatsVO> statistics(@PathVariable Long id, Authentication auth) {
        // 统计含题型/分值分布，属教学侧数据；学生无使用场景（前端仅教师端调用）
        examPaperAccessControl.assertCanRead(examPaperService.getById(id), auth);
        return ApiResponse.success(examPaperService.getPaperStatistics(id));
    }
}
