package com.faust.easychat.dto.chat;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ConversationUpdateDTO {

    private String id;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    private Integer total;
}
