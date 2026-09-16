package com.aicourse.service;

import com.aicourse.entity.UserSession;
import java.util.List;

/**
 * 用户会话服务接口
 * 提供会话管理、在线状态、异常检测等功能
 */
public interface UserSessionService extends com.baomidou.mybatisplus.extension.service.IService<UserSession> {

    /**
     * 检查会话是否活跃（根据 token 判定）
     * @param token 会话令牌
     * @return true 如果会话有效且未过期
     */
    boolean isSessionActive(String token);

    /**
     * 失效会话缓存（令牌轮换/强制下线后清除缓存）
     * @param token 会话令牌
     */
    void evictSessionCache(String token);

    /**
     * 吊销指定用户的全部活跃会话（修复 #10b）。
     *
     * <p>密码泄露场景下，受害者「改密码」本应立刻止损；若改密/重置时不吊销既有会话，
     * 攻击者手里已窃得的令牌仍可通过验签与会话活跃态校验，止损动作完全失效。
     * 因此改密、手机重置、邮箱重置、管理端重置密码成功后，一律全量吊销该用户的活跃会话
     * （含发起改密请求的那一条 —— 用户下一请求即 401，需重新登录）。</p>
     *
     * @param userId 用户ID
     * @return 被吊销的会话条数
     */
    int revokeAllActiveSessions(Long userId);

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
