package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 行政班成员批量操作。
 *
 * <p>{@code moveExisting=false} 时，已在其他行政班的学生一律跳过，
 * 必须由管理员显式勾选“允许转入”才会改变归属，避免批量操作静默搬家。</p>
 */
@Data
public class AdminClassMemberBatchDTO {

    @NotEmpty(message = "请选择学生")
    @Size(max = 200, message = "单次最多处理 200 名学生")
    private List<Long> studentIds;

    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "ADD|REMOVE", message = "操作类型只能是 ADD 或 REMOVE")
    private String action;

    /** 仅 ADD 生效：是否把已在其他行政班的学生转入当前班。 */
    private boolean moveExisting;
}
