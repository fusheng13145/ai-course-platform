package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.CourseChapter;
import com.aicourse.service.CourseChapterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class CourseChapterController {
    private final CourseChapterService chapterService;

    public CourseChapterController(CourseChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/chapters/course/{courseId}")
    public ApiResponse<List<CourseChapter>> getByCourse(@PathVariable Long courseId) {
        List<CourseChapter> all = chapterService.list(
            new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
        List<CourseChapter> tree = all.stream()
            .filter(c -> c.getParentId() == null)
            .peek(c -> c.setContent(null))
            .collect(Collectors.toList());
        return ApiResponse.success(tree);
    }

    @GetMapping("/courses/{courseId}/chapters")
    public ApiResponse<List<CourseChapter>> getByCourseAlt(@PathVariable Long courseId) {
        return getByCourse(courseId);
    }

    @GetMapping("/chapters/{id}")
    public ApiResponse<CourseChapter> getById(@PathVariable Long id) {
        return ApiResponse.success(chapterService.getById(id));
    }

    @PostMapping("/chapters")
    public ApiResponse<CourseChapter> create(@RequestBody CourseChapter chapter) {
        chapterService.save(chapter);
        return ApiResponse.success("创建成功", chapter);
    }

    @PutMapping("/chapters/{id}")
    public ApiResponse<CourseChapter> update(@PathVariable Long id, @RequestBody CourseChapter chapter) {
        chapter.setId(id);
        chapterService.updateById(chapter);
        return ApiResponse.success("更新成功", chapterService.getById(id));
    }

    @DeleteMapping("/chapters/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        chapterService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }
}
