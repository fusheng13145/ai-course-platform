package com.aicourse.architecture;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 文档覆盖守护：每个 Web 控制器都必须有 {@link Tag}，否则 Swagger UI 会把端点
 * 散落到默认分组，接口说明无法按业务域聚合。
 *
 * <p>断言同时检查扫描到的控制器数量，防止包名或扫描范围写错时「零控制器也全绿」。</p>
 */
class OpenApiTagCoverageTest {

    @Test
    void everyControllerHasOpenApiTag() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        var controllers = scanner.findCandidateComponents("com.aicourse.controller");
        assertThat(controllers).as("控制器扫描结果").hasSizeGreaterThan(50);

        for (var bean : controllers) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            assertThat(type.getAnnotation(Tag.class))
                    .as("%s 缺少 @Tag，Swagger UI 无法按业务域聚合", type.getSimpleName())
                    .isNotNull();
        }
    }
}
