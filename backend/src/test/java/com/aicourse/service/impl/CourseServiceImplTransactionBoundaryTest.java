package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程写入路径的事务边界守卫（阶段3 缺陷修复 S2-D02）。
 *
 * <p>课程落库后会联动同步默认教学班（{@code CourseDefaultTeachingClassSync} 内部 3 次写库），
 * 因此 save/updateById 必须是事务入口。用例用反射守注解，避免"有人后来把注解删掉而没人发现"。
 */
class CourseServiceImplTransactionBoundaryTest {

    @Test
    @DisplayName("save 必须带 @Transactional：课程落库与默认教学班同步同成同败")
    void save_isTransactionalBoundary() throws Exception {
        Method save = CourseServiceImpl.class.getMethod("save", Course.class);
        assertTransactional(save, "save");
    }

    @Test
    @DisplayName("updateById 必须带 @Transactional：联动更新默认教学班不得留中间态")
    void updateById_isTransactionalBoundary() throws Exception {
        Method updateById = CourseServiceImpl.class.getMethod("updateById", Course.class);
        assertTransactional(updateById, "updateById");
    }

    private static void assertTransactional(Method method, String name) {
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx, name + " 必须带 @Transactional，否则联动写库失败会留下不一致数据");
        assertTrue(Arrays.asList(tx.rollbackFor()).contains(Exception.class),
                name + " 的 rollbackFor 必须包含 Exception.class，受检异常也要回滚");
    }
}
