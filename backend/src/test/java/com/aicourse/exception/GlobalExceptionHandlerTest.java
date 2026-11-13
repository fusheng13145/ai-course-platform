package com.aicourse.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;

/**
 * 全局异常处理器的逐分支单测（2026-11-06 覆盖率批补件）。
 *
 * <p>判据本体见 docs/后端待办项.md I-04：GlobalExceptionHandler 是「异常 → 标准信封」的
 * 唯一收口，此前行覆盖 47%，任何一个新异常类型接错状态码都不会被现有测试发现。
 * 本测试按处理器逐个断言 HTTP 状态码与业务码/文案，覆盖兜底里的因果链识别
 * （hasCause）与 Sentinel 限流/熔断两条分支。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertStatus(ResponseEntity<ApiResponse<Void>> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("业务异常：code 可解析为 HTTP 状态时按其返回")
    void businessExceptionWithResolvableCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(ApiCode.FORBIDDEN, "无权访问"));
        assertStatus(response, HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("无权访问");
    }

    @Test
    @DisplayName("业务异常：code 无法解析为 HTTP 状态时回落 400 且保留业务码")
    void businessExceptionWithUnresolvableCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(70001, "自定义业务码"));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(70001);
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404")
    void resourceNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("课程不存在"));
        assertStatus(response, HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("课程不存在");
    }

    @Test
    @DisplayName("NoResourceFoundException → 404（路径错误不冒充 500）")
    void noResourceFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNoResourceFound(new NoResourceFoundException(null, "/api/not-exist"));
        assertStatus(response, HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    @DisplayName("BadCredentialsException → 401 且文案固定")
    void badCredentials() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadCredentials(new BadCredentialsException("bad"));
        assertStatus(response, HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("账号或密码错误");
    }

    @Test
    @DisplayName("AccessDeniedException → 403")
    void accessDenied() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertStatus(response, HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400，字段错误消息拼接")
    void methodArgumentNotValid() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                SampleHandler.class.getDeclaredMethod("handle", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "sample");
        bindingResult.addError(new FieldError("sample", "name", "不能为空"));
        bindingResult.addError(new FieldError("sample", "age", "必须为正数"));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleValidationException(new MethodArgumentNotValidException(parameter, bindingResult));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("不能为空").contains("必须为正数");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException → 400")
    void notReadable() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求格式错误");
    }

    @Test
    @DisplayName("ConstraintViolationException → 400，属性路径 + 消息拼接")
    void constraintViolation() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("必须大于0");
        when(violation.getConstraintDescriptor()).thenReturn(mock(ConstraintDescriptor.class));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("必须大于0");
    }

    @Test
    @DisplayName("HandlerMethodValidationException → 400，参数级与跨参数违例合并去重")
    void handlerMethodValidation() {
        MethodValidationResult result =
                mock(org.springframework.validation.method.MethodValidationResult.class);
        ParameterValidationResult parameterResult = mock(ParameterValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of(parameterResult));
        // DefaultMessageSourceResolvable(String) 设的是 code 而非 defaultMessage，
        // 处理器只读 defaultMessage，须用 (codes, defaultMessage) 构造
        when(parameterResult.getResolvableErrors()).thenReturn(List.of(
                new DefaultMessageSourceResolvable(new String[] {"name"}, "字段必填"),
                new DefaultMessageSourceResolvable(new String[] {"name"}, "字段必填")));
        when(result.getCrossParameterValidationResults()).thenReturn(List.of());

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodValidation(new HandlerMethodValidationException(result));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("字段必填");
    }

    @Test
    @DisplayName("HandlerMethodValidationException：无可用消息时用兜底文案")
    void handlerMethodValidationBlankMessage() {
        MethodValidationResult result =
                mock(org.springframework.validation.method.MethodValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of());
        when(result.getCrossParameterValidationResults()).thenReturn(List.of());

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodValidation(new HandlerMethodValidationException(result));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("请求参数校验失败");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException → 400")
    void typeMismatch() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null,
                        new NumberFormatException("abc")));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("参数类型错误");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException → 400 并点名参数")
    void missingParam() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingParam(new MissingServletRequestParameterException("courseId", "Long"));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("缺少必需参数: courseId");
    }

    @Test
    @DisplayName("MissingServletRequestPartException → 400 并点名分片")
    void missingPart() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingPart(new MissingServletRequestPartException("file"));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("缺少必需上传文件: file");
    }

    @Test
    @DisplayName("MultipartException → 400（分片端点收到非 multipart）")
    void notMultipart() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotMultipart(new MultipartException("not multipart"));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("multipart/form-data");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException → 405")
    void methodNotSupported() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE"));
        assertStatus(response, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("DuplicateKeyException → 409（业务冲突不冒充 500）")
    void duplicateKey() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDuplicateKey(new DuplicateKeyException("uk_courses_code"));
        assertStatus(response, HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.CONFLICT);
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 400")
    void dataIntegrity() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("fk"));
        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("数据完整性");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException → 413")
    void uploadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(50L));
        assertStatus(response, HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("兜底：未知异常 → 500")
    void unknownException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnknownException(new RuntimeException("boom"));
        assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.INTERNAL_SERVER_ERROR);
    }

    /** 与 tomcat 容器异常同简单名的测试桩：仅用于验证 hasCause 按简单类名匹配因果链。 */
    private static class SizeLimitExceededException extends RuntimeException {
        SizeLimitExceededException(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("兜底：因果链含容器 SizeLimitExceededException 时识别为 413")
    void unknownExceptionWithContainerSizeLimitCause() {
        RuntimeException wrapped = new IllegalStateException("processing failed",
                new SizeLimitExceededException("too large"));
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknownException(wrapped);
        assertStatus(response, HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("上传文件超过大小限制");
    }

    @Test
    @DisplayName("Sentinel 流控 → 429")
    void sentinelFlow() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBlockException(new FlowException("limited"));
        assertStatus(response, HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Sentinel 熔断 → 503")
    void sentinelDegrade() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBlockException(new DegradeException("degraded"));
        assertStatus(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiCode.SERVICE_UNAVAILABLE);
    }

    /** 仅供 MethodArgumentNotValidException 构造使用的样例处理方法。 */
    private static class SampleHandler {
        @SuppressWarnings("unused")
        public void handle(String name) {
            // no-op
        }
    }
}
