package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.dto.AiGenerationRestrictDTO;
import com.aicourse.entity.AiGenerationRestriction;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AiGenerationRestrictionService;
import com.aicourse.service.UserService;
import com.aicourse.vo.AiGenerationRestrictionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 出题治理接口控制器（仅管理员）：角色矩阵 ④ 修订版——**逐人限制名单** + 平台总闸开关。
 * 基础路径 {@code /api/admin/ai-governance}。
 *
 * <p>平台总闸（{@code system_configs} 的 {@code ai.question-generation.teacher-enabled}）仍由既有的
 * {@code PUT /admin/configs/{key}} 读写，本控制器只管名单：总闸是"一键全禁"，名单是"点名限制"，
 * 两者并存（判定入口 {@code AiGenerationPolicy}）。</p>
 *
 * <p>⚠️ 只能限制**教师**角色：把 ADMIN 放进名单毫无意义（治理者本就绕开判定），
 * 限制学生也不成立（学生本就没有出题入口），故两者一律 400 明确拒绝，而不是静默接受。</p>
 */
@RestController
@RequestMapping("/admin/ai-governance")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI 出题治理", description = "教师 AI 出题限制名单（逐人限制）")
public class AiGovernanceController {

    private final AiGenerationRestrictionService restrictionService;
    private final UserService userService;

    public AiGovernanceController(AiGenerationRestrictionService restrictionService,
                                  UserService userService) {
        this.restrictionService = restrictionService;
        this.userService = userService;
    }

    /**
     * 限制名单（含被限制教师身份信息；用户已删除时姓名置空而不是隐藏该行）。
     */
    @GetMapping("/restrictions")
    @Operation(summary = "限制名单", description = "按加入时间倒序；含被限制教师的登录名与姓名")
    public ApiResponse<List<AiGenerationRestrictionVO>> list() {
        List<AiGenerationRestriction> rows = restrictionService.listAll();
        // 批量取用户信息，避免逐行查询（名单规模小但 N+1 无必要）
        List<Long> ids = rows.stream().map(AiGenerationRestriction::getUserId).toList();
        Map<Long, User> users = ids.isEmpty() ? Map.of()
                : userService.listByIds(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<AiGenerationRestrictionVO> result = new ArrayList<>(rows.size());
        for (AiGenerationRestriction row : rows) {
            User user = users.get(row.getUserId());
            AiGenerationRestrictionVO vo = new AiGenerationRestrictionVO();
            vo.setUserId(row.getUserId());
            vo.setUsername(user != null ? user.getUsername() : null);
            vo.setFullName(user != null ? user.getFullName() : null);
            vo.setReason(row.getReason());
            vo.setRestrictedBy(row.getRestrictedBy());
            vo.setCreatedAt(row.getCreatedAt());
            result.add(vo);
        }
        return ApiResponse.success(result);
    }

    /**
     * 加入限制名单（幂等：已在名单内则更新原因，不新增行）。
     */
    @PostMapping("/restrictions")
    @Operation(summary = "限制教师出题", description = "只接受教师角色；重复加入为幂等更新（保留首次加入时间）")
    public ApiResponse<AiGenerationRestrictionVO> restrict(@Valid @RequestBody AiGenerationRestrictDTO body,
                                                           Authentication auth) {
        User target = userService.getById(body.getUserId());
        if (target == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "账号不存在：" + body.getUserId());
        }
        if (!RoleConstants.TEACHER.equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "只能限制教师角色的账号出题（该账号当前角色：" + target.getRole() + "）");
        }
        Long adminId = auth != null ? (Long) auth.getPrincipal() : null;
        String reason = body.getReason() == null ? null : body.getReason().trim();
        AiGenerationRestriction saved = restrictionService.restrict(body.getUserId(), reason, adminId);

        AiGenerationRestrictionVO vo = new AiGenerationRestrictionVO();
        vo.setUserId(saved.getUserId());
        vo.setUsername(target.getUsername());
        vo.setFullName(target.getFullName());
        vo.setReason(saved.getReason());
        vo.setRestrictedBy(saved.getRestrictedBy());
        vo.setCreatedAt(saved.getCreatedAt());
        return ApiResponse.success("已加入限制名单", vo);
    }

    /**
     * 移出限制名单。
     */
    @DeleteMapping("/restrictions/{userId}")
    @Operation(summary = "解除限制", description = "不在名单内时返回 404，而不是静默成功")
    public ApiResponse<Map<String, Object>> unrestrict(@PathVariable Long userId) {
        if (!restrictionService.unrestrict(userId)) {
            throw new BusinessException(ApiCode.NOT_FOUND, "该教师不在限制名单内：" + userId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("removed", true);
        return ApiResponse.success("已解除限制", data);
    }
}
