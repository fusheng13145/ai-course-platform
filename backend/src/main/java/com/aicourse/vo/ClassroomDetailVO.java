package com.aicourse.vo;

import com.aicourse.entity.Classroom;
import lombok.Data;

/**
 * 班级详情出参（班级实体 + 学生数 + 容量上限）。
 * 字段名与历史 Map 键一致（classroom / studentCount / maxStudents）。
 * 注：原 Map 无条件 put 此三键（恒含），故不标 {@code @JsonInclude(NON_NULL)}，
 * 采用默认 INCLUDE.ALWAYS，精确复刻「三键恒存在」语义，保证前端零改动。
 */
@Data
public class ClassroomDetailVO {

    private Classroom classroom;
    private Integer studentCount;
    private Integer maxStudents;

    public ClassroomDetailVO() {
    }

    public ClassroomDetailVO(Classroom classroom, Integer studentCount, Integer maxStudents) {
        this.classroom = classroom;
        this.studentCount = studentCount;
        this.maxStudents = maxStudents;
    }
}
