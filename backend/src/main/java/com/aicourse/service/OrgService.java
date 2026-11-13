package com.aicourse.service;

import com.aicourse.dto.OrgAdminClassDTO;
import com.aicourse.dto.OrgCollegeDTO;
import com.aicourse.dto.OrgMajorDTO;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.vo.OrgTreeNodeVO;

import java.util.List;

/**
 * 组织架构业务接口（V12 / B-12-P-08 WP1；V14 / WP4 增加组织表 CRUD）。
 */
public interface OrgService {

    /**
     * 查询组织树（学校 → 学院 → 专业 → 行政班）。
     *
     * <p>校级管理员返回全树；院系管理员只返回本学院及其下属。</p>
     *
     * @return 组织树根节点列表（单校部署下通常只有一个根）
     */
    List<OrgTreeNodeVO> getTree();

    /**
     * 学院列表（院系管理员仅本院）。
     */
    List<College> listColleges();

    /**
     * 新建学院（仅校级管理员）。
     */
    College createCollege(OrgCollegeDTO dto);

    /**
     * 修改学院（仅校级管理员）。
     */
    College updateCollege(Long id, OrgCollegeDTO dto);

    /**
     * 归档学院（仅校级管理员；仍有专业时拒绝）。
     */
    void archiveCollege(Long id);

    /**
     * 专业列表（院系管理员仅本院）。
     */
    List<Major> listMajors(Long collegeId);

    /**
     * 新建专业（院系管理员仅本院）。
     */
    Major createMajor(OrgMajorDTO dto);

    /**
     * 修改专业（院系管理员仅本院）。
     */
    Major updateMajor(Long id, OrgMajorDTO dto);

    /**
     * 归档专业（院系管理员仅本院；仍有行政班时拒绝）。
     */
    void archiveMajor(Long id);

    /**
     * 行政班列表（院系管理员仅本院；可按专业过滤）。
     */
    List<AdminClass> listAdminClasses(Long collegeId, Long majorId);

    /**
     * 新建行政班（院系管理员仅本院）。
     */
    AdminClass createAdminClass(OrgAdminClassDTO dto);

    /**
     * 修改行政班（院系管理员仅本院）。
     */
    AdminClass updateAdminClass(Long id, OrgAdminClassDTO dto);

    /**
     * 归档行政班（院系管理员仅本院；仍有学生归属时拒绝）。
     */
    void archiveAdminClass(Long id);
}
