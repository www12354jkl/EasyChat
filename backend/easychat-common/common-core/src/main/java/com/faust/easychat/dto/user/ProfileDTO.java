package com.faust.easychat.dto.user;

import lombok.Data;

@Data
public class ProfileDTO {

    private String id;

    private String nickname;

    private String email;

    private String avatar;

    private Integer gender;

    private String region;

    private String regionCode;

    private String signature;

}
