package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseDefinitionMapper;
import com.aicourse.service.UserService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程库定义服务单元测试。
 *
 * <p>覆盖 search / create / update / requireActive / resolveFromCourse 的正常路径、
 * 校验边界、权限分支与唯一键竞态；不加载 Spring 上下文。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseDefinitionServiceImplTest {

    @Mock
    private CourseDefinitionMapper baseMapper;

    @Mock
    private UserService userService;

    private CourseDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseDefinition.class);
        service = new CourseDefinitionServiceImpl(userService);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);

        lenient().when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        lenient().when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(baseMapper.selectById(any(Serializable.class))).thenReturn(null);
        lenient().when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        lenient().when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        lenient().when(baseMapper.insert(any(CourseDefinition.class))).thenReturn(1);
        lenient().when(baseMapper.updateById(any(CourseDefinition.class))).thenReturn(1);
    }

    private static CourseDefinition definition(String code, String name) {
        CourseDefinition definition = new CourseDefinition();
        definition.setCode(code);
        definition.setName(name);
        return definition;
    }

    private static User user(Long id, Long collegeId) {
        User user = new User();
        user.setId(id);
        user.setCollegeId(collegeId);
        return user;
    }

    private static void assertBusinessCode(int expected, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, exception.getCode());
    }

    // ===================== search =====================

    @Test
    @DisplayName("search: 关键字/学院/状态三条件均生效")
    void search_allFilters() {
        CourseDefinition hit = definition("CN101", "计算机网络");
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(hit));

        List<CourseDefinition> result = service.search("  net  ", 2L, " active ");

        assertEquals(List.of(hit), result);
        verify(baseMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("search: 全空条件不加 where")
    void search_nullFilters() {
        service.search(null, null, null);
        verify(baseMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("search: 空白关键字与状态按未传处理")
    void search_blankFiltersAreIgnored() {
        service.search("   ", null, "   ");
        verify(baseMapper).selectList(any(Wrapper.class));
    }

    // ===================== create =====================

    @Test
    @DisplayName("create: 定义对象为空 -> 400")
    void create_nullDefinition() {
        assertBusinessCode(400, () -> service.create(null, 7L, false));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("create: 编号为空 / 空白 / 超长 -> 400")
    void create_invalidCode() {
        assertBusinessCode(400, () -> service.create(definition(null, "网络"), 7L, true));
        assertBusinessCode(400, () -> service.create(definition("   ", "网络"), 7L, true));
        assertBusinessCode(400, () -> service.create(definition("x".repeat(51), "网络"), 7L, true));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("create: 名称为空 / 空白 / 超长 -> 400")
    void create_invalidName() {
        assertBusinessCode(400, () -> service.create(definition("CN101", null), 7L, true));
        assertBusinessCode(400, () -> service.create(definition("CN101", "   "), 7L, true));
        assertBusinessCode(400, () -> service.create(definition("CN101", "n".repeat(256)), 7L, true));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("create: 教师操作人缺失 -> 404")
    void create_teacherWithoutOperator() {
        assertBusinessCode(404, () -> service.create(definition("CN101", "网络"), null, false));
    }

    @Test
    @DisplayName("create: 教师操作人不存在 -> 404")
    void create_teacherUserNotFound() {
        when(userService.getById(7L)).thenReturn(null);
        assertBusinessCode(404, () -> service.create(definition("CN101", "网络"), 7L, false));
    }

    @Test
    @DisplayName("create: 教师创建强制取本人学院并补默认字段")
    void create_teacherUsesOwnCollegeAndDefaults() {
        when(userService.getById(7L)).thenReturn(user(7L, 3L));

        CourseDefinition input = definition(" cn101 ", " Network ");
        input.setStatus(null);
        input.setNature("   ");

        CourseDefinition created = service.create(input, 7L, false);

        assertEquals("CN101", created.getCode());
        assertEquals("Network", created.getName());
        assertEquals(3L, created.getCollegeId());
        assertEquals(7L, created.getCreatedBy());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(BigDecimal.ZERO, created.getCredits());
        assertEquals("选修", created.getNature());
        verify(baseMapper).insert(created);
    }

    @Test
    @DisplayName("create: 空白状态补 ACTIVE")
    void create_blankStatusDefaultsToActive() {
        CourseDefinition input = definition("CN102A", "网络");
        input.setStatus("   ");

        CourseDefinition created = service.create(input, 9L, true);

        assertEquals("ACTIVE", created.getStatus());
        verify(baseMapper).insert(created);
    }

    @Test
    @DisplayName("create: 空白状态/性质走 isBlank 分支")
    void create_blankStatusAndNature() {
        CourseDefinition created = service.create(definition("CN102", "网络"), 9L, true);

        assertEquals(9L, created.getCreatedBy());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("选修", created.getNature());
        verify(baseMapper).insert(created);
    }

    @Test
    @DisplayName("create: 管理员保留显式学院/创建人/状态与学分")
    void create_adminKeepsExplicitFields() {
        CourseDefinition input = definition("CN103", "网络");
        input.setCollegeId(8L);
        input.setCreatedBy(99L);
        input.setStatus("INACTIVE");
        input.setCredits(new BigDecimal("4.5"));
        input.setNature("必修");

        CourseDefinition created = service.create(input, 9L, true);

        assertEquals(8L, created.getCollegeId());
        assertEquals(99L, created.getCreatedBy());
        assertEquals("INACTIVE", created.getStatus());
        assertEquals(new BigDecimal("4.5"), created.getCredits());
        assertEquals("必修", created.getNature());
        verify(userService, never()).getById(any());
    }

    @Test
    @DisplayName("create: 编号已存在 -> 409")
    void create_duplicateByPreCheck() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertBusinessCode(409, () -> service.create(definition("CN101", "网络"), 7L, true));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("create: 并发唯一键冲突 -> 409")
    void create_duplicateByDatabaseRace() {
        when(baseMapper.insert(any(CourseDefinition.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertBusinessCode(409, () -> service.create(definition("CN101", "网络"), 7L, true));
    }

    // ===================== update =====================

    @Test
    @DisplayName("update: 定义不存在 -> 404")
    void update_notFound() {
        assertBusinessCode(404, () -> service.update(9L, definition("CN101", "网络"), 7L, true));
    }

    @Test
    @DisplayName("update: 非创建者教师修改 -> 403")
    void update_teacherNotOwner() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(5L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        assertBusinessCode(403, () -> service.update(9L, definition("CN102", "新网络"), 7L, false));
        verify(baseMapper, never()).updateById(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("update: patch 为空返回原对象")
    void update_nullPatchReturnsExisting() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        assertSame(existing, service.update(9L, null, 7L, false));
        verify(baseMapper, never()).updateById(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("update: 全空 patch 不修改任何字段")
    void update_emptyPatchKeepsExisting() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        service.update(9L, new CourseDefinition(), 7L, false);

        assertEquals("CN101", existing.getCode());
        assertEquals("网络", existing.getName());
        verify(baseMapper).updateById(existing);
    }

    @Test
    @DisplayName("update: 空白编号/名称/性质/状态按未传处理")
    void update_blankFieldsAreIgnored() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        existing.setNature("必修");
        existing.setStatus("ACTIVE");
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = definition("   ", "   ");
        patch.setNature("   ");
        patch.setStatus("   ");
        service.update(9L, patch, 7L, false);

        assertEquals("CN101", existing.getCode());
        assertEquals("网络", existing.getName());
        assertEquals("必修", existing.getNature());
        assertEquals("ACTIVE", existing.getStatus());
        verify(baseMapper).updateById(existing);
    }

    @Test
    @DisplayName("update: 编号超长 -> 400")
    void update_codeTooLong() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        assertBusinessCode(400, () -> service.update(9L, definition("x".repeat(51), null), 7L, false));
    }

    @Test
    @DisplayName("update: 编号重复 -> 409")
    void update_codeDuplicate() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertBusinessCode(409, () -> service.update(9L, definition(" cn101 ", null), 7L, false));
    }

    @Test
    @DisplayName("update: 名称超长 -> 400")
    void update_nameTooLong() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        assertBusinessCode(400, () -> service.update(9L, definition(null, "n".repeat(256)), 7L, false));
    }

    @Test
    @DisplayName("update: 合法字段全部落盘且编号/状态归一化")
    void update_allValidFields() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setId(9L);
        existing.setCreatedBy(7L);
        existing.setCollegeId(1L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = definition(" cn102 ", " 新网络 ");
        patch.setDescription("描述");
        patch.setCredits(new BigDecimal("3.5"));
        patch.setHours(48);
        patch.setNature(" 必修 ");
        patch.setCollegeId(1L);
        patch.setCategory("核心");
        patch.setDifficulty("中");
        patch.setPrerequisite("无");
        patch.setCoverImage("cover.png");
        patch.setSyllabusUrl("syllabus.pdf");
        patch.setTags("[\"网络\"]");
        patch.setStatus(" active ");

        CourseDefinition updated = service.update(9L, patch, 7L, false);

        assertEquals("CN102", updated.getCode());
        assertEquals("新网络", updated.getName());
        assertEquals("描述", updated.getDescription());
        assertEquals(new BigDecimal("3.5"), updated.getCredits());
        assertEquals(48, updated.getHours());
        assertEquals("必修", updated.getNature());
        assertEquals(1L, updated.getCollegeId());
        assertEquals("核心", updated.getCategory());
        assertEquals("中", updated.getDifficulty());
        assertEquals("无", updated.getPrerequisite());
        assertEquals("cover.png", updated.getCoverImage());
        assertEquals("syllabus.pdf", updated.getSyllabusUrl());
        assertEquals("[\"网络\"]", updated.getTags());
        assertEquals("ACTIVE", updated.getStatus());
        verify(baseMapper).updateById(existing);
    }

    @Test
    @DisplayName("update: 教师不能改所属学院 -> 403")
    void update_teacherCannotChangeCollege() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        existing.setCollegeId(1L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = new CourseDefinition();
        patch.setCollegeId(2L);
        assertBusinessCode(403, () -> service.update(9L, patch, 7L, false));
    }

    @Test
    @DisplayName("update: 管理员可改所属学院且可置 INACTIVE")
    void update_adminCanChangeCollegeAndStatus() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(5L);
        existing.setCollegeId(1L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = new CourseDefinition();
        patch.setCollegeId(2L);
        patch.setStatus("inactive");

        CourseDefinition updated = service.update(9L, patch, 9L, true);

        assertEquals(2L, updated.getCollegeId());
        assertEquals("INACTIVE", updated.getStatus());
        verify(baseMapper).updateById(existing);
    }

    @Test
    @DisplayName("update: 非法状态 -> 400")
    void update_invalidStatus() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = new CourseDefinition();
        patch.setStatus("DELETED");
        assertBusinessCode(400, () -> service.update(9L, patch, 7L, false));
    }

    // ===================== requireActive =====================

    @Test
    @DisplayName("requireActive: 不存在 -> 404")
    void requireActive_notFound() {
        assertBusinessCode(404, () -> service.requireActive(9L));
    }

    @Test
    @DisplayName("requireActive: 状态为空或停用 -> 409")
    void requireActive_inactiveOrNull() {
        when(baseMapper.selectById(9L)).thenReturn(definition("CN101", "网络"));

        assertBusinessCode(409, () -> service.requireActive(9L));

        CourseDefinition inactive = definition("CN101", "网络");
        inactive.setStatus("INACTIVE");
        when(baseMapper.selectById(10L)).thenReturn(inactive);
        assertBusinessCode(409, () -> service.requireActive(10L));
    }

    @Test
    @DisplayName("requireActive: active（忽略大小写）返回定义")
    void requireActive_active() {
        CourseDefinition active = definition("CN101", "网络");
        active.setStatus("active");
        when(baseMapper.selectById(9L)).thenReturn(active);

        assertSame(active, service.requireActive(9L));
    }

    // ===================== resolveFromCourse =====================

    @Test
    @DisplayName("resolveFromCourse: 课程为空或编号为空/空白 -> 400")
    void resolveFromCourse_invalidCourse() {
        assertBusinessCode(400, () -> service.resolveFromCourse(null, 7L));

        Course course = new Course();
        assertBusinessCode(400, () -> service.resolveFromCourse(course, 7L));

        course.setCode("   ");
        assertBusinessCode(400, () -> service.resolveFromCourse(course, 7L));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("resolveFromCourse: 已存在定义直接复用")
    void resolveFromCourse_existingDefinition() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setId(10L);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing);

        Course course = new Course();
        course.setCode(" cn101 ");

        assertEquals(10L, service.resolveFromCourse(course, 7L));
        verify(baseMapper, never()).insert(any(CourseDefinition.class));
    }

    @Test
    @DisplayName("resolveFromCourse: 新建定义，学分空值补零")
    void resolveFromCourse_createsWithZeroCredits() {
        doAnswer(invocation -> {
            ((CourseDefinition) invocation.getArgument(0)).setId(11L);
            return 1;
        }).when(baseMapper).insert(any(CourseDefinition.class));

        Course course = new Course();
        course.setCode(" cn102 ");
        course.setName("网络");
        course.setCollegeId(3L);

        assertEquals(11L, service.resolveFromCourse(course, 7L));

        ArgumentCaptor<CourseDefinition> captor = ArgumentCaptor.forClass(CourseDefinition.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("CN102", captor.getValue().getCode());
        assertEquals("网络", captor.getValue().getName());
        assertEquals(3L, captor.getValue().getCollegeId());
        assertEquals(BigDecimal.ZERO, captor.getValue().getCredits());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals(7L, captor.getValue().getCreatedBy());
    }

    @Test
    @DisplayName("resolveFromCourse: 新建定义保留非空学分")
    void resolveFromCourse_keepsCredits() {
        doAnswer(invocation -> {
            ((CourseDefinition) invocation.getArgument(0)).setId(12L);
            return 1;
        }).when(baseMapper).insert(any(CourseDefinition.class));

        Course course = new Course();
        course.setCode("CN103");
        course.setName("网络");
        course.setCredits(new BigDecimal("4.0"));

        assertEquals(12L, service.resolveFromCourse(course, 7L));

        ArgumentCaptor<CourseDefinition> captor = ArgumentCaptor.forClass(CourseDefinition.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(new BigDecimal("4.0"), captor.getValue().getCredits());
    }

    @Test
    @DisplayName("resolveFromCourse: 唯一键竞态且查不到记录 -> 409")
    void resolveFromCourse_raceWithoutWinner() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null).thenReturn(null);
        when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null).thenReturn(null);
        when(baseMapper.insert(any(CourseDefinition.class)))
                .thenThrow(new DuplicateKeyException("race"));

        Course course = new Course();
        course.setCode("CN101");
        course.setName("网络");

        assertBusinessCode(409, () -> service.resolveFromCourse(course, 7L));
    }

    @Test
    @DisplayName("resolveFromCourse: 唯一键竞态后命中并发写入者")
    void resolveFromCourse_raceResolvesWinner() {
        CourseDefinition raced = definition("CN101", "网络");
        raced.setId(13L);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null).thenReturn(raced);
        when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null).thenReturn(raced);
        when(baseMapper.insert(any(CourseDefinition.class)))
                .thenThrow(new DuplicateKeyException("race"));

        Course course = new Course();
        course.setCode("CN101");
        course.setName("网络");

        assertEquals(13L, service.resolveFromCourse(course, 7L));
    }

    @Test
    @DisplayName("resolveFromCourse: 成功新建后返回定义 ID")
    void resolveFromCourse_returnsGeneratedId() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(baseMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        doAnswer(invocation -> {
            ((CourseDefinition) invocation.getArgument(0)).setId(14L);
            return 1;
        }).when(baseMapper).insert(any(CourseDefinition.class));

        Course course = new Course();
        course.setCode("CN104");
        course.setName("网络");

        assertEquals(14L, service.resolveFromCourse(course, 7L));
    }

    @Test
    @DisplayName("search 返回空集合时不伪造结果")
    void search_emptyResult() {
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.search("无命中", 2L, null).isEmpty());
    }

    @Test
    @DisplayName("update 后返回二次读取结果")
    void update_returnsReloadedEntity() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setId(9L);
        existing.setCreatedBy(7L);
        CourseDefinition reloaded = definition("CN102", "新网络");
        reloaded.setId(9L);
        when(baseMapper.selectById(9L)).thenReturn(existing).thenReturn(reloaded);

        CourseDefinition patch = definition("CN102", "新网络");
        assertSame(reloaded, service.update(9L, patch, 7L, false));
    }

    @Test
    @DisplayName("create 成功后返回同一对象")
    void create_returnsSameInstance() {
        CourseDefinition input = definition("CN105", "网络");
        assertSame(input, service.create(input, 7L, true));
    }

    @Test
    @DisplayName("requireActive 状态为空时按非 ACTIVE 处理")
    void requireActive_nullStatus() {
        CourseDefinition noStatus = definition("CN101", "网络");
        noStatus.setStatus(null);
        when(baseMapper.selectById(9L)).thenReturn(noStatus);

        assertBusinessCode(409, () -> service.requireActive(9L));
    }

    @Test
    @DisplayName("update 非管理员且创建人为空时拒绝")
    void update_nullOwnerRejectedForTeacher() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(null);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        assertBusinessCode(403, () -> service.update(9L, new CourseDefinition(), 7L, false));
    }

    @Test
    @DisplayName("resolveFromCourse 复制课程展示字段")
    void resolveFromCourse_copiesDisplayFields() {
        doAnswer(invocation -> {
            ((CourseDefinition) invocation.getArgument(0)).setId(15L);
            return 1;
        }).when(baseMapper).insert(any(CourseDefinition.class));

        Course course = new Course();
        course.setCode("CN106");
        course.setName("网络");
        course.setDescription("描述");
        course.setCategory("核心");
        course.setDifficulty("中");
        course.setCoverImage("cover");
        course.setSyllabusUrl("syllabus");
        course.setTags("[]");

        assertEquals(15L, service.resolveFromCourse(course, 7L));
        ArgumentCaptor<CourseDefinition> captor = ArgumentCaptor.forClass(CourseDefinition.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("描述", captor.getValue().getDescription());
        assertEquals("核心", captor.getValue().getCategory());
        assertEquals("中", captor.getValue().getDifficulty());
        assertEquals("cover", captor.getValue().getCoverImage());
        assertEquals("syllabus", captor.getValue().getSyllabusUrl());
        assertEquals("[]", captor.getValue().getTags());
    }

    @Test
    @DisplayName("search 关键字只匹配编号/名称，不改变返回顺序")
    void search_preservesMapperOrder() {
        CourseDefinition first = definition("CN001", "网络");
        CourseDefinition second = definition("CN002", "网络");
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        assertEquals(List.of(first, second), service.search("网络", 1L, "ACTIVE"));
    }

    @Test
    @DisplayName("create 管理员创建时编号归一化为大写")
    void create_adminNormalizesCode() {
        CourseDefinition input = definition(" cn107 ", " 网络 ");
        service.create(input, 7L, true);
        assertEquals("CN107", input.getCode());
        assertEquals("网络", input.getName());
    }

    @Test
    @DisplayName("update 教师同学院修改不触发越权")
    void update_teacherSameCollegeAllowed() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        existing.setCollegeId(1L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        CourseDefinition patch = new CourseDefinition();
        patch.setCollegeId(1L);
        patch.setStatus("INACTIVE");
        service.update(9L, patch, 7L, false);
        assertEquals(1L, existing.getCollegeId());
        assertEquals("INACTIVE", existing.getStatus());
    }

    @Test
    @DisplayName("update 名称空白不修改")
    void update_blankNameIgnored() {
        CourseDefinition existing = definition("CN101", "网络");
        existing.setCreatedBy(7L);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        service.update(9L, definition(null, "   "), 7L, false);
        assertEquals("网络", existing.getName());
        assertNull(existing.getDescription());
    }
}
