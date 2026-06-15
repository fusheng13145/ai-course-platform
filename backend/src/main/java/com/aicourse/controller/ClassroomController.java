package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.aicourse.service.ClassroomService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 班级管理接口控制器
 */
@RestController
@RequestMapping("/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    /**
     * 班级列表查询（支持关键词模糊搜索）
     * @param keyword 搜索关键词
     * @return 班级集合
     */
    @GetMapping
    public ApiResponse<List<Classroom>> list(@RequestParam(required = false) String keyword) {
        List<Classroom> classroomList = classroomService.searchClassrooms(keyword, null, null);
        return ApiResponse.success(classroomList);
    }

    /**
     * 根据ID查询班级详情
     * @param id 班级ID
     * @return 班级信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Classroom> getById(@PathVariable Long id) {
        Classroom classroom = classroomService.getById(id);
        return ApiResponse.success(classroom);
    }

    /**
     * 获取班级详情（含统计信息）
     * @param id 班级ID
     * @return 班级详情信息
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> detail = classroomService.getClassroomDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 新增班级（仅管理员）
     * @param classroom 班级信息
     * @return 新增班级数据
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Classroom> create(@RequestBody Classroom classroom) {
        classroomService.save(classroom);
        return ApiResponse.success("创建成功", classroom);
    }

    /**
     * 修改班级信息（仅管理员）
     * @param id 班级ID
     * @param classroom 待更新班级数据
     * @return 更新后班级信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Classroom> update(@PathVariable Long id, @RequestBody Classroom classroom) {
        classroom.setId(id);
        classroomService.updateById(classroom);
        Classroom updatedClassroom = classroomService.getById(id);
        return ApiResponse.success("更新成功", updatedClassroom);
    }

    /**
     * 删除班级（仅管理员）
     * @param id 班级ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        classroomService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 获取班级学生列表（分页）
     * @param id      班级ID
     * @param keyword 搜索关键字
     * @param page    页码
     * @param size    每页大小
     * @return 分页学生列表
     */
    @GetMapping("/{id}/students")
    public ApiResponse<Page<User>> getStudents(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<User> studentPage = classroomService.getClassroomStudents(id, keyword, page, size);
        return ApiResponse.success(studentPage);
    }

    /**
     * 向班级添加学生（教师/管理员权限）
     * @param id        班级ID
     * @param studentId 学生ID
     * @return 操作结果
     */
    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> addStudent(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.addStudent(id, studentId);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 从班级移除学生（教师/管理员权限）
     * @param id        班级ID
     * @param studentId 学生ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.removeStudent(id, studentId);
        return ApiResponse.success("移除成功", null);
    }

    /**
     * 获取班级统计信息
     * @param id 班级ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getStats(@PathVariable Long id) {
        Map<String, Object> stats = classroomService.getClassroomStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 获取当前用户管理的班级列表（辅导员视角）
     * @param advisorId 辅导员ID
     * @return 班级列表
     */
    @GetMapping("/my")
    public ApiResponse<List<Classroom>> getMyClassrooms(@RequestParam Long advisorId) {
        List<Classroom> classrooms = classroomService.getAdvisorClassrooms(advisorId);
        return ApiResponse.success(classrooms);
    }

    /**
     * 高级搜索班级（支持多条件组合）
     * @param keyword    关键字
     * @param gradeLevel 年级
     * @param major      专业
     * @return 匹配的班级列表
     */
    @GetMapping("/search")
    public ApiResponse<List<Classroom>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String major) {
        List<Classroom> classrooms = classroomService.searchClassrooms(keyword, gradeLevel, major);
        return ApiResponse.success(classrooms);
    }
}