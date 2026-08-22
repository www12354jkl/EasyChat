package com.faust.easychat.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faust.easychat.enums.ReturnCodeEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain=true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(ReturnCodeEnum.SUCCESS.getCode())
                .setMessage(ReturnCodeEnum.SUCCESS.getMessage())
                .setData(data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(ReturnCodeEnum returnCodeEnum) {
        return new Result<T>()
                .setCode(returnCodeEnum.getCode())
                .setMessage(returnCodeEnum.getMessage());
    }

    public static <T> Result<T> error(ReturnCodeEnum returnCodeEnum, String message) {
        return new Result<T>()
                .setCode(returnCodeEnum.getCode())
                .setMessage(message);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<T>()
                .setCode(code)
                .setMessage(message);
    }

}

