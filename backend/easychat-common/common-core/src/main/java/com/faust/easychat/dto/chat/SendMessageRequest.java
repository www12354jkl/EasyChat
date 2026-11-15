package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class SendMessageRequest {

    private String conversationId;

    private String content;

    private Integer contentType;

    /** 同一发送者重复提交时只保留第一条 */
    private String clientMsgId;

}
