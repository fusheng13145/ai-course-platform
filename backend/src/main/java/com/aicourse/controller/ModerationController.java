package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.ModerationActionDTO;
import com.aicourse.dto.ModerationBanDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ModerationService;
import com.aicourse.vo.BannedAuthorVO;
import com.aicourse.vo.ModerationPostVO;
import com.aicourse.vo.ModerationResourceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容审核与违规处理接口控制器（仅管理员）。角色矩阵 ③ 的实现落点。
 * 基础路径 {@code /api/admin/moderation}。
 *
 * <p>覆盖**讨论回复**与**平台全部资源**两个内容面，处置动作：隐藏（可恢复）/ 恢复 / 删除（物理删），
 * 并可同时封禁作者；另有独立的封禁/解封端点与"经审核封禁"名单。</p>
 *
 * <p>⚠️ 两条必须与页面文案一致的边界：① 删除是**物理删且不可恢复**（讨论回复会级联删掉楼中楼）；
 * ② 封禁 = 复用既有账号停用开关（`users.is_active=0`），被封禁账号**立即无法登录**，
 * 这不是"仅禁止发帖"。</p>
 */
@RestController
@RequestMapping("/admin/moderation")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "内容审核", description = "讨论回复/资源审核、隐藏、删除与封禁作者")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * 讨论回复审核列表。
     *
     * @param status  NORMAL / HIDDEN（省略为全部，大小写不敏感）
     * @param keyword 正文关键词（可选）
     */
    @GetMapping("/discussion-posts")
    @Operation(summary = "讨论回复列表", description = "按审核状态与正文关键词分页查询")
    public ApiResponse<PagedResponse<ModerationPostVO>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(moderationService.listPosts(page, size, status, keyword));
    }

    /**
     * 资源审核列表（平台全部资源）。
     */
    @GetMapping("/resources")
    @Operation(summary = "资源列表", description = "按审核状态与文件名关键词分页查询")
    public ApiResponse<PagedResponse<ModerationResourceVO>> listResources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(moderationService.listResources(page, size, status, keyword));
    }

    /**
     * 讨论回复处置（HIDE / RESTORE / DELETE），可选同时封禁作者。
     */
    @PostMapping("/discussion-posts/{id}/action")
    @Operation(summary = "处置讨论回复", description = "DELETE 为物理删（级联删除楼中楼）且不可恢复")
    public ApiResponse<Map<String, Object>> applyPostAction(@PathVariable Long id,
                                                            @Valid @RequestBody ModerationActionDTO body,
                                                            Authentication auth) {
        return ApiResponse.success("处置完成",
                moderationService.applyPostAction(id, body.getAction(), body.getReason(),
                        Boolean.TRUE.equals(body.getBanAuthor()), adminId(auth)));
    }

    /**
     * 资源处置（HIDE / RESTORE / DELETE），可选同时封禁上传者。
     */
    @PostMapping("/resources/{id}/action")
    @Operation(summary = "处置资源", description = "DELETE 为物理删且不可恢复")
    public ApiResponse<Map<String, Object>> applyResourceAction(@PathVariable Long id,
                                                                @Valid @RequestBody ModerationActionDTO body,
                                                                Authentication auth) {
        return ApiResponse.success("处置完成",
                moderationService.applyResourceAction(id, body.getAction(), body.getReason(),
                        Boolean.TRUE.equals(body.getBanAuthor()), adminId(auth)));
    }

    /**
     * 经审核封禁的作者列表。
     */
    @GetMapping("/banned-authors")
    @Operation(summary = "被封禁作者", description = "只列经内容审核封禁的账号（banned_at 非空）")
    public ApiResponse<List<BannedAuthorVO>> listBannedAuthors() {
        return ApiResponse.success(moderationService.listBannedAuthors());
    }

    /**
     * 封禁作者（复用账号停用开关并留痕）。
     */
    @PostMapping("/users/{userId}/ban")
    @Operation(summary = "封禁作者", description = "等于停用该账号（立即无法登录）；不能封禁管理员")
    public ApiResponse<Map<String, Object>> banAuthor(@PathVariable Long userId,
                                                      @Valid @RequestBody(required = false) ModerationBanDTO body,
                                                      Authentication auth) {
        String reason = body != null ? body.getReason() : null;
        Map<String, Object> data = new LinkedHashMap<>(moderationService.banAuthor(userId, reason, adminId(auth)));
        data.put("message", "已封禁该账号（立即无法登录）");
        return ApiResponse.success(data);
    }

    /**
     * 解除封禁。
     */
    @PostMapping("/users/{userId}/unban")
    @Operation(summary = "解除封禁", description = "只对经审核封禁的账号有效；其它原因停用不在此列")
    public ApiResponse<Map<String, Object>> unbanAuthor(@PathVariable Long userId) {
        if (!moderationService.unbanAuthor(userId)) {
            throw new BusinessException(ApiCode.NOT_FOUND,
                    "该账号不是经内容审核封禁的账号（或不存在），无法在此解除：" + userId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("unbanned", true);
        return ApiResponse.success("已解除封禁", data);
    }

    private Long adminId(Authentication auth) {
        return auth != null ? (Long) auth.getPrincipal() : null;
    }
}
