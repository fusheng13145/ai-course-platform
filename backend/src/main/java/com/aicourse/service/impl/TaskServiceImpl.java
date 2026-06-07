package com.aicourse.service.impl;

import com.aicourse.entity.Task;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {
}
