package com.aicourse.service;

import com.aicourse.entity.LoginLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 登录日志服务接口
 * 提供登录历史、统计、异常检测、趋势分析等功能
 */
public interface LoginLogService extends com.baomidou.mybatisplus.extension.service.IService<LoginLog> {

    /**
     * 获取用户登录历史（分页）
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页登录日志列表
     */
    Page<LoginLog> getUserLoginHistory(Long userId, int page, int size);

    /**
     * 登录统计（成功/失败次数、最后登录时间等）
     * @param userId 用户ID
     * @return 统计信息Map
     */
    Map<String, Object> getLoginStats(Long userId);

    /**
     * 异常登录检测（频繁失败IP）
     * 查询同一用户最近失败次数过多的IP地址对应的日志
     * @param userId 用户ID
     * @return 可疑登录记录列表
     */
    List<LoginLog> detectSuspiciousLogins(Long userId);

    /**
     * 登录趋势（按日期统计）
     * 统计指定日期范围内的登录数据
     * @param start 开始日期
     * @param end 结束日期
     * @return 每日登录统计数据列表
     */
    List<Map<String, Object>> getLoginTrends(LocalDate start, LocalDate end);
}
