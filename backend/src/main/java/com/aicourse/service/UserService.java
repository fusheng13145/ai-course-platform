package com.aicourse.service;

import com.aicourse.entity.User;
import com.aicourse.vo.UserStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 用户服务接口
 * 提供用户资料管理、班级同学、学生列表、统计、搜索等功能
 */
public interface UserService extends com.baomidou.mybatisplus.extension.service.IService<User> {

    /**
     * 获取用户完整资料（含班级名称关联查询）
     * @param userId 用户ID
     * @return 用户信息（密码已置空）
     */
    User getUserProfile(Long userId);

    /**
     * 修改个人资料
     * @param userId 用户ID
     * @param updates 更新字段
     */
    void updateProfile(Long userId, User updates);

    /**
     * 获取同班同学列表
     * @param userId 用户ID
     * @return 同班同学列表
     */
    List<User> getClassmates(Long userId);

    /**
     * 获取教师的学生列表
     * @param teacherId 教师ID
     * @return 学生列表
     */
    List<User> getTeacherStudents(Long teacherId);

    /**
     * 用户数据统计
     * @param userId 用户ID
     * @return 统计信息Map
     */
    UserStatsVO getUserStats(Long userId);

    /**
     * 搜索用户
     * @param keyword 关键词
     * @param role 角色筛选（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 分页用户列表
     */
    Page<User> searchUsers(String keyword, String role, int page, int size);

    /**
     * 按学院范围搜索用户（院系管理员使用）。
     * @param keyword 关键词
     * @param role 角色筛选（可选）
     * @param page 页码
     * @param size 每页大小
     * @param collegeId 学院ID（必填过滤条件）
     * @return 分页用户列表
     */
    Page<User> searchUsersInCollege(String keyword, String role, int page, int size, Long collegeId);
}
