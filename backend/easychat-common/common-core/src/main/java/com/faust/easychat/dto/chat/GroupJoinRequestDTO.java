package com.faust.easychat.dto.chat;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class GroupJoinRequestDTO {

    private String id;

    private String conversationId;

    private String groupName;

    private String groupAvatar;

    private String userId;

    private String nickname;

    private String avatar;

    private String message;

    private LocalDateTime createdAt;
}
