package com.aicourse.service.impl;

import com.aicourse.entity.CourseChapter;
import com.aicourse.mapper.CourseChapterMapper;
import com.aicourse.service.CourseChapterService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseChapterServiceImpl extends ServiceImpl<CourseChapterMapper, CourseChapter> implements CourseChapterService {
}
