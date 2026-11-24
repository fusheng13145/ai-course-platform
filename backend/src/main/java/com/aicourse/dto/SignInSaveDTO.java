package com.aicourse.dto;

import com.aicourse.entity.SignIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起签到入参（入参收实体加固）。
 *
 * <p>{@code creatorId}/{@code status}/{@code signCode}/{@code startedAt}/{@code closedAt} 全部由服务端写入：
 * 签到码尤其敏感 —— 客户端自带签到码等于可以预设一个「大家都知道」的码，绕开随机化。</p>
 */
@Schema(name = "SignInSaveDTO", description = "发起签到入参；状态/签到码由服务端生成")
@Data
public class SignInSaveDTO {

    @Schema(description = "课程ID", example = "12")
    private Long courseId;

    @Schema(description = "章节ID（可选）")
    private Long chapterId;

    @Schema(description = "签到标题", example = "第 3 次签到")
    @NotBlank(message = "签到标题不能为空")
    private String title;

    @Schema(description = "签到类型：NORMAL/GESTURE/RADAR", example = "NORMAL")
    private String type;

    @Schema(description = "有效时长（分钟）", example = "10")
    private Integer durationMinutes;

    @Schema(description = "手势签到的轨迹（仅 GESTURE 类型使用）")
    private String gesture;

    public SignIn toEntity() {
        SignIn signIn = new SignIn();
        signIn.setCourseId(courseId);
        signIn.setChapterId(chapterId);
        signIn.setTitle(title);
        signIn.setType(type);
        signIn.setDurationMinutes(durationMinutes);
        signIn.setGesture(gesture);
        return signIn;
    }
}
