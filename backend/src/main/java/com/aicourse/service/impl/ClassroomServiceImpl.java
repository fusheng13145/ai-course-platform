package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Classroom;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.enums.SubmissionStatus;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ClassroomMapper;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班级管理服务实现类
 */
@Service
public class ClassroomServiceImpl extends ServiceImpl<ClassroomMapper, Classroom> implements ClassroomService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomServiceImpl.class);

    private final UserService userService;
    private final TaskService taskService;
    private final SubmissionService submissionService;

    public ClassroomServiceImpl(ClassroomMapper classroomMapper,
                                UserService userService,
                                TaskService taskService,
                                SubmissionService submissionService) {
        this.baseMapper = classroomMapper;
        this.userService = userService;
        this.taskService = taskService;
        this.submissionService = submissionService;
    }

    /**
     * 获取班级详情（含基本信息）
     */
    @Override
    public Map<String, Object> getClassroomDetail(Long classroomId) {
        Classroom classroom = this.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException("班级不存在");
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("classroom", classroom);
        detail.put("studentCount", classroom.getStudentCount());
        detail.put("maxStudents", classroom.getMaxStudents());
        return detail;
    }

    /**
     * 获取班级学生列表（分页，通过User.classId关联）
     */
    @Override
    public Page<User> getClassroomStudents(Long classroomId, String keyword, int page, int size) {
        // 验证班级是否存在
        Classroom classroom = this.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException("班级不存在");
        }
        // 构建分页查询条件：classId匹配 + 学生角色 + 可选关键字搜索
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getClassId, classroomId)
                .eq(User::getRole, "STUDENT")
                .and(keyword != null && !keyword.isEmpty(), wrapper -> wrapper
                        .like(User::getFullName, keyword)
                        .or()
                        .like(User::getStudentId, keyword))
                .orderByDesc(User::getCreatedAt);
        Page<User> pageResult = new Page<>(page, size);
        Page<User> result = userService.page(pageResult, queryWrapper);
        // 清空密码字段，避免敏感数据返回
        result.getRecords().forEach(user -> user.setPassword(null));
        return result;
    }

    /**
     * 添加学生到班级（更新用户classId并递增班级人数）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStudent(Long classroomId, Long studentId) {
        log.info("新增课堂");

        Classroom classroom = this.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException("班级不存在");
        }
        User student = userService.getById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        // 检查是否已在其他班级中
        if (student.getClassId() != null) {
            throw new BusinessException("该学生已属于其他班级，请先移除原班级关联");
        }
        // 检查班级容量
        if (classroom.getMaxStudents() != null && classroom.getStudentCount() != null
                && classroom.getStudentCount() >= classroom.getMaxStudents()) {
            throw new BusinessException("班级人数已达上限");
        }
        // 更新学生的班级ID
        student.setClassId(classroomId);
        userService.updateById(student);
        // 递增班级学生计数
        Integer currentCount = classroom.getStudentCount() != null ? classroom.getStudentCount() : 0;
        classroom.setStudentCount(currentCount + 1);
        this.updateById(classroom);
    }

    /**
     * 从班级移除学生（清空用户classId并递减班级人数）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long classroomId, Long studentId) {
        log.info("移除课堂");

        Classroom classroom = this.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException("班级不存在");
        }
        User student = userService.getById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        if (!classroomId.equals(student.getClassId())) {
            throw new BusinessException("该学生不在当前班级中");
        }
        // 清空学生班级ID
        student.setClassId(null);
        userService.updateById(student);
        // 递减班级学生计数
        Integer currentCount = classroom.getStudentCount() != null ? classroom.getStudentCount() : 0;
        classroom.setStudentCount(Math.max(0, currentCount - 1));
        this.updateById(classroom);
    }

    /**
     * 获取教师管理的班级列表
     */
    @Override
    public List<Classroom> getAdvisorClassrooms(Long advisorId) {
        LambdaQueryWrapper<Classroom> queryWrapper = new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getAdvisorId, advisorId)
                .eq(Classroom::getIsActive, true)
                .orderByDesc(Classroom::getCreatedAt);
        return this.list(queryWrapper);
    }

    /**
     * 班级统计信息（人数、按性别/专业/角色分布等）
     */
    @Override
    public Map<String, Object> getClassroomStats(Long classroomId) {
        Classroom classroom = this.getById(classroomId);
        if (classroom == null) {
            throw new BusinessException("班级不存在");
        }
        // 查询班级下所有学生
        LambdaQueryWrapper<User> studentQuery = new LambdaQueryWrapper<User>()
                .eq(User::getClassId, classroomId)
                .eq(User::getRole, "STUDENT");
        List<User> students = userService.list(studentQuery);
        // 统计总人数
        long totalStudents = students.size();
        // 统计男生人数（通过备注字段或其他方式判断性别，此处简化处理）
        // 由于User实体无gender字段，此处统计专业分布和激活状态
        Map<String, Long> majorStats = new HashMap<>();
        long activeCount = 0;
        for (User student : students) {
            String major = student.getMajor() != null ? student.getMajor() : "未指定";
            majorStats.merge(major, 1L, Long::sum);
            if (Boolean.TRUE.equals(student.getIsActive())) {
                activeCount++;
            }
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudents", totalStudents);
        stats.put("activeStudents", activeCount);
        stats.put("inactiveStudents", totalStudents - activeCount);
        stats.put("maxCapacity", classroom.getMaxStudents());
        stats.put("currentCount", classroom.getStudentCount());
        stats.put("majorDistribution", majorStats);
        return stats;
    }

    /**
     * 搜索班级（支持多条件组合查询）
     */
    @Override
    public List<Classroom> searchClassrooms(String keyword, String gradeLevel, String major) {
        LambdaQueryWrapper<Classroom> queryWrapper = new LambdaQueryWrapper<Classroom>()
                .and(keyword != null && !keyword.isEmpty(), wrapper -> wrapper
                        .like(Classroom::getName, keyword)
                        .or()
                        .like(Classroom::getCode, keyword)
                        .or()
                        .like(Classroom::getDescription, keyword))
                .eq(gradeLevel != null && !gradeLevel.isEmpty(), Classroom::getGradeLevel, gradeLevel)
                .eq(major != null && !major.isEmpty(), Classroom::getMajor, major)
                .orderByDesc(Classroom::getCreatedAt);
        return this.list(queryWrapper);
    }

    /**
     * 按课程查询关联班级。
     */
    @Override
    public List<Classroom> listByCourse(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<Classroom>()
                .eq(Classroom::getCourseId, courseId)
                .eq(Classroom::getIsActive, true)
                .orderByDesc(Classroom::getCreatedAt));
    }

    /**
     * 统计某课程下各班级的「待批改作业数」。
     * 聚合链：课程任务(tasks.course_id) → 待批改提交(submissions.status=SUBMITTED)
     *       → 学生归属班级(users.class_id)，按班级汇总。
     * 任一环节数据为空时返回各班级 pendingCount=0 的结构化结果（非空列表），
     * 保证前端驾驶舱始终可渲染真实来源（0 即真实值，非占位）。
     */
    @Override
    public List<Map<String, Object>> countPendingByCourse(Long courseId) {
        List<Classroom> classrooms = listByCourse(courseId);
        if (classrooms.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) 班级学生归属表：studentId -> classId
        List<Long> classroomIds = classrooms.stream().map(Classroom::getId).collect(Collectors.toList());
        List<User> students = userService.list(new LambdaQueryWrapper<User>()
                .in(User::getClassId, classroomIds)
                .eq(User::getRole, "STUDENT"));
        Map<Long, Long> studentClassMap = new HashMap<>();
        for (User student : students) {
            if (student.getId() != null && student.getClassId() != null) {
                studentClassMap.put(student.getId(), student.getClassId());
            }
        }

        // 2) 课程任务集合
        List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>()
                        .eq(Task::getCourseId, courseId))
                .stream().map(Task::getId).collect(Collectors.toList());
        Set<Long> taskIdSet = new HashSet<>(taskIds);

        // 3) 待批改提交聚合：班级 -> 计数
        Map<Long, Long> pendingByClass = new HashMap<>();
        if (!taskIdSet.isEmpty() && !studentClassMap.isEmpty()) {
            List<Submission> pendingSubmissions = submissionService.list(new LambdaQueryWrapper<Submission>()
                    .in(Submission::getTaskId, taskIdSet)
                    .eq(Submission::getStatus, SubmissionStatus.SUBMITTED.name()));
            for (Submission submission : pendingSubmissions) {
                Long classId = studentClassMap.get(submission.getStudentId());
                if (classId != null) {
                    pendingByClass.merge(classId, 1L, Long::sum);
                }
            }
        }

        // 4) 输出结构化结果（班级顺序与 by-course 一致，含真实 0 值）
        List<Map<String, Object>> result = new ArrayList<>();
        for (Classroom classroom : classrooms) {
            Map<String, Object> item = new HashMap<>();
            item.put("classroomId", classroom.getId());
            item.put("classroomName", classroom.getName());
            item.put("pendingCount", pendingByClass.getOrDefault(classroom.getId(), 0L));
            result.add(item);
        }
        return result;
    }
}
