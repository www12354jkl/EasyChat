package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class SendMessageRequest {

    private String conversationId;

    private String content;

    private Integer contentType;

}
