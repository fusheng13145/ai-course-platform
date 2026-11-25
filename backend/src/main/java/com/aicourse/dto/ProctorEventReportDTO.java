package com.aicourse.dto;

import com.aicourse.entity.ProctorEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 考生上报监考事件入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code ProctorEvent} 实体</b>：{@code id}／{@code proctorSessionId}／
 * {@code capturedAt}／{@code createdAt} 由服务端与路径参数决定；{@code status}／{@code reviewNote}／
 * {@code reviewedAt}／{@code reviewerId} 是复核链的服务端字段——实体直绑等于允许考生自己把事件
 * 标成「已豁免」，直接消灭违规证据。</p>
 */
@Schema(name = "ProctorEventReportDTO", description = "监考事件上入参；会话归属、时间与复核字段不接受客户端传入")
@Data
public class ProctorEventReportDTO {

    @Schema(description = "事件类型",
            allowableValues = {"TAB_SWITCH", "FACE_MISSING", "MULTIPLE_FACE", "MOBILE_DETECTED",
                    "COPY_PASTE", "FULLSCREEN_EXIT", "UNKNOWN"})
    private String type;

    @Schema(description = "严重度", allowableValues = {"LOW", "MEDIUM", "HIGH"})
    private String severity;

    @Schema(description = "事件详情描述")
    private String detail;

    @Schema(description = "证据截图/录屏地址")
    private String evidenceUrl;

    public ProctorEvent toEntity() {
        ProctorEvent event = new ProctorEvent();
        applyTo(event);
        return event;
    }

    public void applyTo(ProctorEvent event) {
        event.setType(type);
        event.setSeverity(severity);
        event.setDetail(detail);
        event.setEvidenceUrl(evidenceUrl);
    }
}
