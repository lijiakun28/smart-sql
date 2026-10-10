package com.smartsql.config;

import com.smartsql.dto.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验异常（@Pattern 等）→ HTTP 400
     * 补充：校验失败必须返回 400 让前端明确感知，
     * 而不是落入通用 Exception 处理器变成 500
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    /**
     * 业务侧主动抛出的参数/状态异常 → 消息是【有意】给用户看的，
     * 例如 "会话不存在或已删除"、"问题内容不能为空"，必须原样返回。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }

    /**
     * 兜底：未知异常一律不回吐 e.getMessage()
     * —— 内部异常可能携带文件路径、SQL 片段、连接串等信息。
     * 真实异常只进服务端日志。
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("服务器异常", e);
        return Result.fail("服务器内部错误，请稍后重试");
    }
}
