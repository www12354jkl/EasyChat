package com.faust.easychat.dto.user;

import lombok.Data;

@Data
public class TokenRefreshResponse {

    private String accessToken;

    private String refreshToken;
}
