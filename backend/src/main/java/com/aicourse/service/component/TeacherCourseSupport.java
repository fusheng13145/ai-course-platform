package com.aicourse.service.component;

import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师课程共享支持协作者：数值取整、教师课程 ID 列表、学生姓名批量解析、AI JSON 解析。
 * <p>由 {@code TeacherCourseServiceImpl} 门面下属组件共享调用。</p>
 */
@Component
public class TeacherCourseSupport {

    private static final Logger log = LoggerFactory.getLogger(TeacherCourseSupport.class);

    private final CourseService courseService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TeacherCourseSupport(CourseService courseService, UserService userService) {
        this.courseService = courseService;
        this.userService = userService;
    }

    /** 保留两位小数 */
    public double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 教师名下课程 ID 列表 */
    public List<Long> courseIdsOfTeacher(Long teacherId) {
        return courseService.list(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId))
                .stream().map(Course::getId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 批量解析学生姓名，避免 N+1 */
    public Map<Long, String> userNameMap(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userService.list(new LambdaQueryWrapper<User>().in(User::getId, studentIds));
        Map<Long, String> map = new HashMap<>(users.size());
        for (User u : users) {
            String name = (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername();
            map.put(u.getId(), name);
        }
        return map;
    }

    /** 解析 AI 返回 JSON（去除 Markdown 围栏），失败返回 null */
    public Map<String, Object> tryParseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }
        try {
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception ex) {
            log.error("AI JSON 解析失败: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
