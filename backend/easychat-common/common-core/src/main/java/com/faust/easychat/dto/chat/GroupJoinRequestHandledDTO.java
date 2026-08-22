package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class GroupJoinRequestHandledDTO {

    private String id;

    private String conversationId;

    private String groupName;

    /** 1=同意 2=拒绝 */
    private Integer status;
}
