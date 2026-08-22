package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class AcceptGroupInviteResponse {

    /** joined | pending */
    private String status;

    private String groupConversationId;
}
