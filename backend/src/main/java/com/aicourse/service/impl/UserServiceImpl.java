package com.aicourse.service.impl;

import com.aicourse.entity.User;
import com.aicourse.mapper.UserMapper;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}