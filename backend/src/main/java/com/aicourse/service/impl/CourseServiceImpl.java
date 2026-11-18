package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.Question;
import com.aicourse.entity.Semester;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SemesterService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.org.CourseDefaultTeachingClassSync;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 课程服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    /**
     * 默认教学班同步器：课程一旦落库就保证「恰有 1 个默认教学班」（WP4 判据 9A 的运行态延伸）。
     * 走独立组件而非 TeachingClassService，是因为后者依赖本服务，直接互相注入会成环。
     */
    private final CourseDefaultTeachingClassSync defaultTeachingClassSync;
    /** 学期服务：创建课程时解析默认归属的 ACTIVE 学期（4A，V17 起课程编号学期内唯一）。 */
    private final SemesterService semesterService;
    /** 题库存在性检查：V27 把 questions.course_id 外键改为 RESTRICT，删课前先给出明确业务错误。 */
    private final QuestionMapper questionMapper;
    /** 课程库定义：新开课任务必须显式引用；旧入口按课程字段生成兼容定义。 */
    private final CourseDefinitionService definitionService;
    private final UserService userService;

    public CourseServiceImpl(CourseDefaultTeachingClassSync defaultTeachingClassSync,
                             SemesterService semesterService,
                             QuestionMapper questionMapper,
                             CourseDefinitionService definitionService,
                             UserService userService) {
        this.defaultTeachingClassSync = defaultTeachingClassSync;
        this.semesterService = semesterService;
        this.questionMapper = questionMapper;
        this.definitionService = definitionService;
        this.userService = userService;
    }

    /**
     * 课程详情：按 id 缓存（课程元信息更新频率低，命中率高的读路径）。
     * unless 排除 null 结果：不存在的课程不写入缓存（RedisCache 默认拒绝存储 null，
     * 会抛 IllegalArgumentException 导致查询不存在课程时 500）。
     */
    @Override
    @Cacheable(cacheNames = "courses", key = "#id", unless = "#result == null")
    public Course getById(Serializable id) {
        return super.getById(id);
    }

    /**
     * 全量课程列表（后台/学情统计使用）：以固定 key 缓存，写入时整体失效
     */
    @Override
    @Cacheable(cacheNames = "courses", key = "'all'")
    public List<Course> list() {
        return super.list();
    }


    /** 保存课程 */
    @Override
    @CacheEvict(cacheNames = "courses", key = "'all'")
    public boolean save(Course entity) {
        log.info("保存课程");

        // 4A（V17）：课程编号在学期内唯一的前提是课程归属学期——未显式指定时解析当前 ACTIVE 学期；
        // 无任何 ACTIVE 学期时保持 null（V17 的 NOT NULL 收紧只对回填后的存量生效，新链路缺学期即建课失败
        // 属数据门禁，不在本处静默兜底），唯一键对其不约束（MySQL 唯一索引对 NULL 不去重）。
        if (entity.getSemesterId() == null) {
            semesterService.listByStatus("ACTIVE").stream()
                    .findFirst()
                    .ifPresent(active -> entity.setSemesterId(active.getId()));
        }
        boolean saved = super.save(entity);
        if (saved) {
            defaultTeachingClassSync.sync(entity);
        }
        return saved;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "'all'"),
            @CacheEvict(cacheNames = "courses", key = "#entity.id")
    })

    /** 更新课程 */
    public boolean updateById(Course entity) {
        log.info("更新课程");

        boolean updated = super.updateById(entity);
        if (updated) {
            defaultTeachingClassSync.sync(entity);
        }
        return updated;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "'all'"),
            @CacheEvict(cacheNames = "courses", key = "#id")
    })

    /** 移除课程 */
    public boolean removeById(Serializable id) {
        log.info("移除课程");

        return super.removeById(id);
    }


    /** 移除课程 */
    @Override
    @CacheEvict(cacheNames = "courses", key = "'all'")
    public boolean remove(Wrapper<Course> wrapper) {
        log.info("移除课程");

        return super.remove(wrapper);
    }


    /** 创建课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course createCourse(Course course, Long teacherId) {
        log.info("创建开课任务");
        if (course == null || teacherId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程与授课教师不能为空");
        }
        User teacher = userService.getById(teacherId);
        if (teacher == null || !"TEACHER".equalsIgnoreCase(teacher.getRole())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "授课教师不存在或不是教师");
        }

        // 三层模型：definition_id 是课程库的稳定身份；旧入口按课程字段生成兼容定义。
        Long definitionId = course.getDefinitionId();
        CourseDefinition definition;
        if (definitionId == null) {
            definitionId = definitionService.resolveFromCourse(course, teacherId);
            definition = definitionService.requireActive(definitionId);
        } else {
            definition = definitionService.requireActive(definitionId);
        }
        course.setDefinitionId(definition.getId());
        applyDefinitionSnapshot(course, definition);
        if (definition.getCollegeId() != null && teacher.getCollegeId() != null
                && !definition.getCollegeId().equals(teacher.getCollegeId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "授课教师不属于课程定义所属学院");
        }
        if (course.getCollegeId() == null) {
            course.setCollegeId(definition.getCollegeId() != null
                    ? definition.getCollegeId() : teacher.getCollegeId());
        }

        // 开课任务必须绑定学期；未显式指定时解析当前 ACTIVE 学期。
        Long semesterId = course.getSemesterId();
        if (semesterId == null) {
            semesterId = semesterService.listByStatus("ACTIVE").stream()
                    .findFirst()
                    .map(Semester::getId)
                    .orElse(null);
        }
        if (semesterId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请先创建并启用一个学期");
        }
        Semester semester = semesterService.getById(semesterId);
        if (semester == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "学期不存在");
        }
        course.setSemesterId(semester.getId());
        course.setSemester(semester.getName());
        course.setYear(parseYear(semester.getName()));
        if (course.getStartDate() == null && semester.getStartDate() != null) {
            course.setStartDate(semester.getStartDate().atStartOfDay());
        }
        if (course.getEndDate() == null && semester.getEndDate() != null) {
            course.setEndDate(semester.getEndDate().atTime(23, 59, 59));
        }

        int planned = course.getPlannedSections() == null ? 1 : course.getPlannedSections();
        if (planned < 1 || planned > 50) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "计划教学班数必须在 1 到 50 之间");
        }
        course.setPlannedSections(planned);

        long sameOffering = this.count(new LambdaQueryWrapper<Course>()
                .eq(Course::getDefinitionId, definition.getId())
                .eq(Course::getSemesterId, semester.getId()));
        if (sameOffering > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "该课程本学期已存在开课任务");
        }

        course.setTeacherId(teacherId);
        // V13 审批流水：创建一律进入 PENDING，忽略客户端传入的审批状态/审批人，防止绕过审批。
        course.setApprovalStatus("PENDING");
        course.setApprovedBy(null);
        course.setApprovalComment(null);
        course.setApprovalTime(null);
        course.setCompletedAt(null);
        if (course.getStatus() == null) {
            course.setStatus("ACTIVE");
        }
        if (course.getIsActive() == null) {
            course.setIsActive(true);
        }
        if (course.getEnrollmentType() == null) {
            course.setEnrollmentType("FREE");
        }
        if (course.getMaxStudents() == null) {
            course.setMaxStudents(100);
        }
        if (course.getQuestionPracticeEnabled() == null) {
            course.setQuestionPracticeEnabled(false);
        }
        this.save(course);
        return course;
    }

    /**
     * 开课任务使用课程定义的稳定快照；容量、报名方式、刷题开关仍是开课任务自己的业务字段。
     */
    private static void applyDefinitionSnapshot(Course course, CourseDefinition definition) {
        course.setCode(definition.getCode());
        course.setName(definition.getName());
        course.setDescription(definition.getDescription());
        course.setCredits(definition.getCredits());
        course.setCategory(definition.getCategory());
        course.setDifficulty(definition.getDifficulty());
        course.setCoverImage(definition.getCoverImage());
        course.setSyllabusUrl(definition.getSyllabusUrl());
        course.setTags(definition.getTags());
        if (definition.getCollegeId() != null) {
            course.setCollegeId(definition.getCollegeId());
        }
    }

    private static Integer parseYear(String semesterName) {
        if (semesterName == null || semesterName.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(semesterName.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }


    /** 更新课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course updateCourse(Long id, Course course, Long operatorId, boolean isAdmin) {
        log.info("更新课程");

        Course existCourse = this.getById(id);
        if (existCourse == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!operatorId.equals(existCourse.getTeacherId()) && !isAdmin) {
            // 语义修正（2026-09-14）：越权拒绝应为 403 而非默认 400，避免客户端把「无权限」误判为「参数错误」
            throw new BusinessException(ApiCode.FORBIDDEN, "无权修改该课程");
        }
        course.setId(id);
        // V28：课程名称/编号/学分/学院等稳定信息属于课程库定义，开课任务只允许改运行字段。
        course.setDefinitionId(null);
        course.setName(null);
        course.setCode(null);
        course.setCredits(null);
        course.setCategory(null);
        course.setDifficulty(null);
        course.setDescription(null);
        course.setCoverImage(null);
        course.setSyllabusUrl(null);
        course.setTags(null);
        course.setCollegeId(null);
        course.setTeacherId(null);
        course.setSemesterId(null);
        course.setSemester(null);
        course.setApprovalStatus(null);
        course.setApprovedBy(null);
        course.setApprovalComment(null);
        course.setApprovalTime(null);
        this.updateById(course);
        return this.getById(id);
    }


    /** 更新课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course updateCourseById(Long id, Course course) {
        log.info("更新课程");

        course.setId(id);
        this.updateById(course);
        return this.getById(id);
    }


    /** 删除课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 修复（2026-09-14）：删除需失效缓存，否则已删除课程在 TTL 内仍可被 getById 命中
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public void deleteCourseById(Long id) {
        log.info("删除课程");

        // V27 起 fk_questions_course 为 ON DELETE RESTRICT：先给出明确的业务错误，
        // 避免用户看到数据库外键异常，也避免“删课程静默丢题目”的历史行为。
        long questionCount = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getCourseId, id));
        if (questionCount > 0) {
            throw new BusinessException("课程下仍有 " + questionCount + " 道题目，不能直接删除；请先归档或迁移题目");
        }

        this.removeById(id);
    }


    /** 审批通过课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 修复（2026-09-14）：approveCourse 原先**未失效** getById 的 courses 缓存
    // （getById 为 @Cacheable，key=#id），审批通过后 TTL 内仍能读到旧的 PENDING，
    // 导致「审批已通过但学生仍被判定课程未审核」这类跨请求不一致。
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course approveCourse(Long id) {
        log.info("审批通过课程");

        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        // 修复（2026-09-14）：补状态前置校验，使重复审批成为**幂等**操作。
        // 原实现无任何状态判断，重复调用始终「成功」，掩盖了审批状态机的约束。
        if ("APPROVED".equalsIgnoreCase(course.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(course.getStatus())) {
            log.info("课程已处于「已通过/进行中」状态，跳过重复审批：courseId={}", id);
            return course;
        }
        course.setApprovalStatus("APPROVED");
        course.setStatus("ACTIVE");
        if (course.getApprovalTime() == null) {
            course.setApprovalTime(LocalDateTime.now());
        }
        this.updateById(course);
        return course;
    }


    /** 审批通过课程并记录审批人 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course approveCourse(Long id, Long operatorId) {
        Course before = this.getById(id);
        if (before == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        // 重复审批保持幂等：已通过时不覆盖原审批人
        boolean alreadyApproved = "APPROVED".equalsIgnoreCase(before.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(before.getStatus());
        Course course = approveCourse(id);
        if (!alreadyApproved && operatorId != null && !operatorId.equals(course.getApprovedBy())) {
            course.setApprovedBy(operatorId);
            this.updateById(course);
        }
        return course;
    }


    /** 切换课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 修复（2026-09-14）：状态切换需失效单条 + 列表缓存，避免读到旧状态
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course toggleCourseStatus(Long id) {
        log.info("切换课程");

        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if ("ACTIVE".equalsIgnoreCase(course.getStatus())) {
            course.setStatus("INACTIVE");
        } else {
            course.setStatus("ACTIVE");
        }
        this.updateById(course);
        return course;
    }


    /** 结课归档：课程负责人或管理员可操作。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course completeCourse(Long id, Long operatorId, boolean isAdmin) {
        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!isAdmin && (operatorId == null || !operatorId.equals(course.getTeacherId()))) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权结课该课程");
        }
        if (course.getCompletedAt() != null) {
            return course;
        }
        course.setCompletedAt(LocalDateTime.now());
        course.setStatus("INACTIVE");
        course.setIsActive(false);
        this.updateById(course);
        defaultTeachingClassSync.close(course);
        return this.getById(id);
    }

    /** 审批驳回课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 修复（2026-09-14）：驳回同样需失效缓存，否则驳回后仍读到 PENDING/APPROVED 旧值
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course rejectCourse(Long id, String reason) {
        log.info("审批驳回课程");

        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setApprovalStatus("REJECTED");
        if (reason != null) {
            course.setApprovalComment(reason);
        }
        this.updateById(course);
        return course;
    }

    /** 重新提交被驳回的课程：仅允许 REJECTED → PENDING */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "#id"),
            @CacheEvict(cacheNames = "courses", key = "'all'")
    })
    public Course resubmitCourse(Long id) {
        log.info("重新提交课程");
        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        if (!"REJECTED".equalsIgnoreCase(course.getApprovalStatus())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "仅驳回状态的课程可重新提交");
        }
        course.setApprovalStatus("PENDING");
        course.setStatus("ACTIVE");
        course.setApprovalComment(null);
        course.setApprovedBy(null);
        course.setApprovalTime(null);
        this.updateById(course);
        return course;
    }
}
