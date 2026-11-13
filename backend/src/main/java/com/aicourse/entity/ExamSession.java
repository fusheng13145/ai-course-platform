package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试作答记录实体
 * 记录学生每一次试卷作答的全过程信息、成绩、作答环境等数据
 */
@TableName("exam_sessions")
@Data
public class ExamSession {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属试卷ID
     */
    private Long examPaperId;

    /**
     * 作答学生ID
     */
    private Long studentId;

    /**
     * 作答状态（进行中/已提交/已超时等）
     */
    private String status;

    /**
     * 作答次数（对应试卷重考次数）
     */
    private Integer attemptNumber;

    /**
     * 考试开始时间
     */
    private LocalDateTime startTime;

    /**
     * 提交试卷时间
     */
    private LocalDateTime submitTime;

    /**
     * 作答耗时（单位：秒/分钟）
     */
    private Integer timeSpent;

    /**
     * 试卷总分
     */
    private BigDecimal totalScore;

    /**
     * 得分占比/百分制成绩
     */
    private BigDecimal percentage;

    /**
     * 整卷答案集合（JSON字符串存储）
     */
    private String answers;

    /**
     * 每题得分集合（JSON字符串存储）
     */
    private String questionScores;

    /**
     * 作弊检测结果（JSON字符串存储，独立于 answers，绝不直接改写作答答案）
     * 结构：{"detectedAt":"...","flagCount":n,"details":["...","..."],"riskLevel":"LOW|MEDIUM|HIGH"}
     */
    private String cheatingFlags;

    /**
     * 题目乱序：按展示顺序排列的 questionId 数组（JSON），每位学生独立随机生成，用于防作弊
     */
    private String questionOrder;

    /**
     * 选项乱序映射：{ questionId : [原题选项下标...] }，下标为展示位、值为原题选项下标（JSON）
     * 阅卷时依据此映射把学生提交的"展示位标签"还原为"原题标签"再比对正确答案
     */
    private String optionOrders;

    /**
     * 作答客户端IP地址
     */
    private String ipAddress;

    /**
     * 客户端浏览器/设备标识
     */
    private String userAgent;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 开考时学生所在教学班快照（WP7，V16）。
     * 由 {@code ExamSessionLifecycleComponent} 在创建会话时按选课成员关系解析，
     * 从成员关系取值而不信任请求体；转班后历史会话不漂移。
     */
    private Long teachingClassId;

}