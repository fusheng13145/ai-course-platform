package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.AdminClass;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 行政班对象级 ACL（B-12／P-08 交接文档 §4.2 的 {@code assertAdminClassAdvisor}，WP9 落地）。
 * <p>
 * 判定单一事实源：{@code admin_classes.advisor_teacher_id == operatorId}。
 * 班主任画像（6A）与班级公告（WP8 控制器侧）共用本判定，避免两处各写一份成员关系规则。
 */
@Component
public class AdminClassAccessControl {

    private final AdminClassMapper adminClassMapper;

    public AdminClassAccessControl(AdminClassMapper adminClassMapper) {
        this.adminClassMapper = adminClassMapper;
    }

    /**
     * 断言操作者是该行政班的班主任。
     *
     * @return 命中的行政班（供调用方继续读名称等元数据）
     * @throws BusinessException 班级不存在 → 404；非本班班主任 → 403
     */
    public AdminClass assertAdminClassAdvisor(Long adminClassId, Long operatorId) {
        AdminClass adminClass = adminClassMapper.selectById(adminClassId);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在");
        }
        if (operatorId == null || !operatorId.equals(adminClass.getAdvisorTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "只有本班班主任可以访问该班级画像");
        }
        return adminClass;
    }

    /**
     * 仅取班级行（存在性校验）：供发布公告等「先取行、再做角色分支判定」的调用方使用，
     * 让控制器层不必直接依赖 mapper（ArchUnit：controller 不落 mapper 包）。
     *
     * @throws BusinessException 班级不存在 → 404
     */
    public AdminClass requireAdminClass(Long adminClassId) {
        AdminClass adminClass = adminClassMapper.selectById(adminClassId);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在");
        }
        return adminClass;
    }
}
