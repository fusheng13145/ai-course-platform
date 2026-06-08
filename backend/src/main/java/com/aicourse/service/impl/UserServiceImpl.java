package com.aicourse.service.impl;

import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.UserMapper;
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
 * 用户服务实现类
 * 实现用户资料管理、班级同学、学生列表、统计、搜索等核心业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 获取用户完整资料（含班级名称关联查询）
     * 密码字段置空返回以保护隐私
     */
    @Override
    public User getUserProfile(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 密码字段置空返回
        user.setPassword(null);
        return user;
    }

    /**
     * 修改个人资料
     * 支持部分字段更新，忽略null值
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, User updates) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 部分字段更新，只更新非空字段
        if (updates.getFullName() != null) {
            user.setFullName(updates.getFullName());
        }
        if (updates.getPhone() != null) {
            user.setPhone(updates.getPhone());
        }
        if (updates.getAvatarUrl() != null) {
            user.setAvatarUrl(updates.getAvatarUrl());
        }
        if (updates.getBio() != null) {
            user.setBio(updates.getBio());
        }
        if (updates.getMajor() != null) {
            user.setMajor(updates.getMajor());
        }
        if (updates.getGrade() != null) {
            user.setGrade(updates.getGrade());
        }
        if (updates.getDepartment() != null) {
            user.setDepartment(updates.getDepartment());
        }
        if (updates.getTitle() != null) {
            user.setTitle(updates.getTitle());
        }
        if (updates.getNotificationSettings() != null) {
            user.setNotificationSettings(updates.getNotificationSettings());
        }

        this.updateById(user);
    }

    /**
     * 获取同班同学列表
     * 通过用户classId查找同一班级的其他用户
     */
    @Override
    public List<User> getClassmates(Long userId) {
        User user = this.getById(userId);
        if (user == null || user.getClassId() == null) {
            throw new BusinessException("用户不存在或未加入班级");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getClassId, user.getClassId())
                .ne(User::getId, userId)  // 排除自己
                .eq(User::getIsActive, true)
                .orderByAsc(User::getUsername);

        List<User> classmates = this.list(queryWrapper);
        // 密码脱敏
        classmates.forEach(u -> u.setPassword(null));
        return classmates;
    }

    /**
     * 获取教师的学生列表
     * 通过teacherId字段查找关联的学生
     */
    @Override
    public List<User> getTeacherStudents(Long teacherId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getTeacherId, String.valueOf(teacherId))
                .eq(User::getIsActive, true)
                .orderByAsc(User::getUsername);

        List<User> students = this.list(queryWrapper);
        // 密码脱敏
        students.forEach(u -> u.setPassword(null));
        return students;
    }

    /**
     * 用户数据统计
     * 返回用户基本信息统计
     */
    @Override
    public Map<String, Object> getUserStats(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", user.getId());
        stats.put("username", user.getUsername());
        stats.put("fullName", user.getFullName());
        stats.put("role", user.getRole());
        stats.put("email", user.getEmail());
        stats.put("isActive", user.getIsActive());
        stats.put("lastLogin", user.getLastLogin());
        stats.put("createdAt", user.getCreatedAt());

        // 统计同班同学数量（如果是学生）
        if (user.getClassId() != null) {
            LambdaQueryWrapper<User> classmateWrapper = new LambdaQueryWrapper<User>()
                    .eq(User::getClassId, user.getClassId())
                    .ne(User::getId, userId);
            long classmateCount = this.count(classmateWrapper);
            stats.put("classmateCount", classmateCount);
        }

        return stats;
    }

    /**
     * 搜索用户
     * 支持按关键词和角色筛选
     */
    @Override
    public Page<User> searchUsers(String keyword, String role, int page, int size) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or().like(User::getFullName, keyword)
                        .or().like(User::getEmail, keyword)
                        .or().like(User::getStudentId, keyword))
                .eq(User::getIsActive, true)
                .orderByDesc(User::getCreatedAt);

        Page<User> userPage = this.page(Page.of(page, size), queryWrapper);
        // 密码脱敏
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return userPage;
    }
}
