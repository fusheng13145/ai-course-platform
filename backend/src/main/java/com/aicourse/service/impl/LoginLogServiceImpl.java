package com.aicourse.service.impl;

import com.aicourse.entity.LoginLog;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.service.LoginLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录日志服务实现类
 * 实现登录历史、统计、异常检测、趋势分析等核心业务逻辑
 */
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {

    /**
     * 获取用户登录历史（分页）
     */
    @Override
    public Page<LoginLog> getUserLoginHistory(Long userId, int page, int size) {
        LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getCreatedAt);

        return this.page(Page.of(page, size), queryWrapper);
    }

    /**
     * 登录统计（成功/失败次数、最后登录时间等）
     */
    @Override
    public Map<String, Object> getLoginStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 查询该用户所有登录日志
        LambdaQueryWrapper<LoginLog> allWrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId);
        List<LoginLog> allLogs = this.list(allWrapper);

        // 总登录次数
        long totalCount = allLogs.size();
        stats.put("totalCount", totalCount);

        // 成功次数
        long successCount = allLogs.stream()
                .filter(log -> "SUCCESS".equals(log.getLoginStatus()))
                .count();
        stats.put("successCount", successCount);

        // 失败次数
        long failedCount = totalCount - successCount;
        stats.put("failedCount", failedCount);

        // 成功率
        double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
        stats.put("successRate", Math.round(successRate * 100.0) / 100.0);

        // 最后登录时间（最近一次成功的登录）
        Optional<LoginLog> lastSuccessLog = allLogs.stream()
                .filter(log -> "SUCCESS".equals(log.getLoginStatus()))
                .max(Comparator.comparing(LoginLog::getCreatedAt));
        lastSuccessLog.ifPresent(log -> stats.put("lastLoginTime", log.getCreatedAt()));

        // 最后登录IP
        lastSuccessLog.ifPresent(log -> stats.put("lastLoginIp", log.getIpAddress()));

        // 最后登录地点
        lastSuccessLog.ifPresent(log -> stats.put("lastLoginLocation", log.getLocation()));

        // 不同IP登录数量
        Set<String> uniqueIps = allLogs.stream()
                .map(LoginLog::getIpAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        stats.put("uniqueIpCount", uniqueIps.size());

        return stats;
    }

    /**
     * 异常登录检测（频繁失败IP）
     * 查询同一用户最近失败的登录记录，按IP分组，找出失败次数过多的IP
     */
    @Override
    public List<LoginLog> detectSuspiciousLogins(Long userId) {
        // 查询该用户最近30天内的所有失败登录记录
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<LoginLog> failedWrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .eq(LoginLog::getLoginStatus, "FAILED")
                .ge(LoginLog::getCreatedAt, thirtyDaysAgo)
                .orderByDesc(LoginLog::getCreatedAt);

        List<LoginLog> failedLogs = this.list(failedWrapper);

        if (failedLogs.isEmpty()) {
            return new ArrayList<>();
        }

        // 按IP地址分组统计失败次数
        Map<String, List<LoginLog>> logsByIp = failedLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getIpAddress() != null ? log.getIpAddress() : "unknown"
                ));

        // 筛选出失败次数>=3次的IP对应的日志作为可疑记录
        List<LoginLog> suspiciousLogs = new ArrayList<>();
        for (Map.Entry<String, List<LoginLog>> entry : logsByIp.entrySet()) {
            if (entry.getValue().size() >= 3) {
                suspiciousLogs.addAll(entry.getValue());
            }
        }

        // 按时间倒序排列
        suspiciousLogs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // 只返回最近20条
        return suspiciousLogs.size() > 20 ? suspiciousLogs.subList(0, 20) : suspiciousLogs;
    }

    /**
     * 登录趋势（按日期统计）
     * 统计指定日期范围内每天的登录成功/失败次数
     */
    @Override
    public List<Map<String, Object>> getLoginTrends(LocalDate start, LocalDate end) {
        List<Map<String, Object>> trends = new ArrayList<>();

        // 查询日期范围内的所有登录日志
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<LoginLog>()
                .ge(LoginLog::getCreatedAt, startDateTime)
                .le(LoginLog::getCreatedAt, endDateTime)
                .orderByAsc(LoginLog::getCreatedAt);

        List<LoginLog> logs = this.list(queryWrapper);

        // 按日期分组统计
        Map<LocalDate, List<LoginLog>> logsByDate = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getCreatedAt().toLocalDate()));

        // 遍历日期范围，生成每日统计数据
        LocalDate current = start;
        while (!current.isAfter(end)) {
            Map<String, Object> dayStats = new HashMap<>();
            dayStats.put("date", current.toString());

            List<LoginLog> dayLogs = logsByDate.getOrDefault(current, new ArrayList<>());

            long successCount = dayLogs.stream()
                    .filter(log -> "SUCCESS".equals(log.getLoginStatus()))
                    .count();
            long failedCount = dayLogs.size() - successCount;

            dayStats.put("successCount", successCount);
            dayStats.put("failedCount", failedCount);
            dayStats.put("totalCount", dayLogs.size());

            trends.add(dayStats);
            current = current.plusDays(1);
        }

        return trends;
    }
}
