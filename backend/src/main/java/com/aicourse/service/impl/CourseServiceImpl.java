package com.aicourse.service.impl;

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

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

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

    @Override
    @CacheEvict(cacheNames = "courses", key = "'all'")
    public boolean save(Course entity) {
        return super.save(entity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "'all'"),
            @CacheEvict(cacheNames = "courses", key = "#entity.id")
    })
    public boolean updateById(Course entity) {
        return super.updateById(entity);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "courses", key = "'all'"),
            @CacheEvict(cacheNames = "courses", key = "#id")
    })
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

    @Override
    @CacheEvict(cacheNames = "courses", key = "'all'")
    public boolean remove(Wrapper<Course> wrapper) {
        return super.remove(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course createCourse(Course course, Long teacherId) {
        course.setTeacherId(teacherId);
        this.save(course);
        return course;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course updateCourse(Long id, Course course, Long operatorId, boolean isAdmin) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course updateCourseById(Long id, Course course) {
        course.setId(id);
        this.updateById(course);
        return this.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseById(Long id) {
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course approveCourse(Long id) {
        Course course = this.getById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        course.setApprovalStatus("APPROVED");
        course.setStatus("ACTIVE");
        this.updateById(course);
        return course;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course toggleCourseStatus(Long id) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course rejectCourse(Long id, String reason) {
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
