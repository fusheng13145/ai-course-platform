package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.util.ExcelUtil;
import com.aicourse.util.PasswordPolicy;
import com.aicourse.vo.AdminUserImportResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import com.aicourse.common.RoleConstants;

/**
 * 系统管理接口控制器（仅管理员可访问）
 * 提供用户管理类管理员专属 RESTful API。
 *
 * <p>系统维护与数据统计端点已按 S-01 拆分到 {@link SystemMaintenanceController}
 * 与 {@link AdminStatisticsController}，路由与权限注解保持不变。</p>
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "用户与账号管理")
public class SystemAdminController {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    /**
     * 允许通过导入创建的角色白名单。
     *
     * <p>Excel 导入路径不经过 {@code AdminCreateUserDTO} 的 {@code @Pattern} 校验，
     * 而数据库 {@code users.role} 仅为 {@code varchar(20)} 无枚举约束 —— 若不显式校验，
     * 任意角色值（如 "BOSS"）都能建号成功并进入权限体系。</p>
     */
    private static final Set<String> VALID_ROLES =
            Set.of(RoleConstants.STUDENT, RoleConstants.TEACHER, RoleConstants.ADMIN);

    /**
     * 手机号格式（与 {@code AdminCreateUserDTO} 的 {@code @Pattern("^1[3-9]\\d{9}$")} 一致）。
     *
     * <p>与角色白名单同源缺口：Excel 导入路径不经过 DTO 校验，此前手机号可写入任意文本。</p>
     */
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AdminScopeGuard adminScopeGuard;
    private final UserSessionService userSessionService;

    public SystemAdminController(UserService userService,
                                 PasswordEncoder passwordEncoder,
                                 AdminScopeGuard adminScopeGuard,
                                 UserSessionService userSessionService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.adminScopeGuard = adminScopeGuard;
        this.userSessionService = userSessionService;
    }


    // ==================== 用户管理（7个接口） ====================

    /**
     * 获取用户列表（支持角色/状态/关键词筛选）
     */
    @GetMapping("/admin/users")
    public ApiResponse<PagedResponse<User>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getFullName, keyword)
                        .or()
                        .like(User::getEmail, keyword));
        Long collegeFilter = adminScopeGuard.currentCollegeFilter();
        if (collegeFilter != null) {
            qw.eq(User::getCollegeId, collegeFilter);
        }
        Page<User> userPage = userService.page(Page.of(page, size), qw);
        // 密码脱敏
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return ApiResponse.success(PagedResponse.of(userPage));
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/admin/users/{userId}")
    public ApiResponse<User> getUserDetail(@PathVariable Long userId) {
        User user = userService.getUserProfile(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserAccess(user);
        return ApiResponse.success(user);
    }

    /**
     * 创建用户（修复：改用 AdminCreateUserDTO 白名单绑定，password 可正常反序列化；
     * 原 @Valid @RequestBody User 因 User.password @JsonIgnore + @NotBlank 矛盾，创建永远 400 不可用）
     */
    @PostMapping("/admin/users")
    public ApiResponse<User> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        PasswordPolicy.validate(dto.getPassword());
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        // 密码加密
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (adminScopeGuard.isCollegeAdmin()) {
            if (RoleConstants.ADMIN.equals(dto.getRole())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "院系管理员不能创建管理员账号");
            }
            Long ownCollegeId = adminScopeGuard.currentCollegeId();
            if (dto.getCollegeId() != null && !ownCollegeId.equals(dto.getCollegeId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "只能创建本学院用户");
            }
            user.setCollegeId(ownCollegeId);
        } else if (dto.getCollegeId() != null) {
            user.setCollegeId(dto.getCollegeId());
        }
        if (dto.getAdminClassId() != null) {
            user.setAdminClassId(dto.getAdminClassId() == 0L ? null : dto.getAdminClassId());
        }
        userService.save(user);
        user.setPassword(null);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/admin/users/{userId}")
    public ApiResponse<User> updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUpdateUserDTO dto) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(existing);
        if (adminScopeGuard.isCollegeAdmin()) {
            if (RoleConstants.ADMIN.equals(dto.getRole())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "院系管理员不能把用户改为管理员");
            }
            if (dto.getCollegeId() != null
                    && !adminScopeGuard.currentCollegeId().equals(dto.getCollegeId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "不能把用户移出本学院");
            }
        }
        // 白名单字段复制：仅允许 DTO 暴露的业务字段更新（不含密码/主键/审计字段）
        User user = new User();
        user.setId(userId);
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive());
        user.setAvatarUrl(dto.getAvatarUrl());
        user.setBio(dto.getBio());
        if (dto.getCollegeId() != null) {
            user.setCollegeId(dto.getCollegeId());
        }
        if (dto.getAdminClassId() != null) {
            user.setAdminClassId(dto.getAdminClassId() == 0L ? null : dto.getAdminClassId());
        }
        userService.updateById(user);
        if (dto.getAdminClassId() != null && dto.getAdminClassId() == 0L) {
            // MyBatis-Plus 默认忽略 null 字段；移出行政班需要显式 set null。
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getAdminClassId, null));
        }
        User updated = userService.getUserProfile(userId);
        return ApiResponse.success("更新成功", updated);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/admin/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(existing);
        userService.removeById(userId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 重置用户密码
     */
    @PutMapping("/admin/users/{userId}/reset-password")
    public ApiResponse<Void> resetUserPassword(@PathVariable Long userId,
                                                @Valid @RequestBody Map<String, String> body) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(user);
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        // 修复：重置密码同样必须满足统一复杂度策略，防止管理端设置弱口令
        PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        // 与用户自助改密同口径：密码写库成功后吊销全部活跃会话，旧 access/refresh token 一律失效。
        // 吊销失败不回滚密码更新，但必须显式记日志便于人工核查。
        try {
            int revoked = userSessionService.revokeAllActiveSessions(userId);
            log.info("[admin] 重置用户密码后吊销活跃会话: userId={}, revoked={}", userId, revoked);
        } catch (Exception e) {
            log.error("[admin] 密码已重置但吊销会话失败（需人工核查）: userId={}", userId, e);
        }
        return ApiResponse.success("密码已重置，需重新登录", null);
    }

    /**
     * 批量导入用户
     */
    @Operation(summary = "批量导入用户", description = "JSON 批量导入（兼容保留）；Excel(.xlsx) 批量导入请使用 /admin/users/import-excel")
    @PostMapping("/admin/users/import")
    public ApiResponse<AdminUserImportResultVO> importUsers(@Valid @RequestBody List<AdminCreateUserDTO> userDTOs) {
        adminScopeGuard.assertSchoolOnly();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        for (AdminCreateUserDTO dto : userDTOs) {
            String username = dto.getUsername();
            try {
                // 与 Excel 路径一致的显式角色校验（@Valid 不校验集合元素）
                if (!VALID_ROLES.contains(dto.getRole())) {
                    throw new BusinessException("角色不合法（应为 STUDENT/TEACHER/ADMIN）");
                }
                User user = new User();
                user.setUsername(username);
                user.setFullName(dto.getFullName());
                user.setPhone(dto.getPhone());
                user.setEmail(dto.getEmail());
                user.setRole(dto.getRole());
                user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
                userService.save(user);
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入用户失败，已跳过 [{}]: {}", username, e.getMessage());
                failCount++;
                errors.add("用户名「" + username + "」：" + friendlyImportReason(e));
            }
        }
        return ApiResponse.success("导入完成",
                new AdminUserImportResultVO(successCount, failCount, userDTOs.size(), null,
                        errors.isEmpty() ? null : errors));
    }

    /**
     * 将底层异常转成管理员可读的失败原因。
     *
     * <p>逐行导入的失败多来自唯一索引冲突，原始信息形如
     * {@code Duplicate entry 'x@y.com' for key 'users.uk_users_email'}，直接透出对使用者无意义。</p>
     */
    private static String friendlyImportReason(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("uk_users_username")) {
            return "用户名已存在";
        }
        if (msg.contains("uk_users_email")) {
            return "邮箱已被其他账号使用";
        }
        if (msg.contains("uk_users_phone")) {
            return "手机号已被其他账号使用";
        }
        if (e instanceof BusinessException) {
            return msg;
        }
        return "写入失败：" + (msg.length() > 80 ? msg.substring(0, 80) + "…" : msg);
    }

    /**
     * Excel 批量导入用户
     */
    @Operation(summary = "Excel 批量导入用户",
            description = "上传 .xlsx 文件批量导入用户（表头：用户名/姓名/邮箱/角色/初始密码/手机号/学号/专业/年级/院系，初始密码缺省随机生成 8 位并随响应返回，供管理员转告用户）")
    @PostMapping(value = "/admin/users/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminUserImportResultVO> importUsersExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        adminScopeGuard.assertSchoolOnly();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
        ExcelUtil.UserImportResult result = ExcelUtil.readUsersWithGenerated(file);
        List<User> users = result.users();
        Map<String, String> generatedPasswords = result.generatedPasswords();
        if (users.isEmpty()) {
            throw new BusinessException("文件中没有可导入的用户数据");
        }
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        // 仅返回「未填密码、由系统随机生成」用户的临时密码（明文仅本次响应出现一次，供管理员转告）
        Map<String, String> tempPasswords = new LinkedHashMap<>();
        for (User user : users) {
            String username = user.getUsername();
            try {
                // 角色白名单校验：本路径不经过 AdminCreateUserDTO 的 @Pattern，
                // 缺失该校验时 "BOSS" 等非法角色会被直接写入数据库并通过登录鉴权。
                if (!VALID_ROLES.contains(user.getRole())) {
                    throw new BusinessException(
                            "角色不合法（应为 STUDENT/TEACHER/ADMIN），当前值：「" + user.getRole() + "」");
                }
                // 手机号格式校验（与 AdminCreateUserDTO 的 @Pattern 同口径；留空视为不填）
                if (user.getPhone() != null && !PHONE_PATTERN.matcher(user.getPhone()).matches()) {
                    throw new BusinessException(
                            "手机号格式不正确（应为 1 开头的 11 位数字），当前值：「" + user.getPhone() + "」");
                }
                user.setId(null);
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                user.setIsActive(true);
                user.setIsVerified(false);
                userService.save(user);
                if (generatedPasswords.containsKey(username)) {
                    tempPasswords.put(username, generatedPasswords.get(username));
                }
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入用户失败，已跳过 [{}]: {}", username, e.getMessage());
                failCount++;
                errors.add("用户名「" + username + "」：" + friendlyImportReason(e));
            }
        }
        return ApiResponse.success("导入完成",
                new AdminUserImportResultVO(successCount, failCount, users.size(), tempPasswords,
                        errors.isEmpty() ? null : errors));
    }
}
