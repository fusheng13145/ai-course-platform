package com.aicourse.application.user;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.util.ExcelUtil;
import com.aicourse.util.PasswordPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户管理用例编排（application 层）。
 *
 * <p><b>这一层为什么存在</b>：这些端点的职责不是「接 HTTP」，而是「编排一次完整的业务动作」——
 * 密码策略校验 → 院系作用域断言 → 实体组装与白名单字段复制 → 落库 → 关联动作（吊销会话）。
 * 它原先住在 {@code SystemAdminController} 里，代价是：
 * <ul>
 *   <li>控制器里出现 {@code @Transactional}（唯一一处），事务边界挂在外层壳上，
 *       控制器换个调用顺序就悄悄改变事务语义；</li>
 *   <li>控制器里直接拼 {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}，
 *       持久化细节泄漏到 Web 层，任何表结构/字段调整都要改控制器；</li>
 *   <li>「建号必须过密码策略」「院系管理员不能造管理员」这类规则无法单独测试，
 *       只能起 MockMvc 才能验证——规则被 HTTP 层绑死。</li>
 * </ul>
 *
 * <p>边界口径（阶段3 契约）：
 * controller 只做 HTTP（参数接收、DTO 校验、VO 转换、权限注解）；
 * application 做用例编排与事务边界，可以用查询 DSL，但不直接碰 mapper；
 * domain（service / service.component）持有领域规则与数据访问。</p>
 */
@Slf4j
@Service
public class UserAdminApplicationService {

    /**
     * 允许通过导入创建的角色白名单。
     *
     * <p>Excel 导入路径不经过 {@code AdminCreateUserDTO} 的 {@code @Pattern} 校验，
     * 而数据库 {@code users.role} 仅为 {@code varchar(20)} 无枚举约束 —— 若不显式校验，
     * 任意角色值（如 "BOSS"）都能建号成功并进入权限体系。</p>
     */
    private static final Set<String> VALID_ROLES =
            Set.of(RoleConstants.STUDENT, RoleConstants.TEACHER, RoleConstants.ADMIN);

