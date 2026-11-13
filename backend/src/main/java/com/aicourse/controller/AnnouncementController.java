package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Announcement;
import com.aicourse.exception.BusinessException;
import com.aicourse.common.ApiCode;
import com.aicourse.security.AdminClassAccessControl;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.AnnouncementService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告接口（B-12／P-08 WP8，V16）。
 * <p>
 * 发布 ACL（验收 4）：课程 → {@code CourseAccessControl.assertCanManageCourse}（教师须本人课程）；
 * 教学班 → {@code TeachingClassAccessControl.assertTeachingClassLead}（主讲本人，管理员放行）；
 * 行政班 → 班主任本人（{@code admin_classes.advisor_teacher_id == 当前用户}）或校级管理员。
 * 收件箱复用既有 {@code /notifications}（扇出通道），这里提供公告自身的分页与详情。
 */
@Tag(name = "公告")
@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final CourseAccessControl courseAccessControl;
    private final TeachingClassAccessControl teachingClassAccessControl;
    private final AdminClassAccessControl adminClassAccessControl;

    public AnnouncementController(AnnouncementService announcementService,
                                  CourseAccessControl courseAccessControl,
                                  TeachingClassAccessControl teachingClassAccessControl,
                                  AdminClassAccessControl adminClassAccessControl) {
        this.announcementService = announcementService;
        this.courseAccessControl = courseAccessControl;
        this.teachingClassAccessControl = teachingClassAccessControl;
        this.adminClassAccessControl = adminClassAccessControl;
    }

    /** 发布请求体：三个目标互斥，由服务层兜底校验；ACL 在本控制器内逐目标判定。 */
    public static class PublishRequest {
        // Jackson 请求体绑定：字段由反序列化写入（SpotBugs 的 UWF 按本地写入判定，需显式豁免口径）
        @SuppressWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
        public Long courseId;
        @SuppressWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
        public Long teachingClassId;
        @SuppressWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
        public Long adminClassId;

        @NotBlank(message = "标题不能为空")
        public String title;

        @NotBlank(message = "正文不能为空")
        public String content;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Announcement> publish(@Valid @RequestBody PublishRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        assertPublishAllowed(request, userId, auth);
        String role = isAdmin(auth) ? Announcement.ROLE_ADMIN : guessRole(request, userId);
        return ApiResponse.success("发布成功", announcementService.publish(
                request.courseId, request.teachingClassId, request.adminClassId,
                request.title, request.content, userId, role));
    }

    /** 当前用户可见的公告分页（三维度并集，按发布时间倒序）。 */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<IPage<Announcement>> list(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 Authentication auth) {
        return ApiResponse.success(announcementService.pageVisible(page, size, (Long) auth.getPrincipal()));
    }

    /**
     * 管理端全量公告分页（2026-11-07 全量实现批）：通知公告管理页的数据源。
     * 与 {@link #list} 的区别：不受受众三维度过滤，管理员治理全平台公告；
     * 可按目标类型/关键词过滤，便于定位误发内容。
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<IPage<Announcement>> listAll(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String targetType,
                                                    @RequestParam(required = false) String keyword) {
        // targetType 过滤语义：course/teachingClass/adminClass 分别要求对应目标列非空（无 SCHOOL 全校目标，受众模型只有三型）
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Announcement> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Announcement>()
                        .isNotNull("course".equals(targetType), Announcement::getCourseId)
                        .isNotNull("teachingClass".equals(targetType), Announcement::getTeachingClassId)
                        .isNotNull("adminClass".equals(targetType), Announcement::getAdminClassId)
                        .and(keyword != null && !keyword.isBlank(),
                                w -> w.like(Announcement::getTitle, keyword).or().like(Announcement::getContent, keyword))
                        .orderByDesc(Announcement::getCreatedAt);
        return ApiResponse.success(announcementService.page(new Page<>(page, size), wrapper));
    }

    /** 公告详情：非受众 403（与「不存在 404」语义分开，拆包方案 WP8 验收 2）。 */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Announcement> detail(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(announcementService.getVisible(id, (Long) auth.getPrincipal()));
    }

    private void assertPublishAllowed(PublishRequest request, Long userId, Authentication auth) {
        int targets = (request.courseId != null ? 1 : 0) + (request.teachingClassId != null ? 1 : 0)
                + (request.adminClassId != null ? 1 : 0);
        if (targets != 1) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "公告目标必须且只能指定课程/教学班/行政班之一");
        }
        if (request.courseId != null) {
            courseAccessControl.assertCanManageCourse(request.courseId, auth);
        } else if (request.teachingClassId != null) {
            if (!isAdmin(auth)) {
                teachingClassAccessControl.assertTeachingClassLead(request.teachingClassId, userId);
            }
        } else if (!isAdmin(auth)) {
            AdminClass adminClass = adminClassAccessControl.requireAdminClass(request.adminClassId);
            if (!userId.equals(adminClass.getAdvisorTeacherId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "只有本班班主任可以发布班级公告");
            }
        }
    }

    /** 发布人角色：行政班 → ADVISOR；其余（课程/教学班）→ TEACHER。 */
    private String guessRole(PublishRequest request, Long userId) {
        if (request.adminClassId != null) {
            AdminClass adminClass = adminClassAccessControl.requireAdminClass(request.adminClassId);
            if (userId.equals(adminClass.getAdvisorTeacherId())) {
                return Announcement.ROLE_ADVISOR;
            }
        }
        return Announcement.ROLE_TEACHER;
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
