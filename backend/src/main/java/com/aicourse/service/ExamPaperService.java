package com.aicourse.service;

import com.aicourse.entity.ExamPaper;
import com.aicourse.dto.AutoComposeDTO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;


/**
 * 考试试卷接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface ExamPaperService extends IService<ExamPaper> {

    /**
     * 获取试卷完整详情（含题目列表）
     * @param paperId 试卷ID
     * @return 试卷详情（包含题目信息）
     */
    Map<String, Object> getExamPaperDetail(Long paperId);

    /**
     * 发布试卷
     * @param paperId 试卷ID
     */
    void publishExamPaper(Long paperId);

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
    Map<String, Object> getPaperStatistics(Long paperId);

    /**
     * 自动组卷：按题型数量和难度比例从题库随机抽取题目生成试卷
     * @param dto 组卷参数（课程ID、题型规则、难度比例、分值规则等）
     * @param creatorId 创建人ID
     * @return 生成的试卷
     */
    ExamPaper autoComposeExamPaper(AutoComposeDTO dto, Long creatorId);

}
