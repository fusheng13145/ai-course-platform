package com.aicourse.vo;

import com.aicourse.entity.User;
import lombok.Data;

/**
 * 未提交学生行（教师催交/名单视图）。
 *
 * <p>原实现由控制器逐行拼 {@code LinkedHashMap}（键 {@code studentId/username/fullName/studentNo}），
 * 形状靠字符串约定，改一个键名不会有任何编译或测试提示。收成 VO 后字段名即契约，
 * 且**只暴露名单需要的四列**，不再把 {@code User} 实体的邮箱/密码/角色带出去。</p>
 */
@Data
public class UnsubmittedStudentVO {

    /** 学生账号主键。 */
    private Long studentId;

    /** 登录名。 */
    private String username;

    /** 姓名。 */
    private String fullName;

    /** 学号（{@code User.studentId} 列）。 */
    private String studentNo;

    public static UnsubmittedStudentVO from(User user) {
        UnsubmittedStudentVO vo = new UnsubmittedStudentVO();
        vo.setStudentId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setFullName(user.getFullName());
        vo.setStudentNo(user.getStudentId());
        return vo;
    }
}
