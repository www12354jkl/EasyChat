package com.faust.easychat.dto.user;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private String userId;
        private String email;
        private String nickname;
        private String avatar;
    }
}
