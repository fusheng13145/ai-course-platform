package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseService;
import com.aicourse.service.GradeService;
import com.aicourse.vo.FinalGradeVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 过程性成绩（多维加权）接口
 * 基础路径：/api/grade
 */
@RestController
@RequestMapping("/grade")
@Tag(name = "成绩管理")
public class GradeController {

    private static final Logger log = LoggerFactory.getLogger(GradeController.class);

    private final GradeService gradeService;
    private final CourseService courseService;

    public GradeController(GradeService gradeService, CourseService courseService) {
        this.gradeService = gradeService;
        this.courseService = courseService;
    }

    /** 保存（新增或更新）课程的过程性成绩方案 */
    @PostMapping("/policy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradePolicy> savePolicy(@Valid @RequestBody GradePolicy policy, Authentication auth) {
        assertCourseOwner(policy.getCourseId(), auth);
        return ApiResponse.success("方案已保存",
                gradeService.savePolicy(policy, (Long) auth.getPrincipal()));
    }

    /** 获取某课程的成绩方案 */
    @GetMapping("/policy/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradePolicy> getPolicy(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        return ApiResponse.success(gradeService.getPolicy(courseId));
    }

    /** 新增或更新某学生的单项成绩记录 */
    @PostMapping("/item")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<GradeItem> upsertItem(@Valid @RequestBody GradeItem item, Authentication auth) {
        GradePolicy policy = requirePolicy(item.getPolicyId());
        assertCourseOwner(policy.getCourseId(), auth);
        Long uid = (Long) auth.getPrincipal();
        return ApiResponse.success("成绩已保存",
                gradeService.upsertItem(item.getPolicyId(), item.getStudentId(), item.getComponentKey(),
                        item.getScore(), item.getRawScore(), item.getSourceId(), uid, item.getRemark()));
    }

    /** 按成绩方案计算单个学生的最终总评成绩 */
    @GetMapping("/final/{policyId}/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<FinalGradeVO> computeFinal(@PathVariable Long policyId,
                                                  @PathVariable Long studentId,
                                                  Authentication auth) {
        GradePolicy policy = requirePolicy(policyId);
        assertCourseOwner(policy.getCourseId(), auth);
        return ApiResponse.success(gradeService.computeFinal(policyId, studentId));
    }

    /** 生成某课程的全班成绩报表 */
    @GetMapping("/report/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<FinalGradeVO>> courseReport(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        return ApiResponse.success(gradeService.courseReport(courseId));
    }

    // ===================== 对象级授权 =====================

    private GradePolicy requirePolicy(Long policyId) {
        GradePolicy policy = gradeService.getPolicyById(policyId);
        if (policy == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "成绩方案不存在");
        }
        return policy;
    }

    private void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程成绩");
        }
    }
}
