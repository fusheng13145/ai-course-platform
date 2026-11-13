package com.aicourse.service;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


/**
 * 考试试卷接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface ExamPaperService extends IService<ExamPaper> {

    /**
     * 获取试卷完整详情（含题目列表）
     * @param paperId 试卷ID
     * @return 试卷详情（包含题目信息）
     */
    ExamPaperDetailVO getExamPaperDetail(Long paperId);

    /**
     * 发布试卷（管理员直接发布：DRAFT/REJECTED/ARCHIVED → PUBLISHED）。
     * @param paperId 试卷ID
     */
    void publishExamPaper(Long paperId);

    /**
     * 提交审核（教师）：DRAFT/REJECTED → PENDING_REVIEW，学生仍不可见。
     * @param paperId 试卷ID
     */
    void submitExamPaperForReview(Long paperId);

    /**
     * 审核通过（管理员）：PENDING_REVIEW → PUBLISHED，并向选课学生推送通知。
     * @param paperId 试卷ID
     */
    void approveExamPaper(Long paperId);

    /**
     * 审核驳回（管理员）：PENDING_REVIEW → REJECTED，并向创建人发送通知。
     * @param paperId 试卷ID
     * @param reason 驳回原因（仅通知与日志，不落库）
     */
    void rejectExamPaper(Long paperId, String reason);

    /**
     * 复制试卷
     * @param sourcePaperId 源试卷ID
     * @param newTitle 新试卷标题
     * @return 复制后的新试卷
     */
    ExamPaper copyExamPaper(Long sourcePaperId, String newTitle);

    /**
     * 获取试卷统计（题型分布、分值分布）
     * @param paperId 试卷ID
     * @return 统计数据
     */
    ExamPaperStatsVO getPaperStatistics(Long paperId);

    /**
     * 自动组卷：按题型数量和难度比例从题库随机抽取题目生成试卷
     * @param dto 组卷参数（课程ID、题型规则、难度比例、分值规则等）
     * @param creatorId 创建人ID
     * @return 生成的试卷
     */
    ExamPaper autoComposeExamPaper(AutoComposeDTO dto, Long creatorId);

    /**
     * 批量替换试卷题目（手动选题保存通道）。
     * <p>语义为批量替换：先清空该卷原有关联行，再按清单写入；points/orderIndex 缺省时自动补齐，
     * 并回填卷面 questionCount / totalScore（与自动组卷落库口径一致）。</p>
     * @param paperId 试卷ID
     * @param questions 题目清单（questionId 必填，points/orderIndex 可缺省）
     * @return 写入后的关联行列表
     */
    List<ExamQuestion> replaceExamQuestions(Long paperId, List<ExamQuestionBatchDTO.ExamQuestionItem> questions);

}
