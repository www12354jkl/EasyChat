package com.faust.easychat.api.fallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;

class UserQueryServiceFallbackTest {

    private final UserQueryServiceFallback fallback = new UserQueryServiceFallback();

    @Test
    void getUserById_throwsServiceUnavailable() {
        AppException ex = assertThrows(AppException.class, () -> fallback.getUserById("user-1"));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void batchGetUsers_throwsServiceUnavailable() {
        AppException ex = assertThrows(AppException.class,
                () -> fallback.batchGetUsers(List.of("user-1", "user-2")));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void isFriend_throwsServiceUnavailable() {
        AppException ex = assertThrows(AppException.class, () -> fallback.isFriend("user-1", "user-2"));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }
}
