package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.CourseEnrollmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/classrooms")
public class ClassroomController {
    private final ClassroomService classroomService;
    private final CourseEnrollmentService enrollmentService;

    public ClassroomController(ClassroomService classroomService, CourseEnrollmentService enrollmentService) {
        this.classroomService = classroomService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ApiResponse<List<Classroom>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(classroomService.list(
            new LambdaQueryWrapper<Classroom>()
                .like(keyword != null, Classroom::getName, keyword)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Classroom> getById(@PathVariable Long id) {
        return ApiResponse.success(classroomService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Classroom> create(@RequestBody Classroom classroom) {
        classroomService.save(classroom);
        return ApiResponse.success("创建成功", classroom);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Classroom> update(@PathVariable Long id, @RequestBody Classroom classroom) {
        classroom.setId(id);
        classroomService.updateById(classroom);
        return ApiResponse.success("更新成功", classroomService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classroomService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/{id}/students")
    public ApiResponse<List<CourseEnrollment>> getStudents(@PathVariable Long id) {
        return ApiResponse.success(enrollmentService.list(
            new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, id)));
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<CourseEnrollment> addStudent(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(id);
        enrollment.setStudentId(body.get("studentId"));
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setIsActive(true);
        enrollment.setProgress(java.math.BigDecimal.ZERO);
        enrollmentService.save(enrollment);
        return ApiResponse.success("添加成功", enrollment);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        enrollmentService.remove(new LambdaQueryWrapper<CourseEnrollment>()
            .eq(CourseEnrollment::getCourseId, id)
            .eq(CourseEnrollment::getStudentId, studentId));
        return ApiResponse.success("移除成功", null);
    }
}
