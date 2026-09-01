package com.aicourse.service;

import com.aicourse.entity.LoginLog;
import com.aicourse.vo.AbnormalLoginVO;
import com.aicourse.vo.DailyLoginTrendVO;
import com.aicourse.vo.LoginGlobalStatsVO;
import com.aicourse.vo.LoginStatsVO;
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
    LoginStatsVO getLoginStats(Long userId);

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
    List<DailyLoginTrendVO> getLoginTrends(LocalDate start, LocalDate end);

    /**
     * 记录登录成功（记录IP地址、UserAgent、地理位置等客户端信息）
     * @param userId 用户ID
     * @param username 登录账号
     * @param ipAddress IP地址
     * @param userAgent 浏览器UA标识
     * @param location 地理位置（可选）
     */
    void recordLoginSuccess(Long userId, String username, String ipAddress,
                             String userAgent, String location);

    /**
     * 记录登录失败（记录失败原因）
     * @param userId 用户ID（可能为null，如果用户名不存在）
     * @param username 登录账号
     * @param ipAddress IP地址
     * @param userAgent 浏览器UA标识
     * @param failureReason 失败原因（密码错误/账户锁定/验证码错误等）
     */
    void recordLoginFailure(Long userId, String username, String ipAddress,
                             String userAgent, String failureReason);

    /**
     * 按失败原因分组统计登录失败次数
     * @param userId 用户ID（可选，为null则统计全局）
     * @return 按failureReason分组的失败次数Map
     */
    Map<String, Long> getFailuresGroupedByReason(Long userId);

    /**
     * 获取登录日志列表（支持分页、状态筛选、用户筛选）
     * @param page 页码
     * @param size 每页大小
     * @param status 登录状态筛选（可选）
     * @param userId 用户ID筛选（可选）
     * @return 分页登录日志列表
     */
    Page<LoginLog> getLoginLogList(int page, int size, String status, Long userId);

    /**
     * 获取全局登录统计数据
     * @return 全局登录统计
     */
    LoginGlobalStatsVO getGlobalLoginStats();

    /**
     * 获取最近登录记录
     * @param limit 返回条数上限
     * @return 最近登录记录列表
     */
    List<LoginLog> getRecentLogs(int limit);

    /**
     * 获取失败登录记录（分页）
     * @param page 页码
     * @param size 每页大小
     * @param userId 用户ID筛选（可选，为null则查询全局）
     * @return 失败登录记录分页列表
     */
    Page<LoginLog> getFailureLogs(int page, int size, Long userId);

    /**
     * 异常登录检测（同账号短时间内多地登录检测）
     * @param userId 用户ID
     * @return 异常检测结果（包含是否异常、异常详情等）
     */
    AbnormalLoginVO detectAbnormalLogin(Long userId);
}
