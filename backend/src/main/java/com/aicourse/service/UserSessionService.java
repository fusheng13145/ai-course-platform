package com.aicourse.service;

import com.aicourse.entity.UserSession;
import java.util.List;

/**
 * 用户会话服务接口
 * 提供会话管理、在线状态、异常检测等功能
 */
public interface UserSessionService extends com.baomidou.mybatisplus.extension.service.IService<UserSession> {

    /**
     * 获取用户当前有效会话
     * @param userId 用户ID
     * @return 当前有效会话，无则返回null
     */
    UserSession getCurrentSession(Long userId);

    /**
     * 获取用户所有会话历史
     * @param userId 用户ID
     * @return 会话列表（按登录时间倒序）
     */
    List<UserSession> getSessionHistory(Long userId);

    /**
     * 强制下线某设备
     * 将指定会话标记为无效
     * @param sessionId 会话ID
     */
    void forceLogout(Long sessionId);

    /**
     * 清理过期会话
     * 删除所有已过期的会话记录
     * @return 清理的记录数
     */
    int cleanExpiredSessions();

    /**
     * 获取在线用户数
     * 统计当前有效且未过期的会话对应的用户数量
     * @return 在线用户数
     */
    Long getOnlineUserCount();

    /**
     * 检测异地登录（不同IP/设备同时活跃的会话）
     * 查询用户所有活跃会话，按IP分组，有多组IP则异常
     * @param userId 用户ID
     * @return 异常会话列表
     */
    List<UserSession> detectAbnormalSessions(Long userId);
}
