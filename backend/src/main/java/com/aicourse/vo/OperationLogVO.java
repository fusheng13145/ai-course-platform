package com.aicourse.vo;

import com.aicourse.entity.OperationLog;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志出参（15 字段，实体镜像）。
 *
 * <p>该端点仅 ADMIN 可达（`OperationLogController` 带角色校验），字段零删减。
 */
@Data
public class OperationLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String target;
    private String targetId;
    private String ip;
    private String userAgent;
    private String method;
    private String params;
    private Long durationMs;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;

    public static OperationLogVO from(OperationLog log) {
        if (log == null) {
            return null;
        }
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setTarget(log.getTarget());
        vo.setTargetId(log.getTargetId());
        vo.setIp(log.getIp());
        vo.setUserAgent(log.getUserAgent());
        vo.setMethod(log.getMethod());
        vo.setParams(log.getParams());
        vo.setDurationMs(log.getDurationMs());
        vo.setStatus(log.getStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    public static List<OperationLogVO> from(List<OperationLog> logs) {
        return logs == null ? List.of() : logs.stream().map(OperationLogVO::from).toList();
    }
}
