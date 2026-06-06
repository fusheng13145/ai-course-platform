package com.aicourse.service.impl;

import com.aicourse.entity.VerificationCode;
import com.aicourse.mapper.VerificationCodeMapper;
import com.aicourse.service.VerificationCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class VerificationCodeServiceImpl extends ServiceImpl<VerificationCodeMapper, VerificationCode> implements VerificationCodeService {
}
