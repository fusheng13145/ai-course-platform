package com.aicourse.service.impl;

import com.aicourse.entity.Classroom;
import com.aicourse.mapper.ClassroomMapper;
import com.aicourse.service.ClassroomService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ClassroomServiceImpl extends ServiceImpl<ClassroomMapper, Classroom> implements ClassroomService {
}
