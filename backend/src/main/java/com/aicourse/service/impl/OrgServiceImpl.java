package com.aicourse.service.impl;

import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.entity.School;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CollegeMapper;
import com.aicourse.mapper.MajorMapper;
import com.aicourse.mapper.SchoolMapper;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.OrgService;
import com.aicourse.vo.OrgTreeNodeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织架构业务实现（V12 / B-12-P-08 WP1）。
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_SCHOOL = "SCHOOL";
    private static final String TYPE_COLLEGE = "COLLEGE";
    private static final String TYPE_MAJOR = "MAJOR";
    private static final String TYPE_ADMIN_CLASS = "ADMIN_CLASS";

    private final SchoolMapper schoolMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final AdminClassMapper adminClassMapper;
    private final AdminScopeGuard adminScopeGuard;

    public OrgServiceImpl(SchoolMapper schoolMapper,
                          CollegeMapper collegeMapper,
                          MajorMapper majorMapper,
                          AdminClassMapper adminClassMapper,
                          AdminScopeGuard adminScopeGuard) {
        this.schoolMapper = schoolMapper;
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.adminClassMapper = adminClassMapper;
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

    private static OrgTreeNodeVO node(Long id, String type, String code, String name,
                                      List<OrgTreeNodeVO> children) {
        return new OrgTreeNodeVO(id, type, code, name, children);
    }
}
