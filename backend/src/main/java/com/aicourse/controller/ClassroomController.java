package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Classroom;
import com.aicourse.service.ClassroomService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/classes")
public class ClassroomController {
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
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
}
