package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.UserSession;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserSessionService;
import com.aicourse.vo.UserSessionStatsVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户会话管理接口控制器
 * 提供在线用户、会话历史、强制下线、异常检测等RESTful API
 */
@RestController
@RequestMapping("/user-sessions")
@Tag(name = "用户会话")
public class UserSessionController {

    private static final Logger log = LoggerFactory.getLogger(UserSessionController.class);

    private final UserSessionService userSessionService;
    private final AdminScopeGuard adminScopeGuard;

    public UserSessionController(UserSessionService userSessionService,
                                 AdminScopeGuard adminScopeGuard) {
        this.userSessionService = userSessionService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 获取当前在线用户列表
     * @return 在线用户会话列表
     */
    @GetMapping("/online")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserSession>> getOnlineUsers() {
        // 8A（WP9）：会话监视对院系管理员 403、只读校级；TEACHER 的全站在线枚举一并摘除
        // （教师枚举全平台用户会话本就是越界面，随本批一并收紧）
        adminScopeGuard.assertSchoolOnly();
        return ApiResponse.success(userSessionService.list());
    }

    /**
     * 获取用户会话历史
     * @param userId 用户ID
     * @return 会话历史列表
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<List<UserSession>> getSessionHistory(@PathVariable Long userId) {
        // 8A（WP9）：会话历史属审计面——本人可查自己；管理员侧仅校级
        assertSelfOrSchoolAdmin(userId);
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
        UserSession session = userSessionService.getById(id);
        if (session != null) {
            adminScopeGuard.assertUserAccessById(session.getUserId());
        }
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
    public ApiResponse<UserSessionStatsVO> getSessionStats() {
        adminScopeGuard.assertSchoolOnly();
        Long onlineUserCount = userSessionService.getOnlineUserCount();

        // 清理过期会话并返回清理数量
        int cleanedCount = userSessionService.cleanExpiredSessions();

        return ApiResponse.success(new UserSessionStatsVO(onlineUserCount, cleanedCount));
    }

    /**
     * 检测异常会话（异地登录）
     * @param userId 用户ID
     * @return 异常会话列表
     */
    @GetMapping("/abnormal/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN') or #userId == authentication.principal")
    public ApiResponse<List<UserSession>> detectAbnormal(@PathVariable Long userId) {
        assertSelfOrSchoolAdmin(userId);
        List<UserSession> abnormalSessions = userSessionService.detectAbnormalSessions(userId);
        return ApiResponse.success(abnormalSessions);
    }

    /**
     * 审计自助口径（8A/WP9）：本人可查自己的会话面；管理员侧仅校级（院系 403）。
     */
    private void assertSelfOrSchoolAdmin(Long userId) {
        Long currentUserId = adminScopeGuard.currentUserId();
        if (userId.equals(currentUserId)) {
            return;
        }
        adminScopeGuard.assertSchoolOnly();
    }
}
