package com.aicourse.dto;

import com.aicourse.entity.SignInRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 学生签到入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code SignInRecord} 实体</b>：{@code signInId}／{@code studentId}／
 * {@code status}／{@code signTime}／{@code ip}／{@code userAgent} 全部由
 * {@code SignInServiceImpl#sign} 写入（IP/UA 取自请求头用于防代签）；实体直绑等于允许学生自带
 * 「SIGNED 状态 + 任意时间 + 任意 IP」替别人补签。</p>
 */
@Schema(name = "SignInSubmitDTO", description = "学生签到入参；学生身份、状态、时间与 IP 不接受客户端传入")
@Data
public class SignInSubmitDTO {

    @Schema(description = "拍照核验地址（普通/人脸签到使用）")
    private String photoUrl;

    @Schema(description = "纬度（LOCATION 类型签到使用）", example = "32.20")
    private Double latitude;

    @Schema(description = "经度（LOCATION 类型签到使用）", example = "119.42")
    private Double longitude;

    @Schema(description = "签到码/二维码（CODE/QR 类型校验用）")
    private String submittedCode;

    @Schema(description = "手势（GESTURE 类型校验用）")
    private String submittedGesture;

    public SignInRecord toEntity() {
        SignInRecord record = new SignInRecord();
        applyTo(record);
        return record;
    }

    public void applyTo(SignInRecord record) {
        record.setPhotoUrl(photoUrl);
        record.setLatitude(latitude);
        record.setLongitude(longitude);
        record.setSubmittedCode(submittedCode);
        record.setSubmittedGesture(submittedGesture);
    }
}
