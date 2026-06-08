package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final UserService userService;

    public ClassroomController(ClassroomService classroomService, UserService userService) {
        this.classroomService = classroomService;
        this.userService = userService;
    }

    /**
     * 班级列表查询（支持关键词模糊搜索）
     * @param keyword 搜索关键词
     * @return 班级集合
     */
    @GetMapping
    public ApiResponse<List<Classroom>> list(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Classroom> queryWrapper = new LambdaQueryWrapper<Classroom>()
                .like(keyword != null, Classroom::getName, keyword);
        List<Classroom> classroomList = classroomService.list(queryWrapper);
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
     * 查询班级下所有学生
     * @param id 班级ID
     * @return 学生列表（密码已置空）
     */
    @GetMapping("/{id}/students")
    public ApiResponse<List<User>> getStudents(@PathVariable Long id) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getClassId, id)
                .eq(User::getRole, "STUDENT");
        List<User> studentList = userService.list(queryWrapper);
        // 清空密码字段，避免敏感数据返回
        studentList.forEach(user -> user.setPassword(null));
        return ApiResponse.success(studentList);
    }

    /**
     * 向班级添加学生（教师/管理员权限）
     * @param id 班级ID
     * @param body 请求参数
     * @return 操作结果
     */
    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> addStudent(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long studentId = body.get("studentId");
        User student = userService.getById(studentId);
        if (student != null) {
            student.setClassId(id);
            userService.updateById(student);
        }
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 从班级移除学生（教师/管理员权限）
     * @param id 班级ID
     * @param studentId 学生ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        User student = userService.getById(studentId);
        if (student != null && id.equals(student.getClassId())) {
            student.setClassId(null);
            userService.updateById(student);
        }
        return ApiResponse.success("移除成功", null);
    }
}