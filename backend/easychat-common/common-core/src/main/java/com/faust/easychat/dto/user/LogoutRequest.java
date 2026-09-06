package com.faust.easychat.dto.user;

import lombok.Data;

@Data
public class LogoutRequest {

    private String refreshToken;
}
