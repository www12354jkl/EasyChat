package com.faust.easychat.dto.user.friend;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class FriendRequestDTO {

    private String id;

    private String fromUserId;

    private String fromName;

    private String fromAvatar;

    private String message;

    private Integer status;

    private LocalDateTime createdAt;


}
