package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.OrgAdminClassDTO;
import com.aicourse.dto.OrgCollegeDTO;
import com.aicourse.dto.OrgMajorDTO;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.entity.School;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CollegeMapper;
import com.aicourse.mapper.MajorMapper;
import com.aicourse.mapper.SchoolMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.OrgService;
import com.aicourse.vo.OrgTreeNodeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织架构业务实现（V12 / B-12-P-08 WP1；V14 / WP4 增加组织表 CRUD）。
 *
 * <p>写操作作用域：学院仅校级管理员；专业/行政班校级可跨院、院系管理员仅限本院。
 * 删除语义统一为归档（status=ARCHIVED），避免留下悬空外键。</p>
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String TYPE_SCHOOL = "SCHOOL";
    private static final String TYPE_COLLEGE = "COLLEGE";
    private static final String TYPE_MAJOR = "MAJOR";
    private static final String TYPE_ADMIN_CLASS = "ADMIN_CLASS";

    private final SchoolMapper schoolMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final AdminClassMapper adminClassMapper;
    private final UserMapper userMapper;
    private final AdminScopeGuard adminScopeGuard;

    public OrgServiceImpl(SchoolMapper schoolMapper,
                          CollegeMapper collegeMapper,
                          MajorMapper majorMapper,
                          AdminClassMapper adminClassMapper,
                          UserMapper userMapper,
                          AdminScopeGuard adminScopeGuard) {
        this.schoolMapper = schoolMapper;
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.adminClassMapper = adminClassMapper;
        this.userMapper = userMapper;
        this.adminScopeGuard = adminScopeGuard;
    }

    @Override
    public List<OrgTreeNodeVO> getTree() {
        List<School> schools = schoolMapper.selectList(new LambdaQueryWrapper<School>()
                .eq(School::getStatus, STATUS_ACTIVE)
                .orderByAsc(School::getId));
        if (schools.isEmpty()) {
            return List.of();
        }

        Long collegeFilter = adminScopeGuard.currentCollegeFilter();
        List<College> colleges = collegeMapper.selectList(new LambdaQueryWrapper<College>()
                .in(College::getSchoolId, schools.stream().map(School::getId).toList())
                .eq(College::getStatus, STATUS_ACTIVE)
                .eq(collegeFilter != null, College::getId, collegeFilter)
                .orderByAsc(College::getCode));
        if (colleges.isEmpty()) {
            return schools.stream().map(school -> node(school.getId(), TYPE_SCHOOL, school.getCode(),
                    school.getName(), List.of())).toList();
        }

        List<Long> collegeIds = colleges.stream().map(College::getId).toList();
        List<Major> majors = majorMapper.selectList(new LambdaQueryWrapper<Major>()
                .in(Major::getCollegeId, collegeIds)
                .eq(Major::getStatus, STATUS_ACTIVE)
                .orderByAsc(Major::getCode));

        Map<Long, List<OrgTreeNodeVO>> classNodesByMajor = new HashMap<>();
        if (!majors.isEmpty()) {
            List<Long> majorIds = majors.stream().map(Major::getId).toList();
            List<AdminClass> adminClasses = adminClassMapper.selectList(new LambdaQueryWrapper<AdminClass>()
                    .in(AdminClass::getMajorId, majorIds)
                    .eq(AdminClass::getStatus, STATUS_ACTIVE)
                    .orderByAsc(AdminClass::getCode));
            for (AdminClass adminClass : adminClasses) {
                classNodesByMajor.computeIfAbsent(adminClass.getMajorId(), key -> new ArrayList<>())
                        .add(node(adminClass.getId(), TYPE_ADMIN_CLASS, adminClass.getCode(),
                                adminClass.getName(), List.of()));
            }
        }

        Map<Long, List<OrgTreeNodeVO>> majorNodesByCollege = new HashMap<>();
        for (Major major : majors) {
            majorNodesByCollege.computeIfAbsent(major.getCollegeId(), key -> new ArrayList<>())
                    .add(node(major.getId(), TYPE_MAJOR, major.getCode(), major.getName(),
                            classNodesByMajor.getOrDefault(major.getId(), List.of())));
        }

        Map<Long, List<OrgTreeNodeVO>> collegeNodesBySchool = new HashMap<>();
        for (College college : colleges) {
            collegeNodesBySchool.computeIfAbsent(college.getSchoolId(), key -> new ArrayList<>())
                    .add(node(college.getId(), TYPE_COLLEGE, college.getCode(), college.getName(),
                            majorNodesByCollege.getOrDefault(college.getId(), List.of())));
        }

        List<OrgTreeNodeVO> roots = new ArrayList<>(schools.size());
        for (School school : schools) {
            roots.add(node(school.getId(), TYPE_SCHOOL, school.getCode(), school.getName(),
                    collegeNodesBySchool.getOrDefault(school.getId(), List.of())));
        }
        return roots;
    }

    @Override
    public List<College> listColleges() {
        Long filter = adminScopeGuard.currentCollegeFilter();
        return collegeMapper.selectList(new LambdaQueryWrapper<College>()
                .eq(filter != null, College::getId, filter)
                .orderByAsc(College::getCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public College createCollege(OrgCollegeDTO dto) {
        adminScopeGuard.assertSchoolOnly();
        Long schoolId = defaultSchoolId();
        long exists = collegeMapper.selectCount(new LambdaQueryWrapper<College>()
                .eq(College::getSchoolId, schoolId)
                .eq(College::getCode, dto.getCode().trim()));
        if (exists > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "学院编码已存在：" + dto.getCode());
        }
        College college = new College();
        college.setSchoolId(schoolId);
        college.setCode(dto.getCode().trim());
        college.setName(dto.getName().trim());
        college.setStatus(normalizeStatus(dto.getStatus()));
        college.setCreatedAt(LocalDateTime.now());
        college.setUpdatedAt(LocalDateTime.now());
        collegeMapper.insert(college);
        return college;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public College updateCollege(Long id, OrgCollegeDTO dto) {
        adminScopeGuard.assertSchoolOnly();
        College college = requireCollege(id);
        college.setCode(dto.getCode().trim());
        college.setName(dto.getName().trim());
        college.setStatus(normalizeStatus(dto.getStatus()));
        college.setUpdatedAt(LocalDateTime.now());
        collegeMapper.updateById(college);
        return college;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveCollege(Long id) {
        adminScopeGuard.assertSchoolOnly();
        requireCollege(id);
        long majors = majorMapper.selectCount(new LambdaQueryWrapper<Major>()
                .eq(Major::getCollegeId, id)
                .ne(Major::getStatus, STATUS_ARCHIVED));
        if (majors > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "学院下仍有未归档专业，不能归档");
        }
        College college = requireCollege(id);
        college.setStatus(STATUS_ARCHIVED);
        college.setUpdatedAt(LocalDateTime.now());
        collegeMapper.updateById(college);
    }

    @Override
    public List<Major> listMajors(Long collegeId) {
        Long filter = adminScopeGuard.currentCollegeFilter();
        if (filter != null && collegeId != null && !filter.equals(collegeId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权访问其他学院的专业");
        }
        Long effective = collegeId != null ? collegeId : filter;
        return majorMapper.selectList(new LambdaQueryWrapper<Major>()
                .eq(effective != null, Major::getCollegeId, effective)
                .orderByAsc(Major::getCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Major createMajor(OrgMajorDTO dto) {
        adminScopeGuard.assertCollegeAccess(dto.getCollegeId());
        requireCollege(dto.getCollegeId());
        long exists = majorMapper.selectCount(new LambdaQueryWrapper<Major>()
                .eq(Major::getCollegeId, dto.getCollegeId())
                .eq(Major::getCode, dto.getCode().trim()));
        if (exists > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "专业编码已存在：" + dto.getCode());
        }
        Major major = new Major();
        major.setCollegeId(dto.getCollegeId());
        major.setCode(dto.getCode().trim());
        major.setName(dto.getName().trim());
        major.setStatus(normalizeStatus(dto.getStatus()));
        major.setCreatedAt(LocalDateTime.now());
        major.setUpdatedAt(LocalDateTime.now());
        majorMapper.insert(major);
        return major;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Major updateMajor(Long id, OrgMajorDTO dto) {
        Major major = requireMajor(id);
        adminScopeGuard.assertCollegeAccess(major.getCollegeId());
        if (!major.getCollegeId().equals(dto.getCollegeId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "专业所属学院不可在此接口变更");
        }
        major.setCode(dto.getCode().trim());
        major.setName(dto.getName().trim());
        major.setStatus(normalizeStatus(dto.getStatus()));
        major.setUpdatedAt(LocalDateTime.now());
        majorMapper.updateById(major);
        return major;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveMajor(Long id) {
        Major major = requireMajor(id);
        adminScopeGuard.assertCollegeAccess(major.getCollegeId());
        long classes = adminClassMapper.selectCount(new LambdaQueryWrapper<AdminClass>()
                .eq(AdminClass::getMajorId, id)
                .ne(AdminClass::getStatus, STATUS_ARCHIVED));
        if (classes > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "专业下仍有未归档行政班，不能归档");
        }
        major.setStatus(STATUS_ARCHIVED);
        major.setUpdatedAt(LocalDateTime.now());
        majorMapper.updateById(major);
    }

    @Override
    public List<AdminClass> listAdminClasses(Long collegeId, Long majorId) {
        Long filter = adminScopeGuard.currentCollegeFilter();
        if (filter != null && collegeId != null && !filter.equals(collegeId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权访问其他学院的行政班");
        }
        Long effective = collegeId != null ? collegeId : filter;
        return adminClassMapper.selectList(new LambdaQueryWrapper<AdminClass>()
                .eq(effective != null, AdminClass::getCollegeId, effective)
                .eq(majorId != null, AdminClass::getMajorId, majorId)
                .orderByAsc(AdminClass::getCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminClass createAdminClass(OrgAdminClassDTO dto) {
        adminScopeGuard.assertCollegeAccess(dto.getCollegeId());
        requireCollege(dto.getCollegeId());
        Major major = requireMajor(dto.getMajorId());
        if (!major.getCollegeId().equals(dto.getCollegeId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "所选专业不属于该学院");
        }
        Long schoolId = defaultSchoolId();
        long exists = adminClassMapper.selectCount(new LambdaQueryWrapper<AdminClass>()
                .eq(AdminClass::getSchoolId, schoolId)
                .eq(AdminClass::getCode, dto.getCode().trim()));
        if (exists > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "行政班编码已存在：" + dto.getCode());
        }
        AdminClass adminClass = new AdminClass();
        adminClass.setSchoolId(schoolId);
        adminClass.setCollegeId(dto.getCollegeId());
        adminClass.setMajorId(dto.getMajorId());
        adminClass.setName(dto.getName().trim());
        adminClass.setCode(dto.getCode().trim());
        adminClass.setGradeYear(dto.getGradeYear());
        adminClass.setAdvisorTeacherId(dto.getAdvisorTeacherId());
        adminClass.setStudentCount(0);
        adminClass.setMaxStudents(dto.getMaxStudents());
        adminClass.setStatus(normalizeStatus(dto.getStatus()));
        adminClass.setCreatedAt(LocalDateTime.now());
        adminClass.setUpdatedAt(LocalDateTime.now());
        adminClassMapper.insert(adminClass);
        return adminClass;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminClass updateAdminClass(Long id, OrgAdminClassDTO dto) {
        AdminClass adminClass = requireAdminClass(id);
        adminScopeGuard.assertCollegeAccess(adminClass.getCollegeId());
        if (!adminClass.getCollegeId().equals(dto.getCollegeId())
                || !adminClass.getMajorId().equals(dto.getMajorId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "行政班的学院/专业不可在此接口变更");
        }
        adminClass.setName(dto.getName().trim());
        adminClass.setCode(dto.getCode().trim());
        adminClass.setGradeYear(dto.getGradeYear());
        adminClass.setAdvisorTeacherId(dto.getAdvisorTeacherId());
        adminClass.setMaxStudents(dto.getMaxStudents());
        adminClass.setStatus(normalizeStatus(dto.getStatus()));
        adminClass.setUpdatedAt(LocalDateTime.now());
        adminClassMapper.updateById(adminClass);
        return adminClass;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveAdminClass(Long id) {
        AdminClass adminClass = requireAdminClass(id);
        adminScopeGuard.assertCollegeAccess(adminClass.getCollegeId());
        long members = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getAdminClassId, id));
        if (members > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "行政班仍有学生归属，不能归档");
        }
        adminClass.setStatus(STATUS_ARCHIVED);
        adminClass.setUpdatedAt(LocalDateTime.now());
        adminClassMapper.updateById(adminClass);
    }

    private Long defaultSchoolId() {
        School school = schoolMapper.selectList(new LambdaQueryWrapper<School>()
                        .eq(School::getCode, "DEFAULT"))
                .stream().findFirst().orElse(null);
        if (school == null) {
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "默认学校不存在，请先执行 V12 迁移");
        }
        return school.getId();
    }

    private College requireCollege(Long id) {
        College college = id == null ? null : collegeMapper.selectById(id);
        if (college == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "学院不存在：" + id);
        }
        return college;
    }

    private Major requireMajor(Long id) {
        Major major = id == null ? null : majorMapper.selectById(id);
        if (major == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "专业不存在：" + id);
        }
        return major;
    }

    private AdminClass requireAdminClass(Long id) {
        AdminClass adminClass = id == null ? null : adminClassMapper.selectById(id);
        if (adminClass == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "行政班不存在：" + id);
        }
        return adminClass;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_ARCHIVED.equals(normalized)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "状态非法：" + status);
        }
        return normalized;
    }

    private static OrgTreeNodeVO node(Long id, String type, String code, String name,
                                      List<OrgTreeNodeVO> children) {
        return new OrgTreeNodeVO(id, type, code, name, children);
    }
}
