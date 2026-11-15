package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.exception.BusinessException;
import com.aicourse.dto.ChapterReorderDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程章节接口控制器
 */
@RestController
@Tag(name = "课程章节")
public class CourseChapterController {

    private static final Logger log = LoggerFactory.getLogger(CourseChapterController.class);

    private final CourseChapterService chapterService;
    private final CourseService courseService;
    private final TeacherCourseService teachingService;
    private final CourseAccessControl courseAccessControl;
    private final CourseEnrollmentService enrollmentService;

    public CourseChapterController(CourseChapterService chapterService, CourseService courseService,
                                    TeacherCourseService teachingService,
                                    CourseAccessControl courseAccessControl,
                                    CourseEnrollmentService enrollmentService) {
        this.chapterService = chapterService;
        this.courseService = courseService;
        this.teachingService = teachingService;
        this.courseAccessControl = courseAccessControl;
        this.enrollmentService = enrollmentService;
    }

    /**
     * 根据课程ID查询章节（完整嵌套树，对齐超星章节任务点）
     * 返回带父子层级、内容、发布状态的章节树，前端可直接渲染目录。
     * <p>对象级收口（2026-11-13）：课程管理权限者（本课程教师/管理员）看全量（含未发布与
     * 提交率统计）；学生须已选课或课程仍开放可见，且未发布章节隐藏、教师侧统计剥离。</p>
     * @param courseId 课程ID
     * @return 章节树
     */
    @GetMapping("/chapters/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> getByCourse(@PathVariable Long courseId, Authentication auth) {
        return ApiResponse.success(resolveChapterTree(courseId, auth));
    }

    /**
     * 课程章节查询备用接口
     * <p>对象级收口：与 {@link #getByCourse} 同款归属校验与学生视图过滤。</p>
     * @param courseId 课程ID
     * @return 章节树
     */
    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<Map<String, Object>>> getByCourseAlt(@PathVariable Long courseId, Authentication auth) {
        return ApiResponse.success(resolveChapterTree(courseId, auth));
    }

