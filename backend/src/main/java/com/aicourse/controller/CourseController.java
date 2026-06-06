package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.CourseService;
import com.aicourse.service.CourseEnrollmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/courses")
public class CourseController {
    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;

    public CourseController(CourseService courseService, CourseEnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<Course>> list(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
            .eq(status != null, Course::getStatus, status)
            .like(keyword != null, Course::getName, keyword)
            .orderByDesc(Course::getCreatedAt);
        return ApiResponse.success(PagedResponse.of(courseService.page(Page.of(page, size), wrapper)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Course> getById(@PathVariable Long id) {
        return ApiResponse.success(courseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Course> create(@RequestBody Course course, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        course.setTeacherId(userId);
        courseService.save(course);
        return ApiResponse.success("鍒涘缓鎴愬姛", course);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Course> update(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        courseService.updateById(course);
        return ApiResponse.success("鏇存柊鎴愬姛", courseService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.removeById(id);
        return ApiResponse.success("鍒犻櫎鎴愬姛", null);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Course> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Course course = courseService.getById(id);
        course.setApprovalStatus((String) body.get("status"));
        course.setApprovalComment((String) body.get("comment"));
        courseService.updateById(course);
        return ApiResponse.success("瀹℃牳瀹屾垚", course);
    }

    @GetMapping("/enrolled")
    public ApiResponse<PagedResponse<CourseEnrollment>> enrolledCourses(Authentication auth,
                                                                          @RequestParam(defaultValue = "1") long page,
                                                                          @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        Page<CourseEnrollment> p = enrollmentService.page(Page.of(page, size),
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId));
        return ApiResponse.success(PagedResponse.of(p));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<PagedResponse<Course>> myCourses(Authentication auth,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        Page<Course> p = courseService.page(Page.of(page, size),
            new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId));
        return ApiResponse.success(PagedResponse.of(p));
    }

    @GetMapping("/{id}/students")
    public ApiResponse<PagedResponse<CourseEnrollment>> students(@PathVariable Long id,
                                                                   @RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "10") long size) {
        Page<CourseEnrollment> p = enrollmentService.page(Page.of(page, size),
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, id));
        return ApiResponse.success(PagedResponse.of(p));
    }
}
