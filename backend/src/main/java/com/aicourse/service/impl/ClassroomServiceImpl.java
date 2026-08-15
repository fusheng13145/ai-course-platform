package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ClassroomMapper;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级管理服务实现类
 */
@Service
public class ClassroomServiceImpl extends ServiceImpl<ClassroomMapper, Classroom> implements ClassroomService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomServiceImpl.class);

    private final UserService userService;

    public ClassroomServiceImpl(ClassroomMapper classroomMapper, UserService userService) {
        this.baseMapper = classroomMapper;
        this.userService = userService;
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
}
