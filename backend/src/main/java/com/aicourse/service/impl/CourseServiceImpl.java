package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
}