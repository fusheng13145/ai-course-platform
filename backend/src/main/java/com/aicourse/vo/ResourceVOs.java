package com.aicourse.vo;

import com.aicourse.entity.Resource;
import com.aicourse.entity.ResourceVersion;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源库出参：资源本体与版本快照。
 *
 * <p>字段与实体一一对应、零删减。`moderationReason` / `moderatedBy` 是审核留痕，
 * 学生侧可见「为什么没通过」但对审核人 id 无实际用途，暂按原样保留（收紧属产品决策，
 * 已记入风险表）。
 */
public final class ResourceVOs {

    private ResourceVOs() {
    }

    /** 资源出参。 */
    @Data
    public static class ResourceVO {
        private Long id;
        private String fileName;
        private String fileType;
        private String url;
        private Long courseId;
        private Long chapterId;
        private Long uploaderId;
        private LocalDateTime uploadTime;
        private String description;
        private Boolean visibleToAll;
        private String moderationStatus;
        private String moderationReason;
        private LocalDateTime moderatedAt;
        private Long moderatedBy;

        public static ResourceVO from(Resource resource) {
            if (resource == null) {
                return null;
            }
            ResourceVO vo = new ResourceVO();
            vo.setId(resource.getId());
            vo.setFileName(resource.getFileName());
            vo.setFileType(resource.getFileType());
            vo.setUrl(resource.getUrl());
            vo.setCourseId(resource.getCourseId());
            vo.setChapterId(resource.getChapterId());
            vo.setUploaderId(resource.getUploaderId());
            vo.setUploadTime(resource.getUploadTime());
            vo.setDescription(resource.getDescription());
            vo.setVisibleToAll(resource.getVisibleToAll());
            vo.setModerationStatus(resource.getModerationStatus());
            vo.setModerationReason(resource.getModerationReason());
            vo.setModeratedAt(resource.getModeratedAt());
            vo.setModeratedBy(resource.getModeratedBy());
            return vo;
        }

        public static List<ResourceVO> from(List<Resource> resources) {
            return resources == null ? List.of() : resources.stream().map(ResourceVO::from).toList();
        }
    }

    /** 资源版本出参。 */
    @Data
    public static class ResourceVersionVO {
        private Long id;
        private Long resourceId;
        private Integer versionNumber;
        private String fileName;
        private String fileType;
        private String url;
        private String description;
        private Long fileSize;
        private Long uploaderId;
        private Boolean isActive;
        private LocalDateTime createdAt;

        public static ResourceVersionVO from(ResourceVersion version) {
            if (version == null) {
                return null;
            }
            ResourceVersionVO vo = new ResourceVersionVO();
            vo.setId(version.getId());
            vo.setResourceId(version.getResourceId());
            vo.setVersionNumber(version.getVersionNumber());
            vo.setFileName(version.getFileName());
            vo.setFileType(version.getFileType());
            vo.setUrl(version.getUrl());
            vo.setDescription(version.getDescription());
            vo.setFileSize(version.getFileSize());
            vo.setUploaderId(version.getUploaderId());
            vo.setIsActive(version.getIsActive());
            vo.setCreatedAt(version.getCreatedAt());
            return vo;
        }

        public static List<ResourceVersionVO> from(List<ResourceVersion> versions) {
            return versions == null ? List.of() : versions.stream().map(ResourceVersionVO::from).toList();
        }
    }
}
