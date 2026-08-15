package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.LoginLog;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.service.LoginLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Logger log = LoggerFactory.getLogger(LoginLogServiceImpl.class);

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

        try {
            // 查询日期范围内的所有登录日志
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

            LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<LoginLog>()
                    .ge(LoginLog::getCreatedAt, startDateTime)
                    .le(LoginLog::getCreatedAt, endDateTime)
                    .orderByAsc(LoginLog::getCreatedAt);

            List<LoginLog> logs = this.list(queryWrapper);

            // 按日期分组统计（过滤掉 createdAt 为 null 的记录）
            Map<LocalDate, List<LoginLog>> logsByDate = logs.stream()
                    .filter(log -> log.getCreatedAt() != null)
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
        } catch (Exception e) {
            log.error("查询登录日志失败: {}", e.getMessage(), e);

            // 异常时返回空趋势数据
            trends.clear();
        }

        return trends;
    }

    /**
     * 记录登录成功（记录IP地址、UserAgent、地理位置等客户端信息）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginSuccess(Long userId, String username, String ipAddress,
                                    String userAgent, String location) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginStatus("SUCCESS");
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setLocation(location);
        // 从UserAgent中解析浏览器和操作系统（简单处理）
        if (userAgent != null && !userAgent.isEmpty()) {
            log.setBrowser(extractBrowser(userAgent));
            log.setOperatingSystem(extractOS(userAgent));
        }
        this.save(log);
    }

    /**
     * 记录登录失败（记录失败原因）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordLoginFailure(Long userId, String username, String ipAddress,
                                    String userAgent, String failureReason) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginStatus("FAILED");
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setFailureReason(failureReason != null ? failureReason : "未知原因");
        if (userAgent != null && !userAgent.isEmpty()) {
            log.setBrowser(extractBrowser(userAgent));
            log.setOperatingSystem(extractOS(userAgent));
        }
        this.save(log);
    }

    /**
     * 按失败原因分组统计登录失败次数
     */
    @Override
    public Map<String, Long> getFailuresGroupedByReason(Long userId) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getLoginStatus, "FAILED");
        if (userId != null) {
            wrapper.eq(LoginLog::getUserId, userId);
        }

        List<LoginLog> failedLogs = this.list(wrapper);

        return failedLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getFailureReason() != null ? log.getFailureReason() : "UNKNOWN",
                        Collectors.counting()
                ));
    }

    /**
     * 异常登录检测（同账号短时间内多地登录 → 异常标记）
     */
    @Override
    public Map<String, Object> detectAbnormalLogin(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);

        // 查询该用户最近24小时内的所有登录记录
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<LoginLog> recentLogs = this.list(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .ge(LoginLog::getCreatedAt, twentyFourHoursAgo)
                .orderByDesc(LoginLog::getCreatedAt));

        result.put("recentLoginCount", recentLogs.size());

        // 检测IP变化
        Set<String> uniqueIps = recentLogs.stream()
                .map(LoginLog::getIpAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        boolean isAbnormal = false;
        List<String> abnormalDetails = new ArrayList<>();

        if (uniqueIps.size() > 1) {
            isAbnormal = true;
            abnormalDetails.add("24小时内从" + uniqueIps.size() + "个不同IP登录: " + String.join(", ", uniqueIps));
        }

        // 检测地理位置变化
        Set<String> uniqueLocations = recentLogs.stream()
                .map(LoginLog::getLocation)
                .filter(Objects::nonNull)
                .filter(loc -> !loc.isEmpty())
                .collect(Collectors.toSet());

        if (uniqueLocations.size() > 1) {
            isAbnormal = true;
            abnormalDetails.add("24小时内从" + uniqueLocations.size() + "个不同地点登录");
        }

        // 检测频繁失败后突然成功（可能被暴力破解后登入）
        long recentFailedCount = recentLogs.stream()
                .filter(log -> "FAILED".equals(log.getLoginStatus()))
                .count();
        long recentSuccessCount = recentLogs.stream()
                .filter(log -> "SUCCESS".equals(log.getLoginStatus()))
                .count();

        if (recentFailedCount >= 3 && recentSuccessCount >= 1) {
            isAbnormal = true;
            abnormalDetails.add("频繁失败(" + recentFailedCount + "次)后成功登录，可能存在安全风险");
        }

        // 检测异常时间段登录（凌晨2-5点）
        long nightLogins = recentLogs.stream()
                .filter(log -> log != null && "SUCCESS".equals(log.getLoginStatus())
                        && log.getCreatedAt() != null
                        && log.getCreatedAt().getHour() >= 2 && log.getCreatedAt().getHour() < 6)
                .count();

        if (nightLogins > 0) {
            abnormalDetails.add("检测到凌晨时段登录(" + nightLogins + "次)");
        }

        result.put("isAbnormal", isAbnormal);
        result.put("abnormalDetails", abnormalDetails);
        result.put("uniqueIpCount", uniqueIps.size());
        result.put("uniqueLocationCount", uniqueLocations.size());
        result.put("recentFailedCount", recentFailedCount);
        result.put("recentSuccessCount", recentSuccessCount);
        result.put("checkedAt", LocalDateTime.now());

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取登录日志列表（支持分页、状态筛选、用户筛选）
     */
    @Override
    public Page<LoginLog> getLoginLogList(int page, int size, String status, Long userId) {
        LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<LoginLog>();
        if (userId != null) {
            queryWrapper.eq(LoginLog::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq(LoginLog::getLoginStatus, status);
        }
        queryWrapper.orderByDesc(LoginLog::getCreatedAt);
        return this.page(Page.of(page, size), queryWrapper);
    }

    /**
     * 获取全局登录统计数据
     */
    @Override
    public Map<String, Object> getGlobalLoginStats() {
        Map<String, Object> stats = new HashMap<>();
        // 安全的聚合查询：用 count 替代全表 list()
        long totalCount = this.count();
        stats.put("totalCount", totalCount);

        long successCount = this.count(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getLoginStatus, "SUCCESS"));
        stats.put("successCount", successCount);
        stats.put("failedCount", totalCount - successCount);
        stats.put("successRate", totalCount > 0 ? Math.round(successCount * 100.0 / totalCount * 100.0) / 100.0 : 0);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = this.count(new LambdaQueryWrapper<LoginLog>()
                .ge(LoginLog::getCreatedAt, todayStart));
        stats.put("todayCount", todayCount);

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long weekCount = this.count(new LambdaQueryWrapper<LoginLog>()
                .ge(LoginLog::getCreatedAt, weekAgo));
        stats.put("weekCount", weekCount);

        // 各登录状态分布
        Map<String, Long> statusDist = new HashMap<>();
        String[] statuses = {"SUCCESS", "FAILED", "LOCKED", "EXPIRED"};
        for (String st : statuses) {
            long cnt = this.count(new LambdaQueryWrapper<LoginLog>()
                    .eq(LoginLog::getLoginStatus, st));
            if (cnt > 0) statusDist.put(st, cnt);
        }
        stats.put("statusDistribution", statusDist);

        return stats;
    }

    /**
     * 获取最近登录记录
     */
    @Override
    public List<LoginLog> getRecentLogs(int limit) {
        if (limit <= 0) limit = 10;
        if (limit > 100) limit = 100;
        return this.list(new LambdaQueryWrapper<LoginLog>()
                .orderByDesc(LoginLog::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 获取失败登录记录（分页）
     */
    @Override
    public Page<LoginLog> getFailureLogs(int page, int size, Long userId) {
        LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getLoginStatus, "FAILED");
        if (userId != null) {
            queryWrapper.eq(LoginLog::getUserId, userId);
        }
        queryWrapper.orderByDesc(LoginLog::getCreatedAt);
        return this.page(Page.of(page, size), queryWrapper);
    }

    /**
     * 从UserAgent中提取浏览器名称
     */
    private String extractBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("edg/")) return "Edge";
        if (userAgent.contains("chrome/")) return "Chrome";
        if (userAgent.contains("firefox/")) return "Firefox";
        if (userAgent.contains("safari/") && !userAgent.contains("chrome")) return "Safari";
        if (userAgent.contains("msie") || userAgent.contains("trident")) return "IE";
        return "Other";
    }

    /**
     * 从UserAgent中提取操作系统名称
     */
    private String extractOS(String userAgent) {
        if (userAgent == null) return "Unknown";
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("windows nt 10")) return "Windows 10+";
        if (userAgent.contains("windows")) return "Windows";
        if (userAgent.contains("mac os x")) return "macOS";
        if (userAgent.contains("linux")) return "Linux";
        if (userAgent.contains("android")) return "Android";
        if (userAgent.contains("iphone") || userAgent.contains("ipad")) return "iOS";
        return "Other";
    }
}
