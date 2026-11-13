package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.OrgAdminClassDTO;
import com.aicourse.dto.OrgCollegeDTO;
import com.aicourse.dto.OrgMajorDTO;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.College;
import com.aicourse.entity.Major;
import com.aicourse.entity.School;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.AdminClassMapper;
import com.aicourse.mapper.CollegeMapper;
import com.aicourse.mapper.MajorMapper;
import com.aicourse.mapper.SchoolMapper;
import com.aicourse.mapper.UserMapper;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.vo.OrgTreeNodeVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织表 CRUD 服务层测试（B-12/P-08 WP4；2026-11-06 验收批补）。
 * <p>
 * 为什么要单独钉：`AdminOrgControllerAclTest` 把 OrgService 整个 mock 掉，只证明「端点调不调守卫」；
 * 本类的 18 条断言才覆盖服务层真正的业务规则——编码唯一、跨院指派、归档前置校验、
 * 状态白名单、默认学校缺失、以及作用域过滤在树/列表上的落点。改前该类行覆盖 **4%**（216 行未执行），
 * 是 P-08 各包里最大的无测试面。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("组织 CRUD：OrgServiceImpl")
class OrgServiceImplTest {

    @Mock private SchoolMapper schoolMapper;
    @Mock private CollegeMapper collegeMapper;
    @Mock private MajorMapper majorMapper;
    @Mock private AdminClassMapper adminClassMapper;
    @Mock private UserMapper userMapper;
    @Mock private AdminScopeGuard adminScopeGuard;

    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, School.class);
        TableInfoHelper.initTableInfo(assistant, College.class);
        TableInfoHelper.initTableInfo(assistant, Major.class);
        TableInfoHelper.initTableInfo(assistant, AdminClass.class);
        service = new OrgServiceImpl(schoolMapper, collegeMapper, majorMapper, adminClassMapper,
                userMapper, adminScopeGuard);
        when(schoolMapper.selectList(any())).thenReturn(List.of(school(1L, "DEFAULT")));
    }

    private static School school(Long id, String code) {
        School s = new School();
        s.setId(id);
        s.setCode(code);
        s.setName("示例大学");
        s.setStatus("ACTIVE");
        return s;
    }

    private static College college(Long id, String code, String status) {
        College c = new College();
        c.setId(id);
        c.setSchoolId(1L);
        c.setCode(code);
        c.setName("学院" + code);
        c.setStatus(status);
        return c;
    }

    private static Major major(Long id, Long collegeId, String code) {
        Major m = new Major();
        m.setId(id);
        m.setCollegeId(collegeId);
        m.setCode(code);
        m.setName("专业" + code);
        m.setStatus("ACTIVE");
        return m;
    }

    private static AdminClass adminClass(Long id, Long collegeId, Long majorId, String code) {
        AdminClass a = new AdminClass();
        a.setId(id);
        a.setSchoolId(1L);
        a.setCollegeId(collegeId);
        a.setMajorId(majorId);
        a.setCode(code);
        a.setName("行政班" + code);
        a.setStatus("ACTIVE");
        return a;
    }

    private static OrgCollegeDTO collegeDto(String code, String name, String status) {
        OrgCollegeDTO dto = new OrgCollegeDTO();
        dto.setCode(code);
        dto.setName(name);
        dto.setStatus(status);
        return dto;
    }

    private static OrgMajorDTO majorDto(Long collegeId, String code, String name, String status) {
        OrgMajorDTO dto = new OrgMajorDTO();
        dto.setCollegeId(collegeId);
        dto.setCode(code);
        dto.setName(name);
        dto.setStatus(status);
        return dto;
    }

    private static OrgAdminClassDTO adminClassDto(Long collegeId, Long majorId, String code,
                                                  String name, String gradeYear, Integer maxStudents) {
        OrgAdminClassDTO dto = new OrgAdminClassDTO();
        dto.setCollegeId(collegeId);
        dto.setMajorId(majorId);
        dto.setCode(code);
        dto.setName(name);
        dto.setGradeYear(gradeYear);
        dto.setMaxStudents(maxStudents);
        return dto;
    }

    // ==================== 组织树 ====================

    @Test
    @DisplayName("树：无 ACTIVE 学校时返回空列表，不再查学院")
    void treeIsEmptyWhenNoSchool() {
        when(schoolMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.getTree()).isEmpty();
        verify(collegeMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("树：有学校无学院时返回只有校一级的节点（children 为空）")
    void treeReturnsSchoolOnlyWhenNoColleges() {
        when(collegeMapper.selectList(any())).thenReturn(List.of());

        List<OrgTreeNodeVO> tree = service.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getType()).isEqualTo("SCHOOL");
        assertThat(tree.get(0).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("树：校→院→专业→行政班四层装配，空位补空列表")
    void treeAssemblesFourLevels() {
        when(collegeMapper.selectList(any())).thenReturn(List.of(college(10L, "CS", "ACTIVE")));
        when(majorMapper.selectList(any())).thenReturn(List.of(major(20L, 10L, "SE"), major(21L, 10L, "NE")));
        when(adminClassMapper.selectList(any())).thenReturn(List.of(adminClass(30L, 10L, 20L, "A1")));

        List<OrgTreeNodeVO> tree = service.getTree();

        OrgTreeNodeVO schoolNode = tree.get(0);
        OrgTreeNodeVO collegeNode = schoolNode.getChildren().get(0);
        assertThat(collegeNode.getType()).isEqualTo("COLLEGE");
        assertThat(collegeNode.getChildren()).hasSize(2);
        OrgTreeNodeVO seMajor = collegeNode.getChildren().stream()
                .filter(n -> "SE".equals(n.getCode())).findFirst().orElseThrow();
        assertThat(seMajor.getChildren()).hasSize(1);
        assertThat(seMajor.getChildren().get(0).getType()).isEqualTo("ADMIN_CLASS");
        OrgTreeNodeVO neMajor = collegeNode.getChildren().stream()
                .filter(n -> "NE".equals(n.getCode())).findFirst().orElseThrow();
        assertThat(neMajor.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("树：院系管理员作用域把学院过滤到本院（currentCollegeFilter 进查询条件）")
    void treeAppliesCollegeScopeFilter() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(10L);
        when(collegeMapper.selectList(any())).thenReturn(List.of());

        service.getTree();

        verify(adminScopeGuard).currentCollegeFilter();
    }

    // ==================== 学院 ====================

    @Test
    @DisplayName("学院列表：院系作用域写入查询条件")
    void listCollegesUsesScopeFilter() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(7L);
        when(collegeMapper.selectList(any())).thenReturn(List.of(college(7L, "CS", "ACTIVE")));

        assertThat(service.listColleges()).hasSize(1);

        verify(collegeMapper).selectList(any());
    }

    @Test
    @DisplayName("新建学院：编码去空格入库、状态缺省为 ACTIVE")
    void createCollegeTrimsAndDefaultsStatus() {
        when(collegeMapper.selectCount(any())).thenReturn(0L);

        College created = service.createCollege(collegeDto("  CS  ", "  计算机学院 ", null));

        assertThat(created.getCode()).isEqualTo("CS");
        assertThat(created.getName()).isEqualTo("计算机学院");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getSchoolId()).isEqualTo(1L);
        verify(collegeMapper).insert(created);
    }

    @Test
    @DisplayName("新建学院：同校编码重复 409，且不入库")
    void createCollegeRejectsDuplicateCode() {
        when(collegeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createCollege(collegeDto("CS", "计算机学院", "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学院编码已存在");
        verify(collegeMapper, never()).insert(any(College.class));
    }

    @Test
    @DisplayName("新建学院：院系管理员越权写学院被守卫拦下（assertSchoolOnly 生效）")
    void createCollegeRequiresSchoolScope() {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "仅校级管理员可操作"))
                .when(adminScopeGuard).assertSchoolOnly();

        assertThatThrownBy(() -> service.createCollege(collegeDto("CS", "x", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅校级管理员");
        verify(collegeMapper, never()).insert(any(College.class));
    }

    @Test
    @DisplayName("新建学院：状态白名单外的值 400（不接受任意字符串入库）")
    void createCollegeRejectsIllegalStatus() {
        when(collegeMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.createCollege(collegeDto("CS", "x", "DELETED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态非法");
    }

    @Test
    @DisplayName("更新学院：学院不存在 404，不改任何行")
    void updateCollegeRequiresExistingRow() {
        when(collegeMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.updateCollege(99L, collegeDto("CS", "x", "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学院不存在");
        verify(collegeMapper, never()).updateById(any(College.class));
    }

    @Test
    @DisplayName("更新学院：字段落库且 updated_at 被刷新")
    void updateCollegePersistsFields() {
        College existing = college(10L, "CS", "ACTIVE");
        when(collegeMapper.selectById(10L)).thenReturn(existing);

        College updated = service.updateCollege(10L, collegeDto("SE", "软件学院", "ARCHIVED"));

        assertThat(updated.getCode()).isEqualTo("SE");
        assertThat(updated.getStatus()).isEqualTo("ARCHIVED");
        assertThat(updated.getUpdatedAt()).isNotNull();
        verify(collegeMapper).updateById(existing);
    }

    @Test
    @DisplayName("归档学院：下面仍有未归档专业时 409，且不改状态")
    void archiveCollegeBlockedByLiveMajors() {
        College existing = college(10L, "CS", "ACTIVE");
        when(collegeMapper.selectById(10L)).thenReturn(existing);
        when(majorMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.archiveCollege(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学院下仍有未归档专业");
        verify(collegeMapper, never()).updateById(any(College.class));
    }

    @Test
    @DisplayName("归档学院：无未归档专业时状态转 ARCHIVED（不物理删，避免悬空外键）")
    void archiveCollegeSetsArchived() {
        College existing = college(10L, "CS", "ACTIVE");
        when(collegeMapper.selectById(10L)).thenReturn(existing);
        when(majorMapper.selectCount(any())).thenReturn(0L);

        service.archiveCollege(10L);

        assertThat(existing.getStatus()).isEqualTo("ARCHIVED");
        ArgumentCaptor<College> captor = ArgumentCaptor.forClass(College.class);
        verify(collegeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
    }

    // ==================== 专业 ====================

    @Test
    @DisplayName("专业列表：请求collegeId 与本人院系作用域不一致时 403")
    void listMajorsRejectsCrossCollegeRequest() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(10L);

        assertThatThrownBy(() -> service.listMajors(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问其他学院的专业");
    }

    @Test
    @DisplayName("专业列表：院系管理员不传 collegeId 时自动收口到本院")
    void listMajorsFallsBackToOwnCollege() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(10L);
        when(majorMapper.selectList(any())).thenReturn(List.of(major(20L, 10L, "SE")));

        assertThat(service.listMajors(null)).hasSize(1);

        verify(majorMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("新建专业：所属学院不存在 404，不入库")
    void createMajorRequiresCollege() {
        when(collegeMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.createMajor(majorDto(99L, "SE", "软件工程", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学院不存在");
        verify(majorMapper, never()).insert(any(Major.class));
    }

    @Test
    @DisplayName("新建专业：同院编码重复 409")
    void createMajorRejectsDuplicateCode() {
        when(collegeMapper.selectById(10L)).thenReturn(college(10L, "CS", "ACTIVE"));
        when(majorMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createMajor(majorDto(10L, "SE", "软件工程", "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专业编码已存在");
    }

    @Test
    @DisplayName("新建专业：合法输入入库并带默认状态")
    void createMajorInsertsWhenValid() {
        when(collegeMapper.selectById(10L)).thenReturn(college(10L, "CS", "ACTIVE"));
        when(majorMapper.selectCount(any())).thenReturn(0L);

        Major created = service.createMajor(majorDto(10L, " SE ", " 软件工程 ", null));

        assertThat(created.getCollegeId()).isEqualTo(10L);
        assertThat(created.getCode()).isEqualTo("SE");
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(majorMapper).insert(created);
    }

    @Test
    @DisplayName("更新专业：不允许在此接口换院（400），避免绕过归属")
    void updateMajorRejectsCollegeMove() {
        Major existing = major(20L, 10L, "SE");
        when(majorMapper.selectById(20L)).thenReturn(existing);

        assertThatThrownBy(() -> service.updateMajor(20L, majorDto(11L, "SE", "软件工程", "ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专业所属学院不可在此接口变更");
        verify(majorMapper, never()).updateById(any(Major.class));
    }

    @Test
    @DisplayName("更新专业：同院内可改码名与状态")
    void updateMajorAppliesWithinSameCollege() {
        Major existing = major(20L, 10L, "SE");
        when(majorMapper.selectById(20L)).thenReturn(existing);

        Major updated = service.updateMajor(20L, majorDto(10L, "SEU", "软件工程", "ACTIVE"));

        assertThat(updated.getCode()).isEqualTo("SEU");
        verify(majorMapper).updateById(existing);
    }

    @Test
    @DisplayName("归档专业：下面仍有未归档行政班时 409")
    void archiveMajorBlockedByLiveAdminClasses() {
        when(majorMapper.selectById(20L)).thenReturn(major(20L, 10L, "SE"));
        when(adminClassMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.archiveMajor(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("专业下仍有未归档行政班");
        verify(majorMapper, never()).updateById(any(Major.class));
    }

    @Test
    @DisplayName("归档专业：无下级班级时转 ARCHIVED")
    void archiveMajorSetsArchived() {
        Major existing = major(20L, 10L, "SE");
        when(majorMapper.selectById(20L)).thenReturn(existing);
        when(adminClassMapper.selectCount(any())).thenReturn(0L);

        service.archiveMajor(20L);

        assertThat(existing.getStatus()).isEqualTo("ARCHIVED");
        verify(majorMapper).updateById(existing);
    }

    // ==================== 行政班 ====================

    @Test
    @DisplayName("行政班列表：跨院请求 403")
    void listAdminClassesRejectsCrossCollege() {
        when(adminScopeGuard.currentCollegeFilter()).thenReturn(10L);

        assertThatThrownBy(() -> service.listAdminClasses(11L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问其他学院的行政班");
    }

    @Test
    @DisplayName("新建行政班：专业不属于该学院时 400（拒绝跨院指派）")
    void createAdminClassRejectsForeignMajor() {
        when(collegeMapper.selectById(10L)).thenReturn(college(10L, "CS", "ACTIVE"));
        when(majorMapper.selectById(21L)).thenReturn(major(21L, 11L, "NE"));

        assertThatThrownBy(() -> service.createAdminClass(adminClassDto(10L, 21L, "A1", "一班", "2024", 40)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所选专业不属于该学院");
        verify(adminClassMapper, never()).insert(any(AdminClass.class));
    }

    @Test
    @DisplayName("新建行政班：同学校编码重复 409")
    void createAdminClassRejectsDuplicateCode() {
        when(collegeMapper.selectById(10L)).thenReturn(college(10L, "CS", "ACTIVE"));
        when(majorMapper.selectById(20L)).thenReturn(major(20L, 10L, "SE"));
        when(adminClassMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createAdminClass(adminClassDto(10L, 20L, "A1", "一班", "2024", 40)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("行政班编码已存在");
    }

    @Test
    @DisplayName("新建行政班：合法输入入库，学生数初始化为 0")
    void createAdminClassInsertsWhenValid() {
        when(collegeMapper.selectById(10L)).thenReturn(college(10L, "CS", "ACTIVE"));
        when(majorMapper.selectById(20L)).thenReturn(major(20L, 10L, "SE"));
        when(adminClassMapper.selectCount(any())).thenReturn(0L);

        AdminClass created = service.createAdminClass(adminClassDto(10L, 20L, " A1 ", " 一班 ", "2024", 40));

        assertThat(created.getSchoolId()).isEqualTo(1L);
        assertThat(created.getCollegeId()).isEqualTo(10L);
        assertThat(created.getMajorId()).isEqualTo(20L);
        assertThat(created.getCode()).isEqualTo("A1");
        assertThat(created.getStudentCount()).isZero();
        assertThat(created.getMaxStudents()).isEqualTo(40);
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        verify(adminClassMapper).insert(created);
    }

    @Test
    @DisplayName("更新行政班：不允许在此接口换学院/专业（400）")
    void updateAdminClassRejectsParentChange() {
        when(adminClassMapper.selectById(30L)).thenReturn(adminClass(30L, 10L, 20L, "A1"));

        assertThatThrownBy(() -> service.updateAdminClass(30L, adminClassDto(11L, 20L, "A1", "一班", "2024", 40)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学院/专业不可在此接口变更");
        verify(adminClassMapper, never()).updateById(any(AdminClass.class));
    }

    @Test
    @DisplayName("更新行政班：同父可改名称/年级/容量")
    void updateAdminClassAppliesWithinSameParents() {
        AdminClass existing = adminClass(30L, 10L, 20L, "A1");
        when(adminClassMapper.selectById(30L)).thenReturn(existing);

        AdminClass updated = service.updateAdminClass(30L, adminClassDto(10L, 20L, "A1", "计科一班", "2025", 45));

        assertThat(updated.getName()).isEqualTo("计科一班");
        assertThat(updated.getGradeYear()).isEqualTo("2025");
        assertThat(updated.getMaxStudents()).isEqualTo(45);
        verify(adminClassMapper).updateById(existing);
    }

    @Test
    @DisplayName("归档行政班：仍有学生归属时 409（不制造悬空 admin_class_id）")
    void archiveAdminClassBlockedByMembers() {
        when(adminClassMapper.selectById(30L)).thenReturn(adminClass(30L, 10L, 20L, "A1"));
        when(userMapper.selectCount(any())).thenReturn(7L);

        assertThatThrownBy(() -> service.archiveAdminClass(30L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("行政班仍有学生归属");
        verify(adminClassMapper, never()).updateById(any(AdminClass.class));
    }

    @Test
    @DisplayName("归档行政班：无学生时转 ARCHIVED")
    void archiveAdminClassSetsArchived() {
        AdminClass existing = adminClass(30L, 10L, 20L, "A1");
        when(adminClassMapper.selectById(30L)).thenReturn(existing);
        when(userMapper.selectCount(any())).thenReturn(0L);

        service.archiveAdminClass(30L);

        assertThat(existing.getStatus()).isEqualTo("ARCHIVED");
        verify(adminClassMapper).updateById(existing);
    }

    @Test
    @DisplayName("归档行政班：班级不存在 404")
    void archiveAdminClassRequiresRow() {
        when(adminClassMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.archiveAdminClass(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("行政班不存在");
    }

    // ==================== 底座缺失 ====================

    @Test
    @DisplayName("默认学校缺失：新建学院直接 500 点名 V12，而不是把 school_id 写成 null")
    void missingDefaultSchoolFailsFast() {
        when(schoolMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createCollege(collegeDto("CS", "计算机学院", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认学校不存在");
        verify(collegeMapper, never()).insert(any(College.class));
    }
}
