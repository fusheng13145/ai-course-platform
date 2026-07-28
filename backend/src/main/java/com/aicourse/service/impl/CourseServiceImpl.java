package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    /**
     * 课程详情：按 id 缓存（课程元信息更新频率低，命中率高的读路径）
     */
    @Override
    @Cacheable(cacheNames = "courses", key = "#id")
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
}
