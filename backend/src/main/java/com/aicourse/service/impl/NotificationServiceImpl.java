package com.aicourse.service.impl;

import com.aicourse.entity.Notification;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {
}
