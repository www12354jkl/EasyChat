package com.faust.easychat.exception;

import com.faust.easychat.enums.ReturnCodeEnum;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ReturnCodeEnum returnCode;

    public AppException(ReturnCodeEnum returnCode) {
        super(returnCode.getMessage());
        this.returnCode = returnCode;
    }

    public AppException(ReturnCodeEnum returnCode, String message) {
        super(message);
        this.returnCode = returnCode;
    }
    public static AppException badRequest(String message) {
        return new AppException(ReturnCodeEnum.BAD_REQUEST, message);
    }

    public static AppException unauthorized(String message) {
        return new AppException(ReturnCodeEnum.UNAUTHORIZED, message);
    }

    public static AppException forbidden(String message) {
        return new AppException(ReturnCodeEnum.FORBIDDEN, message);
    }

    public static AppException notFound(String message) {
        return new AppException(ReturnCodeEnum.NOT_FOUND, message);
    }

    public static AppException methodNotAllowed(String message) {
        return new AppException(ReturnCodeEnum.METHOD_NOT_ALLOWED, message);
    }

    public static AppException requestTimeout(String message) {
        return new AppException(ReturnCodeEnum.REQUEST_TIMEOUT, message);
    }

    public static AppException conflict(String message) {
        return new AppException(ReturnCodeEnum.CONFLICT, message);
    }

    public static AppException unsupportedMediaType(String message) {
        return new AppException(ReturnCodeEnum.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public static AppException tooManyRequests(String message) {
        return new AppException(ReturnCodeEnum.TOO_MANY_REQUESTS, message);
    }

    public static AppException internalError(String message) {
        return new AppException(ReturnCodeEnum.INTERNAL_SERVER_ERROR, message);
    }

    public static AppException badGateway(String message) {
        return new AppException(ReturnCodeEnum.BAD_GATEWAY, message);
    }

    public static AppException serviceUnavailable(String message) {
        return new AppException(ReturnCodeEnum.SERVICE_UNAVAILABLE, message);
    }

    public static AppException gatewayTimeout(String message) {
        return new AppException(ReturnCodeEnum.GATEWAY_TIMEOUT, message);
    }

    // ==================== 用户服务相关 ====================

    public static AppException userNotFound() {
        return new AppException(ReturnCodeEnum.USER_NOT_FOUND);
    }

    public static AppException usernameExists() {
        return new AppException(ReturnCodeEnum.USERNAME_EXISTS);
    }

    public static AppException emailExists() {
        return new AppException(ReturnCodeEnum.EMAIL_EXISTS);
    }

    public static AppException passwordError() {
        return new AppException(ReturnCodeEnum.PASSWORD_ERROR);
    }

    public static AppException verifyCodeExpired() {
        return new AppException(ReturnCodeEnum.VERIFY_CODE_EXPIRED);
    }

    public static AppException verifyCodeError() {
        return new AppException(ReturnCodeEnum.VERIFY_CODE_ERROR);
    }

    public static AppException tokenExpired() {
        return new AppException(ReturnCodeEnum.TOKEN_EXPIRED);
    }

    public static AppException tokenInvalid() {
        return new AppException(ReturnCodeEnum.TOKEN_INVALID);
    }

    public static AppException emailFormatError() {
        return new AppException(ReturnCodeEnum.EMAIL_FORMAT_ERROR);
    }

    public static AppException passwordFormatError() {
        return new AppException(ReturnCodeEnum.PASSWORD_FORMAT_ERROR);
    }

    public static AppException accountDisabled() {
        return new AppException(ReturnCodeEnum.ACCOUNT_DISABLED);
    }

    public static AppException loginRequired() {
        return new AppException(ReturnCodeEnum.LOGIN_REQUIRED);
    }

    public static AppException conversationNotFound() {
        return new AppException(ReturnCodeEnum.CONVERSATION_NOT_FOUND);
    }

}
