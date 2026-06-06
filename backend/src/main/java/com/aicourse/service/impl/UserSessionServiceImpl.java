package com.aicourse.service.impl;

import com.aicourse.entity.UserSession;
import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.service.UserSessionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserSessionServiceImpl extends ServiceImpl<UserSessionMapper, UserSession> implements UserSessionService {
}
