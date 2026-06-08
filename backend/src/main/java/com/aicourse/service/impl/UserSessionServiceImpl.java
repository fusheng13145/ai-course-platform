package com.aicourse.service.impl;

import com.aicourse.entity.UserSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.service.UserSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户会话服务实现类
 * 实现会话管理、在线状态、异常检测等核心业务逻辑
 */
@Service
public class UserSessionServiceImpl extends ServiceImpl<UserSessionMapper, UserSession> implements UserSessionService {

    /**
     * 获取用户当前有效会话
     * 查询isCurrent=true且isActive=true且未过期的会话
     */
    @Override
    public UserSession getCurrentSession(Long userId) {
        LambdaQueryWrapper<UserSession> queryWrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getIsActive, true)
                .eq(UserSession::getIsCurrent, true)
                .gt(UserSession::getExpiresAt, LocalDateTime.now())
                .orderByDesc(UserSession::getLastActivity)
                .last("LIMIT 1");

        return this.getOne(queryWrapper);
    }

    /**
     * 获取用户所有会话历史（按登录时间倒序）
     */
    @Override
    public List<UserSession> getSessionHistory(Long userId) {
        LambdaQueryWrapper<UserSession> queryWrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .orderByDesc(UserSession::getLoginTime);

        return this.list(queryWrapper);
    }

    /**
     * 强制下线某设备
     * 将指定会话标记为无效
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceLogout(Long sessionId) {
        UserSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        session.setIsActive(false);
        this.updateById(session);
    }

    /**
     * 清理过期会话
     * 删除所有expiresAt < now的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredSessions() {
        LambdaQueryWrapper<UserSession> queryWrapper = new LambdaQueryWrapper<UserSession>()
                .lt(UserSession::getExpiresAt, LocalDateTime.now());

        return this.count(queryWrapper) > 0 ? this.remove(queryWrapper) ? (int) this.count(queryWrapper) : 0 : 0;
    }

    /**
     * 获取在线用户数
     * 统计isActive=true且expiresAt > now的不同userId数量
     */
    @Override
    public Long getOnlineUserCount() {
        LambdaQueryWrapper<UserSession> queryWrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getIsActive, true)
                .gt(UserSession::getExpiresAt, LocalDateTime.now());

        // 使用SQL去重统计不同用户数
        List<UserSession> activeSessions = this.list(queryWrapper);
        return activeSessions.stream()
                .map(UserSession::getUserId)
                .distinct()
                .count();
    }

    /**
     * 检测异地登录（不同IP/设备同时活跃的会话）
     * 查询用户所有活跃会话，按IP分组，有多组IP则认为存在异地登录风险
     */
    @Override
    public List<UserSession> detectAbnormalSessions(Long userId) {
        // 查询该用户所有活跃且未过期的会话
        LambdaQueryWrapper<UserSession> queryWrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getIsActive, true)
                .gt(UserSession::getExpiresAt, LocalDateTime.now())
                .orderByDesc(UserSession::getLastActivity);

        List<UserSession> activeSessions = this.list(queryWrapper);

        if (activeSessions.size() <= 1) {
            // 只有一个或没有活跃会话，不存在异常
            return new ArrayList<>();
        }

        // 按IP地址分组
        Map<String, List<UserSession>> sessionsByIp = activeSessions.stream()
                .collect(Collectors.groupingBy(
                        session -> session.getIpAddress() != null ? session.getIpAddress() : "unknown",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 如果存在多个不同的IP地址，则可能存在异地登录
        if (sessionsByIp.size() > 1) {
            // 返回除最近活动会话外的其他会话作为可疑会话
            List<UserSession> abnormalSessions = new ArrayList<>();
            boolean first = true;
            for (List<UserSession> sessions : sessionsByIp.values()) {
                if (first) {
                    // 保留第一个IP组的会话（假设为正常）
                    first = false;
                    continue;
                }
                // 其他IP组的会话标记为异常
                abnormalSessions.addAll(sessions);
            }
            return abnormalSessions;
        }

        return new ArrayList<>();
    }
}
