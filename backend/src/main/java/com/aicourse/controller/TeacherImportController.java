package com.aicourse.controller;

import com.aicourse.application.teacher.TeacherImportApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.TeacherImportResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 教师端批量导入接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>题目 JSON / 题目 Excel / 章节 Excel / 知识点 Excel 四条导入路径集中在此；3E-05C 起
 * 逐条容错循环、重复判定、两遍写入（父级回填）与结果组装下移到
 * {@link TeacherImportApplicationService}，本类只留路由。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端批量导入接口", description = "题库/章节/知识点 Excel 与 JSON 批量导入")
public class TeacherImportController {

    private final TeacherImportApplicationService teacherImportApplicationService;

    public TeacherImportController(TeacherImportApplicationService teacherImportApplicationService) {
        this.teacherImportApplicationService = teacherImportApplicationService;
    }

    /** JSON 批量导入题目（兼容保留，逐题课程归属校验）。 */
    @PostMapping("/questions/import")
    @Operation(summary = "批量导入题目", description = "JSON 批量导入（兼容保留，逐题校验课程归属）；Excel(.xlsx) 批量导入请使用 /teacher/questions/import-excel")
    public ApiResponse<TeacherImportResultVO> importQuestions(@Valid @RequestBody List<Question> questions,
                                                              Authentication auth) {
        return ApiResponse.success("导入完成",
                teacherImportApplicationService.importQuestions(questions, auth));
    }

    /** 上传 Excel 批量导入题目（需 .xlsx，可选指定归属课程）。 */
    @PostMapping(value = "/questions/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入题目",
            description = "上传 .xlsx 文件批量导入题目（表头：题型/题干/选项(JSON)/正确答案/难度/分值/知识点/解析），courseId 可指定归属课程")
    public ApiResponse<TeacherImportResultVO> importQuestionsExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID（可选）") @RequestParam(required = false) Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        TeacherImportApplicationService.ImportOutcome outcome =
                teacherImportApplicationService.importQuestionsExcel(ExcelUtil.readQuestions(file), courseId, auth);
        return ApiResponse.success(outcome.hasIssues() ? ISSUES_MESSAGE : OK_MESSAGE, outcome.result());
    }

    /** 上传 Excel 批量导入章节；父级以标题引用，两遍写入。 */
    @PostMapping(value = "/chapters/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入章节",
            description = "表头：章节标题/父章节标题/排序序号/时长(分钟)/是否发布/章节内容。"
                    + "父章节以标题引用（支持二级章节）；同课程同标题视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importChaptersExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        TeacherImportApplicationService.ImportOutcome outcome =
                teacherImportApplicationService.importChaptersExcel(ExcelUtil.readChapters(file), courseId, auth);
        return ApiResponse.success(outcome.hasIssues() ? ISSUES_MESSAGE : OK_MESSAGE, outcome.result());
    }

    /** 上传 Excel 批量导入知识点；父知识点以名称引用，两遍写入。 */
    @PostMapping(value = "/knowledge-points/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入知识点",
            description = "表头：知识点名称/父知识点名称/难度/重要程度(1-10)/预计学习时长(分钟)/类型/描述。"
                    + "父知识点以名称引用；同课程同名称视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importKnowledgePointsExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        TeacherImportApplicationService.ImportOutcome outcome =
                teacherImportApplicationService.importKnowledgePointsExcel(
                        ExcelUtil.readKnowledgePoints(file), courseId, auth);
        return ApiResponse.success(outcome.hasIssues() ? ISSUES_MESSAGE : OK_MESSAGE, outcome.result());
    }

    /** 导入文件基础校验（三条 Excel 导入路径共用；文件属 Web 层关注点，留在这里）。 */
    private void checkImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
    }

    private static final String OK_MESSAGE = "导入完成";
    private static final String ISSUES_MESSAGE = "导入完成（存在跳过或失败项，详见明细）";
}
