package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseDefinitionMapper;
import com.aicourse.service.CourseDefinitionService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CourseDefinitionServiceImpl extends ServiceImpl<CourseDefinitionMapper, CourseDefinition>
        implements CourseDefinitionService {

    private static final int CODE_MAX_LENGTH = 50;
    private static final int NAME_MAX_LENGTH = 255;

    private final UserService userService;

    public CourseDefinitionServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public List<CourseDefinition> search(String keyword, Long collegeId, String status) {
        LambdaQueryWrapper<CourseDefinition> wrapper = new LambdaQueryWrapper<CourseDefinition>()
                .eq(collegeId != null, CourseDefinition::getCollegeId, collegeId)
                .eq(status != null && !status.isBlank(), CourseDefinition::getStatus,
                        status == null ? null : status.trim().toUpperCase())
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(CourseDefinition::getCode, keyword.trim())
                        .or()
                        .like(CourseDefinition::getName, keyword.trim()))
                .orderByAsc(CourseDefinition::getCode)
                .orderByAsc(CourseDefinition::getId);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDefinition create(CourseDefinition definition, Long operatorId, boolean isAdmin) {
        if (definition == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程定义不能为空");
        }
        normalize(definition);
        if (!isAdmin) {
            User creator = requireUser(operatorId);
            // 教师只能在本人学院课程库中新增定义，客户端传入的 collegeId 不可信。
            definition.setCollegeId(creator.getCollegeId());
        }
        if (definition.getCreatedBy() == null) {
            definition.setCreatedBy(operatorId);
        }
        if (definition.getStatus() == null || definition.getStatus().isBlank()) {
            definition.setStatus("ACTIVE");
        }
        if (definition.getCredits() == null) {
            definition.setCredits(BigDecimal.ZERO);
        }
        if (definition.getNature() == null || definition.getNature().isBlank()) {
            definition.setNature("选修");
        }
        long sameCode = this.count(new LambdaQueryWrapper<CourseDefinition>()
                .eq(CourseDefinition::getCode, definition.getCode()));
        if (sameCode > 0) {
            throw new BusinessException(ApiCode.CONFLICT, "课程编号已存在：" + definition.getCode());
        }
        try {
            this.save(definition);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ApiCode.CONFLICT, "课程编号已存在：" + definition.getCode());
        }
        return definition;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseDefinition update(Long id, CourseDefinition patch, Long operatorId, boolean isAdmin) {
        CourseDefinition existing = this.getById(id);
        if (existing == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程定义不存在");
        }
        if (!isAdmin && !operatorId.equals(existing.getCreatedBy())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权修改该课程定义");
        }
        if (patch == null) {
            return existing;
        }
        if (patch.getCode() != null && !patch.getCode().isBlank()) {
            String code = patch.getCode().trim().toUpperCase();
            if (code.length() > CODE_MAX_LENGTH) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "课程编号不能超过 " + CODE_MAX_LENGTH + " 个字符");
            }
            long sameCode = this.count(new LambdaQueryWrapper<CourseDefinition>()
                    .eq(CourseDefinition::getCode, code)
                    .ne(CourseDefinition::getId, id));
            if (sameCode > 0) {
                throw new BusinessException(ApiCode.CONFLICT, "课程编号已存在：" + code);
            }
            existing.setCode(code);
        }
        if (patch.getName() != null && !patch.getName().isBlank()) {
            String name = patch.getName().trim();
            if (name.length() > NAME_MAX_LENGTH) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "课程名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
            }
            existing.setName(name);
        }
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getCredits() != null) existing.setCredits(patch.getCredits());
        if (patch.getHours() != null) existing.setHours(patch.getHours());
        if (patch.getNature() != null && !patch.getNature().isBlank()) existing.setNature(patch.getNature().trim());
        if (patch.getCollegeId() != null) {
            if (!isAdmin && !patch.getCollegeId().equals(existing.getCollegeId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "教师不能修改课程定义所属学院");
            }
            existing.setCollegeId(patch.getCollegeId());
        }
        if (patch.getCategory() != null) existing.setCategory(patch.getCategory());
        if (patch.getDifficulty() != null) existing.setDifficulty(patch.getDifficulty());
        if (patch.getPrerequisite() != null) existing.setPrerequisite(patch.getPrerequisite());
        if (patch.getCoverImage() != null) existing.setCoverImage(patch.getCoverImage());
        if (patch.getSyllabusUrl() != null) existing.setSyllabusUrl(patch.getSyllabusUrl());
        if (patch.getTags() != null) existing.setTags(patch.getTags());
        if (patch.getStatus() != null && !patch.getStatus().isBlank()) {
            String status = patch.getStatus().trim().toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "课程定义状态非法：" + status);
            }
            existing.setStatus(status);
        }
        this.updateById(existing);
        return this.getById(id);
    }

    @Override
    public CourseDefinition requireActive(Long id) {
        CourseDefinition definition = this.getById(id);
        if (definition == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程定义不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(definition.getStatus())) {
            throw new BusinessException(ApiCode.CONFLICT, "课程定义已停用，不能开课");
        }
        return definition;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resolveFromCourse(Course course, Long operatorId) {
        if (course == null || course.getCode() == null || course.getCode().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程编号不能为空");
        }
        String code = course.getCode().trim().toUpperCase();
        CourseDefinition existing = this.getOne(new LambdaQueryWrapper<CourseDefinition>()
                .eq(CourseDefinition::getCode, code), false);
        if (existing != null) {
            return existing.getId();
        }
        CourseDefinition definition = new CourseDefinition();
        definition.setCode(code);
        definition.setName(course.getName());
        definition.setDescription(course.getDescription());
        definition.setCredits(course.getCredits() == null ? BigDecimal.ZERO : course.getCredits());
        definition.setCollegeId(course.getCollegeId());
        definition.setCategory(course.getCategory());
        definition.setDifficulty(course.getDifficulty());
        definition.setCoverImage(course.getCoverImage());
        definition.setSyllabusUrl(course.getSyllabusUrl());
        definition.setTags(course.getTags());
        definition.setStatus("ACTIVE");
        definition.setCreatedBy(operatorId);
        try {
            this.save(definition);
        } catch (DuplicateKeyException ex) {
            CourseDefinition raced = this.getOne(new LambdaQueryWrapper<CourseDefinition>()
                    .eq(CourseDefinition::getCode, code), false);
            if (raced == null) {
                throw new BusinessException(ApiCode.CONFLICT, "课程编号已存在：" + code);
            }
            return raced.getId();
        }
        return definition.getId();
    }

    private User requireUser(Long operatorId) {
        User user = operatorId == null ? null : userService.getById(operatorId);
        if (user == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "当前用户不存在");
        }
        return user;
    }

    private static void normalize(CourseDefinition definition) {
        if (definition.getCode() == null || definition.getCode().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程编号不能为空");
        }
        String code = definition.getCode().trim().toUpperCase();
        if (code.length() > CODE_MAX_LENGTH) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程编号不能超过 " + CODE_MAX_LENGTH + " 个字符");
        }
        definition.setCode(code);
        if (definition.getName() == null || definition.getName().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程名称不能为空");
        }
        String name = definition.getName().trim();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        definition.setName(name);
    }
}
