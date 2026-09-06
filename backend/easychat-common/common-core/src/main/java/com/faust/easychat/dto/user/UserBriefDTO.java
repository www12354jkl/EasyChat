package com.faust.easychat.dto.user;

import java.io.Serializable;

import lombok.Data;

@Data
public class UserBriefDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;

    private String nickname;

    private String avatar;

    private String signature;

}
