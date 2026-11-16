package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.UserService;
import com.aicourse.vo.ClassroomDetailVO;
import com.aicourse.vo.ClassroomStudentVO;
import com.aicourse.vo.ClassroomPendingCountVO;
import com.aicourse.vo.ClassroomStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 班级管理接口控制器
 */
@RestController
@RequestMapping("/classrooms")
@Tag(name = "班级管理")
public class ClassroomController {

    private static final Logger log = LoggerFactory.getLogger(ClassroomController.class);

    private final ClassroomService classroomService;
    private final UserService userService;
    private final AdminScopeGuard adminScopeGuard;
    private final CourseAccessControl courseAccessControl;

    public ClassroomController(ClassroomService classroomService, UserService userService,
                               AdminScopeGuard adminScopeGuard, CourseAccessControl courseAccessControl) {
        this.classroomService = classroomService;
        this.userService = userService;
        this.adminScopeGuard = adminScopeGuard;
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * 班级列表查询（支持关键词模糊搜索）
     * @param keyword 搜索关键词
     * @return 班级集合
     */
    @GetMapping
    public ApiResponse<List<Classroom>> list(@RequestParam(required = false) String keyword,
                                             Authentication auth) {
        List<Classroom> classroomList = visibleClassrooms(keyword, null, null, auth);
        return ApiResponse.success(classroomList);
    }

    /**
     * 根据ID查询班级详情
     * @param id 班级ID
     * @return 班级信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Classroom> getById(@PathVariable Long id, Authentication auth) {
        Classroom classroom = classroomService.getById(id);
        assertClassroomReadable(classroom, auth);
        return ApiResponse.success(classroom);
    }

    /**
     * 获取班级详情（含统计信息）
     * @param id 班级ID
     * @return 班级详情信息
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<ClassroomDetailVO> getDetail(@PathVariable Long id, Authentication auth) {
        assertClassroomReadable(classroomService.getById(id), auth);
        ClassroomDetailVO detail = classroomService.getClassroomDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 新增班级（仅管理员）
     * @param classroom 班级信息
     * @return 新增班级数据
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Classroom> create(@Valid @RequestBody Classroom classroom, Authentication auth) {
        assertAdminClassroomScope(classroom);
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
    public ApiResponse<Classroom> update(@PathVariable Long id, @Valid @RequestBody Classroom classroom,
                                         Authentication auth) {
        Classroom existing = classroomService.getById(id);
        assertClassroomReadable(existing, auth);
        // 只改班级信息时无需重复带归属教师；若改归属教师，则新教师也必须在管理员学院范围内。
        if (classroom.getAdvisorId() != null) {
            assertAdminClassroomScope(classroom);
        }
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
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        assertClassroomReadable(classroomService.getById(id), auth);
        classroomService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 获取班级学生列表（分页）。
     * <p>对象级收口（2026-11-14 审计 N3/P0-3）：本端点此前无任何角色/归属校验且下发
     * 完整 {@link User} 实体（email/phone 明文），任意学生可枚举全校班级名册与联系方式。
     * 现口径（Q6 已拍板）：</p>
     * <ul>
     *   <li>管理员：完整实体（管理台花名册需要联系方式，Q6）；</li>
     *   <li>班主任教师：{@link ClassroomStudentVO} 脱敏投影（结构性剔除 email/phone）；</li>
     *   <li>学生与非班主任教师：403（学生查同班同学走 {@code /users/{id}/classmates}）。</li>
     * </ul>
     */
    @GetMapping("/{id}/students")
    public ApiResponse<?> getStudents(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Classroom classroom = classroomService.getById(id);
        if (classroom == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.NOT_FOUND, "班级不存在");
        }
        boolean admin = RoleUtil.isAdmin(auth);
        if (admin) {
            assertAdminClassroomScope(classroom);
            return ApiResponse.success(classroomService.getClassroomStudents(id, keyword, page, size));
        }
        Long userId = (Long) auth.getPrincipal();
        boolean student = "STUDENT".equals(RoleUtil.roleOf(auth));
        if (student || !userId.equals(classroom.getAdvisorId())) {
            throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN,
                    student ? "班级名册仅班主任与管理员可查看" : "仅该班级的归属教师或管理员可查看名册");
        }
        Page<User> studentPage = classroomService.getClassroomStudents(id, keyword, page, size);
        Page<ClassroomStudentVO> voPage = new Page<>(studentPage.getCurrent(), studentPage.getSize(), studentPage.getTotal());
        voPage.setRecords(studentPage.getRecords().stream().map(ClassroomStudentVO::from).toList());
        return ApiResponse.success(voPage);
    }

    /**
     * 向班级添加学生（教师/管理员权限）。
     * <p>对象级收口（2026-11-13）：班级成员增删原先仅角色校验，教师 A 可改教师 B 的班级。
     * 对齐 {@code AdminClassAccessControl.assertAdminClassAdvisor} 的单一事实源口径：
     * {@code classrooms.advisor_id == 当前用户}，管理员放行。</p>
     * @param id        班级ID
     * @param studentId 学生ID
     * @return 操作结果
     */
    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> addStudent(@PathVariable Long id, @PathVariable Long studentId,
                                        Authentication auth) {
        assertClassroomOwner(id, auth);
        classroomService.addStudent(id, studentId);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * 从班级移除学生（教师/管理员权限）。
     * <p>对象级收口（2026-11-13）：同 addStudent，仅班级归属教师或管理员可移除成员。</p>
     * @param id        班级ID
     * @param studentId 学生ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId,
                                           Authentication auth) {
        assertClassroomOwner(id, auth);
        classroomService.removeStudent(id, studentId);
        return ApiResponse.success("移除成功", null);
    }

    /**
     * 班级归属校验：判定单一事实源为 {@code classrooms.advisor_id}（班级归属教师），
     * 管理员放行；非归属教师 403。班级不存在 404。
     */
    private void assertClassroomOwner(Long classroomId, Authentication auth) {
        Classroom classroom = classroomService.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.NOT_FOUND, "班级不存在");
        }
        boolean isAdmin = RoleUtil.isAdmin(auth);
        if (isAdmin) {
            assertAdminClassroomScope(classroom);
            return;
        }
        if (!((Long) auth.getPrincipal()).equals(classroom.getAdvisorId())) {
            throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN,
                    "只有该班级的归属教师或管理员可管理班级成员");
        }
    }

    /**
     * 班级读面（Q-04）：管理员按学院只读；班主任看本人班级；学生只看自己所属班级。
     */
    private void assertClassroomReadable(Classroom classroom, Authentication auth) {
        if (classroom == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.NOT_FOUND, "班级不存在");
        }
        if (RoleUtil.isAdmin(auth)) {
            assertAdminClassroomScope(classroom);
            return;
        }
        Long userId = (Long) auth.getPrincipal();
        if ("STUDENT".equals(RoleUtil.roleOf(auth))) {
            User student = userService.getById(userId);
            if (student == null || !classroom.getId().equals(student.getClassId())) {
                throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "仅本人班级可见");
            }
            return;
        }
        if (!userId.equals(classroom.getAdvisorId())) {
            throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "仅本人班级可见");
        }
    }

    /**
     * 管理员班级作用域：classrooms 无 college_id，按归属班主任 users.college_id 判定；
     * 校级管理员全平台放行，院系管理员仅本院班主任的班级。
     */
    private void assertAdminClassroomScope(Classroom classroom) {
        if (!adminScopeGuard.isCollegeAdmin()) {
            return;
        }
        if (classroom == null || classroom.getAdvisorId() == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权访问其他学院的班级");
        }
        adminScopeGuard.assertUserAccessById(classroom.getAdvisorId());
    }

    /**
     * 按角色生成可见班级集合；管理员院系过滤在内存中完成，避免给 ClassroomService 增加分页口径。
     */
    private List<Classroom> visibleClassrooms(String keyword, String gradeLevel, String major,
                                              Authentication auth) {
        List<Classroom> base = classroomService.searchClassrooms(keyword, gradeLevel, major);
        if (RoleUtil.isAdmin(auth)) {
            if (!adminScopeGuard.isCollegeAdmin()) {
                return base;
            }
            List<Long> collegeUserIds = adminScopeGuard.currentCollegeUserIds();
            if (collegeUserIds == null || collegeUserIds.isEmpty()) {
                return List.of();
            }
            return base.stream()
                    .filter(c -> c.getAdvisorId() != null && collegeUserIds.contains(c.getAdvisorId()))
                    .toList();
        }
        Long userId = (Long) auth.getPrincipal();
        if ("STUDENT".equals(RoleUtil.roleOf(auth))) {
            User student = userService.getById(userId);
            if (student == null || student.getClassId() == null) {
                return List.of();
            }
            return base.stream().filter(c -> student.getClassId().equals(c.getId())).toList();
        }
        return base.stream().filter(c -> userId.equals(c.getAdvisorId())).toList();
    }

    /**
     * 获取班级统计信息
     * @param id 班级ID
     * @return 统计信息
     */
    @GetMapping("/{id}/stats")
    public ApiResponse<ClassroomStatsVO> getStats(@PathVariable Long id, Authentication auth) {
        assertClassroomReadable(classroomService.getById(id), auth);
        ClassroomStatsVO stats = classroomService.getClassroomStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 获取**当前登录教师**管理的班级列表（班主任自查，WP9 验收 1）。
     *
     * <p>2026-11-07 收紧：原先 {@code advisorId} 由前端任意传值，任意登录用户传他人 ID
     * 即可枚举其班级（横向越权）。现改为只认 {@code Authentication} 的当前用户——
     * 兼容期旧客户端多带的 {@code advisorId} 查询参数由 Spring 静默忽略，不再进入判定；
     * 「查他人班」这一入口整体消失，归属校验由 {@code advisor_teacher_id = currentUserId} 单一事实承担。</p>
     *
     * @return 当前教师为班主任的班级列表
     */
    @GetMapping("/my")
    public ApiResponse<List<Classroom>> getMyClassrooms(Authentication auth) {
        Long currentUserId = (Long) auth.getPrincipal();
        List<Classroom> classrooms = classroomService.getAdvisorClassrooms(currentUserId);
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
            @RequestParam(required = false) String major,
            Authentication auth) {
        List<Classroom> classrooms = visibleClassrooms(keyword, gradeLevel, major, auth);
        return ApiResponse.success(classrooms);
    }

    /**
     * 按课程查询关联班级（课程驾驶舱「按课程切换班级维度」的真实数据来源）。
     * @param courseId 课程ID
     * @return 该课程关联的班级列表（course_id 绑定且有效）
     */
    @GetMapping("/by-course/{courseId}")
    public ApiResponse<List<Classroom>> listByCourse(@PathVariable Long courseId, Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        List<Classroom> classrooms = classroomService.listByCourse(courseId);
        return ApiResponse.success(classrooms);
    }

    /**
     * 统计某课程下各班级的待批改作业数（驾驶舱班级 Tab 待批徽标的数据来源）。
     * @param courseId 课程ID
     * @return 列表项含 classroomId / classroomName / pendingCount
     */
    @GetMapping("/by-course/{courseId}/pending")
    public ApiResponse<List<ClassroomPendingCountVO>> countPendingByCourse(@PathVariable Long courseId,
                                                                          Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        List<ClassroomPendingCountVO> pending = classroomService.countPendingByCourse(courseId);
        return ApiResponse.success(pending);
    }
}