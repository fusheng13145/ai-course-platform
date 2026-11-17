package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.ExamPaperAccessControl;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperQuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 试卷题目接口控制器。
 *
 * <p>S-01 大控制器拆分：从 {@link ExamPaperController} 抽出「题目读取 / 批量替换」
 * 两个端点，收敛题目投影与答案可见性逻辑；路由前缀仍为 {@code /exam-papers}，
 * 与主控制器的试卷 CRUD/审核端点按方法+路径区分，不产生冲突。</p>
 */
@Tag(name = "试卷题目", description = "试卷关联题目的读取与批量替换")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperQuestionController {

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ExamPaperAccessControl examPaperAccessControl;
    private final ActivityVisibilityGuard activityVisibilityGuard;

    public ExamPaperQuestionController(ExamPaperService examPaperService,
                                       ExamQuestionService examQuestionService,
                                       QuestionService questionService,
                                       ExamPaperAccessControl examPaperAccessControl,
                                       ActivityVisibilityGuard activityVisibilityGuard) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.examPaperAccessControl = examPaperAccessControl;
        this.activityVisibilityGuard = activityVisibilityGuard;
    }

    /**
     * 查询试卷关联的题目列表（学生答题 / 教师预览共用，返回体统一脱敏）。
     */
    @GetMapping("/{id}/questions")
    @Operation(summary = "查询试卷关联的题目列表")
    public ApiResponse<List<ExamPaperQuestionVO>> getQuestions(@PathVariable Long id, Authentication auth) {
        // 对象级授权：管理员放行；学生须有已通过审批的选课；教师须为该课程授课教师。
        ExamPaper paper = requirePaper(id);
        examPaperAccessControl.assertCanRead(paper, auth);
        activityVisibilityGuard.assertVisibleExamPaper(paper, auth);

        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, id);
        List<ExamQuestion> examQuestionList = examQuestionService.list(queryWrapper);
        List<ExamPaperQuestionVO> resultList = new ArrayList<>();

        // 答案可见性按角色区分：学生只看作答所需字段（去答案/解析），教师/管理员可见完整题目。
        boolean answerVisible = RoleUtil.isTeacherOrAdmin(auth);
        for (ExamQuestion examQuestion : examQuestionList) {
            Question question = questionService.getById(examQuestion.getQuestionId());
            if (question == null) {
                continue;
            }
            resultList.add(new ExamPaperQuestionVO(
                    examQuestion.getId(), examQuestion.getOrderIndex(),
                    examQuestion.getPoints(),
                    answerVisible ? question : toAnswerHidden(question)));
        }
        return ApiResponse.success(resultList);
    }

    /**
     * 批量替换试卷题目（教师/管理员权限，手动选题保存通道）。
     */
    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "批量替换试卷题目")
    public ApiResponse<List<ExamQuestion>> replaceQuestions(@PathVariable Long id,
            @Valid @RequestBody ExamQuestionBatchDTO body, Authentication auth) {
        // 归属校验：与 update/delete/publish 同口径，仅试卷所属课程的授课教师或管理员可操作
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        List<ExamQuestion> saved = examPaperService.replaceExamQuestions(id, body.getQuestions());
        return ApiResponse.success("保存成功", saved);
    }

    private ExamPaper requirePaper(Long id) {
        ExamPaper paper = examPaperService.getById(id);
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在：" + id);
        }
        return paper;
    }

    /**
     * 作答可见的题目投影：白名单保留作答所需字段，剔除标准答案与解析。
     */
    private static Question toAnswerHidden(Question q) {
        Question safe = new Question();
        safe.setId(q.getId());
        safe.setCourseId(q.getCourseId());
        safe.setType(q.getType());
        safe.setContent(q.getContent());
        safe.setOptions(q.getOptions());
        safe.setDifficulty(q.getDifficulty());
        safe.setScore(q.getScore());
        safe.setUsageCount(q.getUsageCount());
        safe.setCorrectRate(q.getCorrectRate());
        return safe;
    }
}
