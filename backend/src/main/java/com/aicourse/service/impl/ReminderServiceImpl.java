package com.aicourse.service.impl;

import com.aicourse.entity.Reminder;
import com.aicourse.mapper.ReminderMapper;
import com.aicourse.service.ReminderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ReminderServiceImpl extends ServiceImpl<ReminderMapper, Reminder> implements ReminderService {
}
