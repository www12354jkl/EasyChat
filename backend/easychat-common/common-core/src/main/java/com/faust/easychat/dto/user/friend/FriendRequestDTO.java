package com.faust.easychat.dto.user.friend;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class FriendRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String fromUserId;

    private String fromName;

    private String fromAvatar;

    private String message;

    private Integer status;

    private LocalDateTime createdAt;


}
