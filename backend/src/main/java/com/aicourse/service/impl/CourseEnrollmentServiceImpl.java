package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.mapper.CourseEnrollmentMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseEnrollmentServiceImpl extends ServiceImpl<CourseEnrollmentMapper, CourseEnrollment> implements CourseEnrollmentService {
}
