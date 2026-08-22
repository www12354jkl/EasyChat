package com.faust.easychat.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum ReturnCodeEnum {

    SUCCESS(200, "操作成功"),
    CREATED(201, "创建成功"),
    NO_CONTENT(204, "无内容"),
    MOVED_PERMANENTLY(301, "永久重定向"),
    FOUND(302, "临时重定向"),
    NOT_MODIFIED(304, "未修改"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "资源冲突"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的媒体类型"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    BAD_GATEWAY(502, "网关错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    GATEWAY_TIMEOUT(504, "网关超时"),

    // ── 用户服务 1xxx ──
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    EMAIL_EXISTS(1003, "邮箱已注册"),
    PASSWORD_ERROR(1004, "密码错误"),
    VERIFY_CODE_EXPIRED(1005, "验证码已过期"),
    VERIFY_CODE_ERROR(1006, "验证码错误"),
    TOKEN_EXPIRED(1007, "Token已过期"),
    TOKEN_INVALID(1008, "Token无效"),
    EMAIL_FORMAT_ERROR(1009, "邮箱格式错误"),
    PASSWORD_FORMAT_ERROR(1010, "密码格式不符合要求"),
    ACCOUNT_DISABLED(1011, "账户已被禁用"),
    LOGIN_REQUIRED(1012, "请先登录"),
    CONVERSATION_NOT_FOUND(2000, "未找到会话");

    private final int code;
    private final String message;

    ReturnCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ReturnCodeEnum getReturnCodeEnum(int code) {
        return Arrays.stream(ReturnCodeEnum.values()).filter(r -> r.getCode()==code).findFirst().orElse(null);
    }

}