    /**
     * 根据ID查询章节详情
     * <p>对象级收口（2026-11-13）：按章节所属课程做同款归属校验；学生仅可读已发布章节。</p>
     * @param id 章节ID
     * @return 章节信息
     */
    @GetMapping("/chapters/{id}")
    public ApiResponse<CourseChapter> getById(@PathVariable Long id, Authentication auth) {
        CourseChapter chapter = chapterService.getById(id);
        if (chapter == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "章节不存在");
        }
        if (isAdmin(auth)) {
            return ApiResponse.success(chapter);
        }
        Course course = requireCourse(chapter.getCourseId());
        Long userId = (Long) auth.getPrincipal();
        if (courseAccessControl.isStudent(auth)) {
            requireStudentReadable(course, userId);
            if (!Boolean.TRUE.equals(chapter.getIsPublished())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "章节尚未发布，无权查看");
            }
            return ApiResponse.success(chapter);
        }
        courseAccessControl.assertCanManageCourse(chapter.getCourseId(), auth);
        return ApiResponse.success(chapter);
    }

    /**
     * 章节树读取的三分支判定（读 getFullChapterTree 数据来源后的最小实现）：
     * <ul>
     *     <li>管理员：全量；</li>
     *     <li>本课程教师：全量（复用 {@link CourseAccessControl#assertCanManageCourse}，
     *         非本课程教师一律 403）；</li>
     *     <li>学生：须已选课（审批通过）或课程仍开放可见；返回学生视图——
     *         未发布章节隐藏、教师侧提交率统计清零（保留键位，前端结构兼容）。</li>
     * </ul>
     */
    private List<Map<String, Object>> resolveChapterTree(Long courseId, Authentication auth) {
        if (isAdmin(auth)) {
            return teachingService.getFullChapterTree(courseId);
        }
        Course course = requireCourse(courseId);
        Long userId = (Long) auth.getPrincipal();
        if (courseAccessControl.isStudent(auth)) {
            requireStudentReadable(course, userId);
            return toStudentView(teachingService.getFullChapterTree(courseId));
        }
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return teachingService.getFullChapterTree(courseId);
    }

    /**
     * 学生可读判定（语义对齐 {@link CourseAccessControl#assertCanReadCourse}）：
     * 已有审批通过的选课记录，或课程仍开放可见（ACTIVE 且未停用）。
     */
    private void requireStudentReadable(Course course, Long userId) {
        boolean enrolled = enrollmentService.hasApprovedEnrollment(userId, course.getId());
        boolean courseVisible = "ACTIVE".equals(course.getStatus())
                && Boolean.TRUE.equals(course.getIsActive());
        if (!enrolled && !courseVisible) {
            throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程章节");
        }
    }

    /**
     * 学生视图过滤：未发布章节对学生隐藏（含其子章节）；教师侧提交率统计
     * （submittedCount / completionRate，含 taskPoints 内层）剥离数值但保留键位，
     * 保持响应结构与前端兼容。
     */
    private List<Map<String, Object>> toStudentView(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            if (!Boolean.TRUE.equals(node.get("isPublished"))) {
                continue;
            }
            stripTeacherStats(node);
            Object children = node.get("children");
            if (children instanceof List<?> childList) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> childNodes = (List<Map<String, Object>>) childList;
                List<Map<String, Object>> visibleChildren = toStudentView(childNodes);
                childNodes.clear();
                childNodes.addAll(visibleChildren);
            }
            visible.add(node);
        }
        return visible;
    }

    /** 剥离单个章节节点的教师侧统计（保留键位、清零数值）。 */
    private void stripTeacherStats(Map<String, Object> node) {
        node.put("submittedCount", 0);
        node.put("completionRate", 0);
        Object taskPoints = node.get("taskPoints");
        if (taskPoints instanceof List<?> taskList) {
            for (Object tp : taskList) {
                if (tp instanceof Map<?, ?> tpMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> taskPoint = (Map<String, Object>) tpMap;
                    taskPoint.put("submittedCount", 0);
                    taskPoint.put("completionRate", 0);
                }
            }
        }
    }

    /** 取课程，不存在时 404（复用 CourseAccessControl 的存在性口径）。 */
    private Course requireCourse(Long courseId) {
        Course course = courseId == null ? null : courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    /**
     * 新增章节（教师/管理员权限）
     * @param chapter 章节信息
     * @param auth 认证信息
     * @return 新增章节数据
     */
    @PostMapping("/chapters")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<CourseChapter> create(@Valid @RequestBody CourseChapter chapter, Authentication auth) {
        // 课程管理权限校验与落库下沉至服务层（事务化）
        CourseChapter saved = chapterService.createChapter(chapter, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("创建成功", saved);
    }

    /**
     * 修改章节（教师/管理员权限）
     * @param id 章节ID
     * @param chapter 待更新章节数据
     * @param auth 认证信息
     * @return 更新后章节数据
     */
    @PutMapping("/chapters/{id}")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<CourseChapter> update(@PathVariable Long id,
                                             @Valid @RequestBody CourseChapter chapter,
                                             Authentication auth) {
        // 章节存在性/权限校验与更新落库下沉至服务层（事务化）
        CourseChapter updatedChapter = chapterService.updateChapter(id, chapter, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("更新成功", updatedChapter);
    }

    /**
     * 删除章节（教师/管理员权限）
     * @param id 章节ID
     * @param auth 认证信息
     * @return 操作结果
     */
    @DeleteMapping("/chapters/{id}")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        // 章节存在性/权限校验与删除落库下沉至服务层（事务化）
        chapterService.deleteChapter(id, (Long) auth.getPrincipal(), isAdmin(auth));
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 批量更新章节排序
     * @param body orderList: [{id: 1, orderIndex: 0}, {id: 2, orderIndex: 1}, ...]
     * @param auth 认证信息
     * @return 操作结果
     */
    @PutMapping("/batch-reorder")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Void> batchReorder(@Valid @RequestBody ChapterReorderDTO dto, Authentication auth) {
        List<Long> ids = dto.getOrderList().stream()
                .map(ChapterReorderDTO.OrderItem::getId)
                .collect(Collectors.toList());
        Map<Long, CourseChapter> chapterMap = chapterService.listByIds(ids).stream()
                .collect(Collectors.toMap(CourseChapter::getId, c -> c, (a, b) -> a));
        for (ChapterReorderDTO.OrderItem item : dto.getOrderList()) {
            CourseChapter chapter = chapterMap.get(item.getId());
            if (chapter == null) continue;
            // WP9 收紧：非本人课程 fail-closed 403（原实现把「能管理」判成跳过更新、
            // 管理员反而落进更新分支——方向颠倒的存量缺陷，随只读矩阵一并纠正）
            if (!canManageCourse(chapter.getCourseId(), auth)) {
                throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权调整该课程章节排序");
            }
            chapter.setOrderIndex(item.getOrderIndex());
            chapterService.updateById(chapter);
        }
        return ApiResponse.success("排序更新成功", null);
    }

    /**
     * 判断当前用户是否为管理员（create/update/delete 传给服务层作归属旁路口径，维持原语义）
     */
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * 校验当前用户是否为该课程的负责教师（WP9 语义纠正：原实现对管理员返回 false、
     * 对「非本课程教师」反而返回 true，方向完全颠倒——调用处已改为 fail-closed）。
     * @param courseId 课程ID
     * @param auth 认证信息
     * @return true-是本课程负责人
     */
    private boolean canManageCourse(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Course course = courseService.getById(courseId);
        return course != null && userId.equals(course.getTeacherId());
    }
}