package com.aicourse.exception;

import com.aicourse.common.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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
     * 请求路径不存在（未匹配任何 Handler，落到静态资源处理器后抛出的异常）。
     *
     * <p>Spring Boot 3.2+ 对未匹配到 Controller 的路径会抛 {@code NoResourceFoundException}。
     * 若不单独处理，它会落入兜底分支被记为「系统未知异常」并返回 500 ——
     * 使「接口路径写错」表现为「服务端故障」，误导排查方向（实测 {@code GET /api/courses/1/tasks}
     * 即为此例，正确端点应为 {@code /api/tasks/course/1}）。此处按语义返回 404。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求路径不存在：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ApiCode.NOT_FOUND, "请求的资源不存在"));
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
     * 请求整体不是 multipart（如给分片端点发普通 POST）——Spring 在解析
     * {@code @RequestParam MultipartFile} 前抛 {@link MultipartException}。
     *
     * <p>D1-2 批次 2 端到端实测：分片端点由「恒 501」改为真实实现后，这类请求
     * 落入 {@link Exception} 兜底记为「系统未知异常」并返回 500，把「客户端请求体裁
     * 写错」表现成「服务端故障」。按语义返回 400。</p>
     *
     * <p>{@link org.springframework.web.multipart.MaxUploadSizeExceededException} 是 {@code MultipartException}
     * 的子类（对 spring-web 6.1.13 的 jar 用 {@code javap} 取证），Spring 选取最具体的 handler，
     * 故超大文件仍走上方 413 的专用分支。{@link MissingServletRequestPartException} 则<b>不是</b>
     * 其子类——它继承 {@code jakarta.servlet.ServletException}，两条分支互不遮蔽。</p>
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotMultipart(MultipartException e) {
        log.warn("请求不是 multipart 或未携带文件分片：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST,
                        "分片上传须为 multipart/form-data 且携带 file 字段"));
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
     * 唯一约束冲突（如课程编号 code 重复、同一试卷重复加入同一题目）。
     *
     * <p>修复（2026-09-14）：原先 {@code DuplicateKeyException} 无语义化分支，落入兜底 500
     * 「服务器内部错误」，客户端无法区分「业务冲突」与「服务故障」。此处转为 409，
     * 并把底层约束名保留在日志中（响应体不回显 SQL 细节，避免信息泄露）。</p>
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ApiCode.CONFLICT, "数据已存在（唯一约束冲突），请检查编号/名称等唯一字段"));
    }

    /**
     * 其它数据完整性冲突（外键约束、非空约束等）：返回 400 并给出可读提示。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ApiCode.BAD_REQUEST, "数据完整性校验失败，请检查关联数据是否存在"));
    }

    /**
     * 上传文件超限：返回 413 而非兜底 500。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ApiCode.PAYLOAD_TOO_LARGE, "上传文件超过大小限制"));
    }

    /**
     * 系统未知异常（兜底处理）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception e) {
        // 容器层（Tomcat multipart 解析）在 Spring 包装之前直接抛出的超限异常，
        // 会以 IllegalStateException 形式出现；沿因果链识别后按 413 返回，
        // 避免「文件过大」被误报为「服务器内部错误」。
        if (hasCause(e, "SizeLimitExceededException")) {
            log.warn("上传文件超过容器限制：{}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(ApiCode.PAYLOAD_TOO_LARGE, "上传文件超过大小限制"));
        }
        log.error("系统未知异常", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ApiCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试"));
    }

    /** 判断异常因果链中是否包含指定简单类名的异常。 */
    private boolean hasCause(Throwable e, String simpleName) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 20) {
            if (cur.getClass().getSimpleName().equals(simpleName)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
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