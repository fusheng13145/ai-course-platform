package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            throw new BusinessException("课程不存在");
        }
        if (!operatorId.equals(existCourse.getTeacherId()) && !isAdmin) {
            throw new BusinessException("无权修改该课程");
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
    public void deleteCourseById(Long id) {
        log.info("删除课程");

        this.removeById(id);
    }


    /** 审批通过课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course approveCourse(Long id) {
        log.info("审批通过课程");

        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setApprovalStatus("APPROVED");
        course.setStatus("ACTIVE");
        this.updateById(course);
        return course;
    }


    /** 切换课程 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course toggleCourseStatus(Long id) {
        log.info("切换课程");

        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if ("ACTIVE".equals(course.getStatus())) {
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
