package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.UserSession;
import com.aicourse.service.UserSessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户会话管理接口控制器
 * 提供在线用户、会话历史、强制下线、异常检测等RESTful API
 */
@RestController
@RequestMapping("/user-sessions")
public class UserSessionController {

    private final UserSessionService userSessionService;

    public UserSessionController(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    /**
     * 获取当前在线用户列表
     * @return 在线用户会话列表
     */
    @GetMapping("/online")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<UserSession>> getOnlineUsers() {
        // 查询所有活跃且未过期的会话
        // 注意：实际应用中可能需要分页，这里简化处理返回列表
        List<UserSession> onlineSessions = userSessionService.list();
        return ApiResponse.success(onlineSessions);
    }

    /**
     * 获取用户会话历史
     * @param userId 用户ID
     * @return 会话历史列表
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<List<UserSession>> getSessionHistory(@PathVariable Long userId) {
        List<UserSession> sessions = userSessionService.getSessionHistory(userId);
        return ApiResponse.success(sessions);
    }

    /**
     * 强制下线某设备（管理员权限）
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}/force-logout")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> forceLogout(@PathVariable Long id) {
        userSessionService.forceLogout(id);
        return ApiResponse.success("已强制下线", null);
    }

    /**
     * 获取会话统计信息
     * 在线用户数、总活跃会话数等
     * @return 统计数据
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("onlineUserCount", userSessionService.getOnlineUserCount());

        // 清理过期会话并返回清理数量
        int cleanedCount = userSessionService.cleanExpiredSessions();
        stats.put("cleanedExpiredSessions", cleanedCount);

        return ApiResponse.success(stats);
    }

    /**
     * 检测异常会话（异地登录）
     * @param userId 用户ID
     * @return 异常会话列表
     */
    @GetMapping("/abnormal/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<List<UserSession>> detectAbnormal(@PathVariable Long userId) {
        List<UserSession> abnormalSessions = userSessionService.detectAbnormalSessions(userId);
        return ApiResponse.success(abnormalSessions);
    }
}
