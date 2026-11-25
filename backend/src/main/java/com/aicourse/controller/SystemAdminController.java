package com.aicourse.controller;

import com.aicourse.application.user.UserAdminApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.AdminUserImportResultVO;
import com.aicourse.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 系统管理接口控制器（仅管理员可访问）：用户与账号管理。
 *
 * <p><b>分层（阶段3 契约）</b>：本类只做 HTTP 层职责 —— 路由、参数接收、DTO 校验、VO 转换、
 * 权限注解。用例编排（密码策略、院系作用域、批量导入逐行校验、事务边界）已下沉到
 * {@link UserAdminApplicationService}。</p>
 *
 * <p>历史状态：此处曾直接持有 {@code UserService}/{@code PasswordEncoder}/{@code AdminScopeGuard}，
 * 是全仓唯一在控制器上标 {@code @Transactional} 的端点集合（删除用户时「吊销会话 + 软删」需同事务），
 * 且直接在控制器里拼 {@code LambdaQueryWrapper} 做筛选。事务边界与查询细节都属于应用/领域关注点，
 * 故本批一并迁出。</p>
 *
 * <p>系统维护与数据统计端点已按 S-01 拆分到 {@code SystemMaintenanceController}
 * 与 {@code AdminStatisticsController}，路由与权限注解保持不变。</p>
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "用户与账号管理")
public class SystemAdminController {

    private final UserAdminApplicationService userAdminService;

    public SystemAdminController(UserAdminApplicationService userAdminService) {
        this.userAdminService = userAdminService;
    }

    // ==================== 用户管理（7个接口） ====================

    /**
     * 获取用户列表（支持角色/状态/关键词筛选；院系管理员自动限定本院，且不过滤停用账号）。
     */
    @GetMapping("/admin/users")
    public ApiResponse<PagedResponse<UserVO>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
                PagedResponse.of(userAdminService.pageUsers(page, size, role, keyword).convert(UserVO::from)));
    }

    /**
     * 获取用户详情。
     */
    @GetMapping("/admin/users/{userId}")
    public ApiResponse<UserVO> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(UserVO.from(userAdminService.getUserDetail(userId)));
    }

    /**
     * 创建用户（AdminCreateUserDTO 白名单绑定，密码必过统一策略）。
     */
    @PostMapping("/admin/users")
    public ApiResponse<UserVO> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        return ApiResponse.success("创建成功", UserVO.from(userAdminService.createUser(dto)));
    }

    /**
     * 更新用户信息（仅 DTO 暴露的白名单字段）。
     */
    @PutMapping("/admin/users/{userId}")
    public ApiResponse<UserVO> updateUser(@PathVariable Long userId,
                                          @Valid @RequestBody AdminUpdateUserDTO dto) {
        return ApiResponse.success("更新成功", UserVO.from(userAdminService.updateUser(userId, dto)));
    }

    /**
     * 删除用户（软删除；吊销活跃会话与软删除同事务，事务边界在应用层）。
     */
    @DeleteMapping("/admin/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        userAdminService.deleteUser(userId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 重置用户密码。
     */
    @PutMapping("/admin/users/{userId}/reset-password")
    public ApiResponse<Void> resetUserPassword(@PathVariable Long userId,
                                               @Valid @RequestBody Map<String, String> body) {
        userAdminService.resetPassword(userId, body.get("newPassword"));
        return ApiResponse.success("密码已重置，需重新登录", null);
    }

    /**
     * 批量导入用户（JSON）。
     */
    @Operation(summary = "批量导入用户", description = "JSON 批量导入（兼容保留）；Excel(.xlsx) 批量导入请使用 /admin/users/import-excel")
    @PostMapping("/admin/users/import")
    public ApiResponse<AdminUserImportResultVO> importUsers(@Valid @RequestBody List<AdminCreateUserDTO> userDTOs) {
        return ApiResponse.success("导入完成", toVO(userAdminService.importUsers(userDTOs)));
    }

    /**
     * Excel 批量导入用户。
     *
     * <p>文件存在性与格式校验属于传输层职责，留在这里；解析与逐行业务校验在应用层。</p>
     */
    @Operation(summary = "Excel 批量导入用户",
            description = "上传 .xlsx 文件批量导入用户（表头：用户名/姓名/邮箱/角色/初始密码/手机号/学号/专业/年级/院系，初始密码缺省随机生成 8 位并随响应返回，供管理员转告用户）")
    @PostMapping(value = "/admin/users/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminUserImportResultVO> importUsersExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
        return ApiResponse.success("导入完成", toVO(userAdminService.importUsersFromExcel(file.getInputStream())));
    }

    private static AdminUserImportResultVO toVO(UserAdminApplicationService.ImportOutcome outcome) {
        return new AdminUserImportResultVO(outcome.successCount(), outcome.failCount(), outcome.total(),
                outcome.tempPasswords(), outcome.errors());
    }
}
