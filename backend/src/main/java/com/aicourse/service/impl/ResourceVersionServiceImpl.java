package com.aicourse.service.impl;

import com.aicourse.entity.ResourceVersion;
import com.aicourse.mapper.ResourceVersionMapper;
import com.aicourse.service.ResourceVersionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ResourceVersionServiceImpl extends ServiceImpl<ResourceVersionMapper, ResourceVersion> implements ResourceVersionService {
}
