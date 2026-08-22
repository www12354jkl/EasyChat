package com.faust.easychat.dto.user;

import lombok.Data;

@Data
public class UserInfoDTO {

    private String nickname;

    private String avatar;

    private Integer gender;

    private String region;

    private String signature;
}
