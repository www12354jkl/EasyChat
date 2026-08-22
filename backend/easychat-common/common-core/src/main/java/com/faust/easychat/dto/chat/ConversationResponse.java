package com.faust.easychat.dto.chat;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ConversationResponse {

    private String id;

    private String name;

    private String avatar;

    // 1单聊 2群聊 3群邀请
    private Integer type;

    private Boolean isGroup;

    private Boolean isGroupInvite;

    // 单聊对方userId
    private String friendId;

    /** 群邀请：目标群 id */
    private String groupId;

    /** 群邀请：邀请记录 id */
    private String inviteId;

    /** 群邀请：目标群是否需验证 */
    private Integer joinVerify;

    private String inviterName;

    private String inviterAvatar;

    /** pending | waiting | joined | expired */
    private String inviteStatus;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    // 暂填0
    private Integer unread;

    // 群人数
    private Integer total;


}
