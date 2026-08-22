package com.faust.easychat.exception;

import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.faust.easychat.result.Result;
import com.faust.easychat.enums.ReturnCodeEnum;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Result<Void>> handleAppException(AppException e) {
        log.warn("业务异常: code={}, message={}", e.getReturnCode().getCode(), e.getMessage());
        HttpStatus status = mapToHttpStatus(e.getReturnCode().getCode());
        return ResponseEntity.status(status).body(Result.error(e.getReturnCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(ReturnCodeEnum.BAD_REQUEST, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return Result.error(ReturnCodeEnum.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.error(ReturnCodeEnum.BAD_REQUEST, "请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.error(ReturnCodeEnum.INTERNAL_SERVER_ERROR);
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            // token 相关：已过期(1007)/无效(1008)/未登录(1012)
            case 401, 1007, 1008, 1012 -> HttpStatus.UNAUTHORIZED;
            case 403, 1011 -> HttpStatus.FORBIDDEN;                       // 1011 账号禁用
            case 404, 1001, 2000 -> HttpStatus.NOT_FOUND;                 // 1001 用户不存在 / 2000 会话不存在
            case 408 -> HttpStatus.REQUEST_TIMEOUT;
            case 409, 1002, 1003 -> HttpStatus.CONFLICT;                  // 1002/1003 用户名/邮箱已存在
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            // 参数类业务错误：1004 密码错 / 1005/1006 验证码 / 1009/1010 格式
            case 1004, 1005, 1006, 1009, 1010 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

}