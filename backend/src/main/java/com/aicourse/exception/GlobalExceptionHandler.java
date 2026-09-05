package com.aicourse.exception;

import com.aicourse.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.aicourse.common.ApiCode;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import org.springframework.context.MessageSourceResolvable;

/**
 * 全局统一异常处理器
 * 拦截系统各类异常，统一封装为标准接口响应格式返回
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * 自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常[code={}]：{}", e.getCode(), e.getMessage());
        // 使用异常携带的业务错误码作为 HTTP 状态码（如 403 越权、404 不存在、400 参数错误），
        // 使客户端与网关能据此准确区分失败类型，而非一律 400。
        HttpStatus status = HttpStatus.resolve(e.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException e) {
        log.warn("资源不存在：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ApiCode.NOT_FOUND, e.getMessage()));
    }

    /**
     * 账号/密码错误（认证失败）
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        log.warn("登录认证失败：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ApiCode.UNAUTHORIZED, "账号或密码错误"));
    }

    /**
     * 权限不足（已登录但无访问权限）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("访问权限不足：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ApiCode.FORBIDDEN, "权限不足，禁止访问"));
    }

    /**
     * 请求参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String message = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败：{}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, message));
    }

    /**
     * 请求体 JSON 解析失败（格式错误）—— 返回 400 而非落入兜底 500
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, "请求格式错误"));
    }

    /**
     * Bean Validation 方法级约束违例（如 {@code @Validated} 标注的 Controller 上
     * {@code @RequestParam}/{@code @PathVariable} 的校验失败）—— 返回 400 而非 500。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数约束校验失败：{}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, message));
    }

    /**
     * Spring 6.1+ 方法级校验违例（含 {@code @Valid @RequestBody List<T>} 的容器元素校验、
     * 方法参数上的 {@code @NotBlank} 等）—— Spring 6.1 起由
     * {@link HandlerMethodValidationException} 承载，须显式转 400，否则落入兜底 500。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        // Spring 6.2 后期起 MethodValidationResult.getAllValidationResults() 已弃用移除，
        // 拆分为 getParameterValidationResults()（参数级违例，含 @Valid 容器元素）与
        // getCrossParameterValidationResults()（跨参数/方法级违例），此处合并两者提取错误消息。
        List<MessageSourceResolvable> resolvableErrors = new ArrayList<>();
        e.getParameterValidationResults()
                .forEach(r -> resolvableErrors.addAll(r.getResolvableErrors()));
        resolvableErrors.addAll(e.getCrossParameterValidationResults());
        String message = resolvableErrors.stream()
                .map(err -> {
                    try {
                        return err.getDefaultMessage();
                    } catch (Exception ex) {
                        return err.toString();
                    }
                })
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        log.warn("方法参数校验失败：{}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST,
                        message.isBlank() ? "请求参数校验失败" : message));
    }

    /**
     * 请求参数类型不匹配（路径/查询参数类型错误）—— 返回 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, "参数类型错误"));
    }

    /**
     * 必需请求参数缺失（如 {@code @RequestParam} 未提供）—— 返回 400 而非落入兜底 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("请求参数缺失：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, "缺少必需参数: " + e.getParameterName()));
    }

    /**
     * 必需 multipart 文件缺失（multipart 请求未携带 {@code @RequestParam MultipartFile}）——
     * Spring 6 对此抛 {@link MissingServletRequestPartException}（区别于普通参数缺失），返回 400。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("缺少必需上传文件：{}", e.getRequestPartName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, "缺少必需上传文件: " + e.getRequestPartName()));
    }

    /**
     * 请求方法不支持（如对仅接受 POST 的端点发 GET）：返回 405 而非兜底 500
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ApiCode.METHOD_NOT_ALLOWED, "请求方法不支持"));
    }

    /**
     * 系统未知异常（兜底处理）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception e) {
        log.error("系统未知异常", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试"));
    }

    /**
     * Sentinel 限流 / 熔断异常兜底。
     * <p>兜底意义：对任意标注 {@code @SentinelResource}（或经 SentinelWebFilter 包裹）的资源，
     * 若未显式声明 blockHandler/fallback 而将 {@link BlockException} 抛出至控制层，
     * 此处统一封装为项目标准 {@link ApiResponse}：熔断（{@link DegradeException}）→ 503，
     * 其余（流控等）→ 429。与 {@code SentinelWebFilter#writeBlocked} 的语义保持一致。</p>
     */
    @ExceptionHandler(BlockException.class)
    public ResponseEntity<ApiResponse<Void>> handleBlockException(BlockException e) {
        if (e instanceof DegradeException) {
            log.warn("Sentinel 熔断降级触发：{}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(ApiCode.SERVICE_UNAVAILABLE, "服务暂时不可用（熔断降级），请稍后重试"));
        }
        log.warn("Sentinel 限流触发：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ApiCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));
    }
}