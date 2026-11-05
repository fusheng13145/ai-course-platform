package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.OrgService;
import com.aicourse.vo.OrgTreeNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织架构接口控制器（V12 / B-12-P-08 WP1）。
 *
 * <p>本包只提供只读组织树，作为作用域机制的最小接入点；
 * 组织 CRUD、审批与名单端点属于 WP2+，未在此实现。</p>
 */
@RestController
@RequestMapping("/admin/org")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "组织架构", description = "学校/学院/专业/行政班只读树")
public class AdminOrgController {

    private final OrgService orgService;

    public AdminOrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /**
     * 获取组织树（校级管理员全树；院系管理员仅本学院）。
     */
    @GetMapping("/tree")
    @Operation(summary = "组织树", description = "按作用域返回学校→学院→专业→行政班")
    public ApiResponse<List<OrgTreeNodeVO>> getTree() {
        return ApiResponse.success(orgService.getTree());
    }
}
