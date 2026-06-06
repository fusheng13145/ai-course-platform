package com.aicourse.service.impl;

import com.aicourse.entity.LoginLog;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.service.LoginLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {
}
