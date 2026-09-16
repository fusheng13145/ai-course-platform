package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建资源版本请求DTO
 * 用于接收为已有资源创建新版本时的请求数据
 */
@Schema(description = "创建资源版本请求")
@Data
public class ResourceVersionCreateDTO {

    /**
     * 目标资源 ID。
     *
     * <p><b>修复（2026-09-14）</b>：与 {@code fileUrl} 同因——本 DTO 被
     * {@code POST /resource-versions}（建版本）与 {@code POST /resource-versions/{id}/rollback}
     * （回滚）共用。回滚的资源 ID 已在**路径**中给出，若 DTO 再强制 body 必填，
     * 按 REST 惯例只传 {@code {versionNumber}} 的客户端会被 400 拒绝。
     * 现改为可选，建版本端点保留自己的非空校验（缺失时返回 400「资源ID不能为空」）。</p>
     */
    @Schema(description = "目标资源ID（建版本必填；回滚以路径参数为准）", example = "301")
    private Long resourceId;

    @Schema(description = "文件名", example = "slides-v2.pdf")
    private String fileName;

    @Schema(description = "文件类型", example = "PDF")
    private String fileType;

    @Schema(description = "资源URL地址", example = "https://example.com/files/slides-v2.pdf")
    private String url;

    /**
     * 新版文件的 URL 地址。
     *
     * <p><b>修复（2026-09-14）</b>：原先标注 {@code @NotBlank}，但本 DTO 被两个端点共用：
     * {@code POST /resource-versions}（建版本，需要 URL）与
     * {@code POST /resource-versions/{id}/rollback}（回滚，**语义上只需 versionNumber**，
     * 其 JavaDoc 也明确写「仅使用 versionNumber 字段」）。强制 @NotBlank 会让
     * 回滚请求因缺少 fileUrl 直接 400，与该端点自身的契约矛盾。
     * 现改为长度约束，非空校验下移到建版本端点内（缺失时返回 400「资源地址不能为空」）。</p>
     */
    @Size(max = 500, message = "URL长度不能超过500个字符")
    @Schema(description = "新版文件的URL地址（建版本必填；回滚可省略）",
            example = "https://example.com/files/ch1-slides-v2.pdf")
    private String fileUrl;

    @Min(value = 1, message = "文件大小必须大于0字节")
    @Schema(description = "新版文件大小（字节）", example = "5898240")
    private Long fileSize;

    @Min(value = 1, message = "版本号至少为1")
    @Schema(description = "版本号（递增整数）", example = "2")
    private Integer versionNumber;

    @Size(max = 500, message = "变更说明不能超过500个字符")
    @Schema(description = "本次版本的变更说明", example = "修复了第3节的排版问题，补充了练习题答案")
    private String changelog;

    @Schema(description = "版本描述", example = "第二版课件")
    private String description;

    @Schema(description = "上传者用户ID", example = "1001")
    private Long uploaderId;

}
