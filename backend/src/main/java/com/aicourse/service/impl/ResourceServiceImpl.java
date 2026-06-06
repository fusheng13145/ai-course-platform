package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.mapper.ResourceMapper;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {
}
