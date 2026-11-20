package com.faust.easychat.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.faust.easychat.result.Result;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAppException_serviceUnavailable_mapsToHttp503() {
        ResponseEntity<Result<Void>> response = handler.handleAppException(
                AppException.serviceUnavailable("用户服务暂不可用"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().getCode());
        assertEquals("用户服务暂不可用", response.getBody().getMessage());
    }

    @Test
    void handleAppException_gatewayCodes_mapToCorrespondingHttpStatuses() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                handler.handleAppException(AppException.badGateway("bad gateway")).getStatusCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                handler.handleAppException(AppException.gatewayTimeout("timeout")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleAppException(AppException.userNotFound()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleAppException(AppException.badRequest("bad request")).getStatusCode());
    }
}
