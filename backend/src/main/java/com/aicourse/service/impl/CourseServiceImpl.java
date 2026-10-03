package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.service.CourseService;
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

        return super.save(entity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "'all'"),
            @CacheEvict(cacheNames = "courses", key = "#entity.id")
    })

    /** 更新课程 */
    public boolean updateById(Course entity) {
        log.info("更新课程");

        return super.updateById(entity);
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
        log.info("创建课程");

        course.setTeacherId(teacherId);
        // 修复（2026-09-14）：显式落定初始状态，不再依赖 DDL 默认值。
        // 原实现仅 save()，DB 虽按默认值落库 PENDING/active，但返回体不回显这些字段
        // （实体未回填），前端若据响应判断状态会得到 null 而误判；显式赋值使「响应体 = 落库值」。
        if (course.getApprovalStatus() == null) {
            course.setApprovalStatus("PENDING");
        }
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
        this.save(course);
        return course;
    }


    /** 更新课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
        this.updateById(course);
        return this.getById(id);
    }


    /** 更新课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
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
}
