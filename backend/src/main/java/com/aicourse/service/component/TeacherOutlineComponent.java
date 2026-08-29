package com.aicourse.service.component;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师 AI 建课/课程克隆协作者。
 * <p>由 {@code TeacherCourseServiceImpl} 门面委托调用。</p>
 */
@Component
public class TeacherOutlineComponent {

    private static final Logger log = LoggerFactory.getLogger(TeacherOutlineComponent.class);

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final AIService aiService;
    private final TeacherCourseSupport support;

    public TeacherOutlineComponent(CourseService courseService,
                                   CourseChapterService chapterService,
                                   AIService aiService,
                                   TeacherCourseSupport support) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.aiService = aiService;
        this.support = support;
    }

    /** AI 智能建课大纲 */
    @Transactional(readOnly = true)
    public Map<String, Object> generateCourseOutlineByAI(String topic, String category, String level,
                                                         int chapterCount, Long operatorId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (topic == null || topic.isBlank()) {
            throw new BusinessException("课程主题不能为空");
        }
        int n = (chapterCount <= 0) ? 6 : Math.min(chapterCount, 12);
        try {
            String prompt = String.format(
                    "你是一名资深课程设计师。请为高校课程《%s》设计一份课程大纲。" +
                            "课程分类：%s；难度：%s；建议章节数：%d。" +
                            "请严格只返回如下 JSON（不要包含任何解释或 Markdown 围栏）：" +
                            "{\"title\":\"课程标题\",\"description\":\"100字以内的课程简介\",\"chapters\":[" +
                            "{\"title\":\"章节标题\",\"summary\":\"章节要点\",\"suggestedTaskPoints\":[\"任务点1\",\"任务点2\"]}" +
                            "]}",
                    topic, (category == null ? "通用" : category), (level == null ? "入门" : level), n);

            String raw = aiService.chat(prompt, "你是一个专业的课程设计助手，输出必须是合法 JSON。", operatorId);
            Map<String, Object> outline = support.tryParseJson(raw);
            if (outline == null) {
                result.put("success", false);
                result.put("message", "AI 返回内容无法解析为大纲，请稍后重试");
                result.put("raw", raw);
                return result;
            }
            result.put("success", true);
            result.put("outline", outline);
            result.put("applicableCourseCategory", category);
        } catch (Exception e) {
            log.error("生成教师课程失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "AI 建课调用失败: " + e.getMessage());
        }
        return result;
    }

    /** 课程克隆（章节复制 + 父子关系修正） */
    @Transactional
    public Map<String, Object> cloneCourse(Long sourceCourseId, String newName, Long operatorId) {
        Course src = courseService.getById(sourceCourseId);
        if (src == null) {
            throw new BusinessException("源课程不存在");
        }
        if (newName == null || newName.isBlank()) {
            newName = src.getName() + "（副本）";
        }

        Course copy = new Course();
        copy.setName(newName);
        copy.setCode(src.getCode());
        copy.setDescription(src.getDescription());
        copy.setCredits(src.getCredits());
        copy.setTeacherId(operatorId);
        copy.setSemester(src.getSemester());
        copy.setYear(src.getYear());
        copy.setStatus("INACTIVE");
        copy.setApprovalStatus("DRAFT");
        copy.setEnrollmentType(src.getEnrollmentType());
        copy.setMaxStudents(src.getMaxStudents());
        copy.setCategory(src.getCategory());
        copy.setDifficulty(src.getDifficulty());
        copy.setPrice(src.getPrice());
        copy.setTags(src.getTags());
        copy.setStartDate(src.getStartDate());
        copy.setEndDate(src.getEndDate());
        copy.setCoverImage(src.getCoverImage());
        copy.setSyllabusUrl(src.getSyllabusUrl());
        courseService.save(copy);

        List<CourseChapter> chapters = chapterService.list(
                new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, sourceCourseId)
                        .orderByAsc(CourseChapter::getOrderIndex));
        int cloned = 0;
        for (CourseChapter c : chapters) {
            CourseChapter nc = new CourseChapter();
            nc.setCourseId(copy.getId());
            nc.setParentId(c.getParentId());
            nc.setTitle(c.getTitle());
            nc.setContent(c.getContent());
            nc.setOrderIndex(c.getOrderIndex());
            nc.setDuration(c.getDuration());
            nc.setIsPublished(Boolean.FALSE);
            chapterService.save(nc);
            cloned++;
        }
        if (cloned > 0) {
            List<CourseChapter> newChapters = chapterService.list(
                    new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, copy.getId())
                            .orderByAsc(CourseChapter::getOrderIndex));
            Map<String, Long> keyToNewId = new HashMap<>();
            for (CourseChapter nc : newChapters) {
                keyToNewId.put(nc.getTitle() + "#" + nc.getOrderIndex(), nc.getId());
            }
            for (CourseChapter nc : newChapters) {
                if (nc.getParentId() != null) {
                    CourseChapter srcParent = chapters.stream()
                            .filter(sc -> sc.getId().equals(nc.getParentId())).findFirst().orElse(null);
                    if (srcParent != null) {
                        Long newParentId = keyToNewId.get(srcParent.getTitle() + "#" + srcParent.getOrderIndex());
                        if (newParentId != null && !newParentId.equals(nc.getId())) {
                            nc.setParentId(newParentId);
                            chapterService.updateById(nc);
                        }
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("newCourseId", copy.getId());
        result.put("newCourseName", copy.getName());
        result.put("clonedChapters", cloned);
        return result;
    }
}
