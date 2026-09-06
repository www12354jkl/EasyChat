package com.faust.easychat.dto.chat;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MessageDTO {

    private String id;

    private String conversationId;

    private String senderId;

    private String senderName;

    private String senderAvatar;

    private Integer contentType;

    private String content;

    /** 1=正常 2=已撤回 */
    private Integer status;

    private LocalDateTime createdAt;
}