    /** 手机号格式（与 {@code AdminCreateUserDTO} 的 {@code @Pattern("^1[3-9]\\d{9}$")} 一致）。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserService userService;
    private final UserSessionService userSessionService;
    private final PasswordEncoder passwordEncoder;
    private final AdminScopeGuard adminScopeGuard;

    public UserAdminApplicationService(UserService userService,
                                       UserSessionService userSessionService,
                                       PasswordEncoder passwordEncoder,
                                       AdminScopeGuard adminScopeGuard) {
        this.userService = userService;
        this.userSessionService = userSessionService;
        this.passwordEncoder = passwordEncoder;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 用户列表（支持角色/状态/关键词筛选，院系管理员自动限定本院）。
     *
     * <p>与 {@link UserService#searchUsers} 的差别是**故意保留的口径差异**：
     * 管理端列表必须能看到被停用的账号（{@code is_active=false}），因此不加 {@code isActive} 过滤，
     * 也不能复用那条走学生视角的查询。</p>
     */
    public IPage<User> pageUsers(int page, int size, String role, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getFullName, keyword)
                        .or()
                        .like(User::getEmail, keyword));
        Long collegeFilter = adminScopeGuard.currentCollegeFilter();
        if (collegeFilter != null) {
            wrapper.eq(User::getCollegeId, collegeFilter);
        }
        Page<User> userPage = userService.page(Page.of(page, size), wrapper);
        // 密码脱敏
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return userPage;
    }

    /** 用户详情（407 口径由应用层统一：不存在即 404，并做院系作用域断言）。 */
    public User getUserDetail(Long userId) {
        User user = userService.getUserProfile(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserAccess(user);
        return user;
    }

    /**
     * 创建用户。
     *
     * <p>规则顺序与历史实现一致：密码策略 → 院系管理员禁造管理员 / 禁跨院 → 落库。
     * 顺序不能随意调：先做角色与学院断言再校验密码，会让「无权造管理员」的请求先花一次
     * 密码策略计算，也更容易在日志里暴露策略细节。</p>
     */
    public User createUser(AdminCreateUserDTO dto) {
        PasswordPolicy.validate(dto.getPassword());
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
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
        return user;
    }

    /** 更新用户信息（白名单字段复制 + 移出行政班的显式 set null）。 */
    public User updateUser(Long userId, AdminUpdateUserDTO dto) {
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
        return userService.getUserProfile(userId);
    }

    /**
     * 删除用户（软删除：users.deleted=1，保留历史引用）。
     * <p>先吊销该用户全部活跃会话，再落软删除；两步同事务，避免出现「已删用户仍有有效令牌」。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(existing);
        int revoked = userSessionService.revokeAllActiveSessions(userId);
        log.info("[admin] 删除用户前吊销活跃会话: userId={}, revoked={}", userId, revoked);
        userService.removeById(userId);
    }

    /**
     * 重置密码。
     * <p>与用户自助改密同口径：密码写库与吊销全部活跃会话必须在同一事务内完成。
     * 吊销失败则整体回滚——"密码已改、旧 token 仍有效"属于安全缺口，不能以降级日志收场。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(user);
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        // 重置密码同样必须满足统一复杂度策略，防止管理端设置弱口令
        PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        // 旧会话必须在同一事务内吊销：失败向上抛出并回滚密码变更，不留"已改密但旧 token 可用"的窗口。
        int revoked = userSessionService.revokeAllActiveSessions(userId);
        log.info("[admin] 重置用户密码后吊销活跃会话: userId={}, revoked={}", userId, revoked);
    }

    /** JSON 批量导入（兼容保留）：逐行校验、逐行跳过，失败不中断整批。 */
    public ImportOutcome importUsers(List<AdminCreateUserDTO> items) {
        adminScopeGuard.assertSchoolOnly();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        for (AdminCreateUserDTO dto : items) {
            String username = dto.getUsername();
            try {
                // 与 Excel 路径一致的显式角色校验（@Valid 不校验集合元素）
                if (!VALID_ROLES.contains(dto.getRole())) {
                    throw new BusinessException("角色不合法（应为 STUDENT/TEACHER/ADMIN）");
                }
                // 批量导入同样必须走统一密码策略：否则可一次落地 100 个弱口令账号。
                PasswordPolicy.validate(dto.getPassword());
                User user = buildImportUser(dto);
                userService.save(user);
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入用户失败，已跳过 [{}]: {}", username, e.getMessage());
                failCount++;
                errors.add("用户名「" + username + "」：" + friendlyImportReason(e));
            }
        }
        return new ImportOutcome(successCount, failCount, items.size(), null,
                errors.isEmpty() ? null : errors);
    }

    /**
     * Excel 批量导入。
     *
     * <p>入参是 {@link InputStream} 而不是 {@code MultipartFile}：application 层不碰 Web 类型，
     * 上传文件的解析（传输层职责）留在控制器，业务校验（角色白名单/手机号/密码策略）在这里。</p>
     *
     * @throws IOException 解析失败
     */
    public ImportOutcome importUsersFromExcel(InputStream in) throws IOException {
        adminScopeGuard.assertSchoolOnly();
        ExcelUtil.UserImportResult result = ExcelUtil.readUsersWithGenerated(in);
        List<User> users = result.users();
        Map<String, String> generatedPasswords = result.generatedPasswords();
        if (users.isEmpty()) {
            throw new BusinessException("文件中没有可导入的用户数据");
        }
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        // 仅返回「未填密码、由系统随机生成」用户的临时密码（明文仅本次响应出现一次）
        Map<String, String> tempPasswords = new LinkedHashMap<>();
        for (User user : users) {
            String username = user.getUsername();
            try {
                // 角色白名单校验：本路径不经过 AdminCreateUserDTO 的 @Pattern
                if (!VALID_ROLES.contains(user.getRole())) {
                    throw new BusinessException(
                            "角色不合法（应为 STUDENT/TEACHER/ADMIN），当前值：「" + user.getRole() + "」");
                }
                // 手机号格式校验（与 AdminCreateUserDTO 的 @Pattern 同口径；留空视为不填）
                if (user.getPhone() != null && !PHONE_PATTERN.matcher(user.getPhone()).matches()) {
                    throw new BusinessException(
                            "手机号格式不正确（应为 1 开头的 11 位数字），当前值：「" + user.getPhone() + "」");
                }
                // Excel 明文初始密码（含系统随机生成）同样必须过统一策略，堵住导入旁路。
                PasswordPolicy.validate(user.getPassword());
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
        return new ImportOutcome(successCount, failCount, users.size(), tempPasswords,
                errors.isEmpty() ? null : errors);
    }

    private static User buildImportUser(AdminCreateUserDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return user;
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
     * 批量导入结果（application 层的返回类型，控制器再包成 VO）。
     *
     * @param successCount   成功条数
     * @param failCount      跳过条数
     * @param total          总条数
     * @param tempPasswords  仅 Excel 路径会产生：系统随机生成密码的临时明文
     * @param errors         逐行失败原因（全成功时为 null）
     */
    public record ImportOutcome(int successCount, int failCount, int total,
                                Map<String, String> tempPasswords, List<String> errors) {
    }
}
